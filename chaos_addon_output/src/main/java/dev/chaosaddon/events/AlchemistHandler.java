package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.Random;

/**
 * Handles all passive powers for Alchemical Monk:
 * - Material Imbalance: blocks use of crafting table, furnace, anvil, etc.
 * - Price of Creation: deal 1 HP on any craft
 * - Cycle of Substances: 5% chance for random drop when breaking blocks
 */
public class AlchemistHandler {

    private static final Random RNG = new Random();

    private static final List<Block> CRAFTING_BLOCKS = List.of(
        Blocks.CRAFTING_TABLE,
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.ANVIL,
        Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL,
        Blocks.GRINDSTONE,
        Blocks.LOOM,
        Blocks.CARTOGRAPHY_TABLE,
        Blocks.FLETCHING_TABLE,
        Blocks.SMITHING_TABLE,
        Blocks.STONECUTTER,
        Blocks.ENCHANTING_TABLE
    );

    private static final ItemStack[] CYCLE_DROPS = {
        new ItemStack(Items.IRON_INGOT, 2),
        new ItemStack(Items.GOLD_INGOT, 2),
        new ItemStack(Items.EMERALD),
        new ItemStack(Items.LAPIS_LAZULI, 4),
        new ItemStack(Items.REDSTONE, 4),
        new ItemStack(Items.COAL, 3),
        new ItemStack(Items.QUARTZ, 4),
        new ItemStack(Items.BONE, 2),
        new ItemStack(Items.SLIME_BALL, 2),
        new ItemStack(Items.GUNPOWDER, 2),
        new ItemStack(Items.FLINT, 3),
        new ItemStack(Items.CLAY_BALL, 4)
    };

    /**
     * Material Imbalance: block vanilla crafting stations.
     */
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/material_imbalance")) return;

        Block block = player.level().getBlockState(event.getPos()).getBlock();
        if (CRAFTING_BLOCKS.contains(block)) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§c⚗ Алхимический монах не использует обычные станции — только кровь и трансмутацию."));
        }
    }

    /**
     * Price of Creation: every craft costs 1 HP.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/price_of_creation")) return;
        if (event.getCrafting().isEmpty()) return;

        if (player.getHealth() <= 2.0f) {
            event.getCrafting().shrink(event.getCrafting().getCount());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§c⚗ Слишком мало HP — цена крови слишком высока!"));
            return;
        }

        player.hurt(player.damageSources().generic(), 1.0f);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.5, 0.4, 0.05);
        }
    }

    /**
     * Cycle of Substances: 5% chance to replace block drops with a random resource.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/cycle_of_substances")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        if (RNG.nextFloat() >= 0.05f) return;

        event.setCanceled(false);

        BlockPos pos = event.getPos();
        ItemStack randomDrop = CYCLE_DROPS[RNG.nextInt(CYCLE_DROPS.length)].copy();
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, randomDrop));

        level.sendParticles(ParticleTypes.GLOW,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            12, 0.4, 0.4, 0.4, 0.0);
        level.playSound(null, pos, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 0.4f, 1.8f);
    }
}
