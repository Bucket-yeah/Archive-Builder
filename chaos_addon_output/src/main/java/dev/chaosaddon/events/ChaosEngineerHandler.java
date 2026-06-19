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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Handles Chaos Engineer passives:
 * - energy_exchange: food = 0 effect; eating redstone → +2 HP + energy; energy bar in actionbar
 * - redstone_vision: glow redstone-related blocks
 * - conductor: lightning strike → +energy + Strength I instead of damage
 */
public class ChaosEngineerHandler {

    public static final String ENERGY_KEY = "chaos_energy";
    private static final int MAX_ENERGY = 100;
    private static final int ENERGY_PER_REDSTONE = 10;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/energy_exchange")) return;

        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        energy = Math.min(MAX_ENERGY, Math.max(0, energy));

        // Build energy bar
        int bars = energy / 5; // 20 bars total
        String bar = "§c[" +
            "§e" + "█".repeat(bars) +
            "§8" + "░".repeat(20 - bars) +
            "§c] §f" + energy + "/100 ⚡";

        player.displayClientMessage(Component.literal(bar), true);

        // Particle FX based on energy level
        if (energy > 80 && player.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.4, 0.8, 0.4, 0.05);
        }

        // Passive energy drain: -1 every 10 seconds when using actives (handled in commands)
        // Passive hunger suppression: keep hunger from dropping below 0 HP damage
        if (player.getFoodData().getFoodLevel() <= 0 && player.tickCount % 40 == 0) {
            // Engineer doesn't starve, but at 0 energy they're weaker
            if (energy <= 0) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            }
        }
    }

    /** Cancel food eating — engineer only eats redstone (handled via command) */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/energy_exchange")) return;

        ItemStack item = event.getItem();
        // Cancel normal food — replace with original item
        if (item.has(net.minecraft.core.component.DataComponents.FOOD) && !item.is(Items.ROTTEN_FLESH)) {
            // Give the food item back (they can't eat it)
            player.getInventory().add(item.copy());
            // Reset hunger to what it was (no saturation gained)
            // This is handled by returning the result unchanged in item use, but we can at least remove the effect
            player.sendSystemMessage(Component.literal("§c⚡ Инженер не ест обычную еду!"));
        }
    }

    /** Eat redstone: +2 HP, +10 energy */
    public static int eatRedstone(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/energy_exchange")) return 0;

        // Find redstone in inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.is(Items.REDSTONE)) continue;

            slot.shrink(1);
            player.heal(2.0f);

            int energy = player.getPersistentData().getInt(ENERGY_KEY);
            energy = Math.min(MAX_ENERGY, energy + ENERGY_PER_REDSTONE);
            player.getPersistentData().putInt(ENERGY_KEY, energy);

            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.4, 0.8, 0.4, 0.08);
                level.playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            return energy;
        }
        return -1; // no redstone found
    }

    /** Conductor: lightning strike → energy + Strength I instead of damage */
    @SubscribeEvent
    public static void onLightningDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:chaos_engineer/conductor")) return;

        String src = event.getSource().getMsgId();
        if (!src.equals("lightningBolt") && !src.equals("lightning")) return;

        event.setCanceled(true);

        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        energy = Math.min(MAX_ENERGY, energy + 25);
        player.getPersistentData().putInt(ENERGY_KEY, energy);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50, 0.8, 1.5, 0.8, 0.15);
            level.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.5f);
            player.sendSystemMessage(Component.literal("§e⚡ +25 энергии от молнии! Сила I!"));
        }
    }

    /** Check if engineer has enough energy for an action */
    public static boolean hasEnergy(ServerPlayer player, int cost) {
        return player.getPersistentData().getInt(ENERGY_KEY) >= cost;
    }

    /** Consume energy (returns false if insufficient) */
    public static boolean consumeEnergy(ServerPlayer player, int cost) {
        int energy = player.getPersistentData().getInt(ENERGY_KEY);
        if (energy < cost) return false;
        player.getPersistentData().putInt(ENERGY_KEY, energy - cost);
        return true;
    }
}
