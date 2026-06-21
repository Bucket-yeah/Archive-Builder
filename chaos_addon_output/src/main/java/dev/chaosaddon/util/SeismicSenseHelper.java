package dev.chaosaddon.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reusable seismic sense / echo-ping pattern.
 *
 * Extracted from GeneralPowerHandler (Ancient Sentinel seismic sense logic).
 *
 * Reused by:
 *   - Ancient Sentinel:  earth_bond       → ping nearby players
 *   - Deep Geomancer:    earth_hearing    → ping any nearby living entity
 *   - Deep Geomancer:    ore_vision       → direction hint toward nearest ore cluster
 *   - Dimension Judge:   all_seeing_eye   → ping hostile entities
 *
 * Usage example (in a PlayerTickEvent.Post handler, every N ticks):
 * <pre>
 *   // Seismic player detection (Ancient Sentinel style)
 *   if (!player.isSprinting() {@literal &&} player.onGround() {@literal &&} player.tickCount % 20 == 0)
 *       SeismicSenseHelper.pingNearbyPlayers(player, level, 50, "§6🌍 Сейсмо: ");
 *
 *   // General entity detection (Deep Geomancer earth_hearing style)
 *   if (player.tickCount % 40 == 0)
 *       SeismicSenseHelper.pingNearbyEntities(player, level, 30,
 *           e -> !(e instanceof Player),
 *           "§a🌿 Слух Земли: ", ChatFormatting.GREEN);
 * </pre>
 */
public final class SeismicSenseHelper {

    private SeismicSenseHelper() {}

    /**
     * Find the nearest player within {@code radius} blocks and display their cardinal direction
     * and distance in the actionbar. Spawns a stone-block particle ground pulse at the sensor's feet.
     *
     * Callers are responsible for the "still" condition check
     * (not sprinting, not crouching, on-ground) before calling.
     *
     * @param player  The sensing player
     * @param level   Server level
     * @param radius  Detection radius in blocks
     * @param prefix  Actionbar prefix, e.g. "§6🌍 Сейсмо: "
     */
    public static void pingNearbyPlayers(ServerPlayer player, ServerLevel level,
            double radius, String prefix) {
        List<Player> nearby = level.getEntitiesOfClass(Player.class,
            player.getBoundingBox().inflate(radius),
            e -> e != player && e.isAlive() && e.onGround());
        if (nearby.isEmpty()) return;
        Player closest = nearby.stream()
            .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
            .orElse(null);
        if (closest == null) return;
        displayPing(player, level, closest, prefix, ChatFormatting.GOLD);
    }

    /**
     * Find the nearest {@link LivingEntity} matching {@code filter} within {@code radius} blocks
     * and display its cardinal direction and distance in the actionbar.
     *
     * @param player  The sensing player
     * @param level   Server level
     * @param radius  Detection radius in blocks
     * @param filter  Entity predicate — e.g. {@code e -> !(e instanceof Player)} for mobs only
     * @param prefix  Actionbar prefix string
     * @param color   Text colour for the message
     */
    public static void pingNearbyEntities(ServerPlayer player, ServerLevel level,
            double radius, Predicate<LivingEntity> filter,
            String prefix, ChatFormatting color) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(radius),
            e -> e != player && e.isAlive() && filter.test(e));
        if (nearby.isEmpty()) return;
        LivingEntity closest = nearby.stream()
            .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
            .orElse(null);
        if (closest == null) return;
        displayPing(player, level, closest, prefix, color);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private static void displayPing(ServerPlayer player, ServerLevel level,
            Entity target, String prefix, ChatFormatting color) {
        double dist = target.distanceTo(player);
        String dir = getCardinalDirection(player, target);
        player.displayClientMessage(
            Component.literal(prefix + dir + " §8(" + (int) dist + "м)")
                .withStyle(color), true);
        level.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
            player.getX(), player.getY() + 0.05, player.getZ(),
            8, 0.5, 0.05, 0.5, 0.0);
    }

    /**
     * Returns an 8-point compass arrow+abbreviation for the direction from {@code from} to {@code to}.
     * Can also be used stand-alone for any direction-display need.
     */
    public static String getCardinalDirection(Entity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "→ В";
        if (angle < 67.5)  return "↘ ЮВ";
        if (angle < 112.5) return "↓ Ю";
        if (angle < 157.5) return "↙ ЮЗ";
        if (angle < 202.5) return "← З";
        if (angle < 247.5) return "↖ СЗ";
        if (angle < 292.5) return "↑ С";
        return "↗ СВ";
    }
}
