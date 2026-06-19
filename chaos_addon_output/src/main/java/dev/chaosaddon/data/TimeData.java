package dev.chaosaddon.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

/**
 * Stores a "saved state" for the Phantom Archaeologist's Cache Memory power.
 * Saves inventory NBT, health, food level, and position.
 */
public class TimeData {
    private CompoundTag savedInventory;
    private float savedHealth;
    private int   savedFood;
    private double savedX, savedY, savedZ;
    private boolean hasSave;

    public TimeData() { hasSave = false; }

    public boolean hasSave()    { return hasSave; }
    public CompoundTag savedInventory() { return savedInventory; }
    public float savedHealth()  { return savedHealth; }
    public int   savedFood()    { return savedFood; }
    public double savedX()      { return savedX; }
    public double savedY()      { return savedY; }
    public double savedZ()      { return savedZ; }

    public void save(CompoundTag inv, float hp, int food, double x, double y, double z) {
        this.savedInventory = inv;
        this.savedHealth    = hp;
        this.savedFood      = food;
        this.savedX = x; this.savedY = y; this.savedZ = z;
        this.hasSave = true;
    }

    public void clear() { hasSave = false; }

    public static final Codec<TimeData> CODEC = Codec.unit(TimeData::new);
}
