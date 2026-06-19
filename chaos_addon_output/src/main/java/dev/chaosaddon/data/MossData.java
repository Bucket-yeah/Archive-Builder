package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores placed moss-network coordinates for the Mycelial Symbiont race.
 * (Using moss blocks instead of mycelium as specified.)
 */
public class MossData {
    private List<long[]> mossPositions;

    public MossData() { mossPositions = new ArrayList<>(); }
    public MossData(List<long[]> positions) { mossPositions = new ArrayList<>(positions); }

    public void addPosition(BlockPos pos) {
        mossPositions.add(new long[]{ pos.getX(), pos.getY(), pos.getZ() });
    }

    public List<BlockPos> getPositions() {
        return mossPositions.stream()
            .map(a -> new BlockPos((int)a[0], (int)a[1], (int)a[2]))
            .toList();
    }

    public boolean isNearAnyMoss(BlockPos playerPos, int maxDist) {
        return mossPositions.stream().anyMatch(a -> {
            double dx = playerPos.getX() - a[0];
            double dz = playerPos.getZ() - a[2];
            return Math.sqrt(dx*dx + dz*dz) <= maxDist;
        });
    }

    public static final Codec<MossData> CODEC = Codec.unit(MossData::new);
}
