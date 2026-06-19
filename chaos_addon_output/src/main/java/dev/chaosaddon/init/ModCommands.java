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

        // ── Swarm Lord ──
        SwarmAttackCommand.register(dispatcher);
        HivemindCommand.register(dispatcher);    // also registers chaos_addon_parasite_explode

        // ── Deep Navigator ──
        NavigatorCommands.register(dispatcher);  // portal_rift, reality_anchor, void_portal

        // ── Alchemical Monk ──
        AlchemistCommands.register(dispatcher);  // golden_touch, phil_blast, transmute_flesh

        // ── Phantom Archaeologist ──
        ArchaeologistCommands.register(dispatcher); // cache_memory, defrag, chunk_scan

        // ── Parasitic Mind ──
        InfectionCommand.register(dispatcher);

        // ── Biomorph ──
        BiomeCommands.register(dispatcher);      // biome_shift, biome_burst, biome_capture

        // ── Lunar Renegade ──
        LunarDashCommand.register(dispatcher);
        EclipseCommand.register(dispatcher);

        // ── Ancient Sentinel ──
        QuakeCommand.register(dispatcher);       // plate_thrust + earthquake

        // ── Dimension Judge ──
        JudgeCommands.register(dispatcher);      // verdict, annihilate, equalize

        // ── Mycelial Symbiont ──
        SymbiontCommands.register(dispatcher);   // spore_harvest, moss_teleport, spore_fog

        // ── Deep Geomancer ──
        GeomancerCommands.register(dispatcher);  // ore_extract, rock_shift, stone_fist

        // ── Wandering Gardener ──
        GardenerCommands.register(dispatcher);   // growth_blessing, summon_rain, summon_dryad

        // ── Misc (Infernal Shepherd, Nightmare Mimic, Radioactive Phantom, Chaos Engineer, Time Wanderer) ──
        MiscCombatCommands.register(dispatcher);
    }
}
