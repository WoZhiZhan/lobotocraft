package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.world.RoomChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * 客户端 -> 服务端：在监控界面里按空格，对准的那个房间直接开始工作。
 *
 * <h3>为什么会先把玩家传送过去</h3>
 * {@code WorkManager} 有两道硬性距离限制：{@code startWork} 里 {@code distanceToSqr > 100} 直接拒绝，
 * 而且服务端 tick 循环每 tick 都会重查一遍，超过 10 格就 {@code cancelWork("你离异想体太远了")}。
 * 所以"人在别处、隔空对远处异想体工作"在现有工作系统下是不成立的 —— 这里的做法是
 * <b>先把玩家传送到那个房间，再打开原本的工作选择界面</b>，对玩家来说依然是"按一下空格就开工"。
 * <p>
 * 除了距离，这里把 {@code AbnormalityWorkHandler#onEntityInteract} 里的检查全部照搬了一遍：
 * 工具类异想体、正在工作中、精神恐慌 / 疯狂、{@code onOpenWorkScreen} 回调、
 * 以及该异想体是否已被别人占用。
 */
public class StartRoomWorkPacket implements IMessage {

    private ResourceLocation dimension;
    private BlockPos roomPos;

    public StartRoomWorkPacket() {
    }

    public StartRoomWorkPacket(ResourceLocation dimension, BlockPos roomPos) {
        this.dimension = dimension;
        this.roomPos = roomPos;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.roomPos = buf.readBlockPos();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(roomPos);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        // This historical remote-work packet is deliberately inert. Legitimate work
        // starts at the abnormality interaction flow and is validated there.
        if (ctx != null) return;
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.dimension_unloaded"), true);
            return;
        }
        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                player.server, dimension, roomPos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            player.displayClientMessage(Component.translatable(access
                    == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "message.lobotocraft.room.unexpanded_work"
                    : "message.lobotocraft.room.unavailable"), true);
            return;
        }

        RoomChunkTickets.keepAlive(target, roomPos);
        target.getChunk(roomPos.getX() >> 4, roomPos.getZ() >> 4);

        if (!(target.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity be)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.marker_invalid"), true);
            return;
        }

        UUID bound = be.getBoundAbnormality();
        Entity entity = bound == null ? null : target.getEntity(bound);
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.no_work_target"), true);
            return;
        }

        // 1. 工具类不走工作流程
        if (abnormality.isToolType()) {
            player.displayClientMessage(
                    Component.translatable("message.lobotocraft.room.tool_requires_proximity"), true);
            return;
        }

        // 2. 一次只能进行一项工作（WorkManager 的会话是按玩家 UUID 存的）
        if (WorkManager.isPlayerWorking(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.work.already_working"), true);
            return;
        }
        if (com.wzz.lobotocraft.work.AbnormalityWorkRules.isAiEmployee(player)
                && WorkManager.isAiEmployeeWorking(abnormality)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.already_worked"), true);
            return;
        }

        // 3. 异想体自己的开屏回调（有些异想体会拒绝）
        if (!abnormality.onOpenWorkScreen(player)) {
            return;
        }

        // 4. 精神状态
        final boolean[] mentalBroken = {false};
        final boolean[] berserk = {false};
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            if (MentalValueUtil.isPanic(mental)) {
                mentalBroken[0] = true;
                berserk[0] = mental.getPanicType() == PanicType.BERSERK;
            }
        });
        if (mentalBroken[0]) {
            player.displayClientMessage(Component.translatable(berserk[0]
                    ? "message.lobotocraft.work.berserk"
                    : "message.lobotocraft.work.panic"), true);
            return;
        }

        // 5. 距离不够就先送过去（WorkManager 全程要求 10 格以内）
        if (player.level() != target || player.distanceToSqr(abnormality) > 81.0D) {
            BlockPos landing = findLanding(target, roomPos);
            player.teleportTo(target,
                    landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
        }

        // 6. 打开原本的工作选择界面
        String code = abnormality.getAbnormalityCode();
        player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data ->
                player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                    int obsLevel = data.getObservationLevel(code);
                    boolean[] workPrefsUnlocked = new boolean[4];
                    for (int i = 0; i < 4; i++) {
                        workPrefsUnlocked[i] = data.isManualUnlocked(code, 100 + i);
                    }
                    MessageLoader.getLoader().sendToPlayer(player,
                            new OpenWorkScreenPacket(
                                    abnormality.getEntityId(),
                                    abnormality.getFullWorkPreferences(),
                                    abnormality.getRiskLevel(),
                                    obsLevel,
                                     stats.getEmployeeLevel(),
                                    workPrefsUnlocked,
                                    FacilityManager.repeatedWorkPenaltyPercent(
                                            player.serverLevel(), abnormality)
                            ));
                }));
    }

    /** 和传送包用的是同一套落点逻辑 */
    private static BlockPos findLanding(ServerLevel level, BlockPos marker) {
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos feet = marker.above(dy - 1);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return feet;
            }
        }
        return marker.above();
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
