package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.MossData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Handles Mycelial Symbiont passives:
 * - moss_network:    nearby moss nodes buff the player; placed blocks register as nodes
 * - moss_tether:     player > 20 blocks from all nodes → 0.5 HP/s damage
 * - water_kills_moss: water/rain exposure → damage + clear all moss nodes + remove buffs
 */
public class MycelialSymbiontHandler {


    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        boolean hasNetwork = OriginHelper.hasPower(player, "chaos_addon:mycelial_symbiont/moss_network");
        // moss_tether is now the active teleport; moss_escape is the passive damage drawback
        boolean hasTether  = OriginHelper.hasPower(player, "chaos_addon:mycelial_symbiont/moss_escape");
        boolean hasWater   = OriginHelper.hasPower(player, "chaos_addon:mycelial_symbiont/water_kills_moss");

        if (!hasNetwork && !hasTether && !hasWater) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        MossData data = player.getData(ModAttachments.MOSS_DATA);
        List<BlockPos> nodes = data.getPositions();
        BlockPos playerPos = player.blockPosition();

        // ── water_kills_moss: water/rain → damage + clear network ──
        if (hasWater) {
            boolean inWater = player.isInWater();
            boolean rainExposed = level.isRaining() && level.canSeeSky(playerPos);
            if (inWater || rainExposed) {
                if (player.tickCount % cfg.waterKillsMossInterval == 0) {
                    player.hurt(player.damageSources().drown(), cfg.waterKillsMossDamage);
                    // Clear all moss nodes
                    if (!nodes.isEmpty()) {
                        nodes.clear();
                        // Persist the cleared state by setting data again
                        // (MossData is mutable, modifications to list should persist via attachment)
                        player.sendSystemMessage(Component.literal(
                            "§c💧 Вода уничтожила мицелиальную сеть! Все узлы потеряны."));
                        // Remove network buffs
                        player.removeEffect(MobEffects.NIGHT_VISION);
                        player.removeEffect(MobEffects.REGENERATION);
                        player.removeEffect(MobEffects.MOVEMENT_SPEED);
                    }
                    if (player.tickCount % cfg.waterKillsMossWarnInterval == 0) {
                        player.displayClientMessage(
                            Component.literal("§c💧 Вода разрушает сеть мха! Найди укрытие!")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
                return; // Skip buff/tether logic while in water
            }
        }

        // ── moss_network: buff player when near any moss node ──
        if (hasNetwork && player.tickCount % 20 == 0) {
            boolean nearNode = data.isNearAnyMoss(playerPos, cfg.mossBuffRadius);
            if (nearNode) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
                // Moss network sense: show number of nodes + any hostile nearby
                List<net.minecraft.world.entity.LivingEntity> hostiles = level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    player.getBoundingBox().inflate(30),
                    e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player)
                        && e instanceof net.minecraft.world.entity.Mob mob
                        && mob.getTarget() != null);
                String hostileInfo = hostiles.isEmpty() ? "§a✓ Чисто" : "§c⚠ Угроза×" + hostiles.size();
                player.displayClientMessage(
                    Component.literal("§2🍄 Сеть: §a" + nodes.size() + " узлов §8| " + hostileInfo), true);
            } else if (!nodes.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("§8🍄 Сеть: §7далеко от узлов (" + nodes.size() + " активных)"), true);
            }
        }

        // ── moss_tether: damage if > mossMaxRange blocks from all nodes ──
        if (hasTether && player.tickCount % cfg.mossDetachInterval == 0) {
            if (!nodes.isEmpty() && !data.isNearAnyMoss(playerPos, cfg.mossMaxRange)) {
                player.hurt(player.damageSources().magic(), cfg.mossDetachDamage);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    8, 0.4, 0.5, 0.4, 0.02);
                if (player.tickCount % (cfg.mossDetachInterval * 3) == 0) {
                    player.displayClientMessage(
                        Component.literal("§c🍄 Привязь: слишком далеко от мицелиальной сети!")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    // ── moss_network: register placed blocks as moss network nodes ──
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:mycelial_symbiont/moss_network")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState placedState = event.getState();

        // Only register solid blocks (not flowers, torches, etc.)
        if (!placedState.isSolidRender(level, pos)) return;

        MossData data = player.getData(ModAttachments.MOSS_DATA);
        data.addPosition(pos);

        // Visually coat placed block with moss particles
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
            6, 0.4, 0.3, 0.4, 0.02);

        // Convert adjacent stone/cobble to mossy variants as visual feedback
        for (BlockPos adj : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
            BlockState adjState = level.getBlockState(adj);
            Block adjBlock = adjState.getBlock();
            if (adjBlock == Blocks.COBBLESTONE) {
                level.setBlock(adj, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3);
            } else if (adjBlock == Blocks.STONE_BRICKS) {
                level.setBlock(adj, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
            }
        }
    }
}
