package dev.chaosaddon.init;

import com.mojang.brigadier.CommandDispatcher;
import dev.chaosaddon.commands.*;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Registers ALL custom commands used by JSON active powers via origins:execute_command.
 */
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // ── Eater of Worlds ──
        RealityShiftCommand.register(dispatcher);
        AbyssSummonCommand.register(dispatcher);
        TimeLoopCommand.register(dispatcher);
        RealityCollapseCommand.register(dispatcher);

        // ── Swarm Lord ──
        SwarmAttackCommand.register(dispatcher);
        HivemindCommand.register(dispatcher);

        // ── Deep Navigator ──
        NavigatorCommands.register(dispatcher);

        // ── Alchemical Monk ──
        AlchemistCommands.register(dispatcher);

        // ── Phantom Archaeologist ──
        ArchaeologistCommands.register(dispatcher);

        // ── Parasitic Mind ──
        InfectionCommand.register(dispatcher);

        // ── Biomorph ──
        BiomeCommands.register(dispatcher);

        // ── Lunar Renegade ──
        LunarDashCommand.register(dispatcher);
        EclipseCommand.register(dispatcher);

        // ── Ancient Sentinel ──
        QuakeCommand.register(dispatcher);

        // ── Dimension Judge ──
        JudgeCommands.register(dispatcher);

        // ── Mycelial Symbiont ──
        SymbiontCommands.register(dispatcher);

        // ── Deep Geomancer ──
        GeomancerCommands.register(dispatcher);

        // ── Wandering Gardener ──
        GardenerCommands.register(dispatcher);

        // ── Misc (Infernal Shepherd, Nightmare Mimic, Radioactive Phantom, Chaos Engineer, Time Wanderer) ──
        MiscCombatCommands.register(dispatcher);

        // ── NEW v3.0.0 races ──
        NecrovoreCommands.register(dispatcher);
        NeuralHijackerCommands.register(dispatcher);
        BloodSmithCommands.register(dispatcher);
        StarOracleCommands.register(dispatcher);
        MirrorPhantomCommands.register(dispatcher);

        // ── Additional commands ──
        SilverShieldCommand.register(dispatcher);
        DeadLegionCommand.register(dispatcher);
        StarApocalypseCommand.register(dispatcher);
    }
}
