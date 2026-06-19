package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles the Eater of Worlds' Chaotic Aura passive:
 * every N ticks, blocks in a radius have a chance to become random blocks.
 */
public class ChaoticAuraHandler {

    private static final Random RNG = new Random();

    // Pool of random replacement blocks
    private static final Block[] CHAOS_BLOCKS = {
        Blocks.GRAVEL, Blocks.SAND, Blocks.GLASS, Blocks.OBSIDIAN,
        Blocks.DIRT, Blocks.COBBLESTONE, Blocks.NETHERRACK, Blocks.END_STONE,
        Blocks.SOUL_SAND, Blocks.MAGMA_BLOCK, Blocks.MOSSY_COBBLESTONE,
        Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.TUFF
    };

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/chaotic_aura")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        int interval = cfg.eaterChaoticAuraInterval;
        if (player.tickCount % interval != 0) return;

        float chance = cfg.eaterChaoticAuraChance;
        int radius   = cfg.eaterChaoticAuraRadius;

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos origin = player.blockPosition();

        // Collect solid, non-bedrock, non-fluid blocks in radius
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == Blocks.BEDROCK) continue;
                    if (level.getBlockState(pos).isAir()) continue;
                    if (!level.getFluidState(pos).isEmpty()) continue;
                    candidates.add(pos);
                }
            }
        }

        // Replace a random subset based on chance
        for (BlockPos pos : candidates) {
            if (RNG.nextFloat() < chance) {
                Block replacement = CHAOS_BLOCKS[RNG.nextInt(CHAOS_BLOCKS.length)];
                level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);

                // Spawn portal particles at changed block
                level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.PORTAL,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.1
                );
            }
        }

        // Sound effect at player position
        level.playSound(null, player.blockPosition(),
            net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.4f, 1.5f + RNG.nextFloat() * 0.5f);
    }
}
