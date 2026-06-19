package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles XP-based hunger for:
 *  - Eater of Worlds: absorbs XP to fill hunger; faster decay without XP
 *  - Infernal Shepherd: fills hunger by standing in lava; faster decay in water
 */
public class HungerXPHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        handleEaterHunger(player);
        handleInfernalHunger(player);
    }

    private static void handleEaterHunger(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:eater_of_worlds/hunger_of_infinity")) return;

        FoodData food = player.getFoodData();

        if (player.tickCount % 20 == 0) {
            if (player.totalExperience <= 0) {
                player.causeFoodExhaustion(0.025f);
            }
        }

        if (player.tickCount % 20 == 0 && food.getFoodLevel() < 20) {
            if (player.experienceLevel >= 1) {
                player.giveExperienceLevels(-1);
                food.eat(3, 0.0f);

                ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.4, 0.5, 0.4, 0.05
                );
            }
        }
    }

    private static void handleInfernalHunger(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_feeding")) return;

        FoodData food = player.getFoodData();

        if (player.tickCount % 40 == 0 && player.isInLava()) {
            food.eat(1, 0.5f);
        }

        if (player.tickCount % 20 == 0 && player.isInWater()) {
            player.causeFoodExhaustion(0.025f);
        }
    }
}
