package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Random;

public class AlchemistHandler {

    private static final Random RNG = new Random();

    private static final List<Block> CRAFTING_BLOCKS = List.of(
        Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE,
        Blocks.SMOKER, Blocks.ANVIL, Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL, Blocks.GRINDSTONE, Blocks.LOOM,
        Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE,
        Blocks.SMITHING_TABLE, Blocks.STONECUTTER
        // Enchanting Table is intentionally ALLOWED — used as Transmutation Table
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
        new ItemStack(Items.CLAY_BALL, 4),
        new ItemStack(Items.DIAMOND),
        new ItemStack(Items.AMETHYST_SHARD, 3),
        new ItemStack(Items.COPPER_INGOT, 4)
    };

    // ── no_armor: tick check — drop non-leather armor from armor slots ──
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/no_armor")) return;
        if (player.tickCount % 20 != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        EquipmentSlot[] armorSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : armorSlots) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty()) continue;
            boolean isLeather = armor.getItem() instanceof ArmorItem armorItem
                && armorItem.getMaterial().equals(net.minecraft.world.item.ArmorMaterials.LEATHER);
            if (!isLeather) {
                level.addFreshEntity(new ItemEntity(level,
                    player.getX(), player.getY() + 0.5, player.getZ(), armor.copy()));
                player.setItemSlot(slot, ItemStack.EMPTY);
                player.sendSystemMessage(Component.literal(
                    "§c⚗ Монах носит только кожу — броня выброшена!").withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    /** Material Imbalance: block vanilla crafting stations. Enchanting Table = Transmutation Table. */
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/material_imbalance")) return;

        Block block = player.level().getBlockState(event.getPos()).getBlock();

        // Transmutation Table: Enchanting Table → transmute held item at HP cost
        if (block == Blocks.ENCHANTING_TABLE) {
            event.setCanceled(true);
            performTransmutation(player);
            return;
        }

        if (CRAFTING_BLOCKS.contains(block)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                "§c⚗ Алхимический монах не использует обычные станции — только стол трансмутации (стол чар)!"));
        }
    }

    /** T014: Transmutation Table — right-click enchanting table to transmute held item.
     *  Cost: 3 HP. Output: randomized valuable material. Cooldown: 10s via NBT. */
    private static void performTransmutation(ServerPlayer player) {
        if (player.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) player.level();

        // Cooldown check
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        long now = level.getGameTime();
        long lastTransmute = player.getPersistentData().getLong("chaos_transmute_time");
        if (now - lastTransmute < cfg.monkReactionCooldown) {
            long remaining = (cfg.monkReactionCooldown - (now - lastTransmute)) / 20;
            player.sendSystemMessage(Component.literal(
                "§c⚗ Трансмутация перезаряжается: §e" + remaining + "с"));
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                "§c⚗ Возьмите предмет в руку для трансмутации!"));
            return;
        }

        if (player.getHealth() <= cfg.monkTransmuteHpThreshold) {
            player.sendSystemMessage(Component.literal(
                "§c⚗ Мало HP для трансмутации! Нужно > 2❤."));
            return;
        }

        player.hurt(player.damageSources().generic(), cfg.monkTransmuteHpCost);

        // Determine output by input rarity
        String inputId = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(held.getItem()).toString();

        ItemStack output;
        if (inputId.contains("diamond") || inputId.contains("emerald")) {
            output = new ItemStack(Items.NETHERITE_SCRAP, RNG.nextInt(2) + 1);
        } else if (inputId.contains("gold") || inputId.contains("iron") || inputId.contains("quartz")) {
            output = new ItemStack(Items.DIAMOND, RNG.nextInt(3) + 1);
        } else if (inputId.contains("wood") || inputId.contains("stone") || inputId.contains("dirt")) {
            output = new ItemStack(Items.IRON_INGOT, RNG.nextInt(4) + 2);
        } else {
            output = CYCLE_DROPS[RNG.nextInt(CYCLE_DROPS.length)].copy();
        }

        // Consume held item (1 unit)
        held.shrink(1);
        player.getInventory().add(output);
        player.getPersistentData().putLong("chaos_transmute_time", now);

        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            40, 0.6, 1.0, 0.6, 0.08);
        level.playSound(null, player.blockPosition(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.3f);
        player.sendSystemMessage(Component.literal(
            "§d⚗ Трансмутация! §e" + held.getItem().getDescriptionId().replace("item.minecraft.", "")
            + " §7→ §a" + output.getItem().getDescriptionId().replace("item.minecraft.", "")
            + " x" + output.getCount() + " §7(-3❤)"));
    }

    /** Price of Creation: every craft costs 1 HP. Block if HP ≤ 2. */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/price_of_creation")) return;
        if (event.getCrafting().isEmpty()) return;

        if (player.getHealth() <= 2.0f) {
            event.getCrafting().shrink(event.getCrafting().getCount());
            player.sendSystemMessage(Component.literal(
                "§c⚗ Слишком мало HP — цена крови слишком высока!"));
            return;
        }

        // Double HP cost but also double output for potions
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()).toString();
        boolean isPotion = itemId.contains("potion");

        if (isPotion) {
            if (player.getHealth() <= 4.0f) {
                event.getCrafting().shrink(event.getCrafting().getCount());
                player.sendSystemMessage(Component.literal(
                    "§c⚗ Нужно > 2❤ для создания зелий!"));
                return;
            }
            player.hurt(player.damageSources().generic(), 2.0f);
            ItemStack bonus = event.getCrafting().copy();
            player.getInventory().add(bonus);
            player.sendSystemMessage(Component.literal(
                "§a⚗ Двойной выход зелий за двойную цену!"));
        } else {
            player.hurt(player.damageSources().generic(), 1.0f);
        }

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.5, 0.4, 0.05);
        }
    }

    /** Kill restores 0.5 HP (Price of Creation bonus). */
    @SubscribeEvent
    public static void onKillHeal(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/price_of_creation")) return;
        player.heal(0.5f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                player.getX(), player.getY() + 1.5, player.getZ(),
                3, 0.4, 0.3, 0.4, 0.0);
        }
    }

    /**
     * Potion-on-Block Reactions: when the Alchemical Monk right-clicks a block while
     * holding a splash/lingering potion, apply a thematic reaction based on the block type.
     */
    @SubscribeEvent
    public static void onPotionBlockReaction(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/cycle_of_substances")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack held = player.getMainHandItem();
        boolean isSplash = held.is(Items.SPLASH_POTION);
        boolean isLingering = held.is(Items.LINGERING_POTION);
        if (!isSplash && !isLingering) return;

        // Check cooldown
        ChaosAddonConfig aCfg = ChaosAddonConfig.get();
        long now = level.getGameTime();
        long lastReaction = player.getPersistentData().getLong("chaos_monk_reaction_cd");
        if (now - lastReaction < aCfg.monkReactionCooldown) return;

        BlockPos pos = event.getHitVec().getBlockPos();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

        boolean reacted = false;

        if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1.5f, false, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 600, 0, false, true));
            player.sendSystemMessage(Component.literal("§c⚗ Вулканическая реакция! §7Сила I 30с"));
            reacted = true;
        } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SNOW_BLOCK)) {
            level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(5),
                e -> e != player && e.isAlive())
                .forEach(e -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 2, false, true)));
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 2.0, 0.5, 2.0, 0.03);
            player.sendSystemMessage(Component.literal("§b⚗ Криогенная реакция! §7Враги заморожены 10с"));
            reacted = true;
        } else if (state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM)) {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                BlockPos p2 = pos.offset(dx, 1, dz);
                var bs = level.getBlockState(p2);
                if (bs.is(net.minecraft.tags.BlockTags.CROPS)
                        && bs.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock g) {
                    g.performBonemeal(level, level.random, p2, bs);
                }
            }
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 400, 1, false, true));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 20, 1.5, 0.5, 1.5, 0.03);
            player.sendSystemMessage(Component.literal("§a⚗ Флорная реакция! §7Растения выросли, Регенерация II 20с"));
            reacted = true;
        } else if (state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)) {
            ItemStack mineral = CYCLE_DROPS[RNG.nextInt(CYCLE_DROPS.length)].copy();
            level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, mineral));
            player.removeEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DIG_SPEED, 400, 1, false, true));
            level.sendParticles(ParticleTypes.GLOW,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 15, 0.4, 0.4, 0.4, 0.0);
            player.sendSystemMessage(Component.literal(
                "§8⚗ Геохимическая реакция! §7+" + mineral.getCount() + "x " + mineral.getHoverName().getString() + " + Шустрость II"));
            reacted = true;
        } else if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.ENCHANTING_TABLE)) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.LUCK, 2400, 1, false, true));
            player.giveExperiencePoints(20);
            level.sendParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.1);
            player.sendSystemMessage(Component.literal("§6⚗ Магическая реакция! §7Удача II 2мин + 20 XP"));
            reacted = true;
        }

        if (reacted) {
            held.shrink(1);
            player.getPersistentData().putLong("chaos_monk_reaction_cd", now);
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    /** Cycle of Substances: chance to get random resource on block break.
     *  Normal: 10%, Below 5 HP: 25%. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/cycle_of_substances")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        float chance = player.getHealth() < 10.0f ? 0.25f : 0.10f;
        if (RNG.nextFloat() >= chance) return;

        BlockPos pos = event.getPos();
        ItemStack randomDrop = CYCLE_DROPS[RNG.nextInt(CYCLE_DROPS.length)].copy();
        level.addFreshEntity(new ItemEntity(level,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, randomDrop));

        level.sendParticles(ParticleTypes.GLOW,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
        level.playSound(null, pos, SoundEvents.EVOKER_PREPARE_WOLOLO,
            SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    // ── overloaded_damage: each active potion effect on player → +20% bonus outgoing damage (max +150%) ──
    @SubscribeEvent
    public static void onOverloadedDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:alchemical_monk/overloaded_damage")) return;
        int activeEffects = player.getActiveEffects().size();
        if (activeEffects <= 0) return;
        ChaosAddonConfig oCfg = ChaosAddonConfig.get();
        float bonus = Math.min(activeEffects * oCfg.monkOverloadBonusPerEffect, oCfg.monkOverloadMaxBonus);
        event.setAmount(event.getAmount() * (1.0f + bonus));
        if (player.tickCount % 60 == 0) {
            player.displayClientMessage(
                Component.literal("§6⚗ Перегрузка! §e+" + (int)(bonus * 100) + "% §8урона ×" + activeEffects + " веществ")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }
}
