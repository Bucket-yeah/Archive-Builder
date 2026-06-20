package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores per-biome imprint times, carry-biome, and unlocked DNA types for Biomorph.
 * DNA is permanently unlocked after spending 3+ minutes in a biome type.
 */
public class BiomeData {
    private Map<String, Integer> biomeTicks;
    private String carryBiome;
    private Set<String> unlockedDna;

    public BiomeData() {
        biomeTicks = new HashMap<>();
        carryBiome = "";
        unlockedDna = new HashSet<>();
    }
    public BiomeData(Map<String, Integer> bt, String cb, List<String> dna) {
        this.biomeTicks = new HashMap<>(bt);
        this.carryBiome = cb;
        this.unlockedDna = new HashSet<>(dna);
    }

    public void incrementBiomeTime(String biome) {
        biomeTicks.merge(biome, 1, Integer::sum);
    }
    public int getBiomeTime(String biome) { return biomeTicks.getOrDefault(biome, 0); }
    public String carryBiome() { return carryBiome; }
    public void setCarryBiome(String b) { carryBiome = b; }
    public int getUniqueBiomeCount() {
        return (int) biomeTicks.entrySet().stream().filter(e -> e.getValue() >= 3600).count();
    }

    /** Unlock a DNA type permanently. Returns true if it was newly unlocked. */
    public boolean unlockDna(String type) {
        return unlockedDna.add(type);
    }
    public Set<String> getUnlockedDna() { return unlockedDna; }
    public boolean hasDna(String type) { return unlockedDna.contains(type); }

    public static final Codec<BiomeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("biome_ticks").forGetter(d -> d.biomeTicks),
        Codec.STRING.fieldOf("carry_biome").forGetter(d -> d.carryBiome),
        Codec.STRING.listOf().optionalFieldOf("unlocked_dna", new ArrayList<>()).forGetter(d -> new ArrayList<>(d.unlockedDna))
    ).apply(inst, BiomeData::new));
}
