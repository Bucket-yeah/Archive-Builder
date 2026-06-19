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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Blood Smith passives:
 * - blood_bank: convert incoming damage to blood charges (shown in actionbar)
 * - blood_armor: regenerating Absorption shield every 10s
 * - survival_instinct: Speed III at low HP
 */
public class BloodSmithHandler {

    public static final String CHARGE_KEY = "chaos_blood_charges";
    private static final int MAX_CHARGES = 100;
    private static final Map<UUID, Long> INSTINCT_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> ARMOR_REGEN_TICK = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_bank")) return;

        long now = level.getGameTime();
        int charges = getCharges(player);

        // Actionbar display
        String bar = buildChargeBar(charges);
        player.displayClientMessage(
            Component.literal("🩸 " + bar + " " + charges + "/100")
                .withStyle(charges < 20 ? ChatFormatting.RED : ChatFormatting.DARK_RED),
            true);

        // Blood Armor: regenerate absorption every 10 seconds
        if (OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_armor")) {
            Long lastRegen = ARMOR_REGEN_TICK.getOrDefault(player.getUUID(), 0L);
            if (now - lastRegen >= 200) { // 10 seconds
                player.addEffect(new MobEffectInstance(
                    MobEffects.ABSORPTION, 220, 1, false, false)); // 4 HP absorption
                ARMOR_REGEN_TICK.put(player.getUUID(), now);
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    8, 0.4, 0.6, 0.4, 0.03);
            }
        }

        // Survival instinct: speed at low HP
        if (OriginHelper.hasPower(player, "chaos_addon:blood_smith/survival_instinct")) {
            if (player.getHealth() <= 4.0f) {
                Long cooldown = INSTINCT_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                if (now - cooldown >= 600) { // 30 seconds
                    player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 100, 2, false, true)); // Speed III, 5s
                    INSTINCT_COOLDOWN.put(player.getUUID(), now);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        20, 0.4, 0.6, 0.4, 0.1);
                    level.playSound(null, player.blockPosition(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
                }
            }
        }
    }

    /** Blood bank: incoming damage converts to charges */
    @SubscribeEvent
    public static void onDamageReceived(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:blood_smith/blood_bank")) return;

        float dmg = event.getAmount();
        int gained = (int) Math.ceil(dmg * 2); // 1 HP = 2 charges
        // Check if low HP — low cost mode
        if (player.getHealth() <= 4.0f) gained = (int) Math.ceil(gained * 1.5);

        addCharges(player, gained);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA,
                player.getX(), player.getY() + 1.0, player.getZ(),
                (int) (dmg * 3), 0.3, 0.5, 0.3, 0.05);
        }
    }

    // ---------- Charge management ----------
    public static int getCharges(ServerPlayer player) {
        return Math.min(player.getPersistentData().getInt(CHARGE_KEY), MAX_CHARGES);
    }

    public static boolean spendCharges(ServerPlayer player, int amount) {
        int current = getCharges(player);
        // Low HP discount
        if (player.getHealth() <= 4.0f) amount = Math.max(1, amount / 2);
        if (current < amount) return false;
        player.getPersistentData().putInt(CHARGE_KEY, current - amount);
        return true;
    }

    public static void addCharges(ServerPlayer player, int amount) {
        int current = player.getPersistentData().getInt(CHARGE_KEY);
        player.getPersistentData().putInt(CHARGE_KEY,
            Math.min(MAX_CHARGES, current + amount));
    }

    private static String buildChargeBar(int charges) {
        int filled = charges / 10;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        sb.append("]");
        return sb.toString();
    }
}
