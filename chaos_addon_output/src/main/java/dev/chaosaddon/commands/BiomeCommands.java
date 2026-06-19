package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.data.BiomeData;
import dev.chaosaddon.init.ModAttachments;
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
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** chaos_addon_biome_shift, chaos_addon_biome_burst, chaos_addon_biome_capture */
public class BiomeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Biome Shift: apply current biome buffs in carried form for 30s
        dispatcher.register(Commands.literal("chaos_addon_biome_shift")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BiomeData data = player.getData(ModAttachments.BIOME_DATA);

                String biome = level.getBiome(player.blockPosition())
                    .unwrapKey().map(k -> k.location().toString()).orElse("unknown");
                data.setCarryBiome(biome);

                // Apply 30-second biome buff
                if (biome.contains("nether")) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1, false, true));
                } else if (biome.contains("forest") || biome.contains("jungle")) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0, false, true));
                } else if (biome.contains("ocean")) {
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0, false, true));
                } else if (biome.contains("desert")) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1, false, true));
                } else {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0, false, true));
                }

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§2§lНесёшь биом: " + biome));
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    40, 1.0, 1.5, 1.0, 0.06);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 0.8f, 0.9f);
                return 1;
            }));

        // Biome Burst: convert blocks in radius, damage enemies
        dispatcher.register(Commands.literal("chaos_addon_biome_burst")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin = player.blockPosition();
                int radius = 10;

                String biome = level.getBiome(origin)
                    .unwrapKey().map(k -> k.location().toString()).orElse("unknown");

                // Damage enemies
                List<LivingEntity> enemies = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive());
                enemies.forEach(e -> e.hurt(player.damageSources().magic(), 3.0f));

                // Convert surface blocks based on biome
                net.minecraft.world.level.block.state.BlockState convertTo;
                if (biome.contains("desert")) {
                    convertTo = Blocks.SANDSTONE.defaultBlockState();
                } else if (biome.contains("nether")) {
                    convertTo = Blocks.NETHERRACK.defaultBlockState();
                } else if (biome.contains("end")) {
                    convertTo = Blocks.END_STONE.defaultBlockState();
                } else {
                    convertTo = Blocks.GRASS_BLOCK.defaultBlockState();
                }

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz > radius * radius) continue;
                        BlockPos surface = origin.offset(dx, 0, dz);
                        if (!level.getBlockState(surface).isAir()
                                && !level.getBlockState(surface).getBlock().equals(Blocks.BEDROCK)) {
                            level.setBlock(surface, convertTo, 3);
                        }
                    }
                }

                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    80, radius * 0.5, 2.0, radius * 0.5, 0.1);
                level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 0.8f);
                return 1;
            }));

        // Biome Capture: convert surface blocks in radius 20 to biome-specific blocks + effects
        dispatcher.register(Commands.literal("chaos_addon_biome_capture")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos origin = player.blockPosition();

                String biome = level.getBiome(origin)
                    .unwrapKey().map(k -> k.location().toString()).orElse("unknown");
                BiomeData data = player.getData(ModAttachments.BIOME_DATA);
                data.setCarryBiome(biome);

                net.minecraft.world.level.block.state.BlockState surfaceBlock;
                if (biome.contains("nether") || biome.contains("basalt") || biome.contains("soul")) {
                    surfaceBlock = Blocks.NETHERRACK.defaultBlockState();
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 1, false, true));
                } else if (biome.contains("desert") || biome.contains("badlands")) {
                    surfaceBlock = Blocks.SAND.defaultBlockState();
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0, false, true));
                } else if (biome.contains("ocean") || biome.contains("river")) {
                    surfaceBlock = net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState();
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200, 0, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, true));
                } else if (biome.contains("snowy") || biome.contains("frozen") || biome.contains("ice")) {
                    surfaceBlock = net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 1, false, true));
                } else if (biome.contains("end") || biome.contains("void")) {
                    surfaceBlock = net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState();
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 1200, 0, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 2, false, true));
                } else {
                    surfaceBlock = Blocks.GRASS_BLOCK.defaultBlockState();
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 0, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0, false, true));
                }

                int radius = 20;
                java.util.List<BlockPos> toConvert = new java.util.ArrayList<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz > radius * radius) continue;
                        BlockPos surface = origin.offset(dx, 0, dz);
                        for (int dy = 3; dy >= -3; dy--) {
                            BlockPos check = surface.offset(0, dy, 0);
                            if (!level.getBlockState(check).isAir()
                                    && !level.getBlockState(check).getBlock().equals(Blocks.BEDROCK)
                                    && !level.getBlockState(check).getBlock().equals(net.minecraft.world.level.block.Blocks.WATER)) {
                                toConvert.add(check);
                                break;
                            }
                        }
                    }
                }

                final net.minecraft.world.level.block.state.BlockState fs = surfaceBlock;
                toConvert.forEach(pos -> level.setBlock(pos, fs, 3));

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§5§lБиомный Захват: §r" + biome + " §7(60 сек)"));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 1.0, player.getZ(), 200, radius * 0.5, 3.0, radius * 0.5, 0.04);
                level.playSound(null, origin, net.minecraft.sounds.SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
                return 1;
            }));
    }
}
