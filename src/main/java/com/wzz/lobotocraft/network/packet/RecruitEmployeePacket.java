package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.employee.RecruitmentService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class RecruitEmployeePacket implements IMessage {
    private String name;
    private int fortitude;
    private int prudence;
    private int temperance;
    private int justice;
    private String persona;

    public RecruitEmployeePacket() {
    }

    public RecruitEmployeePacket(String name, int fortitude, int prudence, int temperance, int justice) {
        this(name, fortitude, prudence, temperance, justice, "");
    }

    public RecruitEmployeePacket(String name, int fortitude, int prudence, int temperance, int justice,
                                 String persona) {
        this.name = name;
        this.fortitude = fortitude;
        this.prudence = prudence;
        this.temperance = temperance;
        this.justice = justice;
        this.persona = persona;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        name = buffer.readUtf(48);
        fortitude = buffer.readVarInt();
        prudence = buffer.readVarInt();
        temperance = buffer.readVarInt();
        justice = buffer.readVarInt();
        persona = buffer.readUtf(RecruitmentService.MAX_PERSONA_UTF16_LENGTH);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(name == null ? "" : name, 48);
        buffer.writeVarInt(fortitude);
        buffer.writeVarInt(prudence);
        buffer.writeVarInt(temperance);
        buffer.writeVarInt(justice);
        buffer.writeUtf(persona == null ? "" : persona,
                RecruitmentService.MAX_PERSONA_UTF16_LENGTH);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null) return;
        RecruitmentService.Result result = RecruitmentService.recruit(sender, name,
                fortitude, prudence, temperance, justice, persona);
        if (!result.success()) {
            sender.sendSystemMessage(Component.literal("§c" + result.message()));
            FacilityManagementService.open(sender);
            return;
        }
        sender.sendSystemMessage(Component.literal("§a已招募员工 " + result.employee().name()
                + "，当前部门：未分配。"));
        FacilityManagementService.open(sender);
    }
}
