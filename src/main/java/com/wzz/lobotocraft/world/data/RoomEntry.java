package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 一个"收容间"的数据快照。
 * <p>
 * 由 {@link com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity} 每秒扫描一次房间范围后写入
 * {@link CompanyRoomData}（存档级持久化），因此即使房间所在区块已卸载，监控终端依然能显示上一次的状态，
 * 只是会带上"数据延迟 Xs"的提示。
 * <p>
 * 楼层相关的三个字段（floorIndex / floorName / floorY）不是信标自己填的，
 * 而是发包前由 {@link com.wzz.lobotocraft.network.packet.OpenRoomMapPacket} 按楼层标记方块现算的。
 */
public class RoomEntry {

    /** 房间内没有找到任何异想体 */
    public static final int STATUS_EMPTY = 0;
    /** 异想体正常收容中 */
    public static final int STATUS_OCCUPIED = 1;
    /** The bound abnormality is alive and its authoritative hasEscape flag is true. */
    public static final int STATUS_ESCAPED = 2;
    /**
     * 登记过的异想体暂时找不到了 —— 被捕获装进收容单元、死亡等待复活、或者跑进了没加载的区块。
     * 和"出逃"区分开，不然玩家一回收异想体，监控上就一片红色警报。
     */
    public static final int STATUS_LOST = 3;
    /** The department exists, but this containment slot has not been expanded yet. */
    public static final int STATUS_UNEXPANDED = 4;

    /** 房间所在维度 */
    public ResourceLocation dimension;
    /** 标记方块坐标（同时也是传送目标基准点） */
    public BlockPos pos;
    /** 所属部门；空串表示尚未分配。 */
    public String departmentId = "";

    /** 异想体代码，例如 O-03-03。空串表示房间里没有异想体 */
    public String abnormalityCode = "";
    /** 异想体名称 */
    public String abnormalityName = "";
    /** 风险等级名（ZAYIN / TETH / HE / WAW / ALEPH …） */
    public String riskName = "";
    /** 风险等级颜色，直接取自 RiskLevel#getColor */
    public int riskColor = 0xFFAAAAAA;
    /** Snapshot-only classification used by monitor dispatch UI. */
    public boolean toolType;

    public int status = STATUS_EMPTY;

    public int qliphoth = -1;
    public int maxQliphoth = -1;

    /** 异想体血量百分比 0~1 */
    public float healthPercent = 1.0F;
    /** 房间内玩家（员工）数量 */
    public int playersInside = 0;
    /** 出逃时，异想体离信标的直线距离（格）。没出逃就是 0 */
    public int escapeDistance = 0;

    /** Core-suppression Qliphoth meltdown state for this containment unit. */
    public boolean meltdownActive = false;
    /** Remaining server ticks; clients render this as a rounded-up second count. */
    public int meltdownRemainingTicks = 0;
    /** Repeated-work penalty for this containment unit, in percentage points. */
    public int repeatedWorkPenaltyPercent = 0;

    /** 最后一次扫描时的 level.getGameTime() */
    public long lastUpdate = 0L;

    // ---- 以下三个由发包时现算，不代表信标自身的持久化状态 ----
    /** 所属楼层在包内 floors 列表中的下标，-1 = 没有匹配到楼层标记方块 */
    public int floorIndex = -1;
    public String floorName = "";
    public int floorY = 0;

    public RoomEntry() {
    }

    public RoomEntry(ResourceLocation dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    public String key() {
        return makeKey(dimension, pos);
    }

    public static String makeKey(ResourceLocation dimension, BlockPos pos) {
        return dimension + "@" + pos.asLong();
    }

    /** 界面上显示的房间标题 */
    public String resolveTitle() {
        if (status == STATUS_UNEXPANDED) return "未扩建";
        if (abnormalityName != null && !abnormalityName.isEmpty()) return abnormalityName;
        return "空置收容间";
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dimension.toString());
        tag.putLong("Pos", pos.asLong());
        tag.putString("Department", departmentId);
        tag.putString("Code", abnormalityCode);
        tag.putString("AbName", abnormalityName);
        tag.putString("Risk", riskName);
        tag.putInt("RiskColor", riskColor);
        tag.putBoolean("ToolType", toolType);
        tag.putInt("Status", status);
        tag.putInt("Qliphoth", qliphoth);
        tag.putInt("MaxQliphoth", maxQliphoth);
        tag.putFloat("Health", healthPercent);
        tag.putInt("Players", playersInside);
        tag.putInt("EscapeDist", escapeDistance);
        tag.putBoolean("MeltdownActive", meltdownActive);
        tag.putInt("MeltdownRemainingTicks", meltdownRemainingTicks);
        tag.putInt("RepeatedWorkPenalty", repeatedWorkPenaltyPercent);
        tag.putLong("LastUpdate", lastUpdate);
        return tag;
    }

