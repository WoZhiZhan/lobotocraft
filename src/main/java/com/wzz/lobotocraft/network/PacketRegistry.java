package com.wzz.lobotocraft.network;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.wzz.lobotocraft.network.packet.AssignAiEgoPacket;
import com.wzz.lobotocraft.network.packet.AcknowledgeWelcomeNoticePacket;
import com.wzz.lobotocraft.network.packet.AssignEmployeeDepartmentPacket;
import com.wzz.lobotocraft.network.packet.AssignEmployeesMoveAreaPacket;
import com.wzz.lobotocraft.network.packet.AssignRoomEmployeeWorkPacket;
import com.wzz.lobotocraft.network.packet.ConfirmDepartmentExpansionPacket;
import com.wzz.lobotocraft.network.packet.BindRoomAbnormalityPacket;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.network.packet.CoreSuppressionSyncPacket;
import com.wzz.lobotocraft.network.packet.CoreSuppressionVisualChoicePacket;
import com.wzz.lobotocraft.network.packet.CoreSuppressionWarningPacket;
import com.wzz.lobotocraft.network.packet.DamageBorderPacket;
import com.wzz.lobotocraft.network.packet.DevelopEquipmentPacket;
import com.wzz.lobotocraft.network.packet.DisplayItemActivationPacket;
import com.wzz.lobotocraft.network.packet.ElevatorTeleportPacket;
import com.wzz.lobotocraft.network.packet.ElevatorTravelPacket;
import com.wzz.lobotocraft.network.packet.EmployeeStatsSyncPacket;
import com.wzz.lobotocraft.network.packet.EquipPersonalEgoPacket;
import com.wzz.lobotocraft.network.packet.EscapeAlertPacket;
import com.wzz.lobotocraft.network.packet.ExplosionPacket;
import com.wzz.lobotocraft.network.packet.FallBackCameraPacket;
import com.wzz.lobotocraft.network.packet.FearFeedbackPacket;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.network.packet.GeburahCameraShakePacket;
import com.wzz.lobotocraft.network.packet.GeburahStatusSyncPacket;
import com.wzz.lobotocraft.network.packet.GeburahVisualEffectPacket;
import com.wzz.lobotocraft.network.packet.LambdaPacket;
import com.wzz.lobotocraft.network.packet.LargeBirdBorderPacket;
import com.wzz.lobotocraft.network.packet.LockInputPacket;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import com.wzz.lobotocraft.network.packet.NearbyAbnormalitiesPacket;
import com.wzz.lobotocraft.network.packet.ObtainGiftPacket;
import com.wzz.lobotocraft.network.packet.OpenChatScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenCoreSuppressionScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenDepartmentAccessPacket;
import com.wzz.lobotocraft.network.packet.OpenDepartmentExpansionPacket;
import com.wzz.lobotocraft.network.packet.OpenElevatorScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenEmployeeInventoryPacket;
import com.wzz.lobotocraft.network.packet.OpenEmployeeWarehousePacket;
import com.wzz.lobotocraft.network.packet.RequestRoomSuppressionCandidatesPacket;
import com.wzz.lobotocraft.network.packet.RoomSuppressionCandidatesPacket;
import com.wzz.lobotocraft.network.packet.AssignRoomEmployeeSuppressionPacket;
import com.wzz.lobotocraft.network.packet.RoomSuppressionAssignmentResultPacket;
import com.wzz.lobotocraft.network.packet.OpenFacilityManagementPacket;
import com.wzz.lobotocraft.network.packet.OpenLlmConfigPacket;
import com.wzz.lobotocraft.network.packet.OpenManualScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenPlayerProfilePacket;
import com.wzz.lobotocraft.network.packet.OpenRoomAbnormalityInfoPacket;
import com.wzz.lobotocraft.network.packet.OpenRoomMapPacket;
import com.wzz.lobotocraft.network.packet.OpenToolUseScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenWelcomeNoticePacket;
import com.wzz.lobotocraft.network.packet.OpenWorkProgressPacket;
import com.wzz.lobotocraft.network.packet.OpenWorkScreenPacket;
import com.wzz.lobotocraft.network.packet.OrdealTitlePacket;
import com.wzz.lobotocraft.network.packet.RecruitEmployeePacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.network.packet.RequestDepartmentAccessPacket;
import com.wzz.lobotocraft.network.packet.RequestLlmConfigPacket;
import com.wzz.lobotocraft.network.packet.RequestNearbyAbnormalitiesPacket;
import com.wzz.lobotocraft.network.packet.RequestPlayerProfilePacket;
import com.wzz.lobotocraft.network.packet.RequestRoomDataPacket;
import com.wzz.lobotocraft.network.packet.RequestRoomWorkCandidatesPacket;
import com.wzz.lobotocraft.network.packet.RoomWorkAssignmentResultPacket;
import com.wzz.lobotocraft.network.packet.RoomWorkCandidatesPacket;
import com.wzz.lobotocraft.network.packet.RoomTeleportRequestPacket;
import com.wzz.lobotocraft.network.packet.ScreenDistortionEffectPacket;
import com.wzz.lobotocraft.network.packet.SetElevatorPacket;
import com.wzz.lobotocraft.network.packet.SetEmployeeAppearancePacket;
import com.wzz.lobotocraft.network.packet.SetFloorNamePacket;
import com.wzz.lobotocraft.network.packet.SetPlayerDepartmentPacket;
import com.wzz.lobotocraft.network.packet.SetSpecialEgoPacket;
import com.wzz.lobotocraft.network.packet.ShockwaveEffectPacket;
import com.wzz.lobotocraft.network.packet.StartCoreSuppressionPacket;
import com.wzz.lobotocraft.network.packet.StartUsingToolPacket;
import com.wzz.lobotocraft.network.packet.StartUsingToolResultPacket;
import com.wzz.lobotocraft.network.packet.StartWorkPacket;
import com.wzz.lobotocraft.network.packet.StartRoomWorkPacket;
import com.wzz.lobotocraft.network.packet.StopAllSoundPacket;
import com.wzz.lobotocraft.network.packet.StopAmbientSoundPacket;
import com.wzz.lobotocraft.network.packet.StopContinuousWorkPacket;
import com.wzz.lobotocraft.network.packet.StopUsingToolPacket;
import com.wzz.lobotocraft.network.packet.StrangeBadgeEffectPacket;
import com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket;
import com.wzz.lobotocraft.network.packet.ToggleJudgementPacket;
import com.wzz.lobotocraft.network.packet.ToggleFacilityPausePacket;
import com.wzz.lobotocraft.network.packet.ToggleGlobalTT2Packet;
import com.wzz.lobotocraft.network.packet.ToolUsageUpdatePacket;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.network.packet.UpdateDepartmentAccessPacket;
import com.wzz.lobotocraft.network.packet.UnlockManualPacket;
import com.wzz.lobotocraft.network.packet.UpdateLlmConfigPacket;
import com.wzz.lobotocraft.network.packet.WorkCompletePacket;
import com.wzz.lobotocraft.network.packet.WorkExtractionPacket;
import com.wzz.lobotocraft.network.packet.PlayerDepartmentSyncPacket;
import com.wzz.lobotocraft.network.packet.MustOpenPathMeterSyncPacket;
import com.wzz.lobotocraft.debug.prototype.queenlaser.QueenLaserPrototypePacket;
import com.wzz.lobotocraft.debug.prototype.queenstar.QueenStarPrototypePacket;

