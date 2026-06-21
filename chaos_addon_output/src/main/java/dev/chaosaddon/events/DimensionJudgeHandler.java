package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import dev.chaosaddon.util.SeismicSenseHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Handles Dimension Judge passives:
 * - all_seeing_eye:    ping nearby players + hostile mobs showing direction
 * - no_totems:         remove totems of undying from inventory each tick
 * - no_regen_potion:   block Regeneration effect via MobEffectEvent.Applicable
 */
public class DimensionJudgeHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // ── all_seeing_eye: Всевидящее Oko — ping nearby entities every 3s ──
        if (OriginHelper.hasPower(player, "chaos_addon:dimension_judge/all_seeing_eye")
                && player.tickCount % 60 == 0) {
            // Ping hostile mobs + players
            SeismicSenseHelper.pingNearbyEntities(player, level, 50,
                e -> e instanceof Mob mob && mob.getTarget() != null
                    || (e instanceof Player p && p != player),
                "§4⚖ Всевидящее Oko: ", ChatFormatting.DARK_RED);

            // Show HP of all nearby living entities in chat (once every 3s)
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(30),
                e -> e != player && e.isAlive());
            if (!nearby.isEmpty()) {
                LivingEntity nearest = nearby.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                    .orElse(null);
                if (nearest != null) {
                    player.displayClientMessage(
                        Component.literal(
                            "§4⚖ Oko: §c" + nearest.getType().getDescriptionId()
                            + " §7| ❤ " + String.format("%.1f", nearest.getHealth())
                            + "/" + String.format("%.1f", nearest.getMaxHealth()))
                            .withStyle(ChatFormatting.DARK_RED), true);
                }
            }
        }

        // ── no_totems: remove totems from both hands every second ──
        if (OriginHelper.hasPower(player, "chaos_addon:dimension_judge/no_totems")
                && player.tickCount % 20 == 0) {
            removeItemFromPlayer(player, level, Items.TOTEM_OF_UNDYING,
                "§4⚖ Судья не нуждается в тотемах — предмет выброшен!");
        }

        // ── no_regen_potion: strip Regeneration effect every 5 ticks ──
        if (player.tickCount % 5 == 0) {
            applyNoRegenPotion(player);
        }
    }

    /** Drop matching item from all inventory slots onto the ground. */
    private static void removeItemFromPlayer(ServerPlayer player, ServerLevel level,
            net.minecraft.world.item.Item item, String msg) {
        boolean found = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                    player.getX(), player.getY() + 0.5, player.getZ(), stack.copy()));
                inv.setItem(i, ItemStack.EMPTY);
                found = true;
            }
        }
        if (found) {
            player.sendSystemMessage(Component.literal(msg));
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLASH,
                    player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

    // ── no_regen_potion: remove Regeneration effect every 5 ticks ──
    // (MobEffectEvent.Applicable.Result.DENY is not available in NeoForge 21.1.x;
    //  the tick-removal approach is equivalent and reliable)
    private static void applyNoRegenPotion(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/no_regen_potion")) return;
        if (player.hasEffect(MobEffects.REGENERATION)) {
            player.removeEffect(MobEffects.REGENERATION);
            player.displayClientMessage(
                Component.literal("§4⚖ Регенерация запрещена Судьёй.").withStyle(ChatFormatting.DARK_RED), true);
        }
    }
}
