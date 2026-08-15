package com.lobotocraft.facility.conversation;

import com.lobotocraft.facility.network.CarmenConversationPacket;
import com.lobotocraft.facility.network.FacilityNetwork;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CarmenConversationEvents {
    private static final List<String> ESCAPE_MESSAGES = List.of(
        "message.lobotocraft.carmen.escape.0",
        "message.lobotocraft.carmen.escape.1",
        "message.lobotocraft.carmen.escape.2"
    );
    private static final List<String> PANIC_MESSAGES = List.of(
        "message.lobotocraft.carmen.panic.0",
        "message.lobotocraft.carmen.panic.1",
        "message.lobotocraft.carmen.panic.2"
    );
    private static final List<String> DEATH_MESSAGES = List.of(
        "message.lobotocraft.carmen.death.0",
        "message.lobotocraft.carmen.death.1"
    );

    private CarmenConversationEvents() {
    }

    @SubscribeEvent
    public static void onAbnormalityEscape(AbnormalityEscapeEvent event) {
        if (!(event.getAbnormality().level() instanceof ServerLevel level)) return;
        BlockPos position = event.getEscapePosition();
        if (position == null) position = event.getAbnormality().blockPosition();
        broadcast(level, choose(level, ESCAPE_MESSAGES,
            event.getAbnormality().getLocalizedAbnormalityName()), position);
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

    private static Component choose(ServerLevel level, List<String> templates, Component subject) {
        RandomSource random = level.getRandom();
        return Component.translatable(templates.get(random.nextInt(templates.size())), subject);
    }

    private static Component employeeName(ServerPlayer player) {
        return player instanceof EmployeeServerPlayer employee
            ? Component.literal(employee.employeeRecord().name())
            : player.getDisplayName();
    }

    private static void broadcast(ServerLevel level, Component message, BlockPos position) {
        Component withPosition = message.copy().append(Component.translatable(
            "message.lobotocraft.carmen.position", position.getX(), position.getY(), position.getZ()));
        CarmenConversationPacket packet = new CarmenConversationPacket(withPosition);
        for (ServerPlayer player : level.players()) FacilityNetwork.send(player, packet);
    }
}
