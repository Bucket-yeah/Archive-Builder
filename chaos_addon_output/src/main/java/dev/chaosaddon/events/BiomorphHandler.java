package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.BiomeData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Biomorph: applies biome-specific buffs and handles:
 *  - biome-specific food restriction (1 heart damage + hunger for wrong food)
 *  - transition damage when crossing biome borders
 *  - ecosystem imprinting (+5% speed per biome after 5 min)
 */
public class BiomorphHandler {

    private static final java.util.Map<java.util.UUID, String> LAST_BIOME = new java.util.HashMap<>();

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

        // Transition damage
        String lastBiome = LAST_BIOME.get(player.getUUID());
        if (lastBiome != null && !lastBiome.equals(biomeName)) {
            player.hurt(player.damageSources().generic(), cfg.bioTransitionDamage);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                15, 0.4, 0.5, 0.4, 0.05);
            level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
        }
        LAST_BIOME.put(player.getUUID(), biomeName);

        // Ecosystem imprinting: increment timer in data
        if (player.tickCount % 20 == 0) {
            data.incrementBiomeTime(biomeName);
        }

        // Apply biome buffs every second
        if (player.tickCount % 20 != 0) return;

        // Clear previous
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.REGENERATION);

        var biomeTag = biomeHolder.unwrapKey().orElse(null);
        if (biomeTag == null) return;

        // Nether biome
        if (biomeName.contains("nether") || biomeName.contains("basalt") || biomeName.contains("crimson") || biomeName.contains("soul")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, true, false));
            // -50% armor handled in JSON
        }
        // End biome
        else if (biomeName.contains("end")) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0, true, false));
            // +50% fall damage handled in JSON
        }
        // Ocean / water
        else if (biomeName.contains("ocean") || biomeName.contains("deep")) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,    40, 0, true, false));
        }
        // Forest
        else if (biomeName.contains("forest") || biomeName.contains("jungle") || biomeName.contains("taiga")) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, true, false));
        }
        // Desert / savanna
        else if (biomeName.contains("desert") || biomeName.contains("savanna") || biomeName.contains("badlands")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
        }
        // Tundra / snowy
        else if (biomeName.contains("snow") || biomeName.contains("frozen") || biomeName.contains("tundra") || biomeName.contains("ice")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false));
        }
    }
}