/**
 * 网络包白名单注册表。
 * <p>
 * wire 上只传整数 id（在 {@link #PACKETS} 中的下标），取代原先的类全限定名字符串。
 * 解码只能实例化本表登记过的类，杜绝 {@code Class.forName(攻击者可控字符串)} 任意类加载。
 * <p>
 * 顺序即 id，禁止插入/删除中间元素破坏既有编号（追加到末尾是安全的）。
 * 新包只允许追加到列表尾部，保持既有 wire id 稳定。
 */
public final class PacketRegistry {
    public enum Direction {
        CLIENTBOUND,
        SERVERBOUND,
        BIDIRECTIONAL
    }

    private PacketRegistry() {
    }

    /** 顺序即 id。新增包只能追加到末尾。 */
    private static final List<Class<? extends IMessage>> PACKETS = List.of(
            AssignAiEgoPacket.class,
            AssignEmployeeDepartmentPacket.class,
            CompanyDailySyncPacket.class,
            CoreSuppressionSyncPacket.class,
            DamageBorderPacket.class,
            DevelopEquipmentPacket.class,
            DisplayItemActivationPacket.class,
            ElevatorTeleportPacket.class,
            EmployeeStatsSyncPacket.class,
            EquipPersonalEgoPacket.class,
            EscapeAlertPacket.class,
            ExplosionPacket.class,
            FallBackCameraPacket.class,
            FullScreenRenderMessage.class,
            LambdaPacket.class,
            LargeBirdBorderPacket.class,
            LockInputPacket.class,
            MentalValueSyncPacket.class,
            ObtainGiftPacket.class,
            OpenChatScreenPacket.class,
            OpenCoreSuppressionScreenPacket.class,
            OpenElevatorScreenPacket.class,
            OpenFacilityManagementPacket.class,
            OpenLlmConfigPacket.class,
            OpenManualScreenPacket.class,
            OpenToolUseScreenPacket.class,
            OpenWorkProgressPacket.class,
            OpenWorkScreenPacket.class,
            OrdealTitlePacket.class,
            RecruitEmployeePacket.class,
            RequestFacilityManagementPacket.class,
            RequestLlmConfigPacket.class,
            ScreenDistortionEffectPacket.class,
            SetElevatorPacket.class,
            ShockwaveEffectPacket.class,
            StartCoreSuppressionPacket.class,
            StartUsingToolPacket.class,
            StartUsingToolResultPacket.class,
            StartWorkPacket.class,
            StopAllSoundPacket.class,
            StopAmbientSoundPacket.class,
            StopContinuousWorkPacket.class,
            StopUsingToolPacket.class,
            StrangeBadgeEffectPacket.class,
            SuppressorCountSyncPacket.class,
            ToggleJudgementPacket.class,
            ToolUsageUpdatePacket.class,
            TriggerShakePacket.class,
            UnlockManualPacket.class,
            UpdateLlmConfigPacket.class,
            WorkCompletePacket.class,
            WorkExtractionPacket.class,
            OpenEmployeeInventoryPacket.class,
            OpenDepartmentAccessPacket.class,
            RequestDepartmentAccessPacket.class,
            UpdateDepartmentAccessPacket.class,
            BindRoomAbnormalityPacket.class,
            NearbyAbnormalitiesPacket.class,
            OpenRoomAbnormalityInfoPacket.class,
            OpenRoomMapPacket.class,
            RequestNearbyAbnormalitiesPacket.class,
            RequestRoomDataPacket.class,
            RoomTeleportRequestPacket.class,
            SetFloorNamePacket.class,
            StartRoomWorkPacket.class,
            RequestRoomWorkCandidatesPacket.class,
            RoomWorkCandidatesPacket.class,
            AssignRoomEmployeeWorkPacket.class,
            RoomWorkAssignmentResultPacket.class,
            ConfirmDepartmentExpansionPacket.class,
            OpenDepartmentExpansionPacket.class,
            AssignEmployeesMoveAreaPacket.class,
            ElevatorTravelPacket.class,
            OpenEmployeeWarehousePacket.class,
            RequestRoomSuppressionCandidatesPacket.class,
            RoomSuppressionCandidatesPacket.class,
            AssignRoomEmployeeSuppressionPacket.class,
            RoomSuppressionAssignmentResultPacket.class,
            ToggleGlobalTT2Packet.class,
            CoreSuppressionWarningPacket.class,
            CoreSuppressionVisualChoicePacket.class,
            RequestPlayerProfilePacket.class,
            OpenPlayerProfilePacket.class,
            SetPlayerDepartmentPacket.class,
            SetSpecialEgoPacket.class,
            PlayerDepartmentSyncPacket.class,
            SetEmployeeAppearancePacket.class,
            OpenWelcomeNoticePacket.class,
            AcknowledgeWelcomeNoticePacket.class,
            MustOpenPathMeterSyncPacket.class,
            QueenLaserPrototypePacket.class,
            QueenStarPrototypePacket.class,
            GeburahCameraShakePacket.class,
            GeburahStatusSyncPacket.class,
            GeburahVisualEffectPacket.class,
            FearFeedbackPacket.class,
            ToggleFacilityPausePacket.class,
            com.wzz.lobotocraft.network.packet.RequestUniqueRoomWorkPacket.class,
            com.wzz.lobotocraft.network.packet.UniqueWorkStatePacket.class,
            com.wzz.lobotocraft.network.packet.FacilityKnowledgeSyncPacket.class,
            com.wzz.lobotocraft.network.packet.UniqueCommandTargetsPacket.class,
            com.wzz.lobotocraft.network.packet.CommitUniqueCommandPacket.class);

