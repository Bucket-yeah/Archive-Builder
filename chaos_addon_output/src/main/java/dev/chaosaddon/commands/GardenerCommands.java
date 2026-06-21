package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.events.WanderingGardenerHandler;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Commands for Wandering Gardener:
 * chaos_addon_plant_trap       — "Шёпот Цветов" (primary active): place a flower trap at current position
 * chaos_addon_beast_friendship — "Дружба Зверя" (secondary active): pacify nearest hostile mob briefly
 * chaos_addon_growth_blessing  — (tertiary): grow plants in radius
 * chaos_addon_summon_rain      — call rain (with weakness cost)
 * chaos_addon_summon_dryad     — summon iron golem companion
 */
public class GardenerCommands {

    private static final Random RNG = new Random();

    private static final net.minecraft.world.level.block.Block[] PLANTS = {
        Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.RED_TULIP,
        Blocks.OAK_SAPLING, Blocks.FERN, Blocks.SHORT_GRASS, Blocks.SUNFLOWER
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ── "Шёпот Цветов": plant a flower trap at the player's position ──
        dispatcher.register(Commands.literal("chaos_addon_plant_trap")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                boolean placed = WanderingGardenerHandler.placePlantTrap(player);
                return placed ? 1 : 0;
            }));

        // ── "Дружба Зверя": pacify nearest hostile mob for ~8 seconds ──
        dispatcher.register(Commands.literal("chaos_addon_beast_friendship")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                List<Mob> hostiles = level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(15),
                    e -> e.isAlive() && e.getTarget() instanceof Player);

                if (hostiles.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§7🌿 Нет враждебных существ поблизости."));
                    return 0;
                }

                Mob target = hostiles.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceTo(player)))
                    .orElse(null);
                if (target == null) return 0;

                target.setTarget(null);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION,      160, 0, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1, false, true));

                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    20, 0.5, 0.8, 0.5, 0.05);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    target.getX(), target.getY() + 1.5, target.getZ(),
                    10, 0.4, 0.4, 0.4, 0.05);
                level.playSound(null, target.blockPosition(),
                    SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.8f, 1.3f);

                player.displayClientMessage(
                    Component.literal("§a🐾 Дружба Зверя! §7" + target.getType().getDescription().getString()
                        + " успокоен на 8 секунд."), true);
                return 1;
            }));

        // ── "Благословение Роста": grow plants in 5-block radius (tertiary) ──
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

        // ── Призыв Дождя: rain + self-debuff ──
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
                player.sendSystemMessage(Component.literal("§9§lДождь призван!"));
                return 1;
            }));

        // ── Призыв Дриады: summon iron golem companion ──
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
                golem.addTag("chaos_managed_entity");
                golem.getPersistentData().putInt("chaos_despawn_ticks", 1200);
                level.addFreshEntity(golem);

                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                    60, 1.0, 2.0, 1.0, 0.1);
                level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.7f);
                return 1;
            }));
    }
}
