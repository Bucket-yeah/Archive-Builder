package dev.chaosaddon.events;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.data.LunarData;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.util.OriginHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Lunar Renegade: full moon-phase mechanics
 * Phase 0 (Full): dmg x2, speed +80%, defense -50%
 * Phase 1-2: dmg x1.5, speed +40%
 * Phase 3-4 (Half): neutral
 * Phase 5-6: defense +40%, dmg -30%
 * Phase 7 (New): defense x2, dmg -50%, regen 1❤/10s
 */
public class LunarHandler {

    private static final Random RNG = new Random();
    private static final Map<UUID, Long> RITUAL_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> SNEAK_TICKS = new HashMap<>();
    private static final long RITUAL_COOLDOWN_TICKS = 72000L; // 1 real hour
    private static final int RITUAL_SNEAK_REQUIRED = 100; // 5 seconds sneaking

    private static final String[] PHASE_NAMES = {
        "🌕 Полнолуние", "🌔 Убывающая", "🌓 Первая четверть", "🌒 Молодой месяц",
        "🌑 Новолуние", "🌘 Старый серп", "🌗 Последняя четверть", "🌖 Растущая"
    };

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/lunar_cycle")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        LunarData data = player.getData(ModAttachments.LUNAR_DATA);

        int moonPhase = level.getMoonPhase(); // 0=full, 4=new, 1-3/5-7=other
        int prevPhase = data.lastPhase();

