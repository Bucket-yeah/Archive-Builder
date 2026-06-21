package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * chaos_addon_ore_extract  — "Вытягивание Руды"   (LMB primary)
 * chaos_addon_rock_shift   — "Сдвиг Породы"       (RMB secondary)
 * chaos_addon_stone_fist   — "Каменный Кулак"     (ternary)
 * chaos_addon_call_of_depths — "Зов Недр"         (ternary ULTIMATE, 5-min cooldown)
 */
public class GeomancerCommands {

    private static final Random RNG = new Random();

    private static final Map<net.minecraft.world.level.block.Block, net.minecraft.world.item.Item> ORE_DROPS = Map.of(
        Blocks.IRON_ORE,            Items.RAW_IRON,
        Blocks.GOLD_ORE,            Items.RAW_GOLD,
        Blocks.DIAMOND_ORE,         Items.DIAMOND,
        Blocks.EMERALD_ORE,         Items.EMERALD,
        Blocks.COAL_ORE,            Items.COAL,
        Blocks.COPPER_ORE,          Items.RAW_COPPER,
        Blocks.REDSTONE_ORE,        Items.REDSTONE,
        Blocks.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND,
        Blocks.DEEPSLATE_IRON_ORE,  Items.RAW_IRON,
        Blocks.NETHER_GOLD_ORE,     Items.GOLD_NUGGET
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ── "Вытягивание Руды": pull 1-2 resources from ore up to 10 blocks ──
        dispatcher.register(Commands.literal("chaos_addon_ore_extract")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Vec3 look = player.getLookAngle();
                BlockPos target = null;
                net.minecraft.world.level.block.Block oreBlock = null;

                for (int step = 1; step <= 10; step++) {
                    Vec3 pt = player.getEyePosition().add(look.scale(step));
                    BlockPos pos = new BlockPos((int) pt.x, (int) pt.y, (int) pt.z);
                    BlockState bs = level.getBlockState(pos);
                    if (ORE_DROPS.containsKey(bs.getBlock())) {
                        target = pos;
                        oreBlock = bs.getBlock();
                        break;
                    }
                }

                if (target == null || oreBlock == null) {
                    player.sendSystemMessage(Component.literal("§cНет руды в прицеле."));
                    return 0;
                }

                player.hurt(player.damageSources().generic(), 1.0f);

                net.minecraft.world.item.Item drop = ORE_DROPS.get(oreBlock);
                int count = 1 + (RNG.nextFloat() < 0.5f ? 1 : 0);
                player.getInventory().add(new ItemStack(drop, count));

                level.sendParticles(ParticleTypes.SMOKE,
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    15, 0.4, 0.4, 0.4, 0.08);
                level.sendParticles(ParticleTypes.GLOW,
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.0);
                level.playSound(null, target, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 1.3f);
                return 1;
            }));

        // ── "Сдвиг Породы": tunnel 15 blocks in look direction ──
        dispatcher.register(Commands.literal("chaos_addon_rock_shift")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Vec3 look = player.getLookAngle().normalize();
                float cost = 0;

                for (int step = 1; step <= 15; step++) {
                    Vec3 pt = player.getEyePosition().add(look.scale(step));
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos pos = new BlockPos(
                                (int) pt.x + dx, (int) pt.y + dy, (int) pt.z);
                            BlockState bs = level.getBlockState(pos);
                            if (!bs.isAir() && !bs.liquid()
                                    && bs.getBlock() != Blocks.BEDROCK) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                                cost += 0.5f;
                            }
                        }
                    }
                }

                player.hurt(player.damageSources().generic(), Math.min(cost, 8.0f));

                for (int step = 1; step <= 15; step++) {
                    Vec3 pt = player.getEyePosition().add(look.scale(step));
                    level.sendParticles(ParticleTypes.SMOKE,
                        pt.x, pt.y, pt.z, 5, 0.3, 0.3, 0.3, 0.05);
                }
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 0.6f);
                return 1;
            }));

        // ── "Каменный Кулак": shockwave knockback + damage in look direction ──
        dispatcher.register(Commands.literal("chaos_addon_stone_fist")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();

                Vec3 look = player.getLookAngle().normalize();
                List<LivingEntity> targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(5).move(look.scale(3)),
                    e -> e != player && e.isAlive());

                for (LivingEntity e : targets) {
                    e.hurt(player.damageSources().magic(), 4.0f);
                    e.setDeltaMovement(
                        look.x * 1.5 + RNG.nextGaussian() * 0.2,
                        0.6 + Math.abs(RNG.nextGaussian() * 0.1),
                        look.z * 1.5 + RNG.nextGaussian() * 0.2
                    );
                }

                level.sendParticles(ParticleTypes.SONIC_BOOM,
                    player.getX() + look.x * 3, player.getY() + 1.0, player.getZ() + look.z * 3,
                    1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.SMOKE,
                    player.getX() + look.x * 2, player.getY() + 1.0, player.getZ() + look.z * 2,
                    25, 0.5, 0.5, 0.5, 0.1);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.8f);
                return 1;
            }));

        // ── "Зов Недр": ULTIMATE — seismic shockwave, knock up + debuff all in 12 blocks, self-buff ──
        dispatcher.register(Commands.literal("chaos_addon_call_of_depths")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                int radius = 12;

                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    e -> e != player && e.isAlive());

                for (LivingEntity e : targets) {
                    e.hurt(player.damageSources().magic(), 4.0f);
                    e.setDeltaMovement(
                        e.getDeltaMovement().x * 0.2 + (RNG.nextDouble() - 0.5) * 0.5,
                        0.7 + RNG.nextDouble() * 0.5,
                        e.getDeltaMovement().z * 0.2 + (RNG.nextDouble() - 0.5) * 0.5
                    );
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
                }

                // Self: stone armor + regen for 20 seconds
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 2, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1, false, true));

                level.sendParticles(ParticleTypes.EXPLOSION,
                    player.getX(), player.getY(), player.getZ(),
                    8, radius * 0.4, 0.5, radius * 0.4, 0.2);
                level.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    80, radius * 0.4, 2.0, radius * 0.4, 0.08);
                level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    30, radius * 0.3, 1.0, radius * 0.3, 0.0);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.4f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 0.3f);

                player.displayClientMessage(
                    Component.literal("§6⛏ §lЗов Недр§r§6! §7" + targets.size() + " существ отброшено."), true);
                return 1;
            }));
    }
}
