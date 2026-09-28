package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.PlayerAbnormalityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned union of discovered abnormality information, independent of player inventories. */
public final class FacilityKnowledgeData extends SavedData {
    private final PlayerAbnormalityData knowledge = new PlayerAbnormalityData();

    public static FacilityKnowledgeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FacilityKnowledgeData::load, FacilityKnowledgeData::new, "lobotocraft_facility_knowledge");
    }

    public static FacilityKnowledgeData load(CompoundTag tag) {
        FacilityKnowledgeData data = new FacilityKnowledgeData();
        data.knowledge.mergeKnowledge(tag);
        return data;
    }

    public boolean merge(PlayerAbnormalityData personal) {
        if (!knowledge.mergeKnowledge(personal)) return false;
        setDirty();
        return true;
    }

    public boolean applyTo(PlayerAbnormalityData personal) {
        return personal.mergeKnowledge(knowledge);
    }

    public boolean unlock(String code, int index) {
        if (knowledge.isManualUnlocked(code, index)) return false;
        knowledge.unlockManual(code, index);
        setDirty();
        return true;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.merge(knowledge.knowledgeSnapshot());
        return tag;
    }
}
