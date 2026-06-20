package dev.chaosaddon.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to check if a player has a specific NeoOrigins power/origin active.
 * Uses NeoOrigins 2.2.5 API (com.cyberday1.neoorigins).
 *
 * NOTE: NeoOriginsAPI.powers() returns all active powers for the player.
 * We match by ResourceLocation id(), not by capability string.
 */
public class OriginHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosAddon/OriginHelper");

    /**
     * Returns true if the player currently has the given power active.
     * Checks by power ResourceLocation ID, e.g. "chaos_addon:eater_of_worlds/chaotic_aura"
     */
    public static boolean hasPower(Player player, String powerId) {
        try {
            if (!(player instanceof ServerPlayer sp)) return false;
            ResourceLocation loc = ResourceLocation.parse(powerId);
            return com.cyberday1.neoorigins.api.NeoOriginsAPI.powers(sp)
                .stream()
                .anyMatch(h -> h.id().equals(loc));
        } catch (Exception e) {
            LOGGER.warn("OriginHelper.hasPower failed for powerId='{}': {}", powerId, e.getMessage());
            return false;
        }
    }

    /**
     * Returns true if the player currently has the given origin active.
     * Uses the same NeoOriginsAPI.powers() list and checks for a power whose id
     * starts with the given origin id (e.g. all chaos_addon:time_wanderer/* powers
     * share the same origin prefix).
     */
    public static boolean hasOrigin(Player player, String originId) {
        try {
            if (!(player instanceof ServerPlayer sp)) return false;
            String prefix = originId + "/";
            return com.cyberday1.neoorigins.api.NeoOriginsAPI.powers(sp)
                .stream()
                .anyMatch(h -> h.id().toString().startsWith(prefix));
        } catch (Exception e) {
            LOGGER.warn("OriginHelper.hasOrigin failed for originId='{}': {}", originId, e.getMessage());
            return false;
        }
    }
}
