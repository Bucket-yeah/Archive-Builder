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
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** Eater of Worlds – Reality Shift: swaps two random blocks in radius 20. */
public class RealityShiftCommand {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_reality_shift")
            .requires(src -> src.hasPermission(0))
            .executes(RealityShiftCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = player.serverLevel();
        int radius = dev.chaosaddon.config.ChaosAddonConfig.get().eaterChaoticAuraRadius * 2;

        BlockPos origin = player.blockPosition();
        BlockPos posA = findRandomSolidBlock(level, origin, radius);
        BlockPos posB = findRandomSolidBlock(level, origin, radius);
        if (posA == null || posB == null || posA.equals(posB)) return 0;

        BlockState stateA = level.getBlockState(posA);
        BlockState stateB = level.getBlockState(posB);

        level.setBlock(posA, stateB, 3);
        level.setBlock(posB, stateA, 3);

        // Portal particles around both swap points
        for (int i = 0; i < 2; i++) {
            BlockPos target = (i == 0) ? posA : posB;
            level.sendParticles(ParticleTypes.PORTAL,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                40, 0.5, 0.5, 0.5, 0.3);
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.1);
        }
        level.playSound(null, origin, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, origin, SoundEvents.PORTAL_AMBIENT,  SoundSource.PLAYERS, 0.5f, 1.2f);
        return 1;
    }

    private static BlockPos findRandomSolidBlock(ServerLevel level, BlockPos origin, int radius) {
        for (int attempts = 0; attempts < 20; attempts++) {
            int x = origin.getX() + RNG.nextInt(radius * 2 + 1) - radius;
            int y = origin.getY() + RNG.nextInt(5) - 2;
            int z = origin.getZ() + RNG.nextInt(radius * 2 + 1) - radius;
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()
                    && !level.getBlockState(pos).liquid()
                    && level.getBlockState(pos).getBlock() != net.minecraft.world.level.block.Blocks.BEDROCK) {
                return pos;
            }
        }
        return null;
    }
}
