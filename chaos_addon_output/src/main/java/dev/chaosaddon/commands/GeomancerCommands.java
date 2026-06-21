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
 * chaos_addon_stone_claw    — "Каменный Коготь"  (LMB primary)
 * chaos_addon_tunnel_dash   — "Туннельный Рывок" (RMB secondary)
 * chaos_addon_ore_extract   — "Вытягивание Руды" (LMB primary, utility)
 * chaos_addon_rock_shift    — "Сдвиг Породы"     (RMB secondary, tunnel dig)
 * chaos_addon_stone_fist    — "Каменный Кулак"   (ternary)
 * chaos_addon_call_of_depths — "Зов Недр"        (ternary ULTIMATE)
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

    private static boolean isStoneBlock(BlockState bs) {
        return bs.getBlock() == Blocks.STONE
            || bs.getBlock() == Blocks.DEEPSLATE
            || bs.getBlock() == Blocks.COBBLESTONE
            || bs.getBlock() == Blocks.MOSSY_COBBLESTONE
            || bs.getBlock() == Blocks.STONE_BRICKS
            || bs.getBlock() == Blocks.GRANITE
            || bs.getBlock() == Blocks.DIORITE
            || bs.getBlock() == Blocks.ANDESITE
            || bs.getBlock() == Blocks.BLACKSTONE
            || bs.getBlock() == Blocks.BASALT
            || ORE_DROPS.containsKey(bs.getBlock());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ── "Каменный Коготь": melee attack, break stone, bonus on stone ──
        dispatcher.register(Commands.literal("chaos_addon_stone_claw")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                Vec3 look = player.getLookAngle().normalize();

                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(3).move(look.scale(2)),
                    e -> e != player && e.isAlive());

                float bonusDamage = dev.chaosaddon.config.ChaosAddonConfig.get().geoClawBonusDamage;

                for (LivingEntity e : targets) {
                    BlockPos eGround = new BlockPos(
                        (int) Math.floor(e.getX()),
                        (int) Math.floor(e.getY()) - 1,
                        (int) Math.floor(e.getZ()));
                    boolean onStone = isStoneBlock(level.getBlockState(eGround));
                    float dmg = 4.0f + (onStone ? bonusDamage : 0f);
                    e.hurt(player.damageSources().magic(), dmg);
                }

                // Break first stone or ore block in look direction
                for (int step = 1; step <= 4; step++) {
                    Vec3 pt = player.getEyePosition().add(look.scale(step));
                    BlockPos pos = BlockPos.containing(pt.x, pt.y, pt.z);
                    BlockState bs = level.getBlockState(pos);
                    if (isStoneBlock(bs)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        level.sendParticles(ParticleTypes.SMOKE,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            20, 0.3, 0.3, 0.3, 0.05);
                        break;
                    }
                }

                level.playSound(null, player.blockPosition(),
                    SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);

                if (!targets.isEmpty()) {
                    player.displayClientMessage(
                        Component.literal("§6⛏ §lКаменный Коготь — §r§6" + targets.size() + " попад.!"), true);
                }
                return 1;
            }));

        // ── "Туннельный Рывок": teleport through thin stone layer ──
        dispatcher.register(Commands.literal("chaos_addon_tunnel_dash")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                ServerLevel level = player.serverLevel();
                Vec3 look = player.getLookAngle().normalize();
                int maxThickness = dev.chaosaddon.config.ChaosAddonConfig.get().geoDashMaxThickness;

                boolean inStone = false;
                int stoneCount = 0;
                BlockPos destination = null;

                for (int step = 1; step <= 20 && destination == null; step++) {
                    Vec3 pt = player.getEyePosition().add(look.scale(step));
                    BlockPos pos = BlockPos.containing(pt.x, pt.y, pt.z);
                    BlockState bs = level.getBlockState(pos);
                    boolean solid = !bs.isAir() && !bs.liquid()
                        && bs.getBlock() != Blocks.BEDROCK
                        && bs.getBlock() != Blocks.AIR;

                    if (solid && !inStone) {
                        inStone = true;
                        stoneCount = 1;
                    } else if (solid && inStone) {
                        if (++stoneCount > maxThickness) break;
                    } else if (!solid && inStone) {
                        // Found exit — verify headroom
                        if (!level.getBlockState(pos.above()).isSolid()) {
                            destination = pos;
                        }
                    }
                }

                if (destination == null) {
                    player.sendSystemMessage(Component.literal("§c⛏ Порода слишком толстая!"));
                    return 0;
                }

                player.teleportTo(
                    destination.getX() + 0.5,
                    destination.getY(),
                    destination.getZ() + 0.5);

                level.sendParticles(ParticleTypes.SMOKE,
                    destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5,
                    40, 0.6, 0.5, 0.6, 0.06);
                level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                    destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5,
                    10, 0.3, 0.5, 0.3, 0.0);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.3f);

                player.displayClientMessage(
                    Component.literal("§6⛏ §lТуннельный Рывок!"), true);
                return 1;
            }));

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
