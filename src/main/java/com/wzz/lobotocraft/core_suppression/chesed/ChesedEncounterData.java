package com.wzz.lobotocraft.core_suppression.chesed;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.IntUnaryOperator;

public final class ChesedEncounterData extends SavedData {
    private static final String NAME = "lobotocraft_chesed_encounter";

    private boolean active;
    private int selectedDamageMask;
    private int lastHandledOverloadLevel;

    public static ChesedEncounterData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                ChesedEncounterData::load, ChesedEncounterData::new, NAME);
    }

    public void start(IntUnaryOperator nextInt) {
        active = true;
        lastHandledOverloadLevel = 0;
        selectedDamageMask = ChesedDamagePolicy.reroll(1, 0, nextInt);
        setDirty();
    }

    public boolean handleOverload(int overloadLevel, IntUnaryOperator nextInt) {
        if (!active || overloadLevel <= lastHandledOverloadLevel) return false;
        selectedDamageMask = ChesedDamagePolicy.reroll(
                overloadLevel, selectedDamageMask, nextInt);
        lastHandledOverloadLevel = overloadLevel;
        setDirty();
        return true;
    }

    public void clear() {
        if (!active && selectedDamageMask == 0 && lastHandledOverloadLevel == 0) return;
        active = false;
        selectedDamageMask = 0;
        lastHandledOverloadLevel = 0;
        setDirty();
    }

    public boolean isActive() {
        return active;
    }

    public int getSelectedDamageMask() {
        return selectedDamageMask;
    }

    public int getLastHandledOverloadLevel() {
        return lastHandledOverloadLevel;
    }

    public int getVisualStage() {
        return ChesedDamagePolicy.visualStage(selectedDamageMask);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Active", active);
        tag.putInt("SelectedDamageMask", selectedDamageMask);
        tag.putInt("LastHandledOverloadLevel", lastHandledOverloadLevel);
        return tag;
    }

    public static ChesedEncounterData load(CompoundTag tag) {
        ChesedEncounterData data = new ChesedEncounterData();
        data.active = tag.getBoolean("Active");
        data.selectedDamageMask = tag.getInt("SelectedDamageMask")
                & ChesedDamagePolicy.ALL_DAMAGE_MASK;
        data.lastHandledOverloadLevel = Math.max(0, tag.getInt("LastHandledOverloadLevel"));
        if (!data.active) {
            data.selectedDamageMask = 0;
            data.lastHandledOverloadLevel = 0;
        }
        return data;
    }
}
