package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles Phantom Archaeologist passives:
 * - Ephemeral Inventory: items don't drop on death; inventory saved and restored on respawn
 * - Mind Backup: 60% of XP is preserved on death
 * - chunk_vision: show chunk coordinates + load state in actionbar
 * - no_elytra: elytra dropped from chest slot every tick
 * - sound_sensitivity: explosions deal 50% more damage
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

        newPlayer.getPersistentData().putInt("chaos_arch_spawn_lock", ChaosAddonConfig.get().archSpawnLockTicks);
        newPlayer.sendSystemMessage(Component.literal(
            "§b📦 Данные восстановлены из облачного бэкапа. §7(Синхронизация 30с...)"));
    }

    /** Count down the post-death spawn lock + handle chunk_vision + no_elytra each tick. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Spawn lock countdown
        int lock = player.getPersistentData().getInt("chaos_arch_spawn_lock");
        if (lock > 0) {
            player.getPersistentData().putInt("chaos_arch_spawn_lock", lock - 1);
        }

        // ── chunk_vision → "Чутьё Раскопок": detect nearby generated structures ──
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        if (OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/chunk_vision")
                && player.tickCount % cfg.chunkVisionInterval == 0) {
            BlockPos pos = player.blockPosition();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String foundStructure = null;
            int foundDist = Integer.MAX_VALUE;
            outer: for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    try {
                        net.minecraft.world.level.chunk.ChunkAccess chunk =
                            level.getChunk(chunkX + dx, chunkZ + dz);
                        for (var entry : chunk.getAllStarts().entrySet()) {
                            if (!entry.getValue().isValid()) continue;
                            var reg = level.registryAccess()
                                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
                            var key = reg.getKey(entry.getKey());
                            if (key != null) {
                                int d = Math.abs(dx) + Math.abs(dz);
                                if (d < foundDist) {
                                    foundDist = d;
                                    foundStructure = key.getPath().replace("_", " ");
                                }
                                break outer;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (foundStructure != null) {
                player.displayClientMessage(
                    Component.literal("§6🏺 Чутьё раскопок: §e" + foundStructure
                        + " §8(~" + (foundDist * 16) + " блоков)")
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                if (player.tickCount % 300 == 0) {
                    player.displayClientMessage(
                        Component.literal("§8🏺 Чутьё раскопок: §7нет структур поблизости"), true);
                }
            }
        }

        // ── no_elytra: remove elytra from chest slot ──
        if (OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/no_elytra")
                && player.tickCount % 20 == 0) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.isEmpty() && chest.is(Items.ELYTRA)) {
                level.addFreshEntity(new ItemEntity(level,
                    player.getX(), player.getY() + 0.5, player.getZ(), chest.copy()));
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.sendSystemMessage(Component.literal(
                    "§b📦 Археолог не нуждается в элитрах — боится высоты!"));
            }
            // Stop gliding if somehow active
            if (player.isFallFlying()) {
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.5, 0.1, 0.5));
            }
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

    // ── sound_sensitivity: explosions deal 50% more damage to the Archaeologist ──
    @SubscribeEvent
    public static void onSoundSensitivity(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:phantom_archaeologist/sound_sensitivity")) return;
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            event.setAmount(event.getAmount() * ChaosAddonConfig.get().soundSensitivityMultiplier);
            player.displayClientMessage(
                Component.literal("§b📢 Звуковая чувствительность: взрывной урон ×1.5!")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }
}
