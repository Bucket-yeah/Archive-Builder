package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.ParasiteData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Handles the Parasitic Mind's infection mechanics:
 *  - Infected entities glow green and follow the player
 *  - When all infected die simultaneously → withdrawal damage
 *  - Player cannot deal direct damage (handled in JSON via attribute)
 */
public class ParasiteHandler {

    /** Tracks infection expiry ticks: entityUUID → tickExpiry */
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
                    // Restore mob AI on expiry — clear cleared goals so vanilla reloads them on next tick
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
        data.infectedUUIDs().forEach(uuid -> {
            if (!(level.getEntity(uuid) instanceof LivingEntity infected)) return;

            infected.setGlowingTag(true);

            // Apply particle trail
            if (infected.tickCount % 5 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR,
                    infected.getX(), infected.getY() + 0.8, infected.getZ(),
                    3, 0.2, 0.3, 0.2, 0.02);
            }

            if (infected instanceof Mob mob) {
                // Force-clear target every tick so no goal can re-acquire the player
                mob.setTarget(null);
                // Also clear goal/target selectors every tick to prevent AI re-targeting
                mob.goalSelector.getAvailableGoals().clear();
                mob.targetSelector.getAvailableGoals().clear();
                // Follow player if far away
                if (mob.distanceTo(player) > 5.0) {
                    mob.getNavigation().moveTo(player, 1.0);
                }
            }
        });
    }

    /**
     * Called from InfectionCommand when a new entity is infected.
     */
    public static boolean infectEntity(ServerPlayer player, LivingEntity target) {
        if (!OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")) return false;

        ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);
        ChaosAddonConfig cfg = ChaosAddonConfig.get();

        if (data.infectedUUIDs().size() >= cfg.paraMaxTargets) return false;
        // Require < 50% HP (allow infection of any mob when player has no hosts yet)
        if (!data.infectedUUIDs().isEmpty() && target.getHealth() / target.getMaxHealth() > 0.5f) return false;

        UUID targetId = target.getUUID();
        data.infectedUUIDs().add(targetId);
        INFECTION_EXPIRY.put(targetId, player.level().getGameTime() + cfg.paraInfectDuration);

        target.setGlowingTag(true);

        // CRITICAL FIX: Clear mob AI goals immediately so the mob never attacks the owner
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.goalSelector.getAvailableGoals().clear();
            mob.targetSelector.getAvailableGoals().clear();
        }

        // FX: green particles + zombie infect sound
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

        // Find the owning player and check withdrawal
        for (ServerPlayer player : level.players()) {
            if (!OriginHelper.hasPower(player, "chaos_addon:parasitic_mind/infection")) continue;

            ParasiteData data = player.getData(ModAttachments.PARASITE_DATA);
            if (!data.infectedUUIDs().contains(deadId)) continue;

            data.infectedUUIDs().remove(deadId);

            // Withdrawal if ALL infected are now dead
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
}
