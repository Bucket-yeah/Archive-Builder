package dev.chaosaddon.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reusable "hard debuff" pattern — cancels item-use when a player has a given power.
 *
 * Extracted from ChaosEngineerHandler.onNoPotions.
 *
 * Consumers for the following dead markers (P2 race-by-race):
 *   - Alchemical Monk:   no_armor       → block ArmorItem equip
 *   - Dimension Judge:   no_totems      → block Totem of Undying use
 *   - Dimension Judge:   no_regen_potion → block Regeneration potions
 *   - Wandering Gardener: no_weapons    → block sword/axe/bow use
 *   - Deep Geomancer:    no_pickaxes    → block pickaxe use
 *
 * Usage example:
 * <pre>
 *   {@literal @}SubscribeEvent
 *   public static void onNoArmor(LivingEntityUseItemEvent.Start event) {
 *       HardDebuffHelper.blockItemUse(event,
 *           "chaos_addon:alchemical_monk/no_armor",
 *           stack -> stack.getItem() instanceof ArmorItem,
 *           player -> player.sendSystemMessage(
 *               Component.literal("§c⚗ Броня мешает трансмутации!")));
 *   }
 * </pre>
 */
public final class HardDebuffHelper {

    private HardDebuffHelper() {}

    /**
     * Cancel item-use if the player has {@code powerId} and {@code itemCheck} returns true.
     * Runs {@code onBlocked} after cancelling (particles, message, damage, etc.).
     *
     * @param event     LivingEntityUseItemEvent.Start to intercept
     * @param powerId   Full power ID, e.g. "chaos_addon:chaos_engineer/no_potions"
     * @param itemCheck Predicate on the ItemStack — return true to block
     * @param onBlocked Side-effect executed after cancellation
     */
    public static void blockItemUse(
            LivingEntityUseItemEvent.Start event,
            String powerId,
            Predicate<ItemStack> itemCheck,
            Consumer<ServerPlayer> onBlocked) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, powerId)) return;
        if (!itemCheck.test(event.getItem())) return;
        event.setCanceled(true);
        if (player.level() instanceof ServerLevel) {
            onBlocked.accept(player);
        }
    }

    /**
     * Convenience: block a single specific item type.
     */
    public static void blockItemUse(
            LivingEntityUseItemEvent.Start event,
            String powerId,
            Item itemToBlock,
            Consumer<ServerPlayer> onBlocked) {
        blockItemUse(event, powerId, stack -> stack.is(itemToBlock), onBlocked);
    }

    /**
     * Convenience: block all items matching a tag (e.g. ItemTags.SWORDS).
     */
    public static void blockItemTag(
            LivingEntityUseItemEvent.Start event,
            String powerId,
            TagKey<Item> tag,
            Consumer<ServerPlayer> onBlocked) {
        blockItemUse(event, powerId, stack -> stack.is(tag), onBlocked);
    }
}
