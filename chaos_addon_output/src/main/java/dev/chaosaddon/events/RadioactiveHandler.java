package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

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

        // Self-radiation: 0.2 HP every 20 seconds
        if (player.tickCount % 400 == 0) {
            player.hurt(player.damageSources().magic(), 0.2f);
        }

        if (player.tickCount % interval != 0) return;

        List<LivingEntity> nearby = level.getEntitiesOfClass(
            LivingEntity.class, player.getBoundingBox().inflate(radius),
            e -> e != player && e.isAlive());

        boolean overload = nearby.size() >= 5;

        // Geiger counter overload: double damage + double kill regen
        float actualDmg = overload ? dmg * 2 : dmg;
        if (overload && player.tickCount % 200 == 0) {
            player.displayClientMessage(
                Component.literal("☢ ПЕРЕГРУЗКА! Двойное облучение и двойная регенерация от убийств!")
                    .withStyle(ChatFormatting.GREEN), false);
        }

        for (LivingEntity target : nearby) {
            target.hurt(player.damageSources().magic(), actualDmg);
            double dist = target.distanceTo(player);
            int particleCount = (int) Math.max(3, 15 - dist * 1.5);
            level.sendParticles(ParticleTypes.GLOW,
                target.getX(), target.getY() + 0.8, target.getZ(),
                particleCount, 0.3, 0.4, 0.3, 0.0);
        }

        // Ambient green glow around player (scales with nearby entity count)
        int ambientCount = 8 + nearby.size() * 3;
        level.sendParticles(ParticleTypes.WITCH,
            player.getX(), player.getY() + 0.5, player.getZ(),
            ambientCount, 0.6, 0.8, 0.6, 0.03);

        // Geiger counter actionbar
        if (OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/geiger_counter")
                && player.tickCount % 20 == 0) {
            String geiger = buildGeigerBar(nearby.size());
            player.displayClientMessage(
                Component.literal("☢ " + geiger + " [" + nearby.size() + " целей]"
                    + (overload ? " §cПЕРЕГРУЗКА!" : ""))
                    .withStyle(overload ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN),
                true);
        }
    }

    private static String buildGeigerBar(int count) {
        int bars = Math.min(count, 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) sb.append("▐");
        for (int i = bars; i < 10; i++) sb.append("░");
        return sb.toString();
    }

    // Kill within radiation zone: heal player + 5-kill burst
    @SubscribeEvent
    public static void onRadiationKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/flesh_rot")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Kill restores 1 HP if target was in radiation zone
        if (event.getEntity().distanceTo(player) <= ChaosAddonConfig.get().radioAuraRadius) {
            player.heal(1.0f);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.6, 0.4, 0.03);
        }

        // Hunger from kill
        player.getFoodData().eat(2, 1.0f);

        // Kill counter + radioactive burst every 5 kills
        int kills = player.getPersistentData().getInt("chaos_radio_kills") + 1;
        player.getPersistentData().putInt("chaos_radio_kills", kills);
        if (kills % 5 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 2, false, true));
            level.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 1.0, player.getZ(),
                60, 2.0, 2.0, 2.0, 0.1);
            level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f);
            player.displayClientMessage(
                Component.literal("☢ РАДИОАКТИВНЫЙ ВСПЛЕСК! Сила III на 3с!")
                    .withStyle(ChatFormatting.DARK_GREEN), false);
        }
    }
}
