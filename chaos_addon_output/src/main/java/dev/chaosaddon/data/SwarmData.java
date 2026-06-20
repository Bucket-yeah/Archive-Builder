package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SwarmData {
    private List<UUID> bugUUIDs;
    private int evolutionLevel;  // 0=Silverfish, 1=CaveSpider, 2=Spider
    private int totalKills;      // total mob kills by swarm (tracks evolution progress)

    public SwarmData() { this.bugUUIDs = new ArrayList<>(); this.evolutionLevel = 0; this.totalKills = 0; }
    public SwarmData(List<UUID> ids, int evo, int kills) {
        this.bugUUIDs = new ArrayList<>(ids);
        this.evolutionLevel = evo;
        this.totalKills = kills;
    }

    public List<UUID> bugUUIDs() { return bugUUIDs; }
    public int evolutionLevel() { return evolutionLevel; }
    public int totalKills() { return totalKills; }
    public void addKill() { totalKills++; }
    public void setEvolutionLevel(int lvl) { evolutionLevel = Math.min(2, lvl); }

    /** Returns true if the swarm evolved to next tier. */
    public boolean checkEvolution() {
        if (evolutionLevel == 0 && totalKills >= 30) { evolutionLevel = 1; return true; }
        if (evolutionLevel == 1 && totalKills >= 100) { evolutionLevel = 2; return true; }
        return false;
    }

    public static final Codec<SwarmData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        com.mojang.serialization.Codec.list(
            com.mojang.serialization.Codec.STRING.xmap(UUID::fromString, UUID::toString))
            .fieldOf("bug_uuids").forGetter(d -> d.bugUUIDs.stream()
                .map(Object::toString).map(UUID::fromString).toList()),
        com.mojang.serialization.Codec.INT.optionalFieldOf("evolution_level", 0).forGetter(d -> d.evolutionLevel),
        com.mojang.serialization.Codec.INT.optionalFieldOf("total_kills", 0).forGetter(d -> d.totalKills)
    ).apply(inst, SwarmData::new));
}
