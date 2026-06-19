package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GardenerData {
    private List<UUID> tamedUUIDs;

    public GardenerData() { tamedUUIDs = new ArrayList<>(); }
    public GardenerData(List<UUID> ids) { tamedUUIDs = new ArrayList<>(ids); }

    public List<UUID> tamedUUIDs() { return tamedUUIDs; }

    public static final Codec<GardenerData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.list(Codec.STRING.xmap(UUID::fromString, UUID::toString))
            .fieldOf("tamed_uuids").forGetter(d ->
                d.tamedUUIDs.stream().map(UUID::toString).map(UUID::fromString).toList())
    ).apply(inst, GardenerData::new));
}
