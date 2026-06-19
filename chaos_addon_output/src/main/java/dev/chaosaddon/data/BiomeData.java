package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores per-biome imprint times and selected carry-biome for Biomorph.
 */
public class BiomeData {
    private Map<String, Integer> biomeTicks;
    private String carryBiome;

    public BiomeData() { biomeTicks = new HashMap<>(); carryBiome = ""; }
    public BiomeData(Map<String, Integer> bt, String cb) {
        this.biomeTicks = new HashMap<>(bt); this.carryBiome = cb;
    }

    public void incrementBiomeTime(String biome) {
        biomeTicks.merge(biome, 1, Integer::sum);
    }
    public int getBiomeTime(String biome) { return biomeTicks.getOrDefault(biome, 0); }
    public String carryBiome() { return carryBiome; }
    public void setCarryBiome(String b) { carryBiome = b; }

    public static final Codec<BiomeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("biome_ticks").forGetter(d -> d.biomeTicks),
        Codec.STRING.fieldOf("carry_biome").forGetter(d -> d.carryBiome)
    ).apply(inst, BiomeData::new));
}
