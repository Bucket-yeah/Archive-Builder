package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Origin Hunter System: once per in-game day (24000 ticks), a thematic hunter mob
 * spawns near the player. Hunters are stronger than normal versions and pursue the player.
 * They drop unique loot and give progression points on defeat.
 */
public class HunterHandler {

    private static final Random RNG = new Random();
    private static final Map<UUID, Long> LAST_HUNT_TICK = new HashMap<>();

    private static final long HUNT_INTERVAL = 24000L; // 1 in-game day (~20 real minutes)

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 200 != 0) return; // check every 10 seconds

        long now = level.getGameTime();
        UUID pid = player.getUUID();
        long lastHunt = LAST_HUNT_TICK.getOrDefault(pid, 0L);

        if (now - lastHunt < HUNT_INTERVAL) return;
        if (!level.isNight()) return; // only spawn hunters at night
        if (!level.canSeeSky(player.blockPosition())) return; // must be outdoors

        LAST_HUNT_TICK.put(pid, now);
        spawnHunterForOrigin(player, level);
    }

    private static void spawnHunterForOrigin(ServerPlayer player, ServerLevel level) {
        // Spawn ~20 blocks away from player
        double angle = RNG.nextDouble() * Math.PI * 2;
        int dist = 18 + RNG.nextInt(5);
        int tx = (int)(player.getX() + Math.cos(angle) * dist);
        int tz = (int)(player.getZ() + Math.sin(angle) * dist);
        int ty = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, tx, tz);
        BlockPos spawnPos = new BlockPos(tx, ty, tz);

        Mob hunter = null;
        String hunterName;
        String originId = getActiveOrigin(player);

        switch (originId) {
            case "blood_smith" -> {
                hunter = EntityType.VINDICATOR.create(level);
                hunterName = "§cОчищенный Рыцарь";
            }
            case "necrovore" -> {
                hunter = EntityType.PILLAGER.create(level);
                hunterName = "§fПурификатор";
            }
            case "biomorph" -> {
                hunter = EntityType.EVOKER.create(level);
                hunterName = "§2Стабилизатор";
            }
            case "swarm_lord" -> {
                hunter = EntityType.RAVAGER.create(level);
                hunterName = "§6Разрушитель Роя";
            }
            case "lunar_renegade" -> {
                hunter = EntityType.WARDEN.create(level);
                hunterName = "§bСолнечный Страж";
            }
            case "phantom_archaeologist" -> {
                hunter = EntityType.WITHER_SKELETON.create(level);
                hunterName = "§8Призрак Прошлого";
            }
            case "parasitic_mind" -> {
                hunter = EntityType.ILLUSIONER.create(level);
                hunterName = "§5Иммунный Разум";
            }
            case "mirror_phantom" -> {
                hunter = EntityType.WITCH.create(level);
                hunterName = "§dОтражение-Убийца";
            }
            case "ancient_sentinel" -> {
                hunter = EntityType.IRON_GOLEM.create(level);
                hunterName = "§7Разрушитель Камня";
            }
            default -> {
                hunter = EntityType.SKELETON.create(level);
                hunterName = "§eОхотник на Хаос";
            }
        }

        if (hunter == null) return;

        hunter.moveTo(spawnPos.getX() + 0.5, ty, spawnPos.getZ() + 0.5, RNG.nextFloat() * 360, 0);
        hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
        hunter.setCustomName(Component.literal(hunterName));
        hunter.setCustomNameVisible(true);
        hunter.addTag("chaos_managed_entity");
        hunter.addTag("chaos_origin_hunter");
        hunter.addTag("chaos_hunter_target_" + player.getUUID());
        // Boost hunter stats
        var maxHpAttr = hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(maxHpAttr.getBaseValue() * 1.5);
        hunter.setHealth(hunter.getMaxHealth());
        var speedAttr = hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.3);
        hunter.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, true, false));
        hunter.setTarget(player);

        level.addFreshEntity(hunter);

        // Dramatic arrival effects
        level.sendParticles(ParticleTypes.FLASH, spawnPos.getX() + 0.5, ty + 1.0, spawnPos.getZ() + 0.5, 3, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spawnPos.getX() + 0.5, ty + 0.5, spawnPos.getZ() + 0.5,
            40, 0.5, 0.8, 0.5, 0.1);
        level.playSound(null, spawnPos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.5f);
        player.sendSystemMessage(Component.literal(
            "§c☠ " + hunterName + " §7охотится на вас! §8(~" + dist + "м)"));
    }

    private static String getActiveOrigin(ServerPlayer player) {
        String[] origins = {
            "blood_smith", "necrovore", "biomorph", "swarm_lord", "lunar_renegade",
            "phantom_archaeologist", "parasitic_mind", "mirror_phantom", "ancient_sentinel",
            "eater_of_worlds", "time_wanderer", "alchemical_monk", "deep_geomancer",
            "radioactive_phantom", "dimension_judge", "chaos_engineer", "star_oracle",
            "neural_hijacker", "wandering_gardener", "deep_navigator", "nightmare_mimic",
            "infernal_shepherd", "mycelial_symbiont"
        };
        for (String o : origins) {
            if (OriginHelper.hasOrigin(player, "chaos_addon:" + o)) return o;
        }
        return "unknown";
    }
}
