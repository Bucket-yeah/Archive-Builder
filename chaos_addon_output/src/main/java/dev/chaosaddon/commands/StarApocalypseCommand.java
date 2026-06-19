package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

import java.util.Random;

/**
 * Star Oracle – Star Apocalypse (chaos_addon_star_apocalypse).
 * 10s channeling (Slowness V), then 20 meteors rain in radius 30 over 15s.
 * Each meteor: 8 HP in radius 5, sets fire, leaves crater.
 * Massive AOE — dangerous to allies too.
 */
public class StarApocalypseCommand {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_star_apocalypse")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (!OriginHelper.hasPower(player, "chaos_addon:star_oracle/star_aura")) return 0;

                ServerLevel level = player.serverLevel();

                // 10s channeling phase: Slowness V
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4, false, true));

                player.sendSystemMessage(Component.literal(
                    "§e✦ §lЗвёздный Апокалипсис §r§e— Заклинание 10 секунд..."));
                level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 2.0, player.getZ(),
                    60, 2.0, 3.0, 2.0, 0.08);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 1.0f, 0.5f);

                // Schedule 20 meteors over next 15s (300 ticks) starting after 200 ticks (10s channeling)
                // We do this by storing pending meteors as NBT and processing in a tick handler,
                // OR we spawn actual meteors by scheduling FallingBlockEntity with a simple chain.
                // For simplicity: spawn all meteors at random intervals using scheduled tasks approach.
                // NeoForge doesn't have easy deferred tasks from commands, so we store state and process in GeneralPowerHandler.
                // The cleanest way: do the meteor batch here with a for-loop + immediate impact (no real delay).
                // Since true delayed execution requires a server tick callback, we use a simplified version:
                // schedule via player NBT tag processed in StarOracleHandler.

                player.getPersistentData().putBoolean("chaos_apocalypse_pending", true);
                player.getPersistentData().putInt("chaos_apocalypse_start", (int) level.getGameTime() + 200);
                player.getPersistentData().putInt("chaos_apocalypse_meteors", 20);
                player.getPersistentData().putInt("chaos_apocalypse_radius", 30);

                return 1;
            }));
    }

    /**
     * Called from StarOracleHandler tick to process pending meteor rain.
     */
    public static void processMeteors(ServerPlayer player, ServerLevel level) {
        if (!player.getPersistentData().getBoolean("chaos_apocalypse_pending")) return;
        long now = level.getGameTime();
        int startTick = player.getPersistentData().getInt("chaos_apocalypse_start");
        if (now < startTick) return;

        int meteorsLeft = player.getPersistentData().getInt("chaos_apocalypse_meteors");
        if (meteorsLeft <= 0) {
            player.getPersistentData().putBoolean("chaos_apocalypse_pending", false);
            return;
        }

        // Spawn a meteor every 15 ticks (0.75s)
        if ((now - startTick) % 15 == 0) {
            spawnMeteor(player, level);
            player.getPersistentData().putInt("chaos_apocalypse_meteors", meteorsLeft - 1);
            if (meteorsLeft - 1 == 0) {
                player.getPersistentData().putBoolean("chaos_apocalypse_pending", false);
                player.sendSystemMessage(Component.literal("§e✦ §lЗвёздный Апокалипсис §r§7— завершён!"));
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 0.6f);
            }
        }
    }

    private static void spawnMeteor(ServerPlayer player, ServerLevel level) {
        int radius = player.getPersistentData().getInt("chaos_apocalypse_radius");
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist = RNG.nextDouble() * radius;
        double targetX = player.getX() + Math.cos(angle) * dist;
        double targetZ = player.getZ() + Math.sin(angle) * dist;
        BlockPos target = BlockPos.containing(targetX, player.getY(), targetZ);

        // Find actual ground level
        int groundY = level.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
        BlockPos impact = new BlockPos(target.getX(), groundY, target.getZ());

        // Meteor visual: falling end_rod particles from above
        for (int y = groundY + 20; y > groundY; y -= 2) {
            level.sendParticles(ParticleTypes.FIREWORK,
                targetX, y, targetZ, 3, 0.1, 0.0, 0.1, 0.05);
            level.sendParticles(ParticleTypes.FLAME,
                targetX, y, targetZ, 2, 0.1, 0.0, 0.1, 0.05);
        }

        // Impact: 8 HP in radius 5, fire, crater
        level.getEntitiesOfClass(LivingEntity.class,
            new net.minecraft.world.phys.AABB(impact).inflate(5),
            e -> e.isAlive())
            .forEach(e -> {
                e.hurt(level.damageSources().magic(), 8.0f);
                e.setRemainingFireTicks(200);
            });

        // Crater + fire
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    BlockPos bp = impact.offset(dx, 0, dz);
                    if (!level.getBlockState(bp).is(Blocks.BEDROCK)) {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                        BlockPos firePos = bp.above();
                        if (level.getBlockState(firePos).isAir() && RNG.nextFloat() < 0.4f) {
                            level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            targetX, groundY + 1, targetZ, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME,
            targetX, groundY + 1, targetZ, 40, 2.0, 1.0, 2.0, 0.1);
        level.playSound(null, impact,
            SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 0.8f, 1.5f);
        level.playSound(null, impact,
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6f, 0.8f);
    }
}
