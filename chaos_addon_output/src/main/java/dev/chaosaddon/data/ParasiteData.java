package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public class ParasiteData {
    private List<UUID> infectedUUIDs;

    public ParasiteData() { this.infectedUUIDs = new ArrayList<>(); }
    public ParasiteData(List<UUID> ids) { this.infectedUUIDs = new ArrayList<>(ids); }

    public List<UUID> infectedUUIDs() { return infectedUUIDs; }

    public static final Codec<ParasiteData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.list(Codec.STRING.xmap(UUID::fromString, UUID::toString))
            .fieldOf("infected_uuids").forGetter(d ->
                d.infectedUUIDs.stream().map(UUID::toString).map(UUID::fromString).toList())
    ).apply(inst, ParasiteData::new));
}
