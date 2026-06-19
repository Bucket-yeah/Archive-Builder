package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

/**
 * Eater of Worlds – Reality Collapse:
 * Creates a chaos zone radius 8, 5 seconds.
 * Mobs get random effects, blocks mutate, random sounds play.
 * 30% chance: player also gets a random debuff.
 */
public class RealityCollapseCommand {

    private static final Random RNG = new Random();

    private static final Block[] CHAOS_BLOCKS = {
        Blocks.MAGMA_BLOCK, Blocks.CRYING_OBSIDIAN, Blocks.NETHERRACK,
        Blocks.END_STONE, Blocks.SCULK, Blocks.AMETHYST_BLOCK, Blocks.BASALT,
        Blocks.SOUL_SAND, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_reality_collapse")
            .requires(src -> src.hasPermission(0))
            .executes(RealityCollapseCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int radius = 8;

        // Mob chaos effects
        MobEffectInstance[] goodEffects = {
            new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 2),
            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2),
            new MobEffectInstance(MobEffects.REGENERATION, 100, 1),
            new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)
        };
        MobEffectInstance[] badEffects = {
            new MobEffectInstance(MobEffects.CONFUSION, 100, 0),
            new MobEffectInstance(MobEffects.BLINDNESS, 100, 0),
            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2),
            new MobEffectInstance(MobEffects.WEAKNESS, 100, 2),
            new MobEffectInstance(MobEffects.WITHER, 100, 1)
        };

        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(radius),
            e -> e != player && e.isAlive());

        for (LivingEntity mob : nearby) {
            MobEffectInstance randomEffect = RNG.nextBoolean()
                ? goodEffects[RNG.nextInt(goodEffects.length)]
                : badEffects[RNG.nextInt(badEffects.length)];
            mob.addEffect(randomEffect);
        }

        // Block mutation in zone
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                if (RNG.nextFloat() < 0.15f) {
                    var state = level.getBlockState(pos);
                    if (!state.isAir() && !state.liquid()
                            && state.getBlock() != Blocks.BEDROCK) {
                        level.setBlock(pos, CHAOS_BLOCKS[RNG.nextInt(CHAOS_BLOCKS.length)].defaultBlockState(),
                            Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 30% chance player gets random debuff
        if (RNG.nextFloat() < 0.30f) {
            player.addEffect(badEffects[RNG.nextInt(badEffects.length)]);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§5⚠ Хаос не разбирает своих!"),
                false);
        }

        // Chaotic particles
        level.sendParticles(ParticleTypes.WITCH,
            origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
            80, radius, 3.0, radius, 0.15);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ() + 0.5,
            60, radius, 3.0, radius, 0.1);
        level.sendParticles(ParticleTypes.ENCHANT,
            origin.getX() + 0.5, origin.getY() + 2.0, origin.getZ() + 0.5,
            80, radius, 4.0, radius, 0.2);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
            origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
            40, radius, 2.0, radius, 0.1);

        level.playSound(null, origin, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.playSound(null, origin, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 0.7f);

        return 1;
    }
}
