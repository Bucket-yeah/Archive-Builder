package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.LunarData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles Lunar Renegade's moon-phase attribute scaling.
 * Full moon  → +100% dmg, +50% speed, -100% armor (double damage taken)
 * New moon   → -50% dmg, +100% armor (turtle mode)
 * Other phases → interpolated
 * Daytime    → -40% speed, no active skills
 * Sun damage if exposed
 */
public class LunarHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/lunar_cycle")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        LunarData data = player.getData(ModAttachments.LUNAR_DATA);

        int moonPhase = level.getMoonPhase(); // 0=full, 4=new, 1-3/5-7=other
        data.setLastPhase(moonPhase);

        // Sun damage during daytime if exposed
        if (!level.isNight() && level.canSeeSky(player.blockPosition())) {
            if (player.tickCount % cfg.lunarSunDamageInterval == 0) {
                player.hurt(player.damageSources().inFire(), cfg.lunarSunDamage);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    4, 0.3, 0.4, 0.3, 0.02);
            }
        }

        // Apply moon-phase effects every second
        if (player.tickCount % 20 != 0) return;

        // Remove old phase effects
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);

        if (!level.isNight()) {
            // Day penalty: slow
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, false));
            return;
        }

        // Nighttime phase buffs (amplifiers clamped to valid range)
        switch (moonPhase) {
            case 0 -> { // Full moon: berserker
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,    40, 3, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,  40, 2, true, false));
                // Double damage taken: handled in JSON via attribute modifier
            }
            case 4 -> { // New moon: turtle
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3, true, false));
            }
            default -> { // Other phases: mild buff
                int amp = (moonPhase <= 2) ? 1 : 0;
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,   40, amp, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0,   true, false));
            }
        }

        // Ambient lunar particles at night
        if (level.isNight() && player.tickCount % 40 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                player.getX(), player.getY() + 1.5, player.getZ(),
                6, 0.5, 0.8, 0.5, 0.0);
        }
    }
}
