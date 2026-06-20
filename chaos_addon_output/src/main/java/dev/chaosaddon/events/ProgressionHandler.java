package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Progression System: 3 tiers per origin based on milestones.
 * Tier 1: 10 points (kills/events/crafts depending on origin)
 * Tier 2: 50 points
 * Tier 3: 200 points (elite: permanent +2❤ max HP)
 *
 * Points are tracked in persistentData as "chaos_prog_points".
 * Tier is stored as "chaos_prog_tier" (0, 1, 2, 3).
 */
public class ProgressionHandler {

    private static final String POINTS_KEY = "chaos_prog_points";
    private static final String TIER_KEY = "chaos_prog_tier";

    private static final ResourceLocation PROG_HP_MOD =
        ResourceLocation.fromNamespaceAndPath("chaos_addon", "progression_hp_bonus");

    private static final int[] TIER_THRESHOLDS = {0, 10, 50, 200};

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity killed = event.getEntity();
        if (killed instanceof ServerPlayer) return;

        // Award points based on killed entity difficulty
        float maxHp = killed.getMaxHealth();
        int points = maxHp >= 40 ? 5 : maxHp >= 20 ? 2 : 1;

        // Bonus points for killing origin hunters
        if (killed.getTags().contains("chaos_origin_hunter")) points += 20;

        addProgressionPoints(player, points);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 200 != 0) return; // check every 10 seconds

        // Award points for being alive with active buffs (passive play)
        int tier = getCurrentTier(player);
        if (tier > 0) {
            addProgressionPoints(player, 0); // check for tier changes only, no passive points
            applyTierBenefits(player, tier);
        }
    }

    public static void addProgressionPoints(ServerPlayer player, int amount) {
        int current = player.getPersistentData().getInt(POINTS_KEY) + amount;
        player.getPersistentData().putInt(POINTS_KEY, current);

        int oldTier = getCurrentTier(player);
        int newTier = computeTier(current);

        if (newTier > oldTier) {
            player.getPersistentData().putInt(TIER_KEY, newTier);
            onTierUp(player, newTier);
            applyTierBenefits(player, newTier);
        }
    }

    public static int getCurrentTier(ServerPlayer player) {
        return player.getPersistentData().getInt(TIER_KEY);
    }

    public static int getCurrentPoints(ServerPlayer player) {
        return player.getPersistentData().getInt(POINTS_KEY);
    }

    private static int computeTier(int points) {
        if (points >= TIER_THRESHOLDS[3]) return 3;
        if (points >= TIER_THRESHOLDS[2]) return 2;
        if (points >= TIER_THRESHOLDS[1]) return 1;
        return 0;
    }

    private static void onTierUp(ServerPlayer player, int tier) {
        String tierName = switch (tier) {
            case 1 -> "§eI — Пробуждение";
            case 2 -> "§6II — Восхождение";
            case 3 -> "§c§lIII — ЭЛИТА";
            default -> "?";
        };
        String benefit = switch (tier) {
            case 1 -> "+10% скорости передвижения";
            case 2 -> "+2❤ макс. HP";
            case 3 -> "+4❤ макс. HP, постоянная Удача I";
            default -> "";
        };

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                player.getX(), player.getY() + 2.0, player.getZ(), 5, 0, 0, 0, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, tier == 3 ? 0.5f : 1.0f);
        }

        player.sendSystemMessage(Component.literal(
            "§a★ ПРОГРЕСС: Тир " + tierName + "!")
            .withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(
            "§7Получено: §f" + benefit));
    }

    private static void applyTierBenefits(ServerPlayer player, int tier) {
        if (tier <= 0) return;

        // Tier 1: +10% movement speed
        if (tier >= 1) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 220, 0, true, false));
        }

        // Tier 2+: +2 hearts max HP
        var hpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.removeModifier(PROG_HP_MOD);
            double bonus = tier >= 3 ? 8.0 : (tier >= 2 ? 4.0 : 0.0); // 2❤ = 4 attr, 4❤ = 8 attr
            if (bonus > 0) {
                hpAttr.addTransientModifier(new AttributeModifier(PROG_HP_MOD, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // Tier 3: Luck I permanent
        if (tier >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 220, 0, true, false));
        }
    }
}
