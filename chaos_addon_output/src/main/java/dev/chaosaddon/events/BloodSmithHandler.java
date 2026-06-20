package dev.chaosaddon.events;

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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles Blood Smith passives (spec-accurate):
 * - blood_bank: damage → blood charges (1❤ = 2 charges, max 100)
 * - blood_armor: Absorption shield (4❤), regenerates every 10s
 * - survival_instinct: Speed III when HP < 3❤, active costs halved
 * - no_natural_regen: block food-based natural regen (allow explicit heals)
 */
public class BloodSmithHandler {

    public static final String CHARGE_KEY = "chaos_blood_charges";
    private static final int MAX_CHARGES = 100;

    private static final Map<UUID, Long> INSTINCT_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> ARMOR_REGEN_TICK = new HashMap<>();

    /** UUIDs allowed to heal this tick (from our own abilities) */
    private static final Set<UUID> HEALING_ALLOWED = new HashSet<>();

    private static final String LAST_FULL_KEY = "chaos_blood_last_full_tick";
    private static final int OVERLOAD_WARN_TICKS = 400;   // 20s warning
    private static final int OVERLOAD_EXPLODE_TICKS = 600; // 30s → explode

    // ── Per-tick passives ──────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_bank")) return;

        long now = level.getGameTime();
        int charges = getCharges(player);
        boolean lowHp = player.getHealth() <= 6.0f; // < 3❤

        // ── OVERLOAD PRESSURE: charges capped for 30s → discharge explosion ──
        if (charges >= MAX_CHARGES) {
            long lastFull = player.getPersistentData().getLong(LAST_FULL_KEY);
            if (lastFull == 0) {
                player.getPersistentData().putLong(LAST_FULL_KEY, now);
            } else {
                long held = now - lastFull;
                if (held >= OVERLOAD_EXPLODE_TICKS) {
                    // EXPLODE: drain all charges, deal damage
                    player.getPersistentData().putInt(CHARGE_KEY, 0);
                    player.getPersistentData().putLong(LAST_FULL_KEY, 0);
                    player.hurt(player.damageSources().generic(), 8.0f);
                    level.sendParticles(ParticleTypes.EXPLOSION,
                        player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.6, 0.6, 0.6, 0.05);
                    level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.8, 0.8, 0.8, 0.2);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.displayClientMessage(Component.literal(
                        "§c💥 ПЕРЕГРУЗКА КРОВИ! Все заряды потеряны!").withStyle(ChatFormatting.RED), false);
                } else if (held >= OVERLOAD_WARN_TICKS && player.tickCount % 20 == 0) {
                    long secsLeft = (OVERLOAD_EXPLODE_TICKS - held) / 20;
                    level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.7, 0.5, 0.1);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.5f, 2.0f);
                    player.displayClientMessage(Component.literal(
                        "§c⚠ ПЕРЕГРУЗКА через §e" + secsLeft + "с§c! Потрать заряды!"), false);
                }
            }
        } else {
            player.getPersistentData().putLong(LAST_FULL_KEY, 0);
        }

        // Blood Armor: regenerate Absorption every 10 seconds
        if (OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_armor")) {
            long lastRegen = ARMOR_REGEN_TICK.getOrDefault(player.getUUID(), 0L);
            if (now - lastRegen >= 200) {
                // Absorption amount scales with charges (base 4HP, +1 per 20 charges beyond 40)
                int absLevel = Math.max(0, (charges - 40) / 20);
                player.addEffect(new MobEffectInstance(
                    MobEffects.ABSORPTION, 220, absLevel, false, false));
                ARMOR_REGEN_TICK.put(player.getUUID(), now);

                // Blood particles orbit intensity scales with charges
                int particleCount = 4 + (charges / 15);
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    particleCount, 0.4, 0.6, 0.4, 0.03);
            }
        }

        // Survival instinct: Speed III at low HP (< 3❤)
        if (OriginHelper.hasPower(player, "chaos_addon:blood_smith/survival_instinct")
                && lowHp) {
            long cooldown = INSTINCT_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            if (now - cooldown >= 600) {
                player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 100, 2, false, true));
                INSTINCT_COOLDOWN.put(player.getUUID(), now);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.4, 0.6, 0.4, 0.1);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
            }
        }
    }

    // ── Blood bank: incoming damage → charges ───────────────────────────────────
    @SubscribeEvent
    public static void onDamageReceived(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_bank")) return;

        float dmg = event.getAmount();
        int gained = (int) Math.max(1, Math.ceil(dmg * 2)); // 1❤ = 2 charges
        addCharges(player, gained);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                player.getX(), player.getY() + 1.0, player.getZ(),
                (int) (dmg * 3) + 1, 0.3, 0.5, 0.3, 0.05);
        }
    }

    // ── Block natural food regen ─────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/no_natural_regen")) return;

        UUID uid = player.getUUID();

        // Allow heals explicitly granted by our own abilities (blood golem death, blood blade, etc.)
        if (HEALING_ALLOWED.remove(uid)) return;

        // Allow Regeneration potion or Instant Health
        if (player.hasEffect(MobEffects.REGENERATION)) return;

        // Block all other heals (natural food regen, saturation, etc.)
        event.setCanceled(true);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Allow next heal call through (used by Blood Smith commands for explicit heals) */
    public static void allowNextHeal(ServerPlayer player) {
        HEALING_ALLOWED.add(player.getUUID());
    }

    public static int getCharges(ServerPlayer player) {
        return Math.min(player.getPersistentData().getInt(CHARGE_KEY), MAX_CHARGES);
    }

    /**
     * Try to spend charges. At HP < 3❤ (6 attr), cost is halved (spec: "instinct of survival").
     */
    public static boolean spendCharges(ServerPlayer player, int amount) {
        int current = getCharges(player);
        boolean lowHp = player.getHealth() <= 6.0f;
        int cost = lowHp ? Math.max(1, amount / 2) : amount;
        if (current < cost) return false;
        player.getPersistentData().putInt(CHARGE_KEY, current - cost);
        player.getPersistentData().putLong(LAST_FULL_KEY, 0); // reset overload timer on spend
        return true;
    }

    public static void addCharges(ServerPlayer player, int amount) {
        int current = player.getPersistentData().getInt(CHARGE_KEY);
        player.getPersistentData().putInt(CHARGE_KEY,
            Math.min(MAX_CHARGES, current + amount));
    }

    public static boolean isLowHp(ServerPlayer player) {
        return player.getHealth() <= 6.0f;
    }
}
