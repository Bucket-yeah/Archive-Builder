package dev.chaosaddon.events;

import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import dev.chaosaddon.config.ChaosAddonConfig;
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
 * - blood_bank:        damage → blood charges (1❤ = 2 charges, max 100)
 *                      Charges are stored via neoorigins:resource (blood_charge_bar) — HUD rendered natively.
 * - blood_armor:       Absorption shield, regenerates every 10 s, scales with charge count.
 * - survival_instinct: Speed III when HP &lt; 3❤; active ability costs halved.
 * - no_natural_regen:  Block food-based natural regen (allow explicit heals from our abilities).
 *
 * P1 migration note — charge storage moved from NBT to neoorigins:resource:
 *   ResourcePower.getValue(player, RESOURCE_KEY)                              — read
 *   ResourcePower.deduct(player, RESOURCE_KEY, n)                             — spend (false if not enough)
 *   player.getData(CompatAttachments.resourceState()).clampedAdd(key, n, 0, 100) — add with clamp
 *   CompatAttachments.syncResourceValuesToClient(player)                      — push HUD update
 *   Overload timer still uses NBT (LAST_FULL_KEY) — it is a cooldown, not a display value.
 */
public class BloodSmithHandler {

    /**
     * neoorigins:resource power ID — doubles as the attachment storage key for charge values.
     * Used by external callers (BloodSmithCommands, ChaosEventsHandler, HudHandler).
     */
    public static final String RESOURCE_KEY = "chaos_addon:blood_smith/blood_charge_bar";
    public static final int    MAX_CHARGES  = 100;

    private static final Map<UUID, Long> INSTINCT_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> ARMOR_REGEN_TICK  = new HashMap<>();

    /** UUIDs allowed to heal this tick (bypass no_natural_regen for our own ability heals). */
    private static final Set<UUID> HEALING_ALLOWED = new HashSet<>();

    private static final String LAST_FULL_KEY = "chaos_blood_last_full_tick";

    // ── Per-tick passives ──────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_bank")) return;

        ChaosAddonConfig cfg = ChaosAddonConfig.get();
        long now     = level.getGameTime();
        int  charges = getCharges(player);
        boolean lowHp = player.getHealth() <= cfg.bloodLowHpThreshold; // < 3❤

