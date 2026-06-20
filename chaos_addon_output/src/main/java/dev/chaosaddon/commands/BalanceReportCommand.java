package dev.chaosaddon.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.chaosaddon.util.OriginHelper;

import java.util.*;

/**
 * /chaos balance-report — prints per-race kill/death stats from persistent player NBT.
 * Stats keys: chaos_stat_kills, chaos_stat_deaths (written by ProgressionHandler /
 * kill-death hooks).
 */
public class BalanceReportCommand {

    private static final List<String> ORIGINS = List.of(
        "chaos_addon:eater_of_worlds",
        "chaos_addon:swarm_lord",
        "chaos_addon:time_wanderer",
        "chaos_addon:alchemical_monk",
        "chaos_addon:phantom_archaeologist",
        "chaos_addon:deep_geomancer",
        "chaos_addon:biomorph",
        "chaos_addon:parasitic_mind",
        "chaos_addon:lunar_renegade",
        "chaos_addon:radioactive_phantom",
        "chaos_addon:nightmare_mimic",
        "chaos_addon:ancient_sentinel",
        "chaos_addon:dimension_judge",
        "chaos_addon:mycelial_symbiont",
        "chaos_addon:deep_navigator",
        "chaos_addon:infernal_shepherd",
        "chaos_addon:chaos_engineer",
        "chaos_addon:wandering_gardener",
        "chaos_addon:necrovore",
        "chaos_addon:neural_hijacker",
        "chaos_addon:blood_smith",
        "chaos_addon:star_oracle",
        "chaos_addon:mirror_phantom"
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
            Commands.literal("chaos")
                .requires(src -> src.hasPermission(0));

        root.then(Commands.literal("balance-report")
            .executes(ctx -> {
                CommandSourceStack src = ctx.getSource();
                List<ServerPlayer> players = src.getServer().getPlayerList().getPlayers();

                Map<String, int[]> stats = new LinkedHashMap<>(); // originId -> [kills, deaths, count]
                for (String id : ORIGINS) stats.put(id, new int[3]);

                for (ServerPlayer p : players) {
                    for (String originId : ORIGINS) {
                        if (OriginHelper.hasOrigin(p, originId)) {
                            int[] s = stats.get(originId);
                            s[0] += p.getPersistentData().getInt("chaos_stat_kills");
                            s[1] += p.getPersistentData().getInt("chaos_stat_deaths");
                            s[2]++;
                        }
                    }
                }

                src.sendSuccess(() -> Component.literal("§6═══ Chaos Balance Report ═══"), false);
                src.sendSuccess(() -> Component.literal(
                    String.format("§7%-28s §aK §cD §eKD  §bPlayers", "Origin")), false);

                for (Map.Entry<String, int[]> e : stats.entrySet()) {
                    if (e.getValue()[2] == 0) continue;
                    String shortName = e.getKey().replace("chaos_addon:", "");
                    int[] s = e.getValue();
                    float kd = s[1] == 0 ? s[0] : (float) s[0] / s[1];
                    src.sendSuccess(() -> Component.literal(
                        String.format("§f%-28s §a%3d §c%3d §e%.1f §b%d",
                            shortName, s[0], s[1], kd, s[2])), false);
                }

                src.sendSuccess(() -> Component.literal(
                    "§7(Статистика только онлайн-игроков)"), false);
                return 1;
            }));

        root.then(Commands.literal("testmode")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

                boolean active = player.getTags().contains("chaos_testmode");
                if (active) {
                    player.removeTag("chaos_testmode");
                    // Clear all cooldown keys
                    var nbt = player.getPersistentData();
                    for (String key : new HashSet<>(nbt.getAllKeys())) {
                        if (key.startsWith("chaos_") && (key.endsWith("_cd") || key.endsWith("_time"))) {
                            nbt.remove(key);
                        }
                    }
                    player.sendSystemMessage(Component.literal(
                        "§a[Chaos] Тестовый режим §cотключён§a."));
                } else {
                    player.addTag("chaos_testmode");
                    player.sendSystemMessage(Component.literal(
                        "§a[Chaos] Тестовый режим §eвключён§a. " +
                        "Все кулдауны сброшены. §7/chaos testmode для отключения."));
                }
                return 1;
            }));

        dispatcher.register(root);
    }
}
