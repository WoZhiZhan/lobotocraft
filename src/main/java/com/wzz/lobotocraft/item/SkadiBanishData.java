package com.wzz.lobotocraft.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 记录浊心斯卡蒂是否已被特殊唱片镇压(永久消失、不再自然生成)。
 * 存储于主世界维度的持久数据中。
 */
public class SkadiBanishData extends SavedData {

    public static final String ID = "skadi_banish";

    private boolean banished = false;
    // 斯卡蒂变为伊莎玛拉前的原始位置(用于镇压伊莎玛拉后让斯卡蒂归位)
    private boolean hasOrigin = false;
    private double originX, originY, originZ;

    public static SkadiBanishData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                SkadiBanishData::load,
                SkadiBanishData::new,
                ID
        );
    }

    public boolean isBanished() {
        return banished;
    }

    public void setBanished(boolean v) {
        this.banished = v;
        this.setDirty();
    }

    /** 记录斯卡蒂原位置(在它变为伊莎玛拉前调用) */
    public void setSkadiOrigin(double x, double y, double z) {
        this.hasOrigin = true;
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.setDirty();
    }

    public boolean hasSkadiOrigin() { return hasOrigin; }
    public double getOriginX() { return originX; }
    public double getOriginY() { return originY; }
    public double getOriginZ() { return originZ; }

    /** 清除原位置记录(斯卡蒂已归位后调用) */
    public void clearSkadiOrigin() {
        this.hasOrigin = false;
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Banished", banished);
        tag.putBoolean("HasOrigin", hasOrigin);
        tag.putDouble("OriginX", originX);
        tag.putDouble("OriginY", originY);
        tag.putDouble("OriginZ", originZ);
        return tag;
    }

    public static SkadiBanishData load(CompoundTag tag) {
        SkadiBanishData data = new SkadiBanishData();
        data.banished = tag.getBoolean("Banished");
        data.hasOrigin = tag.getBoolean("HasOrigin");
        data.originX = tag.getDouble("OriginX");
        data.originY = tag.getDouble("OriginY");
        data.originZ = tag.getDouble("OriginZ");
        return data;
    }
}
