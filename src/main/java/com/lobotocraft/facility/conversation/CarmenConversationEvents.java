package com.lobotocraft.facility.conversation;

import com.lobotocraft.facility.network.CarmenConversationPacket;
import com.lobotocraft.facility.network.FacilityNetwork;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CarmenConversationEvents {
    private static final List<String> ESCAPE_MESSAGES = List.of(
        "%s似乎觉得收容单元有点太小了？",
        "%s突破了收容单元",
        "%s已经出逃了"
    );
    private static final List<String> PANIC_MESSAGES = List.of(
        "%s似乎有点不太舒服？",
        "%s精神有点不太稳定",
        "%s精神状况似乎不太好"
    );
    private static final List<String> DEATH_MESSAGES = List.of(
        "%s已经安详地闭上了双眼",
        "%s逝去了，又消失一名员工"
    );

    private CarmenConversationEvents() {
    }

    @SubscribeEvent
    public static void onAbnormalityEscape(AbnormalityEscapeEvent event) {
        if (!(event.getAbnormality().level() instanceof ServerLevel level)) return;
        BlockPos position = event.getEscapePosition();
        if (position == null) position = event.getAbnormality().blockPosition();
        broadcast(level, choose(level, ESCAPE_MESSAGES, event.getAbnormalityName()), position);
    }

    @SubscribeEvent
    public static void onMentalDepleted(MentalValueEvent.Depleted event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || player.level().dimension() != ModDimensions.LOBOTO_KEY) return;
        broadcast(player.serverLevel(), choose(player.serverLevel(), PANIC_MESSAGES,
            employeeName(player)), player.blockPosition());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || player.level().dimension() != ModDimensions.LOBOTO_KEY) return;
        broadcast(player.serverLevel(), choose(player.serverLevel(), DEATH_MESSAGES,
            employeeName(player)), player.blockPosition());
    }

    private static String choose(ServerLevel level, List<String> templates, String subject) {
        RandomSource random = level.getRandom();
        return String.format(templates.get(random.nextInt(templates.size())), subject);
    }

    private static String employeeName(ServerPlayer player) {
        return player instanceof EmployeeServerPlayer employee
            ? employee.employeeRecord().name()
            : player.getDisplayName().getString();
    }

    private static void broadcast(ServerLevel level, String message, BlockPos position) {
        String withPosition = message + "\n(" + position.getX() + ","
            + position.getY() + "," + position.getZ() + ")";
        CarmenConversationPacket packet = new CarmenConversationPacket(withPosition);
        for (ServerPlayer player : level.players()) FacilityNetwork.send(player, packet);
    }
}
