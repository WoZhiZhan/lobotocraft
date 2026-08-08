package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Persistent geometry and ownership metadata for one facility space. */
public class FacilityAreaEntry {

    public static final int MAX_KEY_LENGTH = 256;
    public static final int MAX_DEPARTMENT_LENGTH = 64;
    public static final int MAX_GROUP_LENGTH = 128;

    public ResourceLocation dimension;
    public String key = "";
    public BlockPos markerPos = BlockPos.ZERO;
    public BlockPos minPos = BlockPos.ZERO;
    public BlockPos maxPos = BlockPos.ZERO;
    public String departmentId = "";
    public FacilityAreaType type = FacilityAreaType.OTHER;
    public String groupId = "";
    public int floorIndex = -1;

    public FacilityAreaEntry() {
    }

    public FacilityAreaEntry(ResourceLocation dimension, String key, BlockPos markerPos,
                             BlockPos minPos, BlockPos maxPos, String departmentId,
                             FacilityAreaType type, String groupId) {
        this.dimension = dimension;
        this.key = safe(key);
        this.markerPos = markerPos.immutable();
        setBounds(minPos, maxPos);
        this.departmentId = safe(departmentId);
        this.type = type == null ? FacilityAreaType.OTHER : type;
        this.groupId = safe(groupId);
    }

    public void setBounds(BlockPos first, BlockPos second) {
        this.minPos = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        this.maxPos = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public static String makeKey(FacilityAreaType type, ResourceLocation dimension, BlockPos markerPos) {
        FacilityAreaType safeType = type == null ? FacilityAreaType.OTHER : type;
        return safeType.serializedName() + ":" + dimension + "@" + markerPos.asLong();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dimension.toString());
        tag.putString("Key", key);
        tag.putLong("MarkerPos", markerPos.asLong());
        tag.putLong("MinPos", minPos.asLong());
        tag.putLong("MaxPos", maxPos.asLong());
        tag.putString("Department", departmentId);
        tag.putString("Type", type.serializedName());
        tag.putString("Group", groupId);
        tag.putInt("FloorIndex", floorIndex);
        return tag;
    }

    public static FacilityAreaEntry load(CompoundTag tag) {
        FacilityAreaEntry entry = new FacilityAreaEntry();
        entry.dimension = ResourceLocation.tryParse(tag.getString("Dim"));
        entry.markerPos = BlockPos.of(tag.getLong("MarkerPos"));
        entry.type = FacilityAreaType.fromSerializedName(tag.getString("Type"));
        entry.key = tag.getString("Key");
        if (entry.key.isEmpty() && entry.dimension != null) {
            entry.key = makeKey(entry.type, entry.dimension, entry.markerPos);
        }
        BlockPos min = tag.contains("MinPos") ? BlockPos.of(tag.getLong("MinPos")) : entry.markerPos;
        BlockPos max = tag.contains("MaxPos") ? BlockPos.of(tag.getLong("MaxPos")) : entry.markerPos;
        entry.setBounds(min, max);
        entry.departmentId = tag.getString("Department");
        entry.groupId = tag.getString("Group");
        entry.floorIndex = tag.contains("FloorIndex") ? tag.getInt("FloorIndex") : -1;
        return entry;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeUtf(key, MAX_KEY_LENGTH);
        buf.writeBlockPos(markerPos);
        buf.writeBlockPos(minPos);
        buf.writeBlockPos(maxPos);
        buf.writeUtf(departmentId, MAX_DEPARTMENT_LENGTH);
        type.write(buf);
        buf.writeUtf(groupId, MAX_GROUP_LENGTH);
        buf.writeVarInt(floorIndex + 1);
    }

    public static FacilityAreaEntry read(FriendlyByteBuf buf) {
        FacilityAreaEntry entry = new FacilityAreaEntry();
        entry.dimension = buf.readResourceLocation();
        entry.key = buf.readUtf(MAX_KEY_LENGTH);
        entry.markerPos = buf.readBlockPos();
        entry.setBounds(buf.readBlockPos(), buf.readBlockPos());
        entry.departmentId = buf.readUtf(MAX_DEPARTMENT_LENGTH);
        entry.type = FacilityAreaType.read(buf);
        entry.groupId = buf.readUtf(MAX_GROUP_LENGTH);
        entry.floorIndex = buf.readVarInt() - 1;
        return entry;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
