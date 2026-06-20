package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles Phantom Archaeologist passives:
 * - Ephemeral Inventory: items don't drop on death; inventory saved and restored on respawn
 * - Mind Backup: 60% of XP is preserved on death
 */
public class ArchaeologistHandler {

    private static final String KEY_HAS_SAVE = "chaos_arch_has_save";
    private static final String KEY_INVENTORY = "chaos_arch_inventory";
    private static final String KEY_TOTAL_XP  = "chaos_arch_total_xp";

    /**
     * On death: save inventory + XP to persistentData before the player's items scatter.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean hasEphemeral  = OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/ephemeral_inventory");
        boolean hasMindBackup = OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/mind_backup");
        if (!hasEphemeral && !hasMindBackup) return;

        CompoundTag data = player.getPersistentData();
        data.putBoolean(KEY_HAS_SAVE, true);

        if (hasEphemeral) {
            ListTag invList = new ListTag();
            player.getInventory().save(invList);
            data.put(KEY_INVENTORY, invList);
        }

        if (hasMindBackup) {
            data.putInt(KEY_TOTAL_XP, (int)(player.totalExperience * 0.6));
        }
    }

    /**
     * Cancel item drops on death for Ephemeral Inventory.
     */
    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/ephemeral_inventory")) return;
        event.setCanceled(true);
    }

    /**
     * On respawn (clone): restore saved inventory and XP from the original player's persistentData.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer original)) return;

        CompoundTag data = original.getPersistentData();
        if (!data.getBoolean(KEY_HAS_SAVE)) return;

        newPlayer.getPersistentData().merge(data);

        boolean hasEphemeral  = OriginHelper.hasPower(original, "chaos_addon:phantom_archaeologist/ephemeral_inventory");
        boolean hasMindBackup = OriginHelper.hasPower(original, "chaos_addon:phantom_archaeologist/mind_backup");

        if (hasEphemeral && data.contains(KEY_INVENTORY)) {
            ListTag invList = data.getList(KEY_INVENTORY, 10);
            newPlayer.getInventory().load(invList);
        }

        if (hasMindBackup && data.contains(KEY_TOTAL_XP)) {
            newPlayer.giveExperiencePoints(data.getInt(KEY_TOTAL_XP));
        }

        CompoundTag newData = newPlayer.getPersistentData();
        newData.remove(KEY_HAS_SAVE);
        newData.remove(KEY_INVENTORY);
        newData.remove(KEY_TOTAL_XP);

        newPlayer.getPersistentData().putInt("chaos_arch_spawn_lock", 600); // 30s pickup lock
        newPlayer.sendSystemMessage(Component.literal(
            "§b📦 Данные восстановлены из облачного бэкапа. §7(Синхронизация 30с...)"));
    }

    /** Count down the post-death spawn lock each tick. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int lock = player.getPersistentData().getInt("chaos_arch_spawn_lock");
        if (lock > 0) {
            player.getPersistentData().putInt("chaos_arch_spawn_lock", lock - 1);
        }
    }

    /** Block item pickup during spawn lock. */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/ephemeral_inventory")) return;
        int lock = player.getPersistentData().getInt("chaos_arch_spawn_lock");
        if (lock > 0) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
            if (player.tickCount % 60 == 0) {
                player.displayClientMessage(Component.literal(
                    "§b🔒 Десинхронизация данных — подбор заблокирован на §e" + (lock / 20) + "с"), false);
            }
        }
    }
}
