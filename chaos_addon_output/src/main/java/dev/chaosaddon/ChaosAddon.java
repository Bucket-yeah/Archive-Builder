package dev.chaosaddon;

import dev.chaosaddon.config.ChaosAddonConfig;
import dev.chaosaddon.events.*;
import dev.chaosaddon.init.ModAttachments;
import dev.chaosaddon.init.ModCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ChaosAddon.MOD_ID)
public class ChaosAddon {

    public static final String MOD_ID = "chaos_addon";

    public ChaosAddon(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ChaosAddonConfig::onLoad);

        // ── Original 18 races ──
        NeoForge.EVENT_BUS.register(ChaoticAuraHandler.class);
        NeoForge.EVENT_BUS.register(HungerXPHandler.class);
        NeoForge.EVENT_BUS.register(SwarmHandler.class);
        NeoForge.EVENT_BUS.register(ParasiteHandler.class);
        NeoForge.EVENT_BUS.register(LunarHandler.class);
        NeoForge.EVENT_BUS.register(BiomorphHandler.class);
        NeoForge.EVENT_BUS.register(RadioactiveHandler.class);
        NeoForge.EVENT_BUS.register(GeneralPowerHandler.class);
        NeoForge.EVENT_BUS.register(AlchemistHandler.class);
        NeoForge.EVENT_BUS.register(ArchaeologistHandler.class);

        // ── 5 New races (v3.0.0) ──
        NeoForge.EVENT_BUS.register(NecrovoreHandler.class);
        NeoForge.EVENT_BUS.register(NeuralHijackerHandler.class);
        NeoForge.EVENT_BUS.register(BloodSmithHandler.class);
        NeoForge.EVENT_BUS.register(StarOracleHandler.class);
        NeoForge.EVENT_BUS.register(MirrorPhantomHandler.class);

        // ── Commands ──
        NeoForge.EVENT_BUS.register(ModCommands.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
        });
    }
}
