package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.chaosaddon.events.StarOracleHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Star Oracle commands:
 * - chaos_addon_meteor_strike: delayed explosion at look-target location
 * - chaos_addon_celestial_guardian: spawn shulker guardian above player
 */
public class StarOracleCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_meteor_strike")
            .requires(src -> src.hasPermission(0))
            .executes(StarOracleCommands::meteorStrike));

        dispatcher.register(Commands.literal("chaos_addon_celestial_guardian")
            .requires(src -> src.hasPermission(0))
            .executes(StarOracleCommands::celestialGuardian));
    }

    private static int meteorStrike(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        // Find target position (block player is looking at, up to 40 blocks)
        Vec3 lookPos = player.getEyePosition().add(player.getLookAngle().scale(40));
        HitResult hit = player.pick(40.0, 0.0f, false);

        double tx, ty, tz;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = ((BlockHitResult) hit).getBlockPos();
            tx = bp.getX() + 0.5;
            ty = bp.getY() + 0.5;
            tz = bp.getZ() + 0.5;
        } else {
            tx = lookPos.x;
            ty = lookPos.y;
            tz = lookPos.z;
        }

        final double finalX = tx, finalY = ty, finalZ = tz;

        // Mark countdown with particles (3 second warning)
        for (int tick = 0; tick < 3; tick++) {
            final int t = tick;
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + t * 20,
                () -> {
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i;
                        level.sendParticles(ParticleTypes.END_ROD,
                            finalX + Math.cos(angle) * 2.0, finalY + 0.3, finalZ + Math.sin(angle) * 2.0,
                            1, 0, 0.5, 0, 0.0);
                    }
                    level.sendParticles(ParticleTypes.FLAME,
                        finalX, finalY + 8 - t * 2.5, finalZ,
                        10, 0.3, 0.2, 0.3, 0.05);
                    level.playSound(null,
                        BlockPos.containing(finalX, finalY, finalZ),
                        SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS,
                        0.3f + t * 0.3f, 1.5f);
                }));
        }

        // Actual impact after 3 seconds
        level.getServer().tell(new net.minecraft.server.TickTask(
            level.getServer().getTickCount() + 60,
            () -> {
                // Damage in radius 5
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    new net.minecraft.world.phys.AABB(
                        finalX - 5, finalY - 2, finalZ - 5,
                        finalX + 5, finalY + 6, finalZ + 5),
                    e -> e != player && e.isAlive())
                    .forEach(e -> e.hurt(player.damageSources().magic(), 8.0f));

                // Set fire to area
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dz * dz > 9) continue;
                        BlockPos firePos = BlockPos.containing(
                            finalX + dx, finalY, finalZ + dz);
                        if (level.getBlockState(firePos).isAir()) {
                            level.setBlock(firePos,
                                net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }

                // Impact FX
                level.sendParticles(ParticleTypes.EXPLOSION,
                    finalX, finalY + 0.5, finalZ, 3, 0.5, 0, 0.5, 0);
                level.sendParticles(ParticleTypes.FIREWORK,
                    finalX, finalY + 0.5, finalZ, 50, 2.0, 1.0, 2.0, 0.2);
                level.sendParticles(ParticleTypes.FLAME,
                    finalX, finalY + 0.5, finalZ, 60, 2.5, 0.5, 2.5, 0.1);
                level.sendParticles(ParticleTypes.END_ROD,
                    finalX, finalY + 0.5, finalZ, 30, 1.5, 0.5, 1.5, 0.15);
                level.playSound(null, BlockPos.containing(finalX, finalY, finalZ),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.7f);
                level.playSound(null, BlockPos.containing(finalX, finalY, finalZ),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
            }));

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§b☄ Метеор через 3 секунды!"), false);
        return 1;
    }

    private static int celestialGuardian(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();

        Shulker shulker = EntityType.SHULKER.create(level);
        if (shulker == null) return 0;

        // Spawn above player
        shulker.moveTo(player.getX(), player.getY() + 4, player.getZ(), 0, 0);
        shulker.addTag("chaos_celestial_guardian");
        shulker.getPersistentData().putInt("chaos_despawn_ticks", 600); // 30 seconds
        shulker.setGlowingTag(true);

        // Make it a friendly guardian that targets player's enemies
        shulker.getPersistentData().putString("chaos_owner", player.getUUID().toString());

        level.addFreshEntity(shulker);

        // Summon FX
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2 / 12) * i;
            level.sendParticles(ParticleTypes.END_ROD,
                player.getX() + Math.cos(angle) * 2.5,
                player.getY() + 4,
                player.getZ() + Math.sin(angle) * 2.5,
                2, 0, 0.3, 0, 0.05);
        }
        level.sendParticles(ParticleTypes.FIREWORK,
            player.getX(), player.getY() + 3, player.getZ(),
            40, 0.8, 1.0, 0.8, 0.1);
        level.playSound(null, player.blockPosition(),
            SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8f, 0.7f);
        level.playSound(null, player.blockPosition(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 0.8f);

        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("§b⭐ Небесный Страж призван на 30 сек!"),
            false);
        return 1;
    }
}
