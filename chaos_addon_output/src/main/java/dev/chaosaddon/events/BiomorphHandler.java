package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.BiomeData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

/**
 * Biomorph: biome adaptation with 3 tiers of stacking buffs.
 * 0–1 min: weak buff tier 1
 * 1–3 min: medium buff tier 2
 * 3+  min: maximum buff tier 3
 * Transition shock: Nausea + Slowness + damage
 * Ecosystem imprinting: +8% speed permanently per new biome (max 5)
 */
public class BiomorphHandler {

    private static final Map<UUID, String> LAST_BIOME = new HashMap<>();
    private static final Map<UUID, Long> LAST_SHOCK_TIME = new HashMap<>();

    // Effects we apply — never blindly clear external potions
    private static final Set<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> OUR_EFFECTS = new HashSet<>();

    static {
        OUR_EFFECTS.add(MobEffects.FIRE_RESISTANCE);
        OUR_EFFECTS.add(MobEffects.WATER_BREATHING);
        OUR_EFFECTS.add(MobEffects.NIGHT_VISION);
        OUR_EFFECTS.add(MobEffects.REGENERATION);
        OUR_EFFECTS.add(MobEffects.DAMAGE_BOOST);
        OUR_EFFECTS.add(MobEffects.MOVEMENT_SPEED);
        OUR_EFFECTS.add(MobEffects.DAMAGE_RESISTANCE);
        OUR_EFFECTS.add(MobEffects.JUMP);
        OUR_EFFECTS.add(MobEffects.DOLPHINS_GRACE);
        OUR_EFFECTS.add(MobEffects.SATURATION);
        OUR_EFFECTS.add(MobEffects.SLOW_FALLING);
        OUR_EFFECTS.add(MobEffects.LUCK);
    }


    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:biomorph/adaptation")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        BiomeData data = player.getData(ModAttachments.BIOME_DATA);

        Holder<?> biomeHolder = level.getBiome(player.blockPosition());
        String biomeName = biomeHolder.unwrapKey()
            .map(k -> k.location().toString()).orElse("unknown");
        String biomeType = getBiomeType(biomeName);

