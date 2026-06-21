package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.config.ChaosAddonConfig;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;

/**
 * chaos_addon_bloom — "Мицелиальное Цветение"
 * Mycelial Symbiont ultimate ability (tertiary key, 5-min cooldown).
 *
 * Mechanics:
 * - Allies in radius → Regeneration II for {@code mossBloomAllyRegenDuration} ticks
 * - Enemies in radius → Slowness III + Weakness II for {@code mossBloomEnemyDebuffDuration} ticks
 * - Self → Regeneration I + Speed I (same duration)
 * - Supernode: {@code "chaos_moss_supernode_expiry"} NBT stores expiry game-tick;
 *   while active, MycelialSymbiontHandler doubles the moss_network buff radius.
 */
public class BloomCommand {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_bloom")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                if (!OriginHelper.hasPower(player, "chaos_addon:mycelial_symbiont/bloom")) return 0;

                ServerLevel level = player.serverLevel();
                ChaosAddonConfig cfg = ChaosAddonConfig.get();
                int radius = cfg.mossBloomRadius;

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e.isAlive() && e != player);

                int healed = 0;
                int debuffed = 0;

                for (LivingEntity e : entities) {
                    boolean isAlly = (e instanceof TamableAnimal animal && animal.isTame())
                        || e.getTags().contains("chaos_gardener_pet")
                        || e.getTags().contains("chaos_hive_ally")
                        || (e instanceof Player p
                            && player.getTeam() != null
                            && player.getTeam().isAlliedTo(p.getTeam()));

                    if (isAlly) {
                        e.addEffect(new MobEffectInstance(
                            MobEffects.REGENERATION, cfg.mossBloomAllyRegenDuration, 1, false, true));
                        healed++;
                    } else {
                        e.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, cfg.mossBloomEnemyDebuffDuration, 2, false, true));
                        e.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS, cfg.mossBloomEnemyDebuffDuration, 1, false, true));
                        debuffed++;
                    }
                }

                player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, cfg.mossBloomAllyRegenDuration, 0, false, true));
                player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, cfg.mossBloomAllyRegenDuration, 0, false, true));

                long supernodeExpiry = level.getGameTime() + cfg.mossBloomSuperNodeDuration;
                player.getPersistentData().putLong("chaos_moss_supernode_expiry", supernodeExpiry);

                // ── Block explosion: scatter mycelium, moss, moss carpets, mushrooms ──
                placeBloomBlocks(level, player, radius, cfg.mossBloomBlockCount);

                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    200, radius * 0.6, 4.0, radius * 0.6, 0.08);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    60, radius * 0.5, 2.0, radius * 0.5, 0.05);

                level.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 0.5f);

                player.sendSystemMessage(Component.literal(
                    "§a🍄 МИЦЕЛИАЛЬНОЕ ЦВЕТЕНИЕ! §2Радиус §a" + radius
                    + " §2блоков: §a+" + healed + " §2исцелено, §c-" + debuffed
                    + " §2ослаблено. §2Суперузел активен §a"
                    + (cfg.mossBloomSuperNodeDuration / 20) + "с§2!"));
                return 1;
            }));
    }

    /**
     * Places mycelium, moss blocks, moss carpets and mushrooms
     * in a sphere around the player during the bloom explosion.
     */
    private static void placeBloomBlocks(ServerLevel level, ServerPlayer player,
                                         int radius, int maxPlacements) {
        BlockPos origin = player.blockPosition();
        int placed = 0;
        int attempts = radius * radius * 6;

        for (int i = 0; i < attempts && placed < maxPlacements; i++) {
            int dx = RNG.nextInt(radius * 2 + 1) - radius;
            int dy = RNG.nextInt(7) - 3;
            int dz = RNG.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;

            BlockPos pos = origin.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            BlockState stateAbove = level.getBlockState(pos.above());
            Block block = state.getBlock();
            boolean airAbove = stateAbove.isAir();

            // Dirt/grass → mycelium (with chance of moss carpet on top)
            if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT
                    || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                    || block == Blocks.PODZOL) {
                level.setBlock(pos, Blocks.MYCELIUM.defaultBlockState(), 3);
                if (airAbove && RNG.nextFloat() < 0.45f) {
                    level.setBlock(pos.above(), Blocks.MOSS_CARPET.defaultBlockState(), 3);
                }
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    5, 0.3, 0.2, 0.3, 0.03);
                placed++;

            // Stone/cobblestone/gravel → moss block (only if exposed to air above)
            } else if (airAbove && (block == Blocks.STONE || block == Blocks.COBBLESTONE
                    || block == Blocks.GRAVEL || block == Blocks.DEEPSLATE
                    || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.TUFF)) {
                level.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    4, 0.3, 0.2, 0.3, 0.03);
                placed++;

            // Existing mycelium/moss → place carpet or mushroom on top
            } else if (airAbove && (block == Blocks.MYCELIUM || block == Blocks.MOSS_BLOCK)) {
                float roll = RNG.nextFloat();
                if (roll < 0.50f) {
                    level.setBlock(pos.above(), Blocks.MOSS_CARPET.defaultBlockState(), 3);
                    placed++;
                } else if (roll < 0.65f) {
                    Block mushroom = RNG.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM;
                    BlockState mushroomState = mushroom.defaultBlockState();
                    if (mushroomState.canSurvive(level, pos.above())) {
                        level.setBlock(pos.above(), mushroomState, 3);
                        placed++;
                    }
                }

            // Any solid surface with air above → moss carpet (small chance)
            } else if (airAbove && state.isSolidRender(level, pos) && RNG.nextFloat() < 0.20f) {
                level.setBlock(pos.above(), Blocks.MOSS_CARPET.defaultBlockState(), 3);
                placed++;
            }
        }
    }
}
