package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SwarmData {
    private List<UUID> bugUUIDs;

    public SwarmData() { this.bugUUIDs = new ArrayList<>(); }
    public SwarmData(List<UUID> ids) { this.bugUUIDs = new ArrayList<>(ids); }

    public List<UUID> bugUUIDs() { return bugUUIDs; }

    public static final Codec<SwarmData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        com.mojang.serialization.Codec.list(
            com.mojang.serialization.Codec.STRING.xmap(UUID::fromString, UUID::toString))
            .fieldOf("bug_uuids").forGetter(d -> d.bugUUIDs.stream()
                .map(Object::toString).map(UUID::fromString).toList())
    ).apply(inst, SwarmData::new));
}
