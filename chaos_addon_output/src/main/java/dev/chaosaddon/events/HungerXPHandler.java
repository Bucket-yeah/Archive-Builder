package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FoodLevelChangeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles XP-based hunger for:
 *  - Eater of Worlds: absorbs XP to fill hunger; faster decay without XP
 *  - Infernal Shepherd: fills hunger by standing in lava; faster decay in water
 */
public class HungerXPHandler {

    // ───────── Eater of Worlds: XP → Hunger ─────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        handleEaterHunger(player);
        handleInfernalHunger(player);
    }

    private static void handleEaterHunger(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/hunger_of_infinity")) return;

        FoodData food = player.getFoodData();

        // Drain hunger twice as fast if no XP
        if (player.tickCount % 20 == 0) {
            if (player.totalExperience <= 0) {
                // halve the food exhaustion multiplier by adding extra exhaustion
                player.causeFoodExhaustion(0.025f); // extra 0.025 per tick = doubled
            }
        }

        // Absorb 1 XP level every 20 ticks → +3 food points (if hungry)
        if (player.tickCount % 20 == 0 && food.getFoodLevel() < 20) {
            if (player.experienceLevel >= 1) {
                player.giveExperienceLevels(-1);
                food.eat(3, 0.0f);

                // Visual: glow + spell particles
                player.level().sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SPELL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.4, 0.5, 0.4, 0.05
                );
            }
        }
    }

    private static void handleInfernalHunger(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_feeding")) return;

        FoodData food = player.getFoodData();

        // In lava: gain +1 saturation every 2 seconds
        if (player.tickCount % 40 == 0 && player.isInLava()) {
            food.eat(1, 0.5f);
        }

        // In water: extra exhaustion (hunger falls 2× faster)
        if (player.tickCount % 20 == 0 && player.isInWater()) {
            player.causeFoodExhaustion(0.025f);
        }
    }

    // ───────── Block eating from normal food for XP-hunger races ─────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Eater of Worlds: cannot gain hunger from food items
        if (OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/hunger_of_infinity")) {
            // Only allow if this isn't from a food item (e.g., XP absorption above)
            // We cancel natural food events; our ticker above handles XP eating
            if (event.getFoodLevel() > player.getFoodData().getFoodLevel()) {
                event.setCanceled(true);
            }
        }

        // Infernal Shepherd: cannot eat normal food at all
        if (OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_feeding")) {
            if (event.getFoodLevel() > player.getFoodData().getFoodLevel()) {
                event.setCanceled(true);
                // Damage for trying to eat wrong food
                player.hurt(player.damageSources().generic(), 1.0f);
            }
        }
    }
}
