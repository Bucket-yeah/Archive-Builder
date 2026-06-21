package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.ParasiteData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Handles the Parasitic Mind's infection mechanics:
 *  - Infected entities glow green and follow the player
 *  - When all infected die simultaneously → withdrawal damage
 *  - Player cannot deal direct damage (handled in JSON via attribute)
 *  - telepathic_network: actionbar direction to nearest infected host
 *  - parasite_sense: HP display of nearest entity
 *  - no_potions: block potion consumption
 */
public class ParasiteHandler {

    private static final Map<UUID, Long> INFECTION_EXPIRY = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")) return;

        ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);
        long currentTick = player.level().getGameTime();

        // Expire old infections
        data.infectedUUIDs().removeIf(uuid -> {
            Long expiry = INFECTION_EXPIRY.get(uuid);
            if (expiry == null || currentTick > expiry) {
                INFECTION_EXPIRY.remove(uuid);
                var entity = level.getEntity(uuid);
                if (entity != null) {
                    entity.setGlowingTag(false);
                    if (entity instanceof Mob mob) {
                        mob.goalSelector.getAvailableGoals().clear();
                        mob.targetSelector.getAvailableGoals().clear();
                    }
                }
                return true;
            }
            return false;
        });

        // Remove dead entities
        data.infectedUUIDs().removeIf(uuid -> {
            var entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        // Tick: keep infected entities non-hostile and following player
        LivingEntity nearestInfected = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (UUID uuid : data.infectedUUIDs()) {
            if (!(level.getEntity(uuid) instanceof LivingEntity infected)) continue;

            infected.setGlowingTag(true);

            if (infected.tickCount % 5 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR,
                    infected.getX(), infected.getY() + 0.8, infected.getZ(),
                    3, 0.2, 0.3, 0.2, 0.02);
            }

            if (infected instanceof Mob mob) {
                mob.setTarget(null);
                mob.goalSelector.getAvailableGoals().clear();
                mob.targetSelector.getAvailableGoals().clear();
                if (mob.distanceTo(player) > 5.0) {
                    mob.getNavigation().moveTo(player, 1.0);
                }
            }

            double dSq = infected.distanceToSqr(player);
            if (dSq < nearestDistSq) {
                nearestDistSq = dSq;
                nearestInfected = infected;
            }
        }

        // ── telepathic_network: show direction to nearest infected host ──
        if (OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")
                && player.tickCount % 20 == 0) {
            if (nearestInfected != null) {
                double dx = nearestInfected.getX() - player.getX();
                double dz = nearestInfected.getZ() - player.getZ();
                double angle = Math.toDegrees(Math.atan2(dz, dx));
                if (angle < 0) angle += 360;
                String dir;
                if (angle < 22.5 || angle >= 337.5) dir = "→ В";
                else if (angle < 67.5) dir = "↘ ЮВ";
                else if (angle < 112.5) dir = "↓ Ю";
                else if (angle < 157.5) dir = "↙ ЮЗ";
                else if (angle < 202.5) dir = "← З";
                else if (angle < 247.5) dir = "↖ СЗ";
                else if (angle < 292.5) dir = "↑ С";
                else dir = "↗ СВ";
                int dist = (int) Math.sqrt(nearestDistSq);
                float hostHp = nearestInfected.getHealth();
                float hostMaxHp = nearestInfected.getMaxHealth();
                player.displayClientMessage(
                    Component.literal("§5🧠 Сеть: §d" + dir + " §8(" + dist + "м) §4❤"
                        + String.format("%.1f", hostHp) + "§8/§4" + String.format("%.1f", hostMaxHp)
                        + " §8× " + data.infectedUUIDs().size() + " хост(а)")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            } else if (data.infectedUUIDs().isEmpty() && player.tickCount % 100 == 0) {
                player.displayClientMessage(
                    Component.literal("§8🧠 Телепатическая сеть: §7нет заражённых хостов"), true);
            }
        }

        // ── parasite_sense: HP of nearest entity ──
        if (OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")
                && player.tickCount % 40 == 0) {
            List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(20),
                e -> e != player && e.isAlive());
            if (!nearbyEntities.isEmpty()) {
                LivingEntity closest = nearbyEntities.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                    .orElse(null);
                if (closest != null) {
                    boolean isInfected = data.infectedUUIDs().contains(closest.getUUID());
                    String infectedMark = isInfected ? " §5[ЗАРАЖЁН]" : "";
                    float hp = closest.getHealth();
                    float maxHp = closest.getMaxHealth();
                    String hpBar = hp < maxHp * 0.25f ? "§c" : hp < maxHp * 0.6f ? "§e" : "§a";
                    player.displayClientMessage(
                        Component.literal("§5👁 Паразитное чувство: §7" + closest.getType().getDescriptionId()
                            + " " + hpBar + String.format("%.1f", hp) + "❤" + infectedMark)
                            .withStyle(ChatFormatting.DARK_PURPLE), false);
                }
            }
        }
    }

    /**
     * Called from InfectionCommand when a new entity is infected.
     */
    public static boolean infectEntity(ServerPlayer player, LivingEntity target) {
        if (!OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")) return false;

        ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);
        ChaosAddonConfig cfg = ChaosAddonConfig.get();

        if (data.infectedUUIDs().size() >= cfg.paraMaxTargets) return false;
        if (!data.infectedUUIDs().isEmpty() && target.getHealth() / target.getMaxHealth() > 0.5f) return false;

        UUID targetId = target.getUUID();
        data.infectedUUIDs().add(targetId);
        INFECTION_EXPIRY.put(targetId, player.level().getGameTime() + cfg.paraInfectDuration);

        target.setGlowingTag(true);

        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.goalSelector.getAvailableGoals().clear();
            mob.targetSelector.getAvailableGoals().clear();
        }

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR,
                target.getX(), target.getY() + 1.0, target.getZ(),
                30, 0.5, 0.7, 0.5, 0.05);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                target.getX(), target.getY() + 1.0, target.getZ(),
                15, 0.4, 0.6, 0.4, 0.0);
            level.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.ZOMBIE_INFECT,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.8f);
            level.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.EVOKER_PREPARE_ATTACK,
                net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.2f);
        }

        return true;
    }

    @SubscribeEvent
    public static void onInfectedDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        UUID deadId = event.getEntity().getUUID();
        INFECTION_EXPIRY.remove(deadId);

        for (ServerPlayer player : level.players()) {
            if (!OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")) continue;

            ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);
            if (!data.infectedUUIDs().contains(deadId)) continue;

            data.infectedUUIDs().remove(deadId);

            if (data.infectedUUIDs().isEmpty()) {
                float withdrawalDmg = ChaosAddonConfig.get().paraWithdrawalDamage;
                player.hurt(player.damageSources().magic(), withdrawalDmg);

                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    25, 0.5, 0.7, 0.5, 0.1);
                level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.5f);
            }
            break;
        }
    }

    // ── no_potions: block all potion use ──
    @SubscribeEvent
    public static void onNoPotions(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/no_potions")) return;

        Item item = event.getItem().getItem();
        boolean isPotion = item == Items.POTION || item == Items.SPLASH_POTION
            || item == Items.LINGERING_POTION || item == Items.HONEY_BOTTLE
            || item == Items.MILK_BUCKET || item == Items.SUSPICIOUS_STEW;
        if (isPotion) {
            event.setCanceled(true);
            player.displayClientMessage(
                Component.literal("§5🧠 Паразит не пьёт зелий — организм уже изменён!").withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }
}