        // Phase change warning: 10s before change (we use time-of-night heuristic)
        long dayTime = level.getDayTime() % 24000;
        // Night runs from ~13000 to ~23000. Phase changes at next midnight.
        // Warn when within 200 ticks (10s) of midnight (24000)
        if (dayTime >= 23800 && player.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 2.0, player.getZ(),
                20, 1.0, 1.5, 1.0, 0.05);
            level.playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3f, 0.5f);
        }

        data.setLastPhase(moonPhase);

        // Sun damage during daytime if exposed
        if (!level.isNight() && level.canSeeSky(player.blockPosition())) {
            if (player.tickCount % cfg.lunarSunDamageInterval == 0) {
                player.hurt(player.damageSources().inFire(), cfg.lunarSunDamage);
                level.sendParticles(ParticleTypes.FLAME,
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
        player.removeEffect(MobEffects.REGENERATION);

        if (!level.isNight()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, false));
            player.displayClientMessage(
                Component.literal("☀ Дневное ослабление | " + PHASE_NAMES[moonPhase])
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }

        // Nighttime phase buffs per spec
        switch (moonPhase) {
            case 0 -> { // Full moon: berserker — dmg x1.5 (+50%), speed +40% — BALANCED (was 3+3)
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,    40, 1, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,  40, 2, true, false));
            }
            case 4 -> { // New moon: turtle — defense x2, dmg -50%, regen
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,          40, 1, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,      40, 0, true, false));
            }
            case 1, 2 -> { // Waxing strong — dmg x1.5, speed +40%
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,   40, 1, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false));
            }
            case 5, 6 -> { // Waning — defense +40%, dmg -30%
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,          40, 0, true, false));
            }
            default -> { // Half moon — neutral
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,   40, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            }
        }

        // Silver Light: Glowing on all entities in radius at night
        if (OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/silver_light")) {
            int glowRadius = (moonPhase == 0) ? 40 : 20; // full moon = bigger radius
            level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(glowRadius),
                e -> e != player && e.isAlive())
                .forEach(e -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false)));
        }

        // Lunar Calendar actionbar
        if (OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/lunar_calendar")) {
            long ticksToNextDay = 24000 - (level.getDayTime() % 24000);
            long secsToNextDay = ticksToNextDay / 20;
            String phaseInfo = PHASE_NAMES[moonPhase];
            player.displayClientMessage(
                Component.literal("🌙 " + phaseInfo + " | Смена через: " + secsToNextDay + "с")
                    .withStyle(moonPhase == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA),
                true);
        }

        // ── Moon Ritual: sneak 5s at night under full/new moon → permanent buff ──
        long now = level.getGameTime();
        if (OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/lunar_cycle")
                && level.isNight() && level.canSeeSky(player.blockPosition())
                && (moonPhase == 0 || moonPhase == 4)) {
            UUID pid = player.getUUID();
            long lastRitual = RITUAL_COOLDOWN.getOrDefault(pid, 0L);
            if (now - lastRitual >= RITUAL_COOLDOWN_TICKS) {
                if (player.isCrouching()) {
                    int sneakCount = SNEAK_TICKS.merge(pid, 1, Integer::sum);
                    if (sneakCount % 20 == 0) { // show progress every second
                        int secs = sneakCount / 20;
                        player.displayClientMessage(Component.literal(
                            "§9🌙 Лунный Ритуал... " + secs + "/5с"), true);
                    }
                    if (sneakCount >= RITUAL_SNEAK_REQUIRED) {
                        SNEAK_TICKS.remove(pid);
                        RITUAL_COOLDOWN.put(pid, now);
                        performMoonRitual(player, level, moonPhase);
                    }
                } else {
                    SNEAK_TICKS.remove(pid);
                }
            }
        }

        // Ambient lunar particles at night
        if (level.isNight() && player.tickCount % 40 == 0) {
            level.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 1.5, player.getZ(),
                6, 0.5, 0.8, 0.5, 0.0);
            // Player glows at night (Silver Light)
            if (OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/silver_light")) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, true, false));
            }
        }
    }

    /** Moon Ritual: grants permanent attribute bonus (stored as resource location modifier). */
    private static void performMoonRitual(ServerPlayer player, ServerLevel level, int moonPhase) {
        net.minecraft.resources.ResourceLocation modId = net.minecraft.resources.ResourceLocation
            .fromNamespaceAndPath("chaos_addon", "lunar_ritual_" + player.getUUID().toString().substring(0, 8));

        // Full moon = offensive buff, New moon = defensive buff
        boolean fullMoon = (moonPhase == 0);
        String buffName;
        net.minecraft.world.effect.MobEffectInstance longBuff;

        int choice = RNG.nextInt(3);
        if (fullMoon) {
            buffName = switch (choice) {
                case 0 -> "§e+Сила I";
                case 1 -> "§e+Скорость I";
                default -> "§e+Удача I";
            };
            longBuff = switch (choice) {
                case 0 -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 6000, 0, true, true);
                case 1 -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 0, true, true);
                default -> new MobEffectInstance(MobEffects.LUCK, 6000, 0, true, true);
            };
        } else { // New moon
            buffName = switch (choice) {
                case 0 -> "§b+Сопротивление I";
                case 1 -> "§b+Регенерация I";
                default -> "§b+Поглощение I";
            };
            longBuff = switch (choice) {
                case 0 -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0, true, true);
                case 1 -> new MobEffectInstance(MobEffects.REGENERATION, 6000, 0, true, true);
                default -> new MobEffectInstance(MobEffects.ABSORPTION, 6000, 0, true, true);
            };
        }

        player.addEffect(longBuff);
        level.sendParticles(ParticleTypes.GLOW,
            player.getX(), player.getY() + 1.5, player.getZ(), 50, 0.8, 1.0, 0.8, 0.05);
        level.sendParticles(ParticleTypes.FLASH,
            player.getX(), player.getY() + 1.5, player.getZ(), 3, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, fullMoon ? 1.2f : 0.8f);
        player.sendSystemMessage(Component.literal(
            "§9✦ Лунный Ритуал завершён! §r" + buffName + " §7(1 час cooldown)"));
    }

    /**
     * Silver Shield: absorb incoming damage, reflect portion back to attacker.
     * State stored in SilverShieldCommand static maps.
     */
    @SubscribeEvent
    public static void onSilverShieldDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:lunar_renegade/silver_light")) return;

        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        Float shieldHp = dev.chaosaddon.commands.SilverShieldCommand.SHIELD_HP.get(player.getUUID());
        Long expiry = dev.chaosaddon.commands.SilverShieldCommand.SHIELD_EXPIRY.get(player.getUUID());
        if (shieldHp == null || expiry == null || now > expiry || shieldHp <= 0) return;

        float dmg = event.getAmount();
        float reflect = dev.chaosaddon.commands.SilverShieldCommand.SHIELD_REFLECT
            .getOrDefault(player.getUUID(), 0.4f);

        // Absorb damage with shield
        if (dmg <= shieldHp) {
            dev.chaosaddon.commands.SilverShieldCommand.SHIELD_HP.put(player.getUUID(), shieldHp - dmg);
            event.setAmount(0);
        } else {
            event.setAmount(dmg - shieldHp);
            dev.chaosaddon.commands.SilverShieldCommand.SHIELD_HP.put(player.getUUID(), 0f);
        }

        // Reflect to attacker
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.hurt(level.damageSources().magic(), dmg * reflect);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.4, 0.5, 0.4, 0.05);
        }
    }
}