        // ── OVERLOAD: charges capped at 100 for 30 s → discharge explosion ──
        if (charges >= MAX_CHARGES) {
            long lastFull = player.getPersistentData().getLong(LAST_FULL_KEY);
            if (lastFull == 0) {
                player.getPersistentData().putLong(LAST_FULL_KEY, now);
            } else {
                long held = now - lastFull;
                if (held >= cfg.bloodOverloadExplodeTicks) {
                    setCharges(player, 0);
                    player.getPersistentData().putLong(LAST_FULL_KEY, 0);
                    player.hurt(player.damageSources().generic(), cfg.bloodOverloadDamage);
                    level.sendParticles(ParticleTypes.EXPLOSION,
                        player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.6, 0.6, 0.6, 0.05);
                    level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                        player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.8, 0.8, 0.8, 0.2);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.displayClientMessage(Component.literal(
                        "§c💥 ПЕРЕГРУЗКА КРОВИ! Все заряды потеряны!").withStyle(ChatFormatting.RED), false);
                } else if (held >= cfg.bloodOverloadWarnTicks && player.tickCount % 20 == 0) {
                    long secsLeft = (cfg.bloodOverloadExplodeTicks - held) / 20;
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

        // ── Blood Armor: Absorption every 10 s, scales with charge count ──
        if (OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_armor")) {
            long lastRegen = ARMOR_REGEN_TICK.getOrDefault(player.getUUID(), 0L);
            if (now - lastRegen >= cfg.bloodArmorRegenInterval) {
                int absLevel = Math.max(0, (charges - 40) / 20);
                player.addEffect(new MobEffectInstance(
                    MobEffects.ABSORPTION, 220, absLevel, false, false));
                ARMOR_REGEN_TICK.put(player.getUUID(), now);
                int particleCount = 4 + (charges / 15);
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    particleCount, 0.4, 0.6, 0.4, 0.03);
            }
        }

        // ── Survival instinct: Speed III at low HP (< 3❤) ──
        if (OriginHelper.hasPower(player, "chaos_addon:blood_smith/survival_instinct") && lowHp) {
            long cooldown = INSTINCT_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            if (now - cooldown >= cfg.bloodSacrificeCooldown) {
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

    // ── Blood Bank: incoming damage → charges ─────────────────────────────────
    @SubscribeEvent
    public static void onDamageReceived(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_bank")) return;

        float dmg    = event.getAmount();
        int   gained = (int) Math.max(1, Math.ceil(dmg * 2)); // 1❤ = 2 charges
        addCharges(player, gained);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                player.getX(), player.getY() + 1.0, player.getZ(),
                (int) (dmg * 3) + 1, 0.3, 0.5, 0.3, 0.05);
        }
    }

    // ── Blood Fury: outgoing damage bonus from missing HP ─────────────────────
    @SubscribeEvent
    public static void onBloodFury(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/survival_instinct")) return;

        float missing = player.getMaxHealth() - player.getHealth();
        if (missing <= 0) return;

        float bonus = Math.min(0.50f, missing * 0.05f); // 5% per 1❤ lost, cap 50%
        event.setAmount(event.getAmount() * (1.0f + bonus));

        if (bonus >= 0.25f && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ(),
                (int) (bonus * 20), 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ── Block natural food regen ───────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/no_natural_regen")) return;

        UUID uid = player.getUUID();
        if (HEALING_ALLOWED.remove(uid)) return;           // explicitly granted by our abilities
        if (player.hasEffect(MobEffects.REGENERATION)) return; // Regen potion — always allowed
        event.setCanceled(true);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Allow the next heal event through (used by Blood Smith commands for explicit heals). */
    public static void allowNextHeal(ServerPlayer player) {
        HEALING_ALLOWED.add(player.getUUID());
    }

    /** Read current charge count from the neoorigins:resource attachment. */
    public static int getCharges(ServerPlayer player) {
        return ResourcePower.getValue(player, RESOURCE_KEY);
    }

    /**
     * Add {@code amount} charges, clamped to [0, MAX_CHARGES]. Syncs HUD to client immediately.
     */
    public static void addCharges(ServerPlayer player, int amount) {
        player.getData(CompatAttachments.resourceState())
              .clampedAdd(RESOURCE_KEY, amount, 0, MAX_CHARGES);
        CompatAttachments.syncResourceValuesToClient(player);
    }

    /**
     * Try to spend {@code amount} charges.
     * At HP &lt; 3❤, cost is halved (survival instinct perk).
     * Negative {@code amount} acts as a refund — adds charges back unconditionally.
     *
     * @return {@code true} if charges were successfully deducted.
     */
    public static boolean spendCharges(ServerPlayer player, int amount) {
        if (amount < 0) {
            addCharges(player, -amount);
            return true;
        }
        boolean lowHp = player.getHealth() <= ChaosAddonConfig.get().bloodLowHpThreshold;
        int     cost  = lowHp ? Math.max(1, amount / 2) : amount;
        boolean ok    = ResourcePower.deduct(player, RESOURCE_KEY, cost);
        if (ok) player.getPersistentData().putLong(LAST_FULL_KEY, 0); // reset overload timer
        return ok;
    }

    public static boolean isLowHp(ServerPlayer player) {
        return player.getHealth() <= ChaosAddonConfig.get().bloodLowHpThreshold;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /** Directly set charge count (e.g. overload drain). Syncs to client. */
    private static void setCharges(ServerPlayer player, int value) {
        player.getData(CompatAttachments.resourceState()).set(RESOURCE_KEY, value);
        CompatAttachments.syncResourceValuesToClient(player);
    }
}