        // ── Transition shock with 10s grace period (transition_damage power) ──
        String lastBiome = LAST_BIOME.get(player.getUUID());
        if (lastBiome != null && !lastBiome.equals(biomeName)
                && OriginHelper.hasPower(player, "chaos_addon:biomorph/transition_damage")) {
            long now = level.getGameTime();
            long lastShock = LAST_SHOCK_TIME.getOrDefault(player.getUUID(), 0L);
            if (now - lastShock >= cfg.bioShockGraceTicks) {
                LAST_SHOCK_TIME.put(player.getUUID(), now);
                player.hurt(player.damageSources().generic(), cfg.bioTransitionDamage);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
                level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.5, 0.4, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.6f, 1.5f);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§c⚡ Шок перехода! Тело не успело адаптироваться к новому биому."), true);
            }
        }
        LAST_BIOME.put(player.getUUID(), biomeName);

        // Increment biome time every second
        if (player.tickCount % 20 == 0) {
            data.incrementBiomeTime(biomeName);
        }

        if (player.tickCount % 20 != 0) return;

        int biomeTime = data.getBiomeTime(biomeName);
        int tier = biomeTime >= cfg.bioTier3Ticks ? 3 : (biomeTime >= cfg.bioTier2Ticks ? 2 : 1);

        // ── DNA unlock: after 3 min in biome type, permanently unlock it ──
        if (biomeTime >= cfg.bioTier3Ticks) {
            boolean newUnlock = data.unlockDna(biomeType);
            if (newUnlock) {
                player.sendSystemMessage(Component.literal(
                    "§2🧬 ДНК разблокирована: §a" + biomeType + "§7 — постоянная адаптация!")
                    .withStyle(ChatFormatting.GREEN));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
            }
        }

        // ── Clear only OUR ambient effects (not external potions) ──
        for (var holder : OUR_EFFECTS) {
            var inst = player.getEffect(holder);
            if (inst != null && inst.isAmbient()) {
                player.removeEffect(holder);
            }
        }

        // ── Apply current biome buff ──
        applyBiomeBuff(player, biomeName, tier);

        // ── Apply permanently unlocked DNA perks ──
        applyDnaPerks(player, data);

        // ── Ecosystem imprinting: speed per mastered biome ──
        if (OriginHelper.hasPower(player, "chaos_addon:biomorph/ecosystem_imprinting")) {
            ChaosAddonConfig cfg2 = ChaosAddonConfig.get();
            int uniqueBiomes = Math.min(data.getUniqueBiomeCount(), cfg2.bioMaxBiomesForSpeed);
            if (uniqueBiomes > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40,
                    Math.min(uniqueBiomes - 1, 4), true, false));
            }
        }

        // ── wrong_biome_blocks: Mining Fatigue in a biome mismatched from adaptation ──
        if (OriginHelper.hasPower(player, "chaos_addon:biomorph/wrong_biome_blocks")) {
            // The player's "home" biome is whichever biome type they've spent the most time in
            String bestBiome = null;
            int bestTime = 0;
            for (String bt : new String[]{"nether", "end", "ocean", "jungle", "forest",
                    "desert", "frozen", "mushroom", "swamp", "plains", "savanna", "taiga"}) {
                // Use the mastered (unlocked) DNA as "home" biomes
                if (data.getUnlockedDna().contains(bt)) {
                    bestBiome = bt;
                    break;
                }
            }
            if (bestBiome != null && !biomeType.equals(bestBiome)) {
                // Not in home biome: Mining Fatigue I
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, cfg.bioWrongBiomeFatigueLevel, true, false));
                if (player.tickCount % cfg.bioWrongBiomeCheckInterval == 0) {
                    player.displayClientMessage(
                        Component.literal("§3🧬 Чужой биом — §7замедление горной добычи").withStyle(ChatFormatting.DARK_AQUA), true);
                }
            }
        }
    }

    private static void applyBiomeBuff(ServerPlayer player, String biome, int tier) {
        int amp = tier - 1;

        if (biome.contains("nether") || biome.contains("basalt") || biome.contains("crimson") || biome.contains("soul") || biome.contains("warped")) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
        } else if (biome.contains("end") || biome.contains("void")) {
            // FIX: End gives Strength + Jump Boost, NOT Levitation (which is a trap)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, amp, true, false));
            if (tier >= 3) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false));
        } else if (biome.contains("ocean") || biome.contains("deep_") || biome.contains("river")) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, amp, true, false));
        } else if (biome.contains("jungle")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
            if (tier >= 2) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
        } else if (biome.contains("forest")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
            if (tier >= 3) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
        } else if (biome.contains("desert") || biome.contains("badlands")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, amp, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
        } else if (biome.contains("savanna")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, amp, true, false));
        } else if (biome.contains("snow") || biome.contains("frozen") || biome.contains("tundra") || biome.contains("ice")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amp, true, false));
            if (tier >= 2) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
        } else if (biome.contains("taiga")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amp, true, false));
        } else if (biome.contains("mushroom")) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, amp, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, true, false));
        } else if (biome.contains("swamp") || biome.contains("mangrove")) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            if (tier >= 2) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false));
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
        }
    }

    /** Permanent DNA perks active across ALL biomes once unlocked. */
    private static void applyDnaPerks(ServerPlayer player, BiomeData data) {
        Set<String> dna = data.getUnlockedDna();
        if (dna.isEmpty()) return;
        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        if (dna.contains("nether"))
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
        if (dna.contains("ocean"))
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
        if (dna.contains("end") || dna.contains("void"))
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false));
        if (dna.contains("snow") || dna.contains("frozen"))
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false));
        if (dna.contains("jungle") || dna.contains("forest"))
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, true, false));
        if (dna.size() >= cfg.bioDnaLuckThreshold)
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 40, 0, true, false));
    }

    /**
     * Biome Metabolism: Biomorph can only eat food that belongs to the current biome type.
     * Eating wrong-biome food: 1❤ damage + nutrition removed.
     * Eating correct-biome food: small extra saturation bonus.
     * Universal foods (bread, beef, carrot, etc.) are always allowed.
     */
    @SubscribeEvent
    public static void onBiomeMetabolism(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:biomorph/biome_metabolism")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        net.minecraft.world.item.ItemStack item = event.getItem();
        if (!item.has(net.minecraft.core.component.DataComponents.FOOD)) return;

        String biomeName = level.getBiome(player.blockPosition())
            .unwrapKey().map(k -> k.location().toString()).orElse("plains");
        String biomeType = getBiomeType(biomeName);
        String foodBiome = getFoodBiomeType(item.getItem());

        if (foodBiome == null) return; // universal food, always OK

        if (foodBiome.equals(biomeType)) {
            // Correct biome food: bonus saturation
            player.getFoodData().eat(0, 1.5f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
        } else {
            // Wrong biome: undo nutrition, deal 1❤ damage
            var food = item.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null) {
                player.getFoodData().setFoodLevel(
                    Math.max(0, player.getFoodData().getFoodLevel() - food.nutrition()));
            }
            player.hurt(player.damageSources().generic(), ChaosAddonConfig.get().bioWrongFoodDamage);
            player.sendSystemMessage(Component.literal(
                "§3🧬 Метаболизм: §7«" + item.getHoverName().getString()
                + "» не растёт в биоме §b" + biomeType + "§7!"));
            level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Maps specific food items to their home biome type. Returns null for universal foods. */
    private static String getFoodBiomeType(net.minecraft.world.item.Item item) {
        // Ocean foods
        if (item == Items.COD || item == Items.COOKED_COD || item == Items.SALMON
                || item == Items.COOKED_SALMON || item == Items.TROPICAL_FISH
                || item == Items.DRIED_KELP) return "ocean";
        // Jungle foods
        if (item == Items.MELON_SLICE || item == Items.COCOA_BEANS) return "jungle";
        // Forest/cold foods
        if (item == Items.SWEET_BERRIES || item == Items.GLOW_BERRIES) return "forest";
        // Snowy/frozen foods
        if (item == Items.PUMPKIN_PIE) return "frozen";
        // Mushroom foods
        if (item == Items.MUSHROOM_STEW || item == Items.SUSPICIOUS_STEW) return "mushroom";
        // Nether foods
        if (item == Items.CHORUS_FRUIT) return "nether";
        // Universal: bread, beef, pork, chicken, carrot, potato, apple, etc.
        return null;
    }

    private static String getBiomeType(String biome) {
        if (biome.contains("nether") || biome.contains("basalt") || biome.contains("crimson") || biome.contains("soul") || biome.contains("warped")) return "nether";
        if (biome.contains("end") || biome.contains("void")) return "end";
        if (biome.contains("ocean") || biome.contains("deep_") || biome.contains("river")) return "ocean";
        if (biome.contains("jungle")) return "jungle";
        if (biome.contains("forest")) return "forest";
        if (biome.contains("desert") || biome.contains("badlands")) return "desert";
        if (biome.contains("savanna")) return "savanna";
        if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")) return "frozen";
        if (biome.contains("taiga")) return "taiga";
        if (biome.contains("mushroom")) return "mushroom";
        if (biome.contains("swamp") || biome.contains("mangrove")) return "swamp";
        return "plains";
    }
}
