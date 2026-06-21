package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import dev.chaosaddon.util.SeismicSenseHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Dimension Judge passives:
 * - all_seeing_eye:    ping nearby players + hostile mobs showing direction
 * - no_totems:         remove totems of undying from inventory each tick
 * - no_regen_potion:   block Regeneration effect via tick-strip
 * - higher_judgment:   track how many times each entity attacked the player
 */
public class DimensionJudgeHandler {

    /**
     * Tracks how many times each entity (by UUID) attacked each player (by UUID).
     * Used by chaos_addon_higher_judgment command to calculate proportional damage.
     * Thread-safe; entries are cleared after Высший Суд fires.
     */
    public static final Map<UUID, Map<UUID, Integer>> ATTACKER_COUNTS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // ── all_seeing_eye: Всевидящее Oko — ping nearby entities every 3s ──
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        if (OriginHelper.hasPower(player, "chaos_addon:dimension_judge/all_seeing_eye")
                && player.tickCount % cfg.judgeAllSeeingEyeInterval == 0) {
            SeismicSenseHelper.pingNearbyEntities(player, level, cfg.judgeAllSeeingEyeRadius,
                e -> e instanceof Mob mob && mob.getTarget() != null
                    || (e instanceof Player p && p != player),
                "§4⚖ Всевидящее Oko: ", ChatFormatting.DARK_RED);

            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(cfg.judgeAllSeeingEyeNearby),
                e -> e != player && e.isAlive());
            if (!nearby.isEmpty()) {
                LivingEntity nearest = nearby.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                    .orElse(null);
                if (nearest != null) {
                    player.displayClientMessage(
                        Component.literal(
                            "§4⚖ Oko: §c" + nearest.getType().getDescriptionId()
                            + " §7| ❤ " + String.format("%.1f", nearest.getHealth())
                            + "/" + String.format("%.1f", nearest.getMaxHealth()))
                            .withStyle(ChatFormatting.DARK_RED), true);
                }
            }
        }

        // ── no_totems: remove totems from both hands every second ──
        if (OriginHelper.hasPower(player, "chaos_addon:dimension_judge/no_totems")
                && player.tickCount % cfg.judgeAllSeeingEyeInterval == 0) {
            removeItemFromPlayer(player, level, Items.TOTEM_OF_UNDYING,
                "§4⚖ Судья не нуждается в тотемах — предмет выброшен!");
        }

        // ── no_regen_potion: strip Regeneration effect every 5 ticks ──
        if (player.tickCount % 5 == 0) {
            applyNoRegenPotion(player);
        }
    }

    /**
     * Track incoming damage for higher_judgment: count how many times each entity
     * has attacked this player. Only fires for players who have the higher_judgment power.
     */
    @SubscribeEvent
    public static void trackIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/higher_judgment")) return;

        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == player) return;

        ATTACKER_COUNTS
            .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
            .merge(attacker.getUUID(), 1, Integer::sum);
    }

    private static void removeItemFromPlayer(ServerPlayer player, ServerLevel level,
            net.minecraft.world.item.Item item, String msg) {
        boolean found = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                    player.getX(), player.getY() + 0.5, player.getZ(), stack.copy()));
                inv.setItem(i, ItemStack.EMPTY);
                found = true;
            }
        }
        if (found) {
            player.sendSystemMessage(Component.literal(msg));
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLASH,
                    player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void applyNoRegenPotion(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/no_regen_potion")) return;
        if (player.hasEffect(MobEffects.REGENERATION)) {
            player.removeEffect(MobEffects.REGENERATION);
            player.displayClientMessage(
                Component.literal("§4⚖ Регенерация запрещена Судьёй.").withStyle(ChatFormatting.DARK_RED), true);
        }
    }
}
