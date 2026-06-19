package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class MutationData {
    private int killCount;

    public MutationData() { killCount = 0; }
    public MutationData(int k) { killCount = k; }

    public int killCount() { return killCount; }
    public void increment() { killCount++; }

    public static final Codec<MutationData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.fieldOf("kill_count").forGetter(d -> d.killCount)
    ).apply(inst, MutationData::new));
}
