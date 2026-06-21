package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import dev.chaosaddon.util.SeismicSenseHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * Handles Deep Geomancer passives:
 * - ore_vision:     periodic ore-direction ping via actionbar
 * - earth_hearing:  seismic sense for nearby mobs via SeismicSenseHelper
 * - altitude_damage (repurposed as stone_contact): debuff when not on stone/earth
 * - no_pickaxes:    breaks non-netherite pickaxes instantly; slows netherite
 */
public class DeepGeomancerHandler {

    private static final Set<Block> ORE_BLOCKS = Set.of(
        Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.DEEPSLATE_COPPER_ORE, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.ANCIENT_DEBRIS
    );

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // ── ore_vision: Жилы Земли — direction ping to nearest ore every 4s ──
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/ore_vision")
                && player.tickCount % cfg.geoOreVisionInterval == 0) {
            BlockPos center = player.blockPosition();
            int radius = cfg.geoOreVisionRadius;
            BlockPos nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                if (ORE_BLOCKS.contains(level.getBlockState(pos).getBlock())) {
                    double distSq = pos.distSqr(center);
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = pos.immutable();
                    }
                }
            }
            if (nearest != null) {
                double dx = nearest.getX() + 0.5 - player.getX();
                double dz = nearest.getZ() + 0.5 - player.getZ();
                double dy = nearest.getY() + 0.5 - player.getY();
                double angle = Math.toDegrees(Math.atan2(dz, dx));
                if (angle < 0) angle += 360;
                String compass;
                if (angle < 22.5 || angle >= 337.5) compass = "→ В";
                else if (angle < 67.5)  compass = "↘ ЮВ";
                else if (angle < 112.5) compass = "↓ Ю";
                else if (angle < 157.5) compass = "↙ ЮЗ";
                else if (angle < 202.5) compass = "← З";
                else if (angle < 247.5) compass = "↖ СЗ";
                else if (angle < 292.5) compass = "↑ С";
                else                    compass = "↗ СВ";
                int distBlocks = (int) Math.sqrt(nearestDistSq);
                String vertHint = dy < -3 ? " §8↓" + (int) Math.abs(dy) + "м"
                    : dy > 3 ? " §8↑" + (int) dy + "м" : "";
                player.displayClientMessage(
                    Component.literal("§6🪨 Жилы Земли: " + compass + vertHint + " §8(" + distBlocks + "м)")
                        .withStyle(ChatFormatting.GOLD), true);
                level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    player.getX(), player.getY() + 0.05, player.getZ(),
                    6, 0.4, 0.05, 0.4, 0.0);
            } else if (player.tickCount % 160 == 0) {
                player.displayClientMessage(
                    Component.literal("§8🪨 Жилы Земли: руда не найдена в радиусе " + radius + "м"), true);
            }
        }

        // ── earth_hearing: Земляной Слух — seismic sense for nearby mobs ──
        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/earth_hearing")
                && !player.isSprinting() && player.onGround()
                && player.tickCount % cfg.geoEarthHearingInterval == 0) {
            SeismicSenseHelper.pingNearbyEntities(player, level, cfg.geoEarthHearingRadius,
                e -> !(e instanceof Player),
                "§e⛰ Земляной Слух: ", ChatFormatting.YELLOW);
        }

        // ── altitude_damage (repurposed: Оторванность от Земли) ──
        // Standing on non-stone/earth surface → Slowness I; airborne → Weakness I
        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/altitude_damage")
                && player.tickCount % cfg.geoAltitudeDamageInterval == 0) {
            BlockPos below = player.blockPosition().below();
            BlockState stateBelow = level.getBlockState(below);
            boolean onEarth = stateBelow.is(BlockTags.BASE_STONE_OVERWORLD)
                || stateBelow.is(BlockTags.BASE_STONE_NETHER)
                || stateBelow.is(BlockTags.DIRT)
                || stateBelow.is(Blocks.GRAVEL)
                || stateBelow.is(Blocks.COBBLESTONE)
                || stateBelow.is(Blocks.COBBLED_DEEPSLATE)
                || stateBelow.is(Blocks.SMOOTH_STONE)
                || stateBelow.is(Blocks.MOSS_BLOCK)
                || stateBelow.is(Blocks.COARSE_DIRT)
                || stateBelow.is(Blocks.ROOTED_DIRT);
            if (!player.onGround()) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false));
            } else if (!onEarth) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false));
            }
        }

        // ── no_pickaxes (tick component): apply Mining Fatigue II to netherite pickaxe ──
        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/no_pickaxes")) {
            ItemStack held = player.getMainHandItem();
            if (held.is(Items.NETHERITE_PICKAXE)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 1, true, false));
            }
        }
    }

    // ── no_pickaxes (break component): cancel non-netherite pickaxe breaks + destroy the pickaxe ──
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/no_pickaxes")) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof PickaxeItem)) return;
        if (held.is(Items.NETHERITE_PICKAXE)) return; // netherite handled by tick (fatigue)

        // Cancel the break and destroy the pickaxe
        event.setCanceled(true);
        held.hurtAndBreak(held.getMaxDamage(), player, EquipmentSlot.MAINHAND);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.4, 0.4, 0.1);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        player.sendSystemMessage(Component.literal(
            "§c⛏ Кирка сломалась! Геомант §lкопает руками§r§c, а не инструментами."));
    }
}
