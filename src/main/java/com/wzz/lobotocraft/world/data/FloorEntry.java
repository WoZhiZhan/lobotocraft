package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 一个"楼层"，对应世界里的一个楼层标记方块。
 * <p>
 * 每层楼放一个就行，放在哪一格无所谓，只有 Y 有意义 —— 收容间会归属到"位于自己所在高度或下方、
 * 且最接近的那个楼层标记"。这样就不依赖固定层高了，你每层间隔多少都行。
 * <p>
 * 楼层名是存在这里（存档级）的，不是存在方块里的，所以在监控界面里右键改名，
 * 即使那一层的区块没加载也能改。
 */
public class FloorEntry {

    public ResourceLocation dimension;
    public BlockPos pos;
    public String name = "";

    public FloorEntry() {
    }

    public FloorEntry(ResourceLocation dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    public int y() {
        return pos.getY();
    }

    public String displayName() {
        return (name == null || name.isEmpty()) ? ("Y " + pos.getY()) : name;
    }

    public String key() {
        return makeKey(dimension, pos);
    }

    public static String makeKey(ResourceLocation dimension, BlockPos pos) {
        return dimension + "@" + pos.asLong();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dimension.toString());
        tag.putLong("Pos", pos.asLong());
        tag.putString("Name", name);
        return tag;
    }

    public static FloorEntry load(CompoundTag tag) {
        FloorEntry entry = new FloorEntry();
        entry.dimension = ResourceLocation.tryParse(tag.getString("Dim"));
        entry.pos = BlockPos.of(tag.getLong("Pos"));
        entry.name = tag.getString("Name");
        return entry;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(pos);
        buf.writeUtf(name, 48);
    }

    public static FloorEntry read(FriendlyByteBuf buf) {
        FloorEntry entry = new FloorEntry();
        entry.dimension = buf.readResourceLocation();
        entry.pos = buf.readBlockPos();
        entry.name = buf.readUtf(48);
        return entry;
    }
}