    public static RoomEntry load(CompoundTag tag) {
        RoomEntry entry = new RoomEntry();
        entry.dimension = ResourceLocation.tryParse(tag.getString("Dim"));
        entry.pos = BlockPos.of(tag.getLong("Pos"));
        entry.departmentId = tag.getString("Department");
        entry.abnormalityCode = tag.getString("Code");
        entry.abnormalityName = tag.getString("AbName");
        entry.riskName = tag.getString("Risk");
        entry.riskColor = tag.getInt("RiskColor");
        entry.toolType = tag.getBoolean("ToolType");
        entry.status = tag.getInt("Status");
        entry.qliphoth = tag.getInt("Qliphoth");
        entry.maxQliphoth = tag.getInt("MaxQliphoth");
        entry.healthPercent = tag.getFloat("Health");
        entry.playersInside = tag.getInt("Players");
        entry.escapeDistance = tag.getInt("EscapeDist");
        entry.meltdownActive = tag.getBoolean("MeltdownActive");
        entry.meltdownRemainingTicks = Math.max(0, tag.getInt("MeltdownRemainingTicks"));
        entry.repeatedWorkPenaltyPercent = Math.max(0, tag.getInt("RepeatedWorkPenalty"));
        entry.lastUpdate = tag.getLong("LastUpdate");
        return entry;
    }

    // --------------------------------------------------------------- 网络

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(pos);
        buf.writeUtf(departmentId, FacilityAreaEntry.MAX_DEPARTMENT_LENGTH);
        buf.writeUtf(abnormalityCode, 32);
        buf.writeUtf(abnormalityName, 64);
        buf.writeUtf(riskName, 32);
        buf.writeInt(riskColor);
        buf.writeBoolean(toolType);
        buf.writeByte(status);
        buf.writeVarInt(qliphoth);
        buf.writeVarInt(maxQliphoth);
        buf.writeFloat(healthPercent);
        buf.writeVarInt(playersInside);
        buf.writeVarInt(escapeDistance);
        buf.writeBoolean(meltdownActive);
        buf.writeVarInt(Math.max(0, meltdownRemainingTicks));
        buf.writeVarInt(Math.max(0, repeatedWorkPenaltyPercent));
        buf.writeLong(lastUpdate);
        buf.writeVarInt(floorIndex + 1);
        buf.writeUtf(floorName, 48);
        buf.writeInt(floorY);
    }

    public static RoomEntry read(FriendlyByteBuf buf) {
        RoomEntry entry = new RoomEntry();
        entry.dimension = buf.readResourceLocation();
        entry.pos = buf.readBlockPos();
        entry.departmentId = buf.readUtf(FacilityAreaEntry.MAX_DEPARTMENT_LENGTH);
        entry.abnormalityCode = buf.readUtf(32);
        entry.abnormalityName = buf.readUtf(64);
        entry.riskName = buf.readUtf(32);
        entry.riskColor = buf.readInt();
        entry.toolType = buf.readBoolean();
        entry.status = buf.readByte();
        entry.qliphoth = buf.readVarInt();
        entry.maxQliphoth = buf.readVarInt();
        entry.healthPercent = buf.readFloat();
        entry.playersInside = buf.readVarInt();
        entry.escapeDistance = buf.readVarInt();
        entry.meltdownActive = buf.readBoolean();
        entry.meltdownRemainingTicks = Math.max(0, buf.readVarInt());
        entry.repeatedWorkPenaltyPercent = Math.max(0, buf.readVarInt());
        entry.lastUpdate = buf.readLong();
        entry.floorIndex = buf.readVarInt() - 1;
        entry.floorName = buf.readUtf(48);
        entry.floorY = buf.readInt();
        return entry;
    }
}
