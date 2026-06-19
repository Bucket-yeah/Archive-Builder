package dev.chaosaddon.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Utility to check if a player has a specific Origins power active.
 *
 * Uses the Origins NeoForge 0.1-alpha API.
 * If the API changes in future alpha versions, update this class only.
 */
public class OriginHelper {

    /**
     * Returns true if the player currently has the given power active.
     *
     * @param player    the player to check
     * @param powerId   full resource location string, e.g. "chaos_addon:eater_of_worlds/chaotic_aura"
     */
    public static boolean hasPower(Player player, String powerId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(powerId);
            // Origins NeoForge API — adjust package if alpha version changes it
            var component = io.github.apace100.apoli.component.PowerHolderComponent.KEY.getNullable(player);
            if (component == null) return false;
            return component.hasPower(io.github.apace100.apoli.power.PowerReference.of(loc));
        } catch (Exception e) {
            // Graceful fallback: if Origins API not available, return false
            return false;
        }
    }

    /**
     * Returns true if the player currently has the given origin active.
     * Checks via the origin layer "chaos_addon" with origin id.
     */
    public static boolean hasOrigin(Player player, String originId) {
        try {
            ResourceLocation layerLoc  = ResourceLocation.parse("chaos_addon:chaos_addon");
            ResourceLocation originLoc = ResourceLocation.parse(originId);
            var component = io.github.apace100.origins.component.OriginComponent.KEY.getNullable(player);
            if (component == null) return false;
            var layer = io.github.apace100.origins.registry.OriginLayers.getLayer(layerLoc);
            if (layer == null) return false;
            var origin = component.getOrigin(layer);
            return origin != null && origin.getIdentifier().equals(originLoc);
        } catch (Exception e) {
            return false;
        }
    }
}
