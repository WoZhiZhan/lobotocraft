package com.wzz.lobotocraft.world.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * 「更改房间住户」选单里的一个候选异想体。纯网络传输用，不落盘。
 */
public class AbnormalityCandidate {

    public UUID uuid;
    public String code = "";
    public String name = "";
    public int riskColor = 0xFFAAAAAA;
    public String riskName = "";
    /** 离信标的直线距离（格） */
    public int distance;
    /** 是否就在这个房间的扫描范围内 */
    public boolean inRoom;
    /** 是否已经是这个房间登记的住户 */
    public boolean alreadyBound;

    public AbnormalityCandidate() {
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUtf(code, 32);
        buf.writeUtf(name, 64);
        buf.writeInt(riskColor);
        buf.writeUtf(riskName, 32);
        buf.writeVarInt(distance);
        buf.writeBoolean(inRoom);
        buf.writeBoolean(alreadyBound);
    }

    public static AbnormalityCandidate read(FriendlyByteBuf buf) {
        AbnormalityCandidate candidate = new AbnormalityCandidate();
        candidate.uuid = buf.readUUID();
        candidate.code = buf.readUtf(32);
        candidate.name = buf.readUtf(64);
        candidate.riskColor = buf.readInt();
        candidate.riskName = buf.readUtf(32);
        candidate.distance = buf.readVarInt();
        candidate.inRoom = buf.readBoolean();
        candidate.alreadyBound = buf.readBoolean();
        return candidate;
    }
}
