package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent server-wide safety switch used during public demonstrations. */
public final class ExhibitionModeData extends SavedData {
    private static final String NAME = "lobotocraft_exhibition_mode";

    private boolean enabled;
    private long recoverySerial;

    public static ExhibitionModeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ExhibitionModeData::load, ExhibitionModeData::new, NAME);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        setDirty();
    }

    public long getRecoverySerial() {
        return recoverySerial;
    }

    public void recordRecovery() {
        recoverySerial++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Enabled", enabled);
        tag.putLong("RecoverySerial", recoverySerial);
        return tag;
    }

    public static ExhibitionModeData load(CompoundTag tag) {
        ExhibitionModeData data = new ExhibitionModeData();
        data.enabled = tag.getBoolean("Enabled");
        data.recoverySerial = Math.max(0L, tag.getLong("RecoverySerial"));
        return data;
    }
}
