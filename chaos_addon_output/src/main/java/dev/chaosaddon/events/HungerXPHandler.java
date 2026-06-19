package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
        ServerLevel level = (ServerLevel) player.level();

        // Hunger falls twice as fast when no XP
        if (player.tickCount % 20 == 0 && player.experienceLevel <= 0) {
            player.causeFoodExhaustion(0.025f); // extra exhaustion
        }

        // Absorb 1 XP level → +3 hunger every second if hungry
        if (player.tickCount % 20 == 0 && food.getFoodLevel() < 20) {
            if (player.experienceLevel >= 1) {
                player.giveExperienceLevels(-1);
                food.eat(3, 0.0f);
                level.sendParticles(ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.4, 0.5, 0.4, 0.05);
            }
        }

        // Full hunger + no XP = every 5s lose 1 level but gain Regen I for 3s
        if (player.tickCount % 100 == 0 && food.getFoodLevel() >= 20 && player.experienceLevel <= 0) {
            if (player.experienceLevel >= 1) {
                player.giveExperienceLevels(-1);
            }
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.5, player.getZ(),
                8, 0.4, 0.6, 0.4, 0.05);
        }
    }

    private static void handleInfernalHunger(ServerPlayer player) {
        if (!OriginHelper.hasPower(player, "chaos_addon:infernal_shepherd/lava_feeding")) return;

        FoodData food = player.getFoodData();
        ServerLevel level = (ServerLevel) player.level();

        // Lava restores hunger
        if (player.tickCount % 40 == 0 && player.isInLava()) {
            food.eat(1, 0.5f);
        }

        // Water causes double hunger drain
        if (player.tickCount % 20 == 0 && player.isInWater()) {
            player.causeFoodExhaustion(0.025f);
        }

        // After 30s in lava: Strength II for 15s
        int lavaKey = player.getPersistentData().getInt("chaos_lava_ticks");
        if (player.isInLava()) {
            player.getPersistentData().putInt("chaos_lava_ticks", lavaKey + 1);
            if (lavaKey >= 600) { // 30 seconds
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1, false, true));
                player.getPersistentData().putInt("chaos_lava_ticks", 0);
                level.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.5, 0.8, 0.5, 0.1);
            }
        } else {
            player.getPersistentData().putInt("chaos_lava_ticks", Math.max(0, lavaKey - 1));
        }
    }

}

