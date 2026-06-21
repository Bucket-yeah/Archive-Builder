package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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

        // ── material_decay: degrade armor/tools worn by nearby entities (not dropped items) ──
        if (OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/material_decay")
                && player.tickCount % 200 == 0) {
            List<LivingEntity> decayTargets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius + 2), e -> e != player && e.isAlive());
            int decayed = 0;
            for (LivingEntity target : decayTargets) {
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    ItemStack eq = target.getItemBySlot(slot);
                    if (!eq.isEmpty() && eq.isDamageableItem()) {
                        eq.setDamageValue(Math.min(eq.getDamageValue() + 4, eq.getMaxDamage()));
                        target.setItemSlot(slot, eq);
                        decayed++;
                    }
                }
                if (decayed > 0) {
                    level.sendParticles(ParticleTypes.WITCH,
                        target.getX(), target.getY() + 1.0, target.getZ(), 4, 0.3, 0.5, 0.3, 0.06);
                }
            }
            if (decayed > 0) {
                player.displayClientMessage(
                    Component.literal("§2☢ Материальный распад: §8" + decayed + " ед. снаряжения повреждено")
                        .withStyle(ChatFormatting.DARK_GREEN), true);
            }
        }

        // ── geiger_counter: apply Glowing to nearby living entities (biomass sensor) ──
        if (OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/geiger_counter")
                && player.tickCount % interval == 0) {
            List<LivingEntity> sensed = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius + 5), e -> e != player && e.isAlive());
            for (LivingEntity target : sensed) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, interval * 2, 0, false, false));
            }
            int nearbyCount = sensed.size();
            player.getPersistentData().putInt("chaos_radio_nearby", nearbyCount);
            String intensity = nearbyCount == 0 ? "§a● Фон"
                : nearbyCount < 3 ? "§e●● Повышен"
                : nearbyCount < 5 ? "§6●●● Высокий"
                : "§c●●●● КРИТИЧНО";
            player.displayClientMessage(
                Component.literal("☢ " + intensity + " §8(" + nearbyCount + " биомасс)")
                    .withStyle(ChatFormatting.GREEN), true);
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
                Component.literal("☢ ПЕРЕГРУЗКА! Двойное облучение!")
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

        // Store nearby count for HudHandler to read
        player.getPersistentData().putInt("chaos_radio_nearby", nearby.size());

        // ── Radioactive Trail: irradiate entities near previously visited positions ──
        if (player.tickCount % 60 == 0) {
            leaveRadioactiveTrail(player, level);
        }
    }

    /**
     * Radioactive trail: every 3 seconds, save current position as a radiation zone.
     * Any entity entering within 2 blocks of a saved zone in the next 30 seconds gets Poison + Weakness.
     */
    private static void leaveRadioactiveTrail(ServerPlayer player, ServerLevel level) {
        BlockPos cur = player.blockPosition();
        String existing = player.getPersistentData().getString("chaos_radio_trail");
        long expiry = level.getGameTime() + 600;

        String entry = cur.getX() + "," + cur.getY() + "," + cur.getZ() + "," + expiry;
        String[] parts = existing.isEmpty() ? new String[0] : existing.split(";");

        java.util.List<String> active = new java.util.ArrayList<>();
        long now = level.getGameTime();
        for (String p : parts) {
            String[] sp = p.split(",");
            if (sp.length == 4) {
                try {
                    if (Long.parseLong(sp[3]) > now) active.add(p);
                } catch (NumberFormatException ignored) {}
            }
        }
        active.add(entry);
        if (active.size() > 10) active.remove(0);
        player.getPersistentData().putString("chaos_radio_trail", String.join(";", active));

        for (String p : active) {
            String[] sp = p.split(",");
            if (sp.length < 3) continue;
            try {
                BlockPos zonePos = new BlockPos(Integer.parseInt(sp[0]), Integer.parseInt(sp[1]), Integer.parseInt(sp[2]));
                List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(zonePos).inflate(2.0),
                    e -> e != player && e.isAlive());
                for (LivingEntity v : victims) {
                    v.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, false));
                    v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
                }
                if (!victims.isEmpty() && player.tickCount % 60 == 0) {
                    level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        zonePos.getX() + 0.5, zonePos.getY() + 0.5, zonePos.getZ() + 0.5,
                        5, 0.5, 0.3, 0.5, 0.02);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // Kill within radiation zone: heal player + 5-kill burst
    @SubscribeEvent
    public static void onRadiationKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:radioactive_phantom/flesh_rot")) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        if (event.getEntity().distanceTo(player) <= ChaosAddonConfig.get().radioAuraRadius) {
            player.heal(1.0f);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.6, 0.4, 0.03);
        }

        player.getFoodData().eat(2, 1.0f);

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
