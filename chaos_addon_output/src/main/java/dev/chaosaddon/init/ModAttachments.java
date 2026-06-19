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

    public static final Supplier<AttachmentType<SwarmData>> SWARM_DATA =
            ATTACHMENT_TYPES.register("swarm_data",
                    () -> AttachmentType.<SwarmData>builder((Supplier<SwarmData>) SwarmData::new).serialize(SwarmData.CODEC).build());

    public static final Supplier<AttachmentType<ParasiteData>> PARASITE_DATA =
            ATTACHMENT_TYPES.register("parasite_data",
                    () -> AttachmentType.<ParasiteData>builder((Supplier<ParasiteData>) ParasiteData::new).serialize(ParasiteData.CODEC).build());

    public static final Supplier<AttachmentType<TimeData>> TIME_DATA =
            ATTACHMENT_TYPES.register("time_data",
                    () -> AttachmentType.<TimeData>builder((Supplier<TimeData>) TimeData::new).serialize(TimeData.CODEC).build());

    public static final Supplier<AttachmentType<BiomeData>> BIOME_DATA =
            ATTACHMENT_TYPES.register("biome_data",
                    () -> AttachmentType.<BiomeData>builder((Supplier<BiomeData>) BiomeData::new).serialize(BiomeData.CODEC).build());

    public static final Supplier<AttachmentType<LunarData>> LUNAR_DATA =
            ATTACHMENT_TYPES.register("lunar_data",
                    () -> AttachmentType.<LunarData>builder((Supplier<LunarData>) LunarData::new).serialize(LunarData.CODEC).build());

    public static final Supplier<AttachmentType<MossData>> MOSS_DATA =
            ATTACHMENT_TYPES.register("moss_data",
                    () -> AttachmentType.<MossData>builder((Supplier<MossData>) MossData::new).serialize(MossData.CODEC).build());

    public static final Supplier<AttachmentType<GardenerData>> GARDENER_DATA =
            ATTACHMENT_TYPES.register("gardener_data",
                    () -> AttachmentType.<GardenerData>builder((Supplier<GardenerData>) GardenerData::new).serialize(GardenerData.CODEC).build());

    public static final Supplier<AttachmentType<MutationData>> MUTATION_DATA =
            ATTACHMENT_TYPES.register("mutation_data",
                    () -> AttachmentType.<MutationData>builder((Supplier<MutationData>) MutationData::new).serialize(MutationData.CODEC).build());

    public static void register(IEventBus bus) {
        ATTACHMENT_TYPES.register(bus);
    }
}
