package com.wzz.lobotocraft.block.entity;

import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.RoomEntry;
import com.wzz.lobotocraft.world.data.RoomMonitorStatusPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * 收容间信标。摆进异想体房间即可，登记全自动，不需要玩家操作。
 *
 * <h3>为什么身份用"编号"而不是 UUID</h3>
 * {@code AbstractAbnormality#die} 在出逃状态下被击杀时会走 {@code respawnAtEscapePosition}，
 * 那里是 {@code createNewInstance(serverLevel)} 造一个<b>全新实体</b>放回 escapePosition，
 * 也就是说<b>每复活一次 UUID 就变一次</b>。所以这里以 {@code getAbnormalityCode()}
 * 作为房间住户的身份，UUID 只当一次性的快查缓存。
 *
 * <h3>找住户的顺序</h3>
 * <ol>
 *   <li>UUID 直接查（最快，正常情况一次命中）</li>
 *   <li>房间范围内找同编号的 —— 复活、被收容单元回收后又放回本房间，都走这条</li>
 *   <li>大范围（96 格）找同编号的 —— 出逃跑远了。只在前两步都失败时跑，且 3 秒一次</li>
 *   <li>还是没有，但房间里有<b>别的</b>异想体，且原住户已经丢了 {@value #REBIND_AFTER} 次以上
 *       —— 说明玩家换了一只，自动改绑</li>
 * </ol>
 * 找不到已登记实体时立即标记"失联"，避免监控提供一个服务端必然拒绝的镇压入口；
 * 连续 {@value #FORGET_AFTER} 次找不到 -> 彻底清空登记，房间回到空置。
 */
public class RoomMarkerBlockEntity extends BlockEntity {

    /** 可循环切换的扫描半径档位 */
    public static final int[] RADIUS_PRESETS = {6, 8, 10, 12, 16, 21};

    private static final int SCAN_INTERVAL = 20;

    /** 失联多少次之后允许被房间里的新异想体顶替 */
    private static final int REBIND_AFTER = 3;
    /** 失联多少次之后彻底清空登记（60 次 = 60 秒） */
    private static final int FORGET_AFTER = 60;
    /** 大范围搜索（找跑远的出逃者）的半径 */
    private static final int WIDE_RADIUS_XZ = 96;
    private static final int WIDE_RADIUS_Y = 48;

    private int radiusXZ = 10;
    private int radiusY = 5;
    private String departmentId = "";

    /** 住户身份：异想体编号。空串 = 本房间还没登记住户 */
    private String boundCode = "";
    /** 当前实例的 UUID，只是快查用，复活后会变 */
    private UUID boundUuid = null;
    private String boundName = "";
    private String boundRisk = "";
    private int boundRiskColor = 0xFFAAAAAA;

    private int missingScans = 0;
    private int wideScanCooldown = 0;
    private boolean registered = false;

    public RoomMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROOM_MARKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RoomMarkerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (!be.registered) {
            CompanyRoomData data = CompanyRoomData.get(serverLevel);
            RoomEntry room = data.getOrCreateRoom(serverLevel.dimension().location(), pos);
            room.departmentId = be.departmentId;
            data.putRoom(room);
            be.syncArea(serverLevel);
            be.registered = true;
        }

        // 用坐标散列打散扫描时机，避免几十个房间挤在同一 tick
        long offset = Math.floorMod(pos.asLong(), SCAN_INTERVAL);
        if ((serverLevel.getGameTime() % SCAN_INTERVAL) != offset) return;

        be.scan(serverLevel);
    }

    public void scan(ServerLevel level) {
        if (FacilityAccessService.containmentAccess(level.getServer(), level.dimension().location(),
                worldPosition) != FacilityAccessService.ContainmentAccess.OPEN) {
            publishUnexpandedState(level);
            return;
        }

        AABB box = getScanBox();
        List<AbstractAbnormality> inRoom = level.getEntitiesOfClass(
                AbstractAbnormality.class, box, e -> e.isAlive() && !e.isRemoved());

        AbstractAbnormality target = null;
        boolean inside = false;

        // 1. UUID 快路径
        if (boundUuid != null) {
            Entity found = level.getEntity(boundUuid);
            if (found instanceof AbstractAbnormality ab && ab.isAlive() && !ab.isRemoved()
                    && (boundCode.isEmpty() || boundCode.equals(ab.getAbnormalityCode()))) {
                target = ab;
                inside = box.contains(ab.position());
            }
        }

        // 2. 房间里找同编号的（复活 / 放回本房间）
        if (target == null && !boundCode.isEmpty()) {
            for (AbstractAbnormality candidate : inRoom) {
                if (boundCode.equals(candidate.getAbnormalityCode())) {
                    target = candidate;
                    inside = true;
                    break;
                }
            }
        }

        // 3. 大范围找同编号的（出逃跑远了）。只在前两步失败时跑，且 3 秒一次
        if (target == null && !boundCode.isEmpty()) {
            if (wideScanCooldown > 0) {
                wideScanCooldown--;
            } else {
                wideScanCooldown = 3;
                AABB wide = new AABB(worldPosition).inflate(WIDE_RADIUS_XZ, WIDE_RADIUS_Y, WIDE_RADIUS_XZ);
                double best = Double.MAX_VALUE;
                for (AbstractAbnormality candidate : level.getEntitiesOfClass(
                        AbstractAbnormality.class, wide,
                        e -> e.isAlive() && !e.isRemoved() && boundCode.equals(e.getAbnormalityCode()))) {
                    double dist = distSqrToMarker(candidate);
                    if (dist < best) {
                        best = dist;
                        target = candidate;
                    }
                }
                if (target != null) {
                    inside = box.contains(target.position());
                }
            }
        }

        // 4. 原住户丢太久，但房里有别的异想体 -> 玩家换了一只，自动改绑
        if (target == null && !inRoom.isEmpty()
                && (boundCode.isEmpty() || missingScans >= REBIND_AFTER)) {
            double best = Double.MAX_VALUE;
            for (AbstractAbnormality candidate : inRoom) {
                double dist = distSqrToMarker(candidate);
                if (dist < best) {
                    best = dist;
                    target = candidate;
                }
            }
            inside = true;
        }

        List<Player> players = level.getEntitiesOfClass(Player.class, box, Player::isAlive);

        CompanyRoomData data = CompanyRoomData.get(level);
        RoomEntry entry = data.getOrCreateRoom(level.dimension().location(), worldPosition);
        entry.departmentId = departmentId;
        entry.playersInside = players.size();
        entry.lastUpdate = level.getGameTime();
        entry.toolType = false;

        if (target != null) {
            missingScans = 0;
            adopt(target);

            boolean escapeFlag = false;
            try {
                escapeFlag = target.hasEscape();
            } catch (Throwable ignored) {
            }
            boolean escaped = escapeFlag;

            entry.abnormalityCode = boundCode;
            entry.abnormalityName = boundName;
            entry.riskName = boundRisk;
            entry.riskColor = boundRiskColor;
            entry.toolType = target.isToolType();
            entry.status = RoomMonitorStatusPolicy.trackedStatus(escapeFlag, inside);
            entry.qliphoth = target.getQliphothCounter();
            entry.maxQliphoth = target.getMaxQliphothCounter();
            entry.healthPercent = target.getMaxHealth() <= 0 ? 1.0F
                    : Math.max(0.0F, Math.min(1.0F, target.getHealth() / target.getMaxHealth()));
            entry.escapeDistance = escaped ? (int) Math.sqrt(distSqrToMarker(target)) : 0;
            FacilityDayData facilityDay = FacilityDayData.get(level);
            FacilityDayData.ActiveMeltdown facilityMeltdown =
                    facilityDay.getActiveMeltdowns().get(target.getUUID());
            boolean suppressionActive = CoreSuppressionManager.isSuppressionActive(level);
            int facilityRemaining = facilityMeltdown == null ? 0 : (int) Math.max(0L,
                    Math.min(Integer.MAX_VALUE, facilityMeltdown.deadline()
                            - level.getServer().overworld().getGameTime()));
            entry.meltdownActive = suppressionActive && facilityMeltdown != null;
            entry.meltdownRemainingTicks = entry.meltdownActive ? facilityRemaining : 0;
            entry.repeatedWorkPenaltyPercent = FacilityManager.repeatedWorkPenaltyPercent(level, target);

        } else if (!boundCode.isEmpty()) {
            missingScans++;
            if (missingScans >= FORGET_AFTER) {
                // 一分钟都找不到：当它已经被永久移走了，把房间让出来
                clearBindingQuiet();
                entry.abnormalityCode = "";
                entry.abnormalityName = "";
                entry.riskName = "";
                entry.status = RoomEntry.STATUS_EMPTY;
                entry.qliphoth = -1;
                entry.maxQliphoth = -1;
                entry.escapeDistance = 0;
                entry.repeatedWorkPenaltyPercent = 0;
                clearMeltdown(entry);
            } else {
                entry.abnormalityCode = boundCode;
                entry.abnormalityName = boundName;
                entry.riskName = boundRisk;
                entry.riskColor = boundRiskColor;
                entry.status = RoomMonitorStatusPolicy.missingBoundStatus();
                entry.escapeDistance = 0;
                entry.repeatedWorkPenaltyPercent = 0;
                clearMeltdown(entry);
            }
        } else {
            entry.abnormalityCode = "";
            entry.abnormalityName = "";
            entry.riskName = "";
            entry.status = RoomEntry.STATUS_EMPTY;
            entry.qliphoth = -1;
            entry.maxQliphoth = -1;
            entry.escapeDistance = 0;
            entry.repeatedWorkPenaltyPercent = 0;
            clearMeltdown(entry);
        }

        data.putRoom(entry);
    }

    /** Keep the physical abnormality and saved binding dormant until this slot is expanded. */
    private void publishUnexpandedState(ServerLevel level) {
        CompanyRoomData data = CompanyRoomData.get(level);
        RoomEntry entry = data.getOrCreateRoom(level.dimension().location(), worldPosition);
        entry.departmentId = departmentId;
        entry.abnormalityCode = "";
        entry.abnormalityName = "";
        entry.riskName = "";
        entry.status = RoomEntry.STATUS_UNEXPANDED;
        entry.qliphoth = -1;
        entry.maxQliphoth = -1;
        entry.healthPercent = 1.0F;
        entry.playersInside = 0;
        entry.escapeDistance = 0;
        entry.repeatedWorkPenaltyPercent = 0;
        clearMeltdown(entry);
        entry.lastUpdate = level.getGameTime();
        data.putRoom(entry);
    }

    private static void clearMeltdown(RoomEntry entry) {
        entry.meltdownActive = false;
        entry.meltdownRemainingTicks = 0;
    }

    private static int earliestPositive(int first, int second) {
        if (first <= 0) return Math.max(0, second);
        if (second <= 0) return first;
        return Math.min(first, second);
    }

    private double distSqrToMarker(AbstractAbnormality abnormality) {
        return abnormality.distanceToSqr(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    /** 认下这只异想体，并刷新缓存的编号 / 名字 / 风险等级 */
    private void adopt(AbstractAbnormality abnormality) {
        UUID uuid = abnormality.getUUID();
        String code = safe(abnormality.getAbnormalityCode());
        boolean changed = !uuid.equals(boundUuid) || !code.equals(boundCode);

        this.boundUuid = uuid;
        this.boundCode = code;
        this.boundName = safe(abnormality.getAbnormalityName());
        RiskLevel risk = abnormality.getRiskLevel();
        if (risk != null) {
            this.boundRisk = risk.name();
            this.boundRiskColor = 0xFF000000 | (risk.getColor() & 0xFFFFFF);
        }
        if (level != null) {
            abnormality.bindHomeRoom(level.dimension().location(), worldPosition);
        }
        if (changed) {
            setChanged();
        }
    }

    /** 管理界面里手动指定住户 */
    public boolean bindByUuid(ServerLevel level, UUID uuid) {
        if (FacilityAccessService.containmentAccess(level.getServer(), level.dimension().location(),
                worldPosition) != FacilityAccessService.ContainmentAccess.OPEN) {
            return false;
        }
        Entity found = level.getEntity(uuid);
        if (!(found instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()) {
            return false;
        }
        adopt(abnormality);
        missingScans = 0;
        setChanged();
        scan(level);
        return true;
    }

    /** 清空登记。下一次扫描如果房间里有异想体，会自动重新认一只 */
    public void clearBinding() {
        if (level instanceof ServerLevel serverLevel
                && FacilityAccessService.containmentAccess(serverLevel.getServer(),
                serverLevel.dimension().location(), worldPosition)
                != FacilityAccessService.ContainmentAccess.OPEN) {
            return;
        }
        clearBindingQuiet();
        if (level instanceof ServerLevel serverLevel) {
            scan(serverLevel);
        }
    }

    private void clearBindingQuiet() {
        this.boundUuid = null;
        this.boundCode = "";
        this.boundName = "";
        this.boundRisk = "";
        this.missingScans = 0;
        setChanged();
    }

    public UUID getBoundAbnormality() {
        return boundUuid;
    }

    public String getBoundCode() {
        return boundCode;
    }

    public String getBoundName() {
        return boundName;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public AABB getScanBox() {
        return new AABB(worldPosition).inflate(radiusXZ, radiusY, radiusXZ);
    }

    /** 潜行右键切换到下一档扫描半径 */
    public int cycleRadius() {
        int index = 0;
        for (int i = 0; i < RADIUS_PRESETS.length; i++) {
            if (RADIUS_PRESETS[i] == radiusXZ) {
                index = i;
                break;
            }
        }
        radiusXZ = RADIUS_PRESETS[(index + 1) % RADIUS_PRESETS.length];
        radiusY = Math.max(5, radiusXZ / 2);
        setChanged();
        if (level != null) {
            // 同步给客户端，范围框才画得对
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (level instanceof ServerLevel serverLevel) {
            syncArea(serverLevel);
            scan(serverLevel);
        }
        return radiusXZ;
    }

    public int getRadiusXZ() {
        return radiusXZ;
    }

    public int getRadiusY() {
        return radiusY;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        String value = departmentId == null ? "" : departmentId.trim();
        this.departmentId = value.length() > FacilityAreaEntry.MAX_DEPARTMENT_LENGTH
                ? value.substring(0, FacilityAreaEntry.MAX_DEPARTMENT_LENGTH)
                : value;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            CompanyRoomData data = CompanyRoomData.get(serverLevel);
            RoomEntry room = data.getOrCreateRoom(serverLevel.dimension().location(), worldPosition);
            room.departmentId = this.departmentId;
            data.putRoom(room);
            syncArea(serverLevel);
        }
    }

    private void syncArea(ServerLevel level) {
        FacilityAreaEntry area = CompanyRoomData.get(level).getOrCreateArea(
                level.dimension().location(), worldPosition, FacilityAreaType.CONTAINMENT);
        area.markerPos = worldPosition.immutable();
        area.setBounds(
                worldPosition.offset(-radiusXZ, -radiusY, -radiusXZ),
                worldPosition.offset(radiusXZ, radiusY, radiusXZ));
        area.departmentId = departmentId;
        area.type = FacilityAreaType.CONTAINMENT;
        area.groupId = "";
        CompanyRoomData.get(level).putArea(area);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            RoomEntry room = CompanyRoomData.get(serverLevel).getOrCreateRoom(
                    serverLevel.dimension().location(), worldPosition);
            room.departmentId = departmentId;
            CompanyRoomData.get(serverLevel).putRoom(room);
            syncArea(serverLevel);
            registered = true;
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos markerPos = worldPosition.immutable();
            BlockState markerState = getBlockState();
            ResourceLocation dimension = serverLevel.dimension().location();
            serverLevel.getServer().execute(() -> {
                if (serverLevel.hasChunkAt(markerPos)
                        && !serverLevel.getBlockState(markerPos).is(markerState.getBlock())) {
                    CompanyRoomData data = CompanyRoomData.get(serverLevel);
                    data.removeRoom(dimension, markerPos);
                    data.removeArea(FacilityAreaType.CONTAINMENT, dimension, markerPos);
                }
            });
        }
        super.setRemoved();
    }

    // 客户端需要知道扫描半径来画范围框，所以把 BE 数据同步过去
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("RadiusXZ", radiusXZ);
        tag.putInt("RadiusY", radiusY);
        tag.putString("DepartmentId", departmentId);
        tag.putString("BoundCode", boundCode);
        tag.putString("BoundName", boundName);
        tag.putString("BoundRisk", boundRisk);
        tag.putInt("BoundRiskColor", boundRiskColor);
        if (boundUuid != null) {
            tag.putUUID("BoundAbnormality", boundUuid);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("RadiusXZ")) this.radiusXZ = tag.getInt("RadiusXZ");
        if (tag.contains("RadiusY")) this.radiusY = tag.getInt("RadiusY");
        this.departmentId = tag.getString("DepartmentId");
        this.boundCode = tag.getString("BoundCode");
        this.boundName = tag.getString("BoundName");
        this.boundRisk = tag.getString("BoundRisk");
        if (tag.contains("BoundRiskColor")) this.boundRiskColor = tag.getInt("BoundRiskColor");
        this.boundUuid = tag.hasUUID("BoundAbnormality") ? tag.getUUID("BoundAbnormality") : null;
        this.missingScans = 0;
        this.registered = false;
    }
}
