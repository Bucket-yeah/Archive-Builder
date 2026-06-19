package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;

public class GeneralPowerHandler {

    private static final Map<UUID, Integer> HIT_COUNTERS  = new java.util.HashMap<>();
    private static final Map<UUID, Long>    JUDGE_HIT_TIME = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        ChaosAddonConfig cfg = ChaosAddonConfig.get();

        if (OriginHelper.hasPower(player, "chaos_addon:nightmare_mimic/illusory_flesh")) {
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 25, 0, true, false));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    8, 0.5, 0.8, 0.5, 0.05);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    4, 0.4, 0.6, 0.4, 0.0);
            }
        }

        if (OriginHelper.hasPower(player, "chaos_addon:deep_geomancer/stone_flesh")) {
            if (player.blockPosition().getY() > cfg.geoHeightLimit) {
                if (player.tickCount % cfg.geoHighAltitudeInterval == 0) {
                    player.hurt(player.damageSources().generic(), cfg.geoHighAltitudeDamage);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.5, 0.8, 0.5, 0.05);
                }
            }
        }

        if (OriginHelper.hasPower(player, "chaos_addon:wandering_gardener/green_blood")) {
            String biome = level.getBiome(player.blockPosition())
                .unwrapKey().map(k -> k.location().toString()).orElse("");
            boolean badBiome = biome.contains("desert") || biome.contains("nether")
                || biome.contains("savanna") || biome.contains("badlands")
                || biome.contains("end");
            if (badBiome && player.tickCount % cfg.gardenBadBiomeInterval == 0) {
                player.hurt(player.damageSources().generic(), cfg.gardenBadBiomeDamage);
            }
        }
    }

    @SubscribeEvent
    public static void onAttacked(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:time_wanderer/deja_vu")) return;

        int count = HIT_COUNTERS.merge(player.getUUID(), 1, Integer::sum);
        if (count % 5 == 0) {
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.4, 0.6, 0.4, 0.05);
                level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.ILLUSIONER_MIRROR_MOVE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
            }
        }
    }

    @SubscribeEvent
    public static void onJudgeAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/lawfulness")) return;

        long now = player.level().getGameTime();
        Long lastHit = JUDGE_HIT_TIME.get(player.getUUID());

        if (lastHit == null || now - lastHit > 100) {
            if (event.getTarget() instanceof LivingEntity target) {
                event.setCanceled(true);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        1, 0, 0, 0, 0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onJudgeReceivesHit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:dimension_judge/lawfulness")) return;
        JUDGE_HIT_TIME.put(player.getUUID(), player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/flesh_rot")) return;
        player.getFoodData().eat(2, 1.0f);
    }

    @SubscribeEvent
    public static void onSentinelTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:ancient_sentinel/mountain_stride")) return;
        if (player.isInWater() || player.isInLava()) {
            if (player.tickCount % 10 == 0) {
                player.hurt(player.damageSources().drown(), 4.0f);
            }
        }
    }
}
