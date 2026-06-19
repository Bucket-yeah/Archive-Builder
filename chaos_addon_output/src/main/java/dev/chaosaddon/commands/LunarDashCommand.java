package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Lunar Renegade – Lunar Dash + Eclipse + Silver Shield. */
public class LunarDashCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Lunar Dash: teleport 15 blocks in look direction, leave silver trail
        dispatcher.register(Commands.literal("chaos_addon_lunar_dash")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (!player.level().isNight()) return 0; // day restriction
                ServerLevel level = player.serverLevel();

                Vec3 look = player.getLookAngle().normalize().scale(15.0);
                double tx = player.getX() + look.x;
                double ty = player.getY() + look.y;
                double tz = player.getZ() + look.z;

                // Particle trail along path
                for (int step = 1; step <= 15; step++) {
                    double frac = step / 15.0;
                    double px = player.getX() + look.x * frac;
                    double py = player.getY() + look.y * frac;
                    double pz = player.getZ() + look.z * frac;
                    level.sendParticles(ParticleTypes.GLOW,
                        px, py + 0.5, pz, 4, 0.15, 0.15, 0.15, 0.0);
                    level.sendParticles(ParticleTypes.END_ROD,
                        px, py + 0.5, pz, 2, 0.1, 0.1, 0.1, 0.0);

                    // Trail damages enemies
                    List<LivingEntity> trailTargets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().move(look.scale(frac)).inflate(1.0),
                        e -> e != player && e.isAlive()
                    );
                    trailTargets.forEach(e -> e.hurt(player.damageSources().magic(), 2.0f));
                }

                player.teleportTo(tx, ty, tz);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT,     SoundSource.PLAYERS, 1.0f, 1.2f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 1.5f);
                return 1;
            }));
    }
}
