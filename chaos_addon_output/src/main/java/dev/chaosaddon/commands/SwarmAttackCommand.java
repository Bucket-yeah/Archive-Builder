package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.SwarmData;
import dev.chaosaddon.init.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Swarm Lord – Sacrifice to Swarm: all bugs attack the closest enemy. */
public class SwarmAttackCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_swarm_attack")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                SwarmData data = player.getData(ModAttachments.SWARM_DATA);
                ChaosAddonConfig cfg = ChaosAddonConfig.get();

                // Find nearest enemy
                List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(30),
                    e -> e != player && e.isAlive()
                );
                if (enemies.isEmpty()) return 0;

                LivingEntity nearest = enemies.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                    .orElse(null);
                if (nearest == null) return 0;

                float baseDmg = 5.0f + (data.bugUUIDs().size() * cfg.swarmBugDamage);
                nearest.hurt(player.damageSources().mobAttack(player), baseDmg);
                nearest.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, true));

                // Visual: bugs glow red, sweep at target
                data.bugUUIDs().forEach(uuid -> {
                    var bug = level.getEntity(uuid);
                    if (bug == null) return;
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        nearest.getX(), nearest.getY() + 0.8, nearest.getZ(),
                        5, 0.5, 0.4, 0.5, 0.05);
                });

                level.sendParticles(ParticleTypes.FLAME,
                    nearest.getX(), nearest.getY() + 1.0, nearest.getZ(),
                    20, 0.5, 0.7, 0.5, 0.08);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
                level.playSound(null, nearest.blockPosition(),
                    SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 0.8f, 1.5f);
                return 1;
            }));
    }
}
