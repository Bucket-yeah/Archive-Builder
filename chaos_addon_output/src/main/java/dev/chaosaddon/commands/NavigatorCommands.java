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
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/** Registers: chaos_addon_portal_rift, chaos_addon_void_portal, chaos_addon_reality_anchor */
public class NavigatorCommands {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Portal Rift: teleports player to a random dimension gate point
        dispatcher.register(Commands.literal("chaos_addon_portal_rift")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                // Find nearest nether portal and teleport near it
                BlockPos origin = player.blockPosition();
                int radius = 200;
                BlockPos bestPortal = null;
                for (int dx = -radius; dx <= radius; dx += 8) {
                    for (int dz = -radius; dz <= radius; dz += 8) {
                        for (int dy = -50; dy <= 50; dy += 4) {
                            BlockPos pos = origin.offset(dx, dy, dz);
                            if (level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.NETHER_PORTAL
                                || level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.END_PORTAL
                                || level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.END_GATEWAY) {
                                if (bestPortal == null || pos.distSqr(origin) < bestPortal.distSqr(origin)) {
                                    bestPortal = pos;
                                }
                            }
                        }
                    }
                }

                // Suck in all entities in radius 5
                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(5),
                    e -> e != player && e.isAlive());
                for (LivingEntity e : entities) {
                    Vec3 dir = player.position().subtract(e.position()).normalize().scale(2.0);
                    e.setDeltaMovement(dir.x, 0.5, dir.z);
                    e.hurt(player.damageSources().magic(), 5.0f);
                }

                // Teleport player to portal if found
                if (bestPortal != null) {
                    player.teleportTo(bestPortal.getX() + 0.5, bestPortal.getY() + 1, bestPortal.getZ() + 0.5);
                } else {
                    // No portal: random teleport in radius 50
                    int rx = origin.getX() + RNG.nextInt(100) - 50;
                    int rz = origin.getZ() + RNG.nextInt(100) - 50;
                    player.teleportTo(rx, origin.getY(), rz);
                }

                level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));

        // Reality Anchor: creates bi-directional teleport point
        dispatcher.register(Commands.literal("chaos_addon_reality_anchor")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                // Store anchor in NBT
                BlockPos anchor = player.blockPosition();
                net.minecraft.nbt.CompoundTag tag = player.getPersistentData();
                if (!tag.contains("chaos_anchor_x")) {
                    // First press: save anchor
                    tag.putInt("chaos_anchor_x", anchor.getX());
                    tag.putInt("chaos_anchor_y", anchor.getY());
                    tag.putInt("chaos_anchor_z", anchor.getZ());
                    tag.putLong("chaos_anchor_time", level.getGameTime());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§9§lЯкорь установлен!"));
                    level.sendParticles(ParticleTypes.END_ROD,
                        anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5,
                        30, 0.5, 1.0, 0.5, 0.05);
                } else {
                    // Second press: teleport to anchor
                    long anchorTime = tag.getLong("chaos_anchor_time");
                    if (level.getGameTime() - anchorTime < 600L) { // Valid for 30 sec
                        int ax = tag.getInt("chaos_anchor_x");
                        int ay = tag.getInt("chaos_anchor_y");
                        int az = tag.getInt("chaos_anchor_z");
                        player.teleportTo(ax + 0.5, ay, az + 0.5);
                        tag.remove("chaos_anchor_x");
                        level.sendParticles(ParticleTypes.PORTAL,
                            ax + 0.5, ay + 1.0, az + 0.5, 40, 0.5, 1.0, 0.5, 0.2);
                    } else {
                        tag.remove("chaos_anchor_x");
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cЯкорь устарел."));
                    }
                }
                level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.8f, 1.0f);
                return 1;
            }));

        // Void Portal: pull all nearby entities to self, damage, random teleport
        dispatcher.register(Commands.literal("chaos_addon_void_portal")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin = player.blockPosition();

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(10),
                    e -> e != player && e.isAlive());

                for (LivingEntity e : entities) {
                    e.hurt(player.damageSources().magic(), 5.0f);
                    // Teleport to random location
                    int rx = origin.getX() + RNG.nextInt(200) - 100;
                    int rz = origin.getZ() + RNG.nextInt(200) - 100;
                    e.teleportTo(rx, origin.getY(), rz);
                    level.sendParticles(ParticleTypes.PORTAL,
                        e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
                }

                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                    origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
                    60, 5.0, 3.0, 5.0, 0.2);
                level.playSound(null, origin, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 0.5f);
                return 1;
            }));
    }
}
