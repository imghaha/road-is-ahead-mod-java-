package com.watire.longroad.sthcheck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class BiomeCheckData extends SavedData {
    private static final String DATA_NAME = "longroad_biome_check";
    private boolean hasShownWarning = false;

    public static BiomeCheckData load(CompoundTag tag) {
        BiomeCheckData data = new BiomeCheckData();
        data.hasShownWarning = tag.getBoolean("HasShownWarning");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("HasShownWarning", hasShownWarning);
        return tag;
    }

    public boolean hasShownWarning() {
        return hasShownWarning;
    }

    public void setShownWarning(boolean shown) {
        this.hasShownWarning = shown;
        this.setDirty();
    }

    public static BiomeCheckData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(BiomeCheckData::load, BiomeCheckData::new, DATA_NAME);
        }
        return null;
    }
}