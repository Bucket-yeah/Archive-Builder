package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Radioactive Phantom: passive radiation aura
 *  - Deals small damage to all entities in radius every N ticks
 *  - Glowing particles around each victim scale with proximity
 *  - Rain hurts the player
 */
public class RadioactiveHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/radiation_field")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        int interval = cfg.radioAuraInterval;
        int radius   = cfg.radioAuraRadius;
        float dmg    = cfg.radioAuraDamage;

        // Rain damage
        if (player.tickCount % 20 == 0 && level.isRaining()) {
            player.hurt(player.damageSources().generic(), 0.5f);
        }

        if (player.tickCount % interval != 0) return;

        // Damage nearby entities + spawn proximity glow particles
        List<LivingEntity> nearby = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(radius),
            e -> e != player && e.isAlive()
        );

        for (LivingEntity target : nearby) {
            target.hurt(player.damageSources().magic(), dmg);

            double dist = target.distanceTo(player);
            int particleCount = (int) Math.max(3, 15 - dist * 1.5);

            level.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                target.getX(), target.getY() + 0.8, target.getZ(),
                particleCount, 0.3, 0.4, 0.3, 0.0);
        }

        // Ambient green glow around player
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
            player.getX(), player.getY() + 0.5, player.getZ(),
            8, 0.6, 0.8, 0.6, 0.03);
    }
}
