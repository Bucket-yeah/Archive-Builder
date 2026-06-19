package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Ancient Sentinel – Plate Thrust, Crystal Armor, Earthquake.
 */
public class QuakeCommand {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Plate Thrust: push blocks in radius 5, damage enemies
        dispatcher.register(Commands.literal("chaos_addon_plate_thrust")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                int radius = 5;

                List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive());

                for (LivingEntity e : enemies) {
                    e.hurt(player.damageSources().magic(), 3.0f);
                    Vec3 pushDir = e.position().subtract(player.position()).normalize().scale(0.8);
                    e.setDeltaMovement(pushDir.x, 0.4, pushDir.z);

                    level.sendParticles(ParticleTypes.EXPLOSION,
                        e.getX(), e.getY() + 0.5, e.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
                }

                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    player.getX(), player.getY(), player.getZ(), 40, radius, 0.5, radius, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.5f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.AMBIENT_CAVE,    SoundSource.PLAYERS, 0.6f, 0.8f);
                return 1;
            }));

        // Earthquake: radius 20, massive damage, gravel conversion
        dispatcher.register(Commands.literal("chaos_addon_earthquake")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                int radius = 20;
                float damage = dev.chaosaddon.config.ChaosAddonConfig.get().sentinelEqDamage;

                // Damage all entities in radius
                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive());
                entities.forEach(e -> e.hurt(player.damageSources().magic(), damage));

                // Convert dirt/stone surface blocks to gravel (crack effect)
                BlockPos origin = player.blockPosition();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz > radius * radius) continue;
                        if (RNG.nextFloat() > 0.25f) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos pos = origin.offset(dx, dy, dz);
                            BlockState bs = level.getBlockState(pos);
                            if (bs.getBlock() == Blocks.DIRT || bs.getBlock() == Blocks.STONE
                                    || bs.getBlock() == Blocks.GRASS_BLOCK) {
                                level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 3);
                            }
                        }
                    }
                }

                // Massive FX
                level.sendParticles(ParticleTypes.EXPLOSION,
                    player.getX(), player.getY() + 0.5, player.getZ(), 20, radius * 0.5, 1.0, radius * 0.5, 0.1);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    player.getX(), player.getY(), player.getZ(), 100, radius, 2.0, radius, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE,   SoundSource.PLAYERS, 1.5f, 0.4f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
                return 1;
            }));
    }
}
