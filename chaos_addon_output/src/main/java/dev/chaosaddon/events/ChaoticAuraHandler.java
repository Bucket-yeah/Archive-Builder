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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ChaoticAuraHandler {

    private static final Random RNG = new Random();

    // Mad Whisper: separate auto-timer, fires every ~3600 ticks (3 min) independently
    private static final Map<UUID, Long> MAD_WHISPER_TIMER = new HashMap<>();
    private static final int MAD_WHISPER_BASE = 3000;
    private static final int MAD_WHISPER_JITTER = 1200; // ±1 min variance

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

        // Mad Whisper: auto-fires every ~3 min independently of chaotic_aura interval
        if (OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/mad_whisper")) {
            UUID pid = player.getUUID();
            long now = level.getGameTime();
            long lastWhisper = MAD_WHISPER_TIMER.getOrDefault(pid, 0L);
            long whisperInterval = MAD_WHISPER_BASE + (long)(RNG.nextDouble() * MAD_WHISPER_JITTER);

            if (now - lastWhisper >= whisperInterval) {
                MAD_WHISPER_TIMER.put(pid, now);
                triggerMadWhisper(player, level);
            }
        }
    }

    /**
     * Mad Whisper: randomly fires one of three abilities automatically.
     * 1) Mob chaos — all nearby mobs attack each other for 10s
     * 2) Reality crack — teleport all nearby mobs to random positions
     * 3) Fear wave — all nearby mobs flee from player for 5s
     */
    private static void triggerMadWhisper(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(
            LivingEntity.class, player.getBoundingBox().inflate(16),
            e -> e != player && e.isAlive() && e instanceof Mob);

        if (nearby.isEmpty()) return;

        int roll = RNG.nextInt(3);
        String abilityName;

        switch (roll) {
            case 0 -> { // Mob Chaos: retarget all mobs to attack each other
                abilityName = "§4БЕЗУМНЫЙ ШЁПОТ: §cХаос Толпы!";
                List<LivingEntity> allTargets = new java.util.ArrayList<>(nearby);
                for (LivingEntity mob : nearby) {
                    if (!(mob instanceof Mob m)) continue;
                    LivingEntity newTarget = allTargets.get(RNG.nextInt(allTargets.size()));
                    if (newTarget != mob) {
                        m.setTarget(newTarget);
                    }
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        mob.getX(), mob.getY() + 1.5, mob.getZ(),
                        5, 0.3, 0.3, 0.3, 0.0);
                }
            }
            case 1 -> { // Reality Crack: teleport mobs randomly
                abilityName = "§4БЕЗУМНЫЙ ШЁПОТ: §5Трещина Реальности!";
                for (LivingEntity mob : nearby) {
                    double nx = mob.getX() + (RNG.nextDouble() - 0.5) * 20;
                    double nz = mob.getZ() + (RNG.nextDouble() - 0.5) * 20;
                    int ny = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)nx, (int)nz);
                    mob.teleportTo(nx, ny, nz);
                    level.sendParticles(ParticleTypes.PORTAL,
                        mob.getX(), mob.getY() + 1.0, mob.getZ(),
                        10, 0.3, 0.5, 0.3, 0.1);
                }
            }
            default -> { // Fear Wave: mob flees from player (set them on fire + disorient)
                abilityName = "§4БЕЗУМНЫЙ ШЁПОТ: §eВолна Страха!";
                for (LivingEntity mob : nearby) {
                    if (!(mob instanceof Mob m)) continue;
                    m.setTarget(null);
                    // Move them away by knocking back
                    double dx = mob.getX() - player.getX();
                    double dz = mob.getZ() - player.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0) {
                        mob.setDeltaMovement(dx / len * 1.5, 0.4, dz / len * 1.5);
                    }
                    net.minecraft.world.effect.MobEffectInstance fear =
                        new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.BLINDNESS, 100, 0, false, true);
                    mob.addEffect(fear);
                    level.sendParticles(ParticleTypes.SMOKE,
                        mob.getX(), mob.getY() + 1.5, mob.getZ(),
                        6, 0.2, 0.3, 0.2, 0.05);
                }
            }
        }

        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            30, 0.8, 1.0, 0.8, 0.08);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.8f, 0.5f);
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(abilityName + " §7(" + nearby.size() + " мобов)"),
            false);
    }
}