    /** Class -> id 的缓存，用 IdentityHashMap（Class 用引用相等即可，避免 equals/hashCode 开销）。 */
    private static final Map<Class<?>, Integer> ID_BY_CLASS = new IdentityHashMap<>();
    private static final Map<Class<?>, Direction> DIRECTION_BY_CLASS = new IdentityHashMap<>();

    static {
        for (int i = 0; i < PACKETS.size(); i++) {
            Class<? extends IMessage> packet = PACKETS.get(i);
            ID_BY_CLASS.put(packet, i);
            DIRECTION_BY_CLASS.put(packet, discoverDirection(packet));
        }
    }

    private static Direction discoverDirection(Class<? extends IMessage> packet) {
        boolean clientbound = declaresDirection(packet, "sendToClient");
        boolean serverbound = declaresDirection(packet, "sendToServer");
        if (clientbound && serverbound) return Direction.BIDIRECTIONAL;
        if (clientbound) return Direction.CLIENTBOUND;
        if (serverbound) return Direction.SERVERBOUND;
        throw new IllegalStateException("Packet has no declared direction: " + packet.getName());
    }

    private static boolean declaresDirection(Class<? extends IMessage> packet, String method) {
        try {
            return packet.getMethod(method).getDeclaringClass() != IMessage.class;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Packet direction method missing: " + packet.getName(), e);
        }
    }

    /** 返回该包类型登记的 id；未登记抛异常（发送端 bug，应尽早暴露）。 */
    public static int idOf(Class<? extends IMessage> clazz) {
        Integer id = ID_BY_CLASS.get(clazz);
        if (id == null) {
            throw new IllegalStateException("未注册的网络包类型: " + clazz.getName());
        }
        return id;
    }

    /** 按 id 取回包类型；id 越界（含负数、恶意值）返回 null，由调用方拒绝。 */
    public static Class<? extends IMessage> byId(int id) {
        if (id < 0 || id >= PACKETS.size()) {
            return null;
        }
        return PACKETS.get(id);
    }

    public static boolean accepts(int id, boolean receivedOnClient) {
        Class<? extends IMessage> packet = byId(id);
        if (packet == null) return false;
        Direction direction = DIRECTION_BY_CLASS.get(packet);
        return direction == Direction.BIDIRECTIONAL
                || receivedOnClient && direction == Direction.CLIENTBOUND
                || !receivedOnClient && direction == Direction.SERVERBOUND;
    }

    public static Direction directionOf(Class<? extends IMessage> packet) {
        return DIRECTION_BY_CLASS.get(packet);
    }

    public static int size() {
        return PACKETS.size();
    }
}
