package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.data.MossData;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

/** chaos_addon_spore_harvest, chaos_addon_moss_teleport, chaos_addon_spore_fog */
public class SymbiontCommands {

    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("chaos_addon_spore_harvest")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                BlockPos pos = player.blockPosition();

                int y = pos.getY();
                net.minecraft.world.item.ItemStack result;

                if (y < -40) {
                    result = RNG.nextFloat() < 0.4f
                        ? new net.minecraft.world.item.ItemStack(Items.DIAMOND)
                        : new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT, 2);
                } else if (y < 0) {
                    result = RNG.nextFloat() < 0.3f
                        ? new net.minecraft.world.item.ItemStack(Items.EMERALD)
                        : new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, 3);
                } else if (y < 64) {
                    result = RNG.nextFloat() < 0.5f
                        ? new net.minecraft.world.item.ItemStack(Items.BONE_MEAL, 5)
                        : new net.minecraft.world.item.ItemStack(Items.BROWN_MUSHROOM, 3);
                } else {
                    result = new net.minecraft.world.item.ItemStack(Items.OAK_SAPLING, 2);
                }

                player.getInventory().add(result);
                MossData data = player.getData(ModAttachments.MOSS_DATA);
                data.addPosition(pos);

                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    50, 1.0, 0.5, 1.0, 0.1);
                level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_moss_teleport")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                MossData data = player.getData(ModAttachments.MOSS_DATA);

                List<BlockPos> positions = data.getPositions();
                if (positions.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cНет узлов мицелия!"));
                    return 0;
                }

                BlockPos best = positions.stream()
                    .filter(p -> p.distSqr(player.blockPosition()) > 25)
                    .min((a, b) -> Integer.compare(
                        (int) a.distSqr(player.blockPosition()),
                        (int) b.distSqr(player.blockPosition())))
                    .orElse(positions.get(0));

                boolean hasBoneMeal = player.getInventory().hasAnyOf(
                    java.util.Set.of(Items.BONE_MEAL));
                if (!hasBoneMeal) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cНужна костная мука!"));
                    return 0;
                }
                player.getInventory().clearOrCountMatchingItems(
                    s -> s.is(Items.BONE_MEAL), 1, player.inventoryMenu.getCraftSlots());

                player.teleportTo(best.getX() + 0.5, best.getY() + 1, best.getZ() + 0.5);
                level.sendParticles(ParticleTypes.PORTAL,
                    best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5,
                    40, 0.5, 1.0, 0.5, 0.2);
                level.playSound(null, best, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.1f);
                return 1;
            }));

        dispatcher.register(Commands.literal("chaos_addon_spore_fog")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                int radius = 10;

                List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(radius),
                    e -> e.isAlive());

                for (LivingEntity e : entities) {
                    boolean isAlly = (e instanceof net.minecraft.world.entity.TamableAnimal animal && animal.isTame())
                        || e.getTags().contains("chaos_gardener_pet")
                        || e.getTags().contains("chaos_hive_ally");
                    if (isAlly) {
                        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, true));
                    } else if (e != player) {
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 0, false, true));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 1, false, true));
                    }
                }

                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1, false, true));

                level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    80, radius * 0.5, 3.0, radius * 0.5, 0.04);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    60, radius * 0.4, 2.5, radius * 0.4, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.7f, 0.8f);
                return 1;
            }));
    }
}
