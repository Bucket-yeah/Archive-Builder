package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class LunarData {
    private int lastPhase;

    public LunarData() { lastPhase = 0; }
    public LunarData(int phase) { lastPhase = phase; }

    public int lastPhase() { return lastPhase; }
    public void setLastPhase(int p) { lastPhase = p; }

    public static final Codec<LunarData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.fieldOf("last_phase").forGetter(d -> d.lastPhase)
    ).apply(inst, LunarData::new));
}
