package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.BiomeData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Biomorph: biome adaptation with 3 tiers of stacking buffs.
 * 0–1 min: weak buff tier 1
 * 1–3 min: medium buff tier 2
 * 3+  min: maximum buff tier 3
 * Transition shock: Nausea + Slowness + damage
 * Ecosystem imprinting: +8% speed permanently per new biome (max 5)
 */
public class BiomorphHandler {

    private static final Map<UUID, String> LAST_BIOME = new java.util.HashMap<>();

    // Biome time thresholds in ticks
    private static final int TIER1 = 0;      // 0s
    private static final int TIER2 = 1200;   // 1 min
    private static final int TIER3 = 3600;   // 3 min

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:biomorph/adaptation")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        BiomeData data = player.getData(ModAttachments.BIOME_DATA);

        Holder<?> biomeHolder = level.getBiome(player.blockPosition());
        String biomeName = biomeHolder.unwrapKey()
            .map(k -> k.location().toString())
            .orElse("unknown");

        // Transition shock
        String lastBiome = LAST_BIOME.get(player.getUUID());
        if (lastBiome != null && !lastBiome.equals(biomeName)) {
            player.hurt(player.damageSources().generic(), cfg.bioTransitionDamage);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, true));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                15, 0.4, 0.5, 0.4, 0.05);
            level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
        }
        LAST_BIOME.put(player.getUUID(), biomeName);

        // Ecosystem imprinting: increment timer
        if (player.tickCount % 20 == 0) {
            data.incrementBiomeTime(biomeName);
        }

        // Apply biome buffs every second
        if (player.tickCount % 20 != 0) return;

        // Clear previous buffs
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.LEVITATION);

        int biomeTime = data.getBiomeTime(biomeName);
        int tier = biomeTime >= TIER3 ? 3 : (biomeTime >= TIER2 ? 2 : 1);

        applyBiomeBuff(player, biomeName, tier);

        // Ecosystem imprinting: permanent speed bonus for explored biomes
        if (OriginHelper.hasPower(player, "chaos_addon:biomorph/ecosystem_imprinting")) {
            int uniqueBiomes = Math.min(data.getUniqueBiomeCount(), 5);
            if (uniqueBiomes > 0) {
                // +8% speed per biome after 3 min = speed amp 0 = ~20%
                // Scale: 1 biome = amp 0, 5 biomes = amp 2
                int speedAmp = Math.min(uniqueBiomes - 1, 4);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40,
                    speedAmp, true, false));
            }

            // Actionbar
            long secsInBiome = (biomeTime / 20);
            String tierLabel = tier == 3 ? "§aМАКС" : (tier == 2 ? "§eСРЕД" : "§7СЛАБ");
            player.displayClientMessage(
                Component.literal("🌿 " + getBiomeShortName(biomeName) + " | Уровень: " + tierLabel
                    + " | Биомов: " + uniqueBiomes + "/5 | " + secsInBiome + "с")
                    .withStyle(ChatFormatting.GREEN),
                true);
        }
    }

    private static void applyBiomeBuff(ServerPlayer player, String biome, int tier) {
        // Amplifier: tier 1 = 0, tier 2 = 1, tier 3 = 2
        int amp = tier - 1;

        if (biome.contains("nether") || biome.contains("basalt") || biome.contains("crimson") || biome.contains("soul")) {
            // Nether: fire immunity + damage
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
        } else if (biome.contains("end")) {
            // End: levitation + HP penalty
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, Math.min(amp, 1), true, false));
        } else if (biome.contains("ocean") || biome.contains("deep") || biome.contains("river")) {
            // Ocean: dolphin swim + water breathing
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, amp, true, false));
        } else if (biome.contains("jungle") || biome.contains("forest")) {
            // Jungle: damage + attack speed
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
        } else if (biome.contains("desert") || biome.contains("savanna") || biome.contains("badlands")) {
            // Desert: speed + fire resistance
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, amp, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
        } else if (biome.contains("snow") || biome.contains("frozen") || biome.contains("tundra") || biome.contains("ice")) {
            // Snow: defence + slow regen
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amp, true, false));
            if (tier >= 2) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
        } else {
            // Generic: mild regen
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
        }
    }

    private static String getBiomeShortName(String biomeFull) {
        if (biomeFull.contains(":")) biomeFull = biomeFull.split(":")[1];
        String[] parts = biomeFull.split("_");
        if (parts.length > 2) return parts[0] + "_" + parts[1];
        return biomeFull;
    }
}
