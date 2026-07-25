package com.charselect.character;

import net.minecraft.nbt.CompoundTag;

/**
 * What a character remembers about one specific world: where it last stood, and any player
 * data the config keeps world-local instead of carrying between worlds.
 */
public final class WorldSlot {
    private CompoundTag data = new CompoundTag();
    private boolean hasPosition;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String dimension = "minecraft:overworld";
    private long lastPlayed;

    public CompoundTag data() {
        return data;
    }

    public void setData(CompoundTag data) {
        this.data = data;
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public String dimension() {
        return dimension;
    }

    public long lastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public void setPosition(double x, double y, double z, float yaw, float pitch, String dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dimension = dimension;
        this.hasPosition = true;
    }

    public void clearPosition() {
        this.hasPosition = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Data", data);
        tag.putBoolean("HasPosition", hasPosition);
        if (hasPosition) {
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
        }
        tag.putString("Dimension", dimension);
        tag.putLong("LastPlayed", lastPlayed);
        return tag;
    }

    public static WorldSlot load(CompoundTag tag) {
        WorldSlot slot = new WorldSlot();
        slot.data = tag.getCompound("Data");
        slot.hasPosition = tag.getBoolean("HasPosition");
        if (slot.hasPosition) {
            slot.x = tag.getDouble("X");
            slot.y = tag.getDouble("Y");
            slot.z = tag.getDouble("Z");
            slot.yaw = tag.getFloat("Yaw");
            slot.pitch = tag.getFloat("Pitch");
        }
        if (tag.contains("Dimension")) {
            slot.dimension = tag.getString("Dimension");
        }
        slot.lastPlayed = tag.getLong("LastPlayed");
        return slot;
    }
}
