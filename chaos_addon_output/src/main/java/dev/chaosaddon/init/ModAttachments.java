package dev.chaosaddon.init;

import dev.chaosaddon.ChaosAddon;
import dev.chaosaddon.data.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ChaosAddon.MOD_ID);

    /** Swarm Lord: bug count + UUIDs */
    public static final Supplier<AttachmentType<SwarmData>> SWARM_DATA =
            ATTACHMENT_TYPES.register("swarm_data",
                    () -> AttachmentType.builder(SwarmData::new).serialize(SwarmData.CODEC).build());

    /** Parasitic Mind: list of infected entity UUIDs */
    public static final Supplier<AttachmentType<ParasiteData>> PARASITE_DATA =
            ATTACHMENT_TYPES.register("parasite_data",
                    () -> AttachmentType.builder(ParasiteData::new).serialize(ParasiteData.CODEC).build());

    /** Phantom Archaeologist: saved player state (cache memory) */
    public static final Supplier<AttachmentType<TimeData>> TIME_DATA =
            ATTACHMENT_TYPES.register("time_data",
                    () -> AttachmentType.builder(TimeData::new).serialize(TimeData.CODEC).build());

    /** Biomorph: selected carry-biome */
    public static final Supplier<AttachmentType<BiomeData>> BIOME_DATA =
            ATTACHMENT_TYPES.register("biome_data",
                    () -> AttachmentType.builder(BiomeData::new).serialize(BiomeData.CODEC).build());

    /** Lunar Renegade: cached moon phase */
    public static final Supplier<AttachmentType<LunarData>> LUNAR_DATA =
            ATTACHMENT_TYPES.register("lunar_data",
                    () -> AttachmentType.builder(LunarData::new).serialize(LunarData.CODEC).build());

    /** Mycelial Symbiont (Moss): placed moss coordinates */
    public static final Supplier<AttachmentType<MossData>> MOSS_DATA =
            ATTACHMENT_TYPES.register("moss_data",
                    () -> AttachmentType.builder(MossData::new).serialize(MossData.CODEC).build());

    /** Wandering Gardener: tamed mob UUIDs */
    public static final Supplier<AttachmentType<GardenerData>> GARDENER_DATA =
            ATTACHMENT_TYPES.register("gardener_data",
                    () -> AttachmentType.builder(GardenerData::new).serialize(GardenerData.CODEC).build());

    /** Radioactive Phantom / general kill counter */
    public static final Supplier<AttachmentType<MutationData>> MUTATION_DATA =
            ATTACHMENT_TYPES.register("mutation_data",
                    () -> AttachmentType.builder(MutationData::new).serialize(MutationData.CODEC).build());

    public static void register(IEventBus bus) {
        ATTACHMENT_TYPES.register(bus);
    }
}
