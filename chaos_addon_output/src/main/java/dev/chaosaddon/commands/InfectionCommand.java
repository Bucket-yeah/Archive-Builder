package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.events.ParasiteHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Parasitic Mind – Infection: infects the nearest entity with <50% HP. */
public class InfectionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chaos_addon_infect")
            .requires(src -> src.hasPermission(0))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

                List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(player.blockPosition()).inflate(20),
                    e -> e != player && e.isAlive()
                        && e.getHealth() / e.getMaxHealth() < 0.5f
                );
                if (candidates.isEmpty()) return 0;

                LivingEntity target = candidates.stream()
                    .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                    .orElse(null);
                if (target == null) return 0;

                ParasiteHandler.infectEntity(player, target);
                return 1;
            }));
    }
}
