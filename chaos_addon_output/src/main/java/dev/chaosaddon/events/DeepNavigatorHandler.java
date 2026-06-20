package dev.chaosaddon.events;

import dev.chaosaddon.util.OriginHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Handles Deep Navigator (Dimension Traveler) passives:
 * - spatial_memory: each visited dimension gives a permanent stacking bonus
 * - portal_vision: glow entities near portals; 5s glow after teleport
 * - ephemeral_flesh: lava/fall immunity; potions 30% shorter; can walk on lava (via JSON + partial Java)
 */
public class DeepNavigatorHandler {

    private static final String DIM_KEY = "chaos_navigator_dims";
    private static final Map<UUID, Set<String>> VISITED_DIMS = new HashMap<>();
    private static final Map<UUID, Long> PORTAL_GLOW_EXPIRY = new HashMap<>();

    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:deep_navigator/spatial_memory")) return;

        long now = level.getGameTime();
        UUID pid = player.getUUID();

        // Track current dimension
        String currentDim = level.dimension().location().toString();
        Set<String> visited = VISITED_DIMS.computeIfAbsent(pid, k -> {
            // Load from NBT
            Set<String> set = new HashSet<>();
            String[] saved = player.getPersistentData()
                .getString(DIM_KEY).split(",");
            for (String s : saved) if (!s.isBlank()) set.add(s);
            set.add("minecraft:overworld"); // always have overworld
            return set;
        });
        boolean newDim = visited.add(currentDim);
        if (newDim) {
            // Save to NBT
            player.getPersistentData().putString(DIM_KEY,
                String.join(",", visited));
            player.sendSystemMessage(Component.literal(
                "§5✦ Новое измерение исследовано: §r" + currentDim +
                " §7(+" + visited.size() + " бонусов)"));
        }

        // Apply buffs based on visited dims (applied every 10s to refresh)
        if (now % 200 == 0) {
            if (visited.contains(NETHER)) {
                // +5% speed (Speed I)
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 220, 0, false, false));
            }
            if (visited.contains(END)) {
                // +1 HP max — we simulate via absorption
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 220, 0, false, false));
            }
            // Each extra dimension: Luck I
            if (visited.size() >= 3) {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 220, Math.min(visited.size() - 2, 3), false, false));
            }
        }

        // Portal Vision: glow entities near portals (within 5 blocks of portal blocks)
        if (OriginHelper.hasPower(player, "chaos_addon:deep_navigator/portal_vision") && now % 40 == 0) {
            // Check post-portal glow
            Long glowExpiry = PORTAL_GLOW_EXPIRY.getOrDefault(pid, 0L);
            if (now < glowExpiry) {
                level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(20),
                    e -> e != player && e.isAlive())
                    .forEach(e -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false)));
            }
        }
    }

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!OriginHelper.hasPower(player, "chaos_addon:deep_navigator/portal_vision")) return;

        UUID pid = player.getUUID();
        if (player.level() instanceof ServerLevel level) {
            // Give 5s glow after dimension change
            long expiry = level.getGameTime() + 100;
            PORTAL_GLOW_EXPIRY.put(pid, expiry);

            level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                30, 0.5, 1.0, 0.5, 0.1);

            // Dimensional Fatigue: brief debuffs on each dimension transition
            // Reduced if player has visited many dims (adaptation)
            Set<String> visited = VISITED_DIMS.computeIfAbsent(pid, k -> new HashSet<>());
            boolean mastered = visited.containsAll(Set.of("minecraft:overworld", NETHER, END));
            if (!mastered) {
                int fatigueDuration = Math.max(40, 100 - visited.size() * 20); // 100→40 ticks as dims increase
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, fatigueDuration, 1, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, fatigueDuration, 0, false, true));
                level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.4, 0.6, 0.4, 0.15);
                player.displayClientMessage(Component.literal(
                    "§5⚡ Размерная усталость §8(" + (fatigueDuration / 20) + "с)"), true);
            } else {
                // Mastered: instant boost instead
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
                player.displayClientMessage(Component.literal("§b✦ Мастер измерений!"), true);
            }
        }

        // Track new dimension in visited set
        String dest = event.getDimension().location().toString();
        VISITED_DIMS.computeIfAbsent(pid, k -> new HashSet<>()).add(dest);
        player.getPersistentData().putString(DIM_KEY,
            String.join(",", VISITED_DIMS.get(pid)));
    }
}
