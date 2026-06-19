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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** chaos_addon_growth_blessing, chaos_addon_summon_rain, chaos_addon_summon_dryad */
public class GardenerCommands {

    private static final Random RNG = new Random();

    private static final net.minecraft.world.level.block.Block[] PLANTS = {
        Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.RED_TULIP,
        Blocks.OAK_SAPLING, Blocks.FERN, Blocks.GRASS, Blocks.SUNFLOWER
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Growth Blessing: grow plants in radius 5
        dispatcher.register(Commands.literal("chaos_addon_growth_blessing")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin = player.blockPosition();
                int radius = 5;

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz > radius * radius) continue;
                        BlockPos ground = origin.offset(dx, 0, dz);
                        BlockPos above  = ground.above();
                        BlockState groundState = level.getBlockState(ground);
                        if ((groundState.getBlock() == Blocks.GRASS_BLOCK
                                || groundState.getBlock() == Blocks.DIRT)
                                && level.getBlockState(above).isAir()) {
                            BlockState plant = PLANTS[RNG.nextInt(PLANTS.length)].defaultBlockState();
                            if (level.isEmptyBlock(above)) {
                                level.setBlock(above, plant, 3);
                            }
                        }
                    }
                }

                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                    origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ() + 0.5,
                    80, radius * 0.5, 2.0, radius * 0.5, 0.06);
                level.playSound(null, origin, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
                return 1;
            }));

        // Summon Rain: weather change + growth boost
        dispatcher.register(Commands.literal("chaos_addon_summon_rain")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                level.setWeatherParameters(0, 400, true, false);
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,  400, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 0, false, true));

                level.sendParticles(ParticleTypes.RAIN,
                    player.getX(), player.getY() + 3.0, player.getZ(),
                    100, 25.0, 5.0, 25.0, 0.3);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.6f);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§9§lДождь призван! Рост растений ×10 на 20 сек."));
                return 1;
            }));

        // Summon Dryad: iron golem tagged as dryad
        dispatcher.register(Commands.literal("chaos_addon_summon_dryad")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos pos = player.blockPosition().offset(1, 0, 1);

                IronGolem golem = EntityType.IRON_GOLEM.create(level);
                if (golem == null) return 0;

                golem.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(30.0);
                golem.setHealth(30.0f);
                golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(5.0);
                golem.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
                golem.addTag("chaos_gardener_pet");
                golem.getPersistentData().putInt("chaos_despawn_ticks", 1200); // 60 sec
                level.addFreshEntity(golem);

                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                    60, 1.0, 2.0, 1.0, 0.1);
                level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));
    }
}
