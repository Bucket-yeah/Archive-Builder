package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChaoticAuraHandler {

    private static final Random RNG = new Random();

    private static final Block[] CHAOS_BLOCKS = {
        Blocks.GRAVEL, Blocks.SAND, Blocks.GLASS, Blocks.OBSIDIAN,
        Blocks.DIRT, Blocks.COBBLESTONE, Blocks.NETHERRACK, Blocks.END_STONE,
        Blocks.SOUL_SAND, Blocks.MAGMA_BLOCK, Blocks.MOSSY_COBBLESTONE,
        Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.TUFF,
        Blocks.CRYING_OBSIDIAN, Blocks.AMETHYST_BLOCK, Blocks.CALCITE,
        Blocks.DEEPSLATE, Blocks.SCULK, Blocks.TINTED_GLASS,
        Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SOUL_SOIL
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

        for (BlockPos pos : candidates) {
            if (RNG.nextFloat() < chance) {
                Block replacement = CHAOS_BLOCKS[RNG.nextInt(CHAOS_BLOCKS.length)];
                level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);

                // Purple portal thread from player to changed block
                double mx = (player.getX() + pos.getX() + 0.5) / 2;
                double my = (player.getY() + 1.0 + pos.getY() + 0.5) / 2;
                double mz = (player.getZ() + pos.getZ() + 0.5) / 2;
                level.sendParticles(ParticleTypes.PORTAL,
                    mx, my, mz, 6, 0.3, 0.3, 0.3, 0.1);
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.2, 0.2, 0.2, 0.05);
            }
        }

        level.playSound(null, player.blockPosition(),
            SoundEvents.PORTAL_TRIGGER,
            SoundSource.PLAYERS,
            0.4f, 1.5f + RNG.nextFloat() * 0.5f);

        // Mad Whisper: 15% retarget + 5% mob-vs-mob
        if (OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/mad_whisper")) {
            if (player.tickCount % 60 == 0) {
                List<LivingEntity> nearby = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(12),
                    e -> e != player && e.isAlive() && e instanceof Mob);
                if (!nearby.isEmpty()) {
                    List<LivingEntity> allTargets = level.getEntitiesOfClass(
                        LivingEntity.class, player.getBoundingBox().inflate(12),
                        LivingEntity::isAlive);
                    for (LivingEntity mob : nearby) {
                        if (!(mob instanceof Mob m)) continue;
                        if (RNG.nextFloat() < 0.15f && !allTargets.isEmpty()) {
                            LivingEntity newTarget = allTargets.get(RNG.nextInt(allTargets.size()));
                            if (newTarget != mob) {
                                m.setTarget(newTarget);
                                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                                    mob.getX(), mob.getY() + 1.5, mob.getZ(),
                                    3, 0.3, 0.3, 0.3, 0.0);
                                // 5% chance: attack another mob specifically
                                if (RNG.nextFloat() < 0.05f) {
                                    List<LivingEntity> otherMobs = level.getEntitiesOfClass(
                                        LivingEntity.class, mob.getBoundingBox().inflate(8),
                                        e -> e != mob && e != player && e.isAlive() && e instanceof Mob);
                                    if (!otherMobs.isEmpty()) {
                                        m.setTarget(otherMobs.get(RNG.nextInt(otherMobs.size())));
                                        level.sendParticles(ParticleTypes.SMOKE,
                                            mob.getX(), mob.getY() + 1.5, mob.getZ(),
                                            5, 0.2, 0.3, 0.2, 0.02);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
