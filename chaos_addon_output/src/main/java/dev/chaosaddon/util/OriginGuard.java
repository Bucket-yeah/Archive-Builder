package dev.chaosaddon.util;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralised origin/power gate for all Brigadier commands.
 * Use instead of ad-hoc inline checks to ensure consistent feedback and logging.
 */
public final class OriginGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosAddon/OriginGuard");

    private OriginGuard() {}

    /**
     * Returns the ServerPlayer if they have the required origin, null otherwise.
     * Sends an in-chat error to the player when the check fails.
     *
     * @param ctx       command context
     * @param originId  full origin id, e.g. "chaos_addon:time_wanderer"
     */
    public static ServerPlayer requireOrigin(CommandContext<CommandSourceStack> ctx, String originId) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return null;
        if (!OriginHelper.hasOrigin(player, originId)) {
            player.sendSystemMessage(Component.literal(
                "§c[Chaos] Эта способность доступна только носителям расы «" + originId + "»."));
            LOGGER.debug("Command blocked: {} lacks origin {}", player.getName().getString(), originId);
            return null;
        }
        return player;
    }

    /**
     * Returns the ServerPlayer if they have the required power, null otherwise.
     *
     * @param ctx     command context
     * @param powerId full power id, e.g. "chaos_addon:time_wanderer/temporal_dominion"
     */
    public static ServerPlayer requirePower(CommandContext<CommandSourceStack> ctx, String powerId) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return null;
        if (!OriginHelper.hasPower(player, powerId)) {
            player.sendSystemMessage(Component.literal(
                "§c[Chaos] У вас нет нужной способности."));
            LOGGER.debug("Command blocked: {} lacks power {}", player.getName().getString(), powerId);
            return null;
        }
        return player;
    }

    /**
     * Returns the ServerPlayer from the source with no origin check, or null if not a player.
     */
    public static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }
}
