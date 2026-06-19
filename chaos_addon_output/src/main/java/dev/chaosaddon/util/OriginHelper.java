package dev.chaosaddon.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Utility to check if a player has a specific NeoOrigins power/origin active.
 * Uses NeoOrigins 2.2.5 API (com.cyberday1.neoorigins).
 *
 * NOTE: NeoOriginsAPI.hasCapability() checks if any power's PowerType.capabilities()
 * set contains the given string — it does NOT check by power ID.
 * We must use NeoOriginsAPI.powers() and match by ResourceLocation id().
 */
public class OriginHelper {

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
            return false;
        }
    }

    /**
     * Returns true if the player currently has the given origin active.
     */
    public static boolean hasOrigin(Player player, String originId) {
        try {
            if (!(player instanceof ServerPlayer sp)) return false;
            ResourceLocation loc = ResourceLocation.parse(originId);
            return com.cyberday1.neoorigins.api.NeoOriginsAPI.powers(sp)
                .stream()
                .anyMatch(h -> h.id().equals(loc));
        } catch (Exception e) {
            return false;
        }
    }
}
