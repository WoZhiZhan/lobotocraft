package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.performance.ClientPerformanceMonitor;
import com.wzz.lobotocraft.client.core.CoreSuppressionClientState;
import com.wzz.lobotocraft.client.room.FacilityLayoutTemplate;
import com.wzz.lobotocraft.client.room.FacilityMonitorVisibility;
import com.wzz.lobotocraft.client.room.RoomMapViewState;
import com.wzz.lobotocraft.client.room.RoomViewClient;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.BindRoomAbnormalityPacket;
import com.wzz.lobotocraft.network.packet.AssignRoomEmployeeWorkPacket;
import com.wzz.lobotocraft.network.packet.AssignEmployeesMoveAreaPacket;
import com.wzz.lobotocraft.network.packet.OpenRoomAbnormalityInfoPacket;
import com.wzz.lobotocraft.network.packet.RequestNearbyAbnormalitiesPacket;
import com.wzz.lobotocraft.network.packet.RequestRoomWorkCandidatesPacket;
import com.wzz.lobotocraft.network.packet.RequestRoomSuppressionCandidatesPacket;
import com.wzz.lobotocraft.network.packet.AssignRoomEmployeeSuppressionPacket;
import com.wzz.lobotocraft.network.packet.RoomTeleportRequestPacket;
import com.wzz.lobotocraft.network.packet.SetFloorNamePacket;
import com.wzz.lobotocraft.network.packet.ToggleGlobalTT2Packet;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.world.data.AbnormalityCandidate;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaSelection;
import com.wzz.lobotocraft.world.data.FloorEntry;
import com.wzz.lobotocraft.world.data.RoomEntry;
import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 二维设施平面图。
 * <p>
 * 房间按真实世界的 X / Z 坐标排布，所以这张图和你实际建的公司布局是一致的。
 * 左侧楼层列表来自世界里的「楼层标记方块」——每层放一个即可，间距多少都行；
 * 一个楼层标记都没放的话，退化成按 Y 坐标自动聚类分层。
 * <p>
 * 操作：
 * <ul>
 *   <li>左键房间：选择异想体并打开工作派遣草稿</li>
 *   <li>左键空白拖动：框选员工</li>
 *   <li>右键收容单元：资料；Shift+右键：传送；Ctrl+右键：更改住户</li>
 *   <li>框选员工后右键区域：移动；右键或中键拖动：平移视角</li>
 *   <li>左键楼层条：切换楼层；右键楼层条：给这层改名</li>
 *   <li>滚轮缩放，视野位置会记忆</li>
 *   <li>R 复位视野</li>
 * </ul>
 */
public class RoomMapScreen extends Screen {

    private static final ResourceLocation QUESTION_MARK =
            ResourceUtil.createInstance("textures/gui/question_mark.png");
    private static final ResourceLocation[] WORK_ICONS = {
            ResourceUtil.createInstance("textures/gui/work_pref_instinct.png"),
            ResourceUtil.createInstance("textures/gui/work_pref_insight.png"),
            ResourceUtil.createInstance("textures/gui/work_pref_attachment.png"),
            ResourceUtil.createInstance("textures/gui/work_pref_repression.png")
    };
    private static final ResourceLocation MALKUTH_UNKNOWN_WORK =
            ResourceUtil.createInstance("textures/gui/core_suppression/malkuth_unknown_work.png");
    private static final String[] WORK_NAME_KEYS = {
            "work.lobotocraft.instinct", "work.lobotocraft.insight",
            "work.lobotocraft.attachment", "work.lobotocraft.repression"};
    private static final ResourceLocation[] STAT_ICONS = {
            ResourceUtil.createInstance("textures/gui/theme/stat_fortitude.png"),
            ResourceUtil.createInstance("textures/gui/theme/stat_prudence.png"),
            ResourceUtil.createInstance("textures/gui/theme/stat_temperance.png"),
            ResourceUtil.createInstance("textures/gui/theme/stat_justice.png")
    };
    private static final ResourceLocation PAGE_ARROW =
            ResourceUtil.createInstance("textures/gui/monitor/page_arrow.png");
    private static final ResourceLocation PAGE_ARROW_HOVER =
            ResourceUtil.createInstance("textures/gui/monitor/page_arrow_hover.png");
    private static final ResourceLocation DEPARTMENT_HEADER =
            ResourceUtil.createInstance("textures/gui/monitor/department_header.png");

    private static final int HEADER_H = 32;

    private static final int CARD_W = 94;
    private static final int CARD_H = 72;
    private static final int PORTRAIT = 30;

    private static final int GUTTER_W = 108;
    private static final int FLOOR_LABEL_H = 28;
    private static final int FLOOR_HEADER_H = 20;
    private static final int NODE_X_PITCH = 112;
    private static final int NODE_Y_PITCH = 88;
    private static final int MAX_NODES_PER_ROW = 8;
    private static final int FLOOR_GAP = 18;
    private static final String OVERVIEW_VIEW_KEY = "__facility_overview__";
    private static final double TEMPLATE_WIDTH = 1700.0D;
    private static final double TEMPLATE_HEIGHT = 1700.0D;
    private static final int TAB_X = 8;
    private static final int TAB_W = GUTTER_W - 16;
    private static final int TAB_H = FLOOR_LABEL_H;
    private static final int TAB_GAP = 0;
    private static final int TAB_TOP = HEADER_H;

    /** 没有楼层标记方块时，自动分层用的 Y 容差 */
    private static final int AUTO_FLOOR_TOLERANCE = 6;

    private static final int COLOR_BG = LobotomyUiTheme.BG_SCRIM;
    private static final int COLOR_CARD_BG = LobotomyUiTheme.BG_PANEL_SOLID;
    private static final int COLOR_CARD_BG_HOVER = LobotomyUiTheme.BG_INSET;
    private static final int COLOR_PANEL = LobotomyUiTheme.BG_PANEL_SOLID;
    private static final int COLOR_TEXT = LobotomyUiTheme.TEXT_TITLE;
    private static final int COLOR_TEXT_DIM = LobotomyUiTheme.TEXT_MUTED;
    private static final int COLOR_ACCENT = LobotomyUiTheme.ACCENT;

    private static final int STATUS_OCCUPIED = LobotomyUiTheme.ENERGY_GREEN;
    private static final int STATUS_ESCAPED = LobotomyUiTheme.ALERT_RED;
    private static final int STATUS_EMPTY = LobotomyUiTheme.TEXT_DISABLED;
    private static final int STATUS_LOST = LobotomyUiTheme.WARN_YELLOW;
    private static final int STATUS_UNEXPANDED = 0xFF74808E;

    private static final int PICKER_W = 340;
    private static final int PICKER_ROW_H = 30;
    private static final int PICKER_HEADER_H = 26;
    private static final int PICKER_FOOTER_H = 28;
    private static final int PICKER_MAX_ROWS = 8;
    private static final int DETAILS_W = 126;
    private static final int DETAILS_H = 218;
    private static final int CANDIDATE_W = 174;
    private static final int CANDIDATE_H = 256;
    private static final int COMMAND_GAP = 4;
    private static final int DETAILS_MARGIN = 10;
    private static final int DETAILS_TOP = HEADER_H + 10;
    private static final int WORK_ICON_SIZE = 38;
    private static final int WORK_ICON_GAP = 10;
    private static final int ACTIVE_WORK_ICON_SIZE = 8;
    private static final int ACTIVE_WORK_ICON_GAP = 3;
    private static final int CANDIDATE_ROW_H = 44;
    private static final int CANDIDATES_PER_PAGE = 5;
    // Lobotomy Corporation's SefiraManager.Init order, with the two Tiphereth
    // sections represented by this mod's single Central department.
    private static final List<EmployeeDepartment> CANDIDATE_DEPARTMENT_ORDER = List.of(
            EmployeeDepartment.CONTROL,
            EmployeeDepartment.INFORMATION,
            EmployeeDepartment.TRAINING,
            EmployeeDepartment.SAFETY,
            EmployeeDepartment.CENTRAL,
            EmployeeDepartment.DISCIPLINARY,
            EmployeeDepartment.WELFARE,
            EmployeeDepartment.EXTRACTION,
            EmployeeDepartment.RECORD,
            EmployeeDepartment.ARCHITECTURE);
    private static final int SUPPRESSION_SELECTION = WorkType.values().length;
    private static final double BOX_SELECT_THRESHOLD_SQ = 16.0D;

    private List<RoomEntry> rooms;
    private List<FloorEntry> floorEntries;
    private List<FacilityAreaEntry> areas;
    private List<RoomEmployeeSnapshot> employees;
    private Set<String> dangerousAreaIds;
    private long serverGameTime;
    private boolean globalTt2Enabled;
    private long snapshotReceivedAtMillis;
    private final ResourceLocation dimension;

    private final List<Floor> floors = new ArrayList<>();
    private int selectedFloor = 0;
    private String selectedLabel = null;
    private final Map<String, LayoutPoint> roomLayout = new HashMap<>();
    private final Map<String, String> roomAreaIds = new HashMap<>();
    private final FacilityLayoutTemplate facilityLayout = FacilityLayoutTemplate.defaultLayout();
    private FacilityMonitorVisibility facilityVisibility = FacilityMonitorVisibility.empty();
    private double layoutMinX;
    private double layoutMaxX;
    private double layoutMinY;
    private double layoutMaxY;
    private double tabScroll = 0.0D;

    /** 正在改名的楼层下标，-1 = 没有 */
    private int renamingFloor = -1;
    private EditBox renameBox;

    private final Map<String, ResourceLocation> portraitCache = new HashMap<>();

    private double centerWorldX;
    private double centerWorldZ;
    private double zoom = 2.0D;
    private double panX;
    private double panY;

    private final long openedAt = Util.getMillis();
    private RoomEntry hovered;
    private FacilityLayoutTemplate.Area hoveredFacilityArea;
    private RoomEntry selectedRoom;
    private int selectedWork = -1;
    private int hoveredWork = -1;

    private boolean selectionArmed;
    private boolean selectingEmployees;
    private int cameraPanButton = -1;
    private boolean moveCommandArmed;
    private double moveCommandStartX;
    private double moveCommandStartY;
    private String pendingMoveGroupId;
    private String movePulseGroupId;
    private long movePulseAt;
    private double selectionStartX;
    private double selectionStartY;
    private double selectionEndX;
    private double selectionEndY;
    private final List<EmployeeMapMarker> employeeMarkers = new ArrayList<>();
    private final Set<UUID> selectedEmployees = new HashSet<>();
    private Consumer<Set<UUID>> employeeSelectionListener = ignored -> { };
    private List<RoomEmployeeSnapshot> workCandidates = List.of();
    private List<CandidatePage> candidatePages = List.of();
    private int candidatePage;
    private boolean candidatesLoading;
    private String candidateError = "";
    private String assignmentMessage = "";
    private boolean assignmentSucceeded;
    private long assignmentMessageAt;
    private long workSelectedAt;
    private RoomEmployeeSnapshot hoveredCandidate;
    private RoomEmployeeSnapshot hoveredMapEmployee;
    private boolean hoveredTt2Button;
    /** Ctrl 拖拽中的房间 */

    /** "更改住户"选单。null = 没打开 */
    private RoomEntry pickerRoom;
    private List<AbnormalityCandidate> pickerCandidates;
    private int pickerScroll = 0;

    public RoomMapScreen(List<RoomEntry> rooms, List<FloorEntry> floorEntries,
                         List<FacilityAreaEntry> areas, List<RoomEmployeeSnapshot> employees,
                         Set<String> dangerousAreaIds, long serverGameTime,
                         boolean globalTt2Enabled, ResourceLocation dimension) {
        super(Component.translatable("screen.lobotocraft.monitor.title"));
        this.rooms = new ArrayList<>(rooms);
        this.floorEntries = new ArrayList<>(floorEntries);
        this.areas = new ArrayList<>(areas);
        this.facilityVisibility = FacilityMonitorVisibility.fromAreas(this.areas);
        this.employees = new ArrayList<>(employees);
        this.dangerousAreaIds = Set.copyOf(dangerousAreaIds);
        this.serverGameTime = serverGameTime;
        this.globalTt2Enabled = globalTt2Enabled;
        this.snapshotReceivedAtMillis = Util.getMillis();
        this.dimension = dimension;
        this.selectedLabel = RoomMapViewState.getLastFloorLabel();
    }

    public RoomMapScreen(List<RoomEntry> rooms, List<FloorEntry> floorEntries,
                         List<FacilityAreaEntry> areas, List<RoomEmployeeSnapshot> employees,
                         Set<String> dangerousAreaIds, long serverGameTime,
                         ResourceLocation dimension) {
        this(rooms, floorEntries, areas, employees, dangerousAreaIds, serverGameTime,
                false, dimension);
    }

    @Override
    public void init() {
        super.init();
        buildFloors();
        restoreOrFitView();
        rebuildEmployeeMarkers();
    }

    // ------------------------------------------------------------ 楼层分组

    private static class Floor {
        final String label;
        /** 对应的楼层标记方块坐标，null = 自动分层出来的，不能改名 */
        final BlockPos markerPos;
        int topY = Integer.MIN_VALUE;
        final List<RoomEntry> rooms = new ArrayList<>();
        double layoutTop;
        double layoutBottom;
        double layoutCenter;

        Floor(String label, BlockPos markerPos) {
            this.label = label;
            this.markerPos = markerPos;
        }

        int escaped() {
            int count = 0;
            for (RoomEntry entry : rooms) {
                if (entry.status == RoomEntry.STATUS_ESCAPED) count++;
            }
            return count;
        }
    }

    private record LayoutPoint(double x, double y) {
    }

    private record ScreenBounds(int left, int top, int right, int bottom) {
        int width() {
            return Math.max(0, right - left);
        }

        int height() {
            return Math.max(0, bottom - top);
        }

        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /**
     * Pure-client adapter for the later employee snapshot. Coordinates use this screen's layout
     * world (the same coordinate space returned by {@link #screenToLayout(double, double)}).
     */
    public record EmployeeMapMarker(UUID employeeId, String displayName, String departmentId,
                                    double layoutX, double layoutY,
                                    int areaIndex, int areaPopulation, boolean commandable) {
    }

    private record EmployeeMarkerDraft(RoomEmployeeSnapshot employee, String areaKey,
                                       double layoutX, double layoutY) {
    }

    private record MarkerScreenPoint(double x, double y) {
    }

    /** Immutable work-selection draft exposed to the dispatch controls. */
    public record WorkDispatchSelection(RoomEntry room, WorkType workType, Set<UUID> employeeIds) {
        public WorkDispatchSelection {
            employeeIds = Set.copyOf(employeeIds);
        }
    }

    private record CandidatePage(String departmentId, List<RoomEmployeeSnapshot> employees) {
    }

    /** Converts a screen point to the stable schematic coordinate space used by employee markers. */
    public double[] screenToLayout(double screenX, double screenY) {
        return new double[]{screenToWorldX(screenX), screenToWorldZ(screenY)};
    }

    /** Replaces the employee markers without coupling this screen to a packet or snapshot format. */
    public void setEmployeeMarkers(List<EmployeeMapMarker> markers) {
        employeeMarkers.clear();
        if (markers != null) employeeMarkers.addAll(markers);
        Set<UUID> available = new HashSet<>();
        for (EmployeeMapMarker marker : employeeMarkers) available.add(marker.employeeId());
        if (selectedEmployees.retainAll(available)) notifyEmployeeSelectionChanged();
    }

    public void setEmployeeSelectionListener(Consumer<Set<UUID>> listener) {
        employeeSelectionListener = listener == null ? ignored -> { } : listener;
    }

    public Set<UUID> selectedEmployeeIds() {
        return Set.copyOf(selectedEmployees);
    }

    public Optional<WorkDispatchSelection> workDispatchSelection() {
        if (selectedRoom == null || selectedWork < 0 || selectedWork >= WorkType.values().length) {
            return Optional.empty();
        }
        if (selectedRoom.status == RoomEntry.STATUS_UNEXPANDED) return Optional.empty();
        return Optional.of(new WorkDispatchSelection(
                selectedRoom, WorkType.values()[selectedWork], selectedEmployees));
    }

    private void buildFloors() {
        floors.clear();

        if (!floorEntries.isEmpty()) {
            // 世界里放了楼层标记方块：服务端已经把每个房间归好层了，这里照抄
            for (FloorEntry entry : floorEntries) {
                floors.add(new Floor(entry.displayName(), entry.pos));
            }
            List<RoomEntry> orphans = new ArrayList<>();
            for (RoomEntry room : rooms) {
                if (room.floorIndex >= 0 && room.floorIndex < floors.size()) {
                    Floor floor = floors.get(room.floorIndex);
                    floor.rooms.add(room);
                    floor.topY = Math.max(floor.topY, room.pos.getY());
                } else {
                    orphans.add(room);
                }
            }
            for (int i = 0; i < floors.size(); i++) {
                if (floors.get(i).topY == Integer.MIN_VALUE) {
                    floors.get(i).topY = floorEntries.get(i).y();
                }
            }
            if (!orphans.isEmpty()) {
                Floor other = new Floor(tr("screen.lobotocraft.monitor.floor.unassigned"), null);
                other.rooms.addAll(orphans);
                other.topY = Integer.MIN_VALUE + 1;
                floors.add(other);
            }
            // 空楼层也留着，方便你确认标记方块放对了没
        } else {
            // 一个楼层标记都没放：按 Y 从高到低自动聚类
            List<RoomEntry> sorted = new ArrayList<>(rooms);
            sorted.sort(Comparator.comparingInt((RoomEntry e) -> e.pos.getY()).reversed());
            Floor current = null;
            int lastY = 0;
            for (RoomEntry entry : sorted) {
                int y = entry.pos.getY();
                if (current == null || Math.abs(lastY - y) > AUTO_FLOOR_TOLERANCE) {
                    current = new Floor("Y " + y, null);
                    floors.add(current);
                }
                current.rooms.add(entry);
                current.topY = Math.max(current.topY, y);
                lastY = y;
            }
        }

        floors.sort(Comparator.comparingInt((Floor f) -> f.topY).reversed());

        selectedFloor = 0;
        if (selectedLabel != null) {
            for (int i = 0; i < floors.size(); i++) {
                if (floors.get(i).label.equals(selectedLabel)) {
                    selectedFloor = i;
                    break;
                }
            }
        }
        if (!floors.isEmpty()) {
            selectedLabel = floors.get(selectedFloor).label;
        }
        buildOverviewLayout();
    }

    private List<RoomEntry> visibleRooms() {
        return rooms;
    }

    private boolean showTabs() {
        return !floors.isEmpty();
    }

    private int mapLeft() {
        return showTabs() ? GUTTER_W : 0;
    }

    private void selectFloor(int index) {
        if (floors.isEmpty()) return;
        cancelRename();
        selectedFloor = Mth.clamp(index, 0, floors.size() - 1);
        selectedLabel = floors.get(selectedFloor).label;
        RoomMapViewState.setLastFloorLabel(selectedLabel);
        focusFloor(floors.get(selectedFloor));
    }

    /** Builds the whole-company schematic and assigns each room to one stable template slot. */
    private void buildOverviewLayout() {
        roomLayout.clear();
        roomAreaIds.clear();

        List<RoomEntry> orderedRooms = new ArrayList<>();

        for (Floor floor : floors) {
            floor.rooms.sort(Comparator
                    .comparingInt((RoomEntry room) -> room.pos.getZ())
                    .thenComparingInt(room -> room.pos.getX())
                    .thenComparingInt(room -> room.pos.getY())
                    .thenComparing(RoomEntry::key));
            orderedRooms.addAll(floor.rooms);
        }

        List<FacilityLayoutTemplate.Area> containmentSlots = facilityLayout.allAreas().stream()
                .filter(area -> area.role() == FacilityLayoutTemplate.AreaRole.CONTAINMENT_ROOM
                        && facilityVisibility.isVisible(area))
                .toList();
        Set<String> usedAreaIds = new HashSet<>();
        Set<String> assignedRoomKeys = new HashSet<>();

        // A groupId equal to a template areaId is the explicit binding interface for real coordinates.
        for (RoomEntry room : orderedRooms) {
            Optional<FacilityLayoutTemplate.Area> explicit = explicitTemplateArea(room);
            if (explicit.isPresent() && usedAreaIds.add(explicit.get().areaId())) {
                assignRoomToArea(room, explicit.get());
                assignedRoomKeys.add(room.key());
            }
        }

        // Department ownership is the normal automatic binding path.
        for (RoomEntry room : orderedRooms) {
            if (assignedRoomKeys.contains(room.key()) || room.departmentId == null
                    || room.departmentId.isBlank()) continue;
            for (FacilityLayoutTemplate.Area slot : facilityLayout.areasForDepartment(room.departmentId)) {
                if (slot.role() == FacilityLayoutTemplate.AreaRole.CONTAINMENT_ROOM
                        && facilityVisibility.isVisible(slot)
                        && usedAreaIds.add(slot.areaId())) {
                    assignRoomToArea(room, slot);
                    assignedRoomKeys.add(room.key());
                    break;
                }
            }
        }

        // Old saves do not have department metadata. Preserve deterministic top-to-bottom placement.
        int nextSlot = 0;
        for (RoomEntry room : orderedRooms) {
            if (assignedRoomKeys.contains(room.key())) continue;
            while (nextSlot < containmentSlots.size()
                    && usedAreaIds.contains(containmentSlots.get(nextSlot).areaId())) {
                nextSlot++;
            }
            if (nextSlot < containmentSlots.size()) {
                FacilityLayoutTemplate.Area slot = containmentSlots.get(nextSlot++);
                usedAreaIds.add(slot.areaId());
                assignRoomToArea(room, slot);
                assignedRoomKeys.add(room.key());
            }
        }

        // More rooms than the reference layout supports remain visible in an overflow row.
        int overflowIndex = 0;
        int overflowColumns = Math.max(1, Math.min(MAX_NODES_PER_ROW,
                orderedRooms.size() - assignedRoomKeys.size()));
        for (RoomEntry room : orderedRooms) {
            if (assignedRoomKeys.contains(room.key())) continue;
            int row = overflowIndex / overflowColumns;
            int column = overflowIndex % overflowColumns;
            double x = (column - (overflowColumns - 1) / 2.0D) * NODE_X_PITCH;
            double y = TEMPLATE_HEIGHT + CARD_H + row * NODE_Y_PITCH;
            roomLayout.put(room.key(), new LayoutPoint(x, y));
            overflowIndex++;
        }

        for (int floorIndex = 0; floorIndex < floors.size(); floorIndex++) {
            Floor floor = floors.get(floorIndex);
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (RoomEntry room : floor.rooms) {
                LayoutPoint point = roomLayout.get(room.key());
                if (point == null) continue;
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
            }
            if (!Double.isFinite(minY)) {
                double step = TEMPLATE_HEIGHT / Math.max(1, floors.size());
                minY = maxY = (floorIndex + 0.5D) * step;
            }
            floor.layoutTop = minY - CARD_H / 2.0D - 8.0D;
            floor.layoutBottom = maxY + CARD_H / 2.0D + 8.0D;
            floor.layoutCenter = (floor.layoutTop + floor.layoutBottom) / 2.0D;
        }

        layoutMinX = -TEMPLATE_WIDTH / 2.0D;
        layoutMaxX = TEMPLATE_WIDTH / 2.0D;
        layoutMinY = 0.0D;
        layoutMaxY = overflowIndex == 0 ? TEMPLATE_HEIGHT
                : TEMPLATE_HEIGHT + CARD_H * 1.5D
                + ((overflowIndex - 1) / overflowColumns) * NODE_Y_PITCH;
    }

    private Optional<FacilityLayoutTemplate.Area> explicitTemplateArea(RoomEntry room) {
        for (FacilityAreaEntry area : areas) {
            if (!area.markerPos.equals(room.pos) || area.dimension == null
                    || !area.dimension.equals(room.dimension)
                    || area.groupId == null || area.groupId.isBlank()) continue;
            Optional<FacilityLayoutTemplate.Area> templateArea = facilityLayout.areaById(area.groupId);
            if (templateArea.isPresent()
                    && templateArea.get().role() == FacilityLayoutTemplate.AreaRole.CONTAINMENT_ROOM) {
                return templateArea;
            }
        }
        return Optional.empty();
    }

    private void assignRoomToArea(RoomEntry room, FacilityLayoutTemplate.Area area) {
        FacilityLayoutTemplate.Point center = area.center();
        roomLayout.put(room.key(), new LayoutPoint(templateWorldX(center.x()), templateWorldY(center.y())));
        roomAreaIds.put(room.key(), area.areaId());
    }

    // ------------------------------------------------------------ 视野

    /** 有记住的位置就用记住的，没有就自动适配 */
    private void restoreOrFitView() {
        double[] saved = RoomMapViewState.getFloorView(OVERVIEW_VIEW_KEY);
        if (saved != null && saved[2] > 0) {
            computeCenter();
            panX = saved[0];
            panY = saved[1];
            zoom = saved[2];
            return;
        }
        fitToView();
    }

    private void computeCenter() {
        centerWorldX = (layoutMinX + layoutMaxX) / 2.0D;
        centerWorldZ = (layoutMinY + layoutMaxY) / 2.0D;
    }

    private void fitToView() {
        if (floors.isEmpty()) return;

        computeCenter();
        double spanX = Math.max(CARD_W, layoutMaxX - layoutMinX + CARD_W);
        double spanY = Math.max(CARD_H, layoutMaxY - layoutMinY + 24.0D);
        double usableW = Math.max(120.0D, this.width - mapLeft() - 30.0D);
        double usableH = Math.max(120.0D, this.height - HEADER_H - 36.0D);

        this.zoom = Mth.clamp(Math.min(usableW / spanX, usableH / spanY), 0.45D, 1.35D);
        this.panX = 0;
        this.panY = 0;
        saveCurrentView();
    }

    private void saveCurrentView() {
        RoomMapViewState.setFloorView(OVERVIEW_VIEW_KEY, panX, panY, zoom);
    }

    private void focusFloor(Floor floor) {
        computeCenter();
        panX = 0.0D;
        panY = -(floor.layoutCenter - centerWorldZ) * zoom;
        saveCurrentView();
    }

    public void onDataRefreshed(List<RoomEntry> newRooms, List<FloorEntry> newFloors,
                                List<FacilityAreaEntry> newAreas,
                                List<RoomEmployeeSnapshot> newEmployees,
                                Set<String> newDangerousAreaIds, long newServerGameTime,
                                boolean newGlobalTt2Enabled) {
        long sectionStarted = ClientPerformanceMonitor.beginSection("room_map_refresh");
        try {
            applyDataRefresh(newRooms, newFloors, newAreas, newEmployees,
                    newDangerousAreaIds, newServerGameTime, newGlobalTt2Enabled);
        } finally {
            ClientPerformanceMonitor.endSection("room_map_refresh", sectionStarted);
        }
    }

    private void applyDataRefresh(List<RoomEntry> newRooms, List<FloorEntry> newFloors,
                                  List<FacilityAreaEntry> newAreas,
                                  List<RoomEmployeeSnapshot> newEmployees,
                                  Set<String> newDangerousAreaIds, long newServerGameTime,
                                  boolean newGlobalTt2Enabled) {
        boolean layoutChanged = !hasSameLayout(newRooms, newFloors, newAreas);
        boolean employeeMarkerLayoutChanged = layoutChanged
                || !hasSameEmployeeMarkerLayout(this.employees, newEmployees);
        String selectedKey = selectedRoom == null ? null : selectedRoom.key();
        if (layoutChanged) {
            this.rooms = new ArrayList<>(newRooms);
            this.floorEntries = new ArrayList<>(newFloors);
            this.areas = new ArrayList<>(newAreas);
            this.facilityVisibility = FacilityMonitorVisibility.fromAreas(this.areas);
        } else {
            updateRoomSnapshotsInPlace(newRooms);
        }
        this.employees = new ArrayList<>(newEmployees);
        this.dangerousAreaIds = Set.copyOf(newDangerousAreaIds);
        this.serverGameTime = newServerGameTime;
        this.globalTt2Enabled = newGlobalTt2Enabled;
        this.snapshotReceivedAtMillis = Util.getMillis();

        if (layoutChanged) {
            buildFloors();
            computeCenter();
            selectedRoom = null;
            if (selectedKey != null) {
                for (RoomEntry room : rooms) {
                    if (selectedKey.equals(room.key())) {
                        selectedRoom = room;
                        break;
                    }
                }
            }
        }
        if (selectedRoom == null) selectedWork = -1;
        else if (isSuppressionSelection() && selectedRoom.status != RoomEntry.STATUS_ESCAPED) {
            selectedWork = -1;
            workCandidates = List.of();
            candidatePages = List.of();
        }
        if (employeeMarkerLayoutChanged) rebuildEmployeeMarkers();
    }

    public void onDataRefreshed(List<RoomEntry> newRooms, List<FloorEntry> newFloors,
                                List<FacilityAreaEntry> newAreas,
                                List<RoomEmployeeSnapshot> newEmployees,
                                Set<String> newDangerousAreaIds, long newServerGameTime) {
        onDataRefreshed(newRooms, newFloors, newAreas, newEmployees,
                newDangerousAreaIds, newServerGameTime, globalTt2Enabled);
    }

    private boolean hasSameLayout(List<RoomEntry> newRooms, List<FloorEntry> newFloors,
                                  List<FacilityAreaEntry> newAreas) {
        if (rooms.size() != newRooms.size() || floorEntries.size() != newFloors.size()
                || areas.size() != newAreas.size()) return false;

        for (int index = 0; index < rooms.size(); index++) {
            RoomEntry current = rooms.get(index);
            RoomEntry updated = newRooms.get(index);
            if (!java.util.Objects.equals(current.dimension, updated.dimension)
                    || !java.util.Objects.equals(current.pos, updated.pos)
                    || !java.util.Objects.equals(current.departmentId, updated.departmentId)
                    || current.floorIndex != updated.floorIndex
                    || !java.util.Objects.equals(current.floorName, updated.floorName)
                    || current.floorY != updated.floorY) return false;
        }
        for (int index = 0; index < floorEntries.size(); index++) {
            FloorEntry current = floorEntries.get(index);
            FloorEntry updated = newFloors.get(index);
            if (!java.util.Objects.equals(current.dimension, updated.dimension)
                    || !java.util.Objects.equals(current.pos, updated.pos)
                    || !java.util.Objects.equals(current.name, updated.name)) return false;
        }
        for (int index = 0; index < areas.size(); index++) {
            if (!sameAreaGeometry(areas.get(index), newAreas.get(index))) return false;
        }
        return true;
    }

    private static boolean sameAreaGeometry(FacilityAreaEntry current, FacilityAreaEntry updated) {
        return java.util.Objects.equals(current.dimension, updated.dimension)
                && java.util.Objects.equals(current.key, updated.key)
                && java.util.Objects.equals(current.markerPos, updated.markerPos)
                && java.util.Objects.equals(current.minPos, updated.minPos)
                && java.util.Objects.equals(current.maxPos, updated.maxPos)
                && java.util.Objects.equals(current.departmentId, updated.departmentId)
                && current.type == updated.type
                && java.util.Objects.equals(current.groupId, updated.groupId)
                && current.floorIndex == updated.floorIndex;
    }

    private static boolean hasSameEmployeeMarkerLayout(List<RoomEmployeeSnapshot> current,
                                                       List<RoomEmployeeSnapshot> updated) {
        if (current.size() != updated.size()) return false;
        for (int index = 0; index < current.size(); index++) {
            RoomEmployeeSnapshot before = current.get(index);
            RoomEmployeeSnapshot after = updated.get(index);
            if (!before.id().equals(after.id())
                    || !java.util.Objects.equals(before.name(), after.name())
                    || !java.util.Objects.equals(before.departmentId(), after.departmentId())
                    || !java.util.Objects.equals(before.dimension(), after.dimension())
                    || !java.util.Objects.equals(before.position(), after.position())
                    || before.commandable() != after.commandable()) return false;
        }
        return true;
    }

    private void updateRoomSnapshotsInPlace(List<RoomEntry> snapshots) {
        for (int index = 0; index < rooms.size(); index++) {
            copyRoomSnapshot(rooms.get(index), snapshots.get(index));
        }
    }

    private static void copyRoomSnapshot(RoomEntry target, RoomEntry source) {
        target.dimension = source.dimension;
        target.pos = source.pos;
        target.departmentId = source.departmentId;
        target.abnormalityCode = source.abnormalityCode;
        target.abnormalityName = source.abnormalityName;
        target.riskName = source.riskName;
        target.riskColor = source.riskColor;
        target.toolType = source.toolType;
        target.status = source.status;
        target.qliphoth = source.qliphoth;
        target.maxQliphoth = source.maxQliphoth;
        target.healthPercent = source.healthPercent;
        target.playersInside = source.playersInside;
        target.escapeDistance = source.escapeDistance;
        target.meltdownActive = source.meltdownActive;
        target.meltdownRemainingTicks = source.meltdownRemainingTicks;
        target.lastUpdate = source.lastUpdate;
        target.floorIndex = source.floorIndex;
        target.floorName = source.floorName;
        target.floorY = source.floorY;
    }

    private void rebuildEmployeeMarkers() {
        List<EmployeeMarkerDraft> drafts = new ArrayList<>();
        Map<String, Integer> areaPopulations = new HashMap<>();
        for (RoomEmployeeSnapshot employee : employees) {
            FacilityAreaEntry worldArea = smallestAreaContaining(employee.dimension(), employee.position());
            FacilityLayoutTemplate.Area templateArea = worldArea == null
                    ? null : facilityLayout.areaById(worldArea.groupId).orElse(null);
            if (worldArea != null && templateArea != null) {
                String areaKey = employee.dimension() + "@" + worldArea.groupId;
                drafts.add(new EmployeeMarkerDraft(employee, areaKey,
                        templateWorldX(templateArea.center().x()),
                        templateWorldY(templateArea.center().y())));
                areaPopulations.merge(areaKey, 1, Integer::sum);
                continue;
            }

            FacilityAreaEntry nearest = nearestMappedPassage(employee);
            FacilityLayoutTemplate.Area nearestTemplate = nearest == null
                    ? null : facilityLayout.areaById(nearest.groupId).orElse(null);
            if (nearestTemplate != null) {
                String areaKey = employee.dimension() + "@" + nearest.groupId;
                drafts.add(new EmployeeMarkerDraft(employee, areaKey,
                        templateWorldX(nearestTemplate.center().x()),
                        templateWorldY(nearestTemplate.center().y())));
                areaPopulations.merge(areaKey, 1, Integer::sum);
            }
        }

        List<EmployeeMapMarker> markers = new ArrayList<>(drafts.size());
        Map<String, Integer> areaIndices = new HashMap<>();
        for (EmployeeMarkerDraft draft : drafts) {
            int index = areaIndices.getOrDefault(draft.areaKey(), 0);
            areaIndices.put(draft.areaKey(), index + 1);
            RoomEmployeeSnapshot employee = draft.employee();
            markers.add(new EmployeeMapMarker(employee.id(), employee.name(), employee.departmentId(),
                    draft.layoutX(), draft.layoutY(), index,
                    areaPopulations.getOrDefault(draft.areaKey(), 1), employee.commandable()));
        }
        setEmployeeMarkers(markers);
    }

    private FacilityAreaEntry smallestAreaContaining(ResourceLocation employeeDimension, BlockPos pos) {
        return FacilityAreaSelection.containing(areas, employeeDimension, pos).orElse(null);
    }

    private FacilityAreaEntry nearestMappedPassage(RoomEmployeeSnapshot employee) {
        FacilityAreaEntry nearest = null;
        double nearestScore = Double.POSITIVE_INFINITY;
        for (FacilityAreaEntry area : areas) {
            if (area == null || area.dimension == null
                    || !area.dimension.equals(employee.dimension())
                    || area.type == com.wzz.lobotocraft.world.data.FacilityAreaType.CONTAINMENT
                    || area.groupId == null || area.groupId.isBlank()
                    || facilityLayout.areaById(area.groupId).isEmpty()) continue;
            double score = squaredDistanceToArea(employee.position(), area);
            if (!area.departmentId.equals(employee.departmentId())) {
                score += 1_000_000.0D;
            }
            if (score < nearestScore) {
                nearest = area;
                nearestScore = score;
            }
        }
        return nearest;
    }

    private static double squaredDistanceToArea(BlockPos position, FacilityAreaEntry area) {
        int dx = Math.max(area.minPos.getX() - position.getX(),
                Math.max(0, position.getX() - area.maxPos.getX()));
        int dy = Math.max(area.minPos.getY() - position.getY(),
                Math.max(0, position.getY() - area.maxPos.getY()));
        int dz = Math.max(area.minPos.getZ() - position.getZ(),
                Math.max(0, position.getZ() - area.maxPos.getZ()));
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    private static double normalizedWithin(int value, int min, int max) {
        return min >= max ? 0.5D : Mth.clamp((value - min) / (double) (max - min), 0.0D, 1.0D);
    }

    public void showWorkCandidates(BlockPos roomPos, WorkType workType,
                                   List<RoomEmployeeSnapshot> candidates, String error) {
        if (selectedRoom == null || !selectedRoom.pos.equals(roomPos)
                || selectedWork < 0 || selectedWork >= WorkType.values().length
                || WorkType.values()[selectedWork] != workType) return;
        String preferredDepartment = roomDepartmentId(selectedRoom);
        List<RoomEmployeeSnapshot> ordered = new ArrayList<>(candidates == null ? List.of() : candidates);
        ordered.sort(Comparator
                .comparingInt((RoomEmployeeSnapshot candidate) ->
                        candidateDepartmentRank(candidate.departmentId()))
                .thenComparing(RoomEmployeeSnapshot::departmentId,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(RoomEmployeeSnapshot::name, String.CASE_INSENSITIVE_ORDER));
        workCandidates = List.copyOf(ordered);
        rebuildCandidatePages(preferredDepartment);
        candidatesLoading = false;
        candidateError = error == null ? "" : error;
    }

    public void showSuppressionCandidates(BlockPos roomPos,
                                          List<RoomEmployeeSnapshot> candidates, String error) {
        if (selectedRoom == null || !selectedRoom.pos.equals(roomPos) || !isSuppressionSelection()) return;
        String preferredDepartment = roomDepartmentId(selectedRoom);
        List<RoomEmployeeSnapshot> ordered = new ArrayList<>(candidates == null ? List.of() : candidates);
        ordered.sort(Comparator
                .comparingInt((RoomEmployeeSnapshot candidate) ->
                        candidateDepartmentRank(candidate.departmentId()))
                .thenComparing(RoomEmployeeSnapshot::departmentId,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(RoomEmployeeSnapshot::name, String.CASE_INSENSITIVE_ORDER));
        workCandidates = List.copyOf(ordered);
        rebuildCandidatePages(preferredDepartment);
        candidatesLoading = false;
        candidateError = error == null ? "" : error;
    }

    public void onWorkAssignmentResult(UUID employeeId, boolean success, String message) {
        assignmentSucceeded = success;
        assignmentMessage = message == null ? "" : message;
        assignmentMessageAt = Util.getMillis();
        if (success) {
            selectedRoom = null;
            selectedWork = -1;
            workCandidates = List.of();
            candidatePages = List.of();
            playUiSound(1.35F);
        } else {
            playUiSound(0.55F);
            if (this.minecraft != null && this.minecraft.player != null && !assignmentMessage.isEmpty()) {
                this.minecraft.player.displayClientMessage(Component.literal("§c" + assignmentMessage), true);
            }
        }
    }

    public void onSuppressionAssignmentResult(UUID employeeId, boolean success, String message) {
        assignmentSucceeded = success;
        assignmentMessage = message == null ? "" : message;
        assignmentMessageAt = Util.getMillis();
        playUiSound(success ? 1.25F : 0.55F);
        if (!success && this.minecraft != null && this.minecraft.player != null && !assignmentMessage.isEmpty()) {
            this.minecraft.player.displayClientMessage(Component.literal("§c" + assignmentMessage), true);
        }
        if (selectedRoom != null && isSuppressionSelection()) requestSuppressionCandidates();
    }

    private boolean isSuppressionSelection() {
        return selectedWork == SUPPRESSION_SELECTION;
    }

    private void beginSuppressionSelection(RoomEntry room) {
        selectedRoom = room;
        selectedWork = SUPPRESSION_SELECTION;
        workSelectedAt = Util.getMillis();
        workCandidates = List.of();
        candidatePages = List.of();
        candidatePage = 0;
        candidateError = "";
        candidatesLoading = true;
        requestSuppressionCandidates();
    }

    private void requestSuppressionCandidates() {
        if (selectedRoom == null) return;
        MessageLoader.getLoader().sendToServer(new RequestRoomSuppressionCandidatesPacket(
                selectedRoom.dimension, selectedRoom.pos));
    }

    private void rebuildCandidatePages(String preferredDepartment) {
        Set<String> departmentIds = new HashSet<>();
        for (FacilityLayoutTemplate.Area area : facilityLayout.allAreas()) {
            if (area.role() == FacilityLayoutTemplate.AreaRole.DEPARTMENT_BACKGROUND
                    && facilityVisibility.isVisible(area)) {
                departmentIds.add(area.departmentId());
            }
        }
        for (RoomEmployeeSnapshot candidate : workCandidates) {
            if (candidate.departmentId() != null && !candidate.departmentId().isBlank()) {
                departmentIds.add(candidate.departmentId());
            }
        }
        if (preferredDepartment != null && !preferredDepartment.isBlank()) {
            departmentIds.add(preferredDepartment);
        }
        List<String> departments = new ArrayList<>(departmentIds);
        departments.sort(Comparator
                .comparingInt(RoomMapScreen::candidateDepartmentRank)
                .thenComparing(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        List<CandidatePage> pages = new ArrayList<>();
        for (String department : departments) {
            List<RoomEmployeeSnapshot> departmentEmployees = workCandidates.stream()
                    .filter(candidate -> Objects.equals(candidate.departmentId(), department)).toList();
            pages.add(new CandidatePage(department, List.copyOf(departmentEmployees)));
        }
        candidatePages = List.copyOf(pages);
        candidatePage = 0;
        for (int index = 0; index < candidatePages.size(); index++) {
            if (Objects.equals(candidatePages.get(index).departmentId(), preferredDepartment)) {
                candidatePage = index;
                break;
            }
        }
    }

    static int candidateDepartmentRank(String departmentId) {
        EmployeeDepartment department = EmployeeDepartment.byId(departmentId);
        int rank = CANDIDATE_DEPARTMENT_ORDER.indexOf(department);
        return rank >= 0 ? rank : CANDIDATE_DEPARTMENT_ORDER.size();
    }

    static int moveCandidatePage(int current, int pageCount, int delta) {
        if (pageCount <= 0) return 0;
        int direction = Integer.compare(delta, 0);
        return Math.max(0, Math.min(pageCount - 1, current + direction));
    }

    private double mapCenterX() {
        return mapLeft() + (this.width - mapLeft()) / 2.0D;
    }

    private double mapCenterY() {
        return HEADER_H + (this.height - HEADER_H) / 2.0D;
    }

    private double worldToScreenX(double worldX) {
        return (worldX - centerWorldX) * zoom + mapCenterX() + panX;
    }

    private double worldToScreenY(double worldZ) {
        return (worldZ - centerWorldZ) * zoom + mapCenterY() + panY;
    }

    private double screenToWorldX(double screenX) {
        return (screenX - mapCenterX() - panX) / zoom + centerWorldX;
    }

    private double screenToWorldZ(double screenY) {
        return (screenY - mapCenterY() - panY) / zoom + centerWorldZ;
    }

    private static double templateWorldX(double normalizedX) {
        return (normalizedX - 0.5D) * TEMPLATE_WIDTH;
    }

    private static double templateWorldY(double normalizedY) {
        return normalizedY * TEMPLATE_HEIGHT;
    }

    private static double templateNormalizedX(double worldX) {
        return worldX / TEMPLATE_WIDTH + 0.5D;
    }

    private static double templateNormalizedY(double worldY) {
        return worldY / TEMPLATE_HEIGHT;
    }

    /** Stable screen-to-area adapter used by selection and movement dispatch. */
    public Optional<FacilityLayoutTemplate.Area> hitFacilityArea(double screenX, double screenY) {
        if (screenX < mapLeft() || screenY < HEADER_H || screenX > this.width || screenY > this.height) {
            return Optional.empty();
        }
        return facilityLayout.hitTestAll(
                        templateNormalizedX(screenToWorldX(screenX)),
                        templateNormalizedY(screenToWorldZ(screenY))).stream()
                .filter(facilityVisibility::isVisible)
                .findFirst();
    }

    /** Resolves a template area to the synchronized world area bound through groupId. */
    public Optional<FacilityAreaEntry> worldAreaFor(String templateAreaId) {
        for (FacilityAreaEntry area : areas) {
            if (templateAreaId.equals(area.groupId)) return Optional.of(area);
        }
        for (RoomEntry room : rooms) {
            if (!templateAreaId.equals(roomAreaIds.get(room.key()))) continue;
            for (FacilityAreaEntry area : areas) {
                if (area.markerPos.equals(room.pos) && area.dimension != null
                        && area.dimension.equals(room.dimension)) {
                    return Optional.of(area);
                }
            }
        }
        return Optional.empty();
    }

    /** 房间在图上的位置 = 真实坐标 + 手动偏移 */
    private double cardWorldX(RoomEntry entry) {
        LayoutPoint point = roomLayout.get(entry.key());
        return point == null ? 0.0D : point.x();
    }

    private double cardWorldZ(RoomEntry entry) {
        LayoutPoint point = roomLayout.get(entry.key());
        return point == null ? 0.0D : point.y();
    }

    private double nodeScale() {
        return Mth.clamp(zoom, 0.45D, 1.0D);
    }

    private Optional<ScreenBounds> containmentScreenBounds(RoomEntry entry) {
        String areaId = roomAreaIds.get(entry.key());
        if (areaId == null) return Optional.empty();

        FacilityLayoutTemplate.Area area = facilityLayout.areaById(areaId).orElse(null);
        if (area == null || area.role() != FacilityLayoutTemplate.AreaRole.CONTAINMENT_ROOM) {
            return Optional.empty();
        }

        FacilityLayoutTemplate.Bounds bounds = area.bounds();
        int left = (int) Math.round(worldToScreenX(templateWorldX(bounds.minX())));
        int top = (int) Math.round(worldToScreenY(templateWorldY(bounds.minY())));
        int right = (int) Math.round(worldToScreenX(templateWorldX(bounds.maxX())));
        int bottom = (int) Math.round(worldToScreenY(templateWorldY(bounds.maxY())));
        return Optional.of(new ScreenBounds(
                Math.min(left, right), Math.min(top, bottom),
                Math.max(left, right), Math.max(top, bottom)));
    }

    private ScreenBounds roomScreenBounds(RoomEntry entry) {
        Optional<ScreenBounds> containment = containmentScreenBounds(entry);
        if (containment.isPresent()) return containment.get();

        int cx = (int) Math.round(worldToScreenX(cardWorldX(entry)));
        int cy = (int) Math.round(worldToScreenY(cardWorldZ(entry)));
        int scaledW = (int) Math.ceil(CARD_W * nodeScale());
        int scaledH = (int) Math.ceil(CARD_H * nodeScale());
        return new ScreenBounds(cx - scaledW / 2, cy - scaledH / 2,
                cx + (scaledW + 1) / 2, cy + (scaledH + 1) / 2);
    }

    // ------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float alpha = Mth.clamp((Util.getMillis() - openedAt) / 260.0F, 0F, 1F);

        graphics.fill(0, 0, this.width, this.height, withAlpha(COLOR_BG, alpha));
        graphics.enableScissor(mapLeft(), HEADER_H + 1, this.width, this.height - 18);
        this.hoveredFacilityArea = hitFacilityArea(mouseX, mouseY).orElse(null);
        renderFacilityTemplate(graphics, alpha);
        renderMoveCommandPulse(graphics, alpha);

        List<RoomEntry> visible = visibleRooms();

        this.hovered = null;
        boolean pointerOnDetails = pointerOnCommandWindow(mouseX, mouseY);
        if (mouseX >= mapLeft() && mouseY >= HEADER_H && !pointerOnDetails) {
            for (RoomEntry entry : visible) {
                if (roomScreenBounds(entry).contains(mouseX, mouseY)) {
                    this.hovered = entry;
                }
            }
        }

        for (RoomEntry entry : visible) {
            if (entry != hovered) renderCard(graphics, entry, false, alpha);
        }
        if (hovered != null) {
            renderCard(graphics, hovered, true, alpha);
        }
        renderSelectedRoomOutline(graphics, alpha);
        hoveredMapEmployee = null;
        renderEmployeeMarkers(graphics, mouseX, mouseY, alpha);
        if (selectingEmployees) renderEmployeeSelectionBox(graphics, alpha);
        graphics.disableScissor();

        if (showTabs()) {
            renderFloorGutter(graphics, mouseX, mouseY, alpha);
        }
        renderHeader(graphics, mouseX, mouseY, alpha);
        renderFooter(graphics, alpha);
        hoveredWork = -1;
        hoveredCandidate = null;
        if (selectedRoom != null) {
            renderRoomDetails(graphics, mouseX, mouseY, alpha);
            if (selectedWork >= 0) renderCandidatePanel(graphics, mouseX, mouseY, alpha);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (pickerRoom != null) {
            renderPicker(graphics, mouseX, mouseY, alpha);
        } else if (hoveredTt2Button) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("screen.lobotocraft.monitor.tt2.title"),
                    Component.translatable("screen.lobotocraft.monitor.tt2.tooltip")),
                    mouseX, mouseY);
        } else if (hoveredCandidate != null) {
            graphics.renderComponentTooltip(this.font, buildEmployeeTooltip(hoveredCandidate), mouseX, mouseY);
        } else if (hoveredMapEmployee != null) {
            graphics.renderComponentTooltip(this.font, buildEmployeeTooltip(hoveredMapEmployee), mouseX, mouseY);
        } else if (hoveredWork >= 0) {
            graphics.renderTooltip(this.font, Component.literal(
                    CoreSuppressionClientState.isActive(CoreSuppressionType.MALKUTH)
                            ? "???" : tr(WORK_NAME_KEYS[hoveredWork])), mouseX, mouseY);
        } else if (hovered != null) {
            graphics.renderComponentTooltip(this.font, buildTooltip(hovered), mouseX, mouseY);
        } else if (hoveredFacilityArea != null) {
            graphics.renderComponentTooltip(this.font, buildAreaTooltip(hoveredFacilityArea), mouseX, mouseY);
        }
    }

    private ScreenBounds detailsBounds() {
        if (selectedRoom == null) {
            return new ScreenBounds(this.width, DETAILS_TOP, this.width, DETAILS_TOP + DETAILS_H);
        }
        ScreenBounds room = roomScreenBounds(selectedRoom);
        int fullWidth = DETAILS_W + COMMAND_GAP + CANDIDATE_W;
        int left = room.right() + 8;
        if (left + fullWidth > this.width - DETAILS_MARGIN) {
            left = room.left() - fullWidth - 8;
        }
        left = Mth.clamp(left, mapLeft() + 4,
                Math.max(mapLeft() + 4, this.width - DETAILS_MARGIN - fullWidth));
        int fullHeight = Math.max(DETAILS_H, CANDIDATE_H);
        int top = Mth.clamp(room.top() - 18, DETAILS_TOP,
                Math.max(DETAILS_TOP, this.height - 22 - fullHeight));
        return new ScreenBounds(left, top, left + DETAILS_W, top + DETAILS_H);
    }

    private ScreenBounds candidateTargetBounds() {
        ScreenBounds details = detailsBounds();
        int left = details.right() + COMMAND_GAP;
        return new ScreenBounds(left, details.top(), left + CANDIDATE_W, details.top() + CANDIDATE_H);
    }

    private float candidatePanelProgress() {
        if (selectedWork < 0) return 0.0F;
        float raw = Mth.clamp((Util.getMillis() - workSelectedAt) / 180.0F, 0.0F, 1.0F);
        float inverse = 1.0F - raw;
        return 1.0F - inverse * inverse * inverse;
    }

    private ScreenBounds candidateBounds() {
        ScreenBounds target = candidateTargetBounds();
        int offset = Math.round((1.0F - candidatePanelProgress()) * (CANDIDATE_W + COMMAND_GAP));
        return new ScreenBounds(target.left() - offset, target.top(),
                target.right() - offset, target.bottom());
    }

    private boolean pointerOnCommandWindow(double mouseX, double mouseY) {
        if (selectedRoom == null) return false;
        if (detailsBounds().contains(mouseX, mouseY)) return true;
        return selectedWork >= 0 && candidatePanelProgress() > 0.05F
                && candidateBounds().contains(mouseX, mouseY);
    }

    private void renderSelectedRoomOutline(GuiGraphics graphics, float alpha) {
        if (selectedRoom == null) return;
        ScreenBounds bounds = roomScreenBounds(selectedRoom);
        if (bounds.right() < mapLeft() || bounds.left() > this.width
                || bounds.bottom() < HEADER_H || bounds.top() > this.height) return;
        drawBorder(graphics, bounds.left() - 1, bounds.top() - 1,
                bounds.width() + 2, bounds.height() + 2,
                withAlpha(LobotomyUiTheme.LINE_BRIGHT, alpha));
    }

    private void renderEmployeeMarkers(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        for (EmployeeMapMarker marker : employeeMarkers) {
            MarkerScreenPoint point = markerScreenPoint(marker);
            int x = (int) Math.round(point.x());
            int y = (int) Math.round(point.y());
            if (x < mapLeft() + 4 || x > this.width - 4 || y < HEADER_H + 5 || y > this.height - 22) {
                continue;
            }
            boolean selected = selectedEmployees.contains(marker.employeeId());
            int color = marker.departmentId() == null || marker.departmentId().isBlank()
                    ? LobotomyUiTheme.ACCENT
                    : LobotomyUiTheme.departmentColor(marker.departmentId());
            if (selected && marker.commandable()) {
                graphics.fill(x - 5, y - 6, x + 6, y + 7,
                        withAlpha(LobotomyUiTheme.BG_PANEL_SOLID, alpha * 0.92F));
                drawBorder(graphics, x - 5, y - 6, 11, 13,
                        withAlpha(LobotomyUiTheme.LINE_BRIGHT, alpha));
            }
            // Compact employee glyph: head, shoulders and department-colored body.
            graphics.fill(x - 2, y - 5, x + 3, y - 1, withAlpha(color, alpha));
            graphics.fill(x - 4, y, x + 5, y + 2, withAlpha(color, alpha));
            graphics.fill(x - 2, y + 2, x + 3, y + 6, withAlpha(color, alpha));

            RoomEmployeeSnapshot employee = employeeSnapshot(marker.employeeId());
            if (!pointerOnCommandWindow(mouseX, mouseY)
                    && mouseX >= x - 6 && mouseX <= x + 6
                    && mouseY >= y - 7 && mouseY <= y + 7) {
                hoveredMapEmployee = employee;
            }
        }
    }

    private MarkerScreenPoint markerScreenPoint(EmployeeMapMarker marker) {
        double centerX = worldToScreenX(marker.layoutX());
        double centerY = worldToScreenY(marker.layoutY());
        int population = Math.max(1, marker.areaPopulation());
        int columns = Math.min(5, population);
        int rows = (population + columns - 1) / columns;
        int row = marker.areaIndex() / columns;
        int column = marker.areaIndex() % columns;
        int rowStart = row * columns;
        int columnsInRow = Math.min(columns, population - rowStart);
        double offsetX = (column - (columnsInRow - 1) * 0.5D) * 11.0D;
        double offsetY = (row - (rows - 1) * 0.5D) * 14.0D;
        return new MarkerScreenPoint(centerX + offsetX, centerY + offsetY);
    }

    private RoomEmployeeSnapshot employeeSnapshot(UUID employeeId) {
        for (RoomEmployeeSnapshot employee : employees) {
            if (employee.id().equals(employeeId)) return employee;
        }
        return null;
    }

    private void renderEmployeeSelectionBox(GuiGraphics graphics, float alpha) {
        int left = (int) Math.max(mapLeft(), Math.min(selectionStartX, selectionEndX));
        int right = (int) Math.min(this.width, Math.max(selectionStartX, selectionEndX));
        int top = (int) Math.max(HEADER_H + 1, Math.min(selectionStartY, selectionEndY));
        int bottom = (int) Math.min(this.height - 18, Math.max(selectionStartY, selectionEndY));
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, bottom, withAlpha(LobotomyUiTheme.ACCENT, alpha * 0.10F));
        drawBorder(graphics, left, top, right - left, bottom - top,
                withAlpha(LobotomyUiTheme.LINE_ACCENT, alpha));
    }

    private void renderMoveCommandPulse(GuiGraphics graphics, float alpha) {
        if (movePulseGroupId == null) return;
        long elapsed = Util.getMillis() - movePulseAt;
        if (elapsed >= 650L) {
            movePulseGroupId = null;
            return;
        }
        FacilityLayoutTemplate.Area area = facilityLayout.areaById(movePulseGroupId).orElse(null);
        if (area == null) return;
        FacilityLayoutTemplate.Bounds source = area.bounds();
        int left = (int) Math.round(worldToScreenX(templateWorldX(source.minX())));
        int right = (int) Math.round(worldToScreenX(templateWorldX(source.maxX())));
        int top = (int) Math.round(worldToScreenY(templateWorldY(source.minY())));
        int bottom = (int) Math.round(worldToScreenY(templateWorldY(source.maxY())));
        float progress = elapsed / 650.0F;
        int expand = 1 + Math.round(progress * 6.0F);
        int pulseAlpha = withAlpha(LobotomyUiTheme.LINE_BRIGHT,
                alpha * (1.0F - progress) * (1.0F - progress));
        drawBorder(graphics, Math.min(left, right) - expand, Math.min(top, bottom) - expand,
                Math.abs(right - left) + expand * 2, Math.abs(bottom - top) + expand * 2, pulseAlpha);
    }

    private void renderRoomDetails(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        ScreenBounds panel = detailsBounds();
        int left = panel.left();
        int top = panel.top();
        int right = panel.right();
        int department = roomDepartmentColor(selectedRoom);

        graphics.fill(left, top, right, panel.bottom(), withAlpha(LobotomyUiTheme.BG_PANEL, alpha));
        drawBorder(graphics, left, top, panel.width(), panel.height(),
                withAlpha(LobotomyUiTheme.LINE_MID, alpha));
        graphics.fill(left, top, right, top + 2, withAlpha(department, alpha));

        int closeX = right - 15;
        int closeY = top + 8;
        int closeColor = mouseX >= closeX - 3 && mouseX < right - 3
                && mouseY >= closeY - 3 && mouseY < closeY + 10
                ? LobotomyUiTheme.TEXT_TITLE : LobotomyUiTheme.TEXT_MUTED;
        graphics.fill(closeX, closeY, closeX + 1, closeY + 1, withAlpha(closeColor, alpha));
        graphics.fill(closeX + 6, closeY, closeX + 7, closeY + 1, withAlpha(closeColor, alpha));
        for (int i = 1; i < 6; i++) {
            graphics.fill(closeX + i, closeY + i, closeX + i + 1, closeY + i + 1,
                    withAlpha(closeColor, alpha));
            graphics.fill(closeX + 6 - i, closeY + i, closeX + 7 - i, closeY + i + 1,
                    withAlpha(closeColor, alpha));
        }

        int portraitX = left + 8;
        int portraitY = top + 14;
        int portraitSize = 42;
        graphics.fill(portraitX, portraitY, portraitX + portraitSize, portraitY + portraitSize,
                withAlpha(LobotomyUiTheme.BG_INSET, alpha));
        drawBorder(graphics, portraitX, portraitY, portraitSize, portraitSize,
                withAlpha(department, alpha * 0.78F));
        if (!selectedRoom.abnormalityCode.isEmpty()) {
            RenderSystem.enableBlend();
            graphics.setColor(1F, 1F, 1F, alpha);
            graphics.blit(resolvePortrait(selectedRoom.abnormalityCode), portraitX + 1, portraitY + 1,
                    0, 0, portraitSize - 2, portraitSize - 2, portraitSize - 2, portraitSize - 2);
            graphics.setColor(1F, 1F, 1F, 1F);
        } else {
            drawCentered(graphics, "-", portraitX + portraitSize / 2,
                    portraitY + 19, withAlpha(COLOR_TEXT_DIM, alpha));
        }

        int textX = portraitX + portraitSize + 6;
        int textW = Math.max(24, right - textX - 8);
        drawFittedString(graphics, selectedRoom.resolveTitle(), textX, portraitY,
                textW, withAlpha(COLOR_TEXT, alpha));
        drawFittedString(graphics, selectedRoom.abnormalityCode, textX,
                portraitY + 12, textW, withAlpha(COLOR_TEXT_DIM, alpha));
        if (!selectedRoom.riskName.isEmpty()) {
            drawFittedString(graphics, selectedRoom.riskName, textX,
                    portraitY + 24, textW, withAlpha(selectedRoom.riskColor, alpha));
        }
        drawFittedString(graphics, roomStatusText(selectedRoom), textX,
                portraitY + 36, textW, withAlpha(statusColor(selectedRoom), alpha));

        int barTop = portraitY + portraitSize + 6;
        int barLeft = left + 8;
        int barRight = right - 8;
        graphics.fill(barLeft, barTop, barRight, barTop + 3,
                withAlpha(LobotomyUiTheme.BAR_TRACK, alpha));
        if (hasActiveOccupant(selectedRoom)) {
            int hpRight = barLeft + (int) ((barRight - barLeft)
                    * Mth.clamp(selectedRoom.healthPercent, 0F, 1F));
            graphics.fill(barLeft, barTop, hpRight, barTop + 3,
                    withAlpha(statusColor(selectedRoom), alpha));
        }
        String hp = !hasActiveOccupant(selectedRoom)
                ? "--" : Math.round(selectedRoom.healthPercent * 100) + "%";
        graphics.drawString(this.font, hp, barRight - this.font.width(hp), barTop + 6,
                withAlpha(COLOR_TEXT_DIM, alpha), false);

        if (selectedRoom.maxQliphoth > 0) {
            String qliphoth = "Q " + selectedRoom.qliphoth + " / " + selectedRoom.maxQliphoth;
            graphics.drawString(this.font, qliphoth, barLeft, barTop + 6,
                    withAlpha(LobotomyUiTheme.GOLD, alpha), false);
        }

        int dividerY = barTop + 20;
        graphics.fill(left + 8, dividerY, right - 8, dividerY + 1,
                withAlpha(LobotomyUiTheme.LINE_FAINT, alpha));
        graphics.drawString(this.font, tr(isSuppressionSelection()
                        ? "screen.lobotocraft.monitor.dispatch.suppression"
                        : selectedRoom.toolType
                        ? "screen.lobotocraft.monitor.dispatch.tool"
                        : "screen.lobotocraft.monitor.dispatch.work_type"), left + 8, dividerY + 8,
                withAlpha(COLOR_TEXT_DIM, alpha), false);

        if (selectedRoom.status == RoomEntry.STATUS_UNEXPANDED) {
            drawCentered(graphics, tr("screen.lobotocraft.monitor.room.unexpanded"), left + panel.width() / 2,
                    dividerY + 34, withAlpha(STATUS_UNEXPANDED, alpha));
            return;
        }

        if (selectedRoom.toolType) {
            drawCentered(graphics, tr("screen.lobotocraft.monitor.select_tool_employee"), left + panel.width() / 2,
                    dividerY + 35, withAlpha(LobotomyUiTheme.GOLD, alpha));
            return;
        }

        if (isSuppressionSelection()) {
            drawCentered(graphics, tr("screen.lobotocraft.monitor.select_suppression_employee"),
                    left + panel.width() / 2,
                    dividerY + 35, withAlpha(LobotomyUiTheme.ALERT_RED, alpha));
            return;
        }

        int iconsTop = dividerY + 22;
        int iconsWidth = WORK_ICON_SIZE * 2 + WORK_ICON_GAP;
        int iconsLeft = left + (panel.width() - iconsWidth) / 2;
        for (int i = 0; i < WORK_ICONS.length; i++) {
            int iconX = iconsLeft + (i % 2) * (WORK_ICON_SIZE + WORK_ICON_GAP);
            int iconY = iconsTop + (i / 2) * (WORK_ICON_SIZE + WORK_ICON_GAP);
            boolean iconHovered = mouseX >= iconX && mouseX < iconX + WORK_ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + WORK_ICON_SIZE;
            if (iconHovered) hoveredWork = i;
            if (selectedWork == i || iconHovered) {
                graphics.fill(iconX - 2, iconY - 2,
                        iconX + WORK_ICON_SIZE + 2, iconY + WORK_ICON_SIZE + 2,
                        withAlpha(selectedWork == i ? LobotomyUiTheme.GOLD
                                : LobotomyUiTheme.BG_ROW_HOVER, selectedWork == i ? alpha * 0.28F : alpha));
                drawBorder(graphics, iconX - 2, iconY - 2,
                        WORK_ICON_SIZE + 4, WORK_ICON_SIZE + 4,
                        withAlpha(selectedWork == i
                                ? LobotomyUiTheme.GOLD : LobotomyUiTheme.LINE_MID, alpha));
            }
            RenderSystem.enableBlend();
            graphics.setColor(1F, 1F, 1F, alpha);
            if (CoreSuppressionClientState.isActive(CoreSuppressionType.MALKUTH)) {
                graphics.pose().pushPose();
                graphics.pose().translate(iconX, iconY, 0.0F);
                graphics.pose().scale(WORK_ICON_SIZE / 136.0F, WORK_ICON_SIZE / 184.0F, 1.0F);
                graphics.blit(MALKUTH_UNKNOWN_WORK, 0, 0, 0, 0,
                        136, 184, 136, 184);
                graphics.pose().popPose();
            } else {
                graphics.blit(WORK_ICONS[i], iconX, iconY, 0, 0,
                        WORK_ICON_SIZE, WORK_ICON_SIZE, WORK_ICON_SIZE, WORK_ICON_SIZE);
            }
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }

    private void renderCandidatePanel(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        float progress = candidatePanelProgress();
        if (progress <= 0.001F) return;
        ScreenBounds panel = candidateBounds();
        ScreenBounds target = candidateTargetBounds();
        int department = roomDepartmentColor(selectedRoom);
        float panelAlpha = alpha * progress;

        graphics.enableScissor(target.left(), target.top(), target.right(), target.bottom());
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(),
                withAlpha(LobotomyUiTheme.BG_PANEL, panelAlpha));
        drawBorder(graphics, panel.left(), panel.top(), panel.width(), panel.height(),
                withAlpha(LobotomyUiTheme.LINE_MID, panelAlpha));
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.top() + 2,
                withAlpha(department, panelAlpha));

        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, panelAlpha);
        graphics.blit(DEPARTMENT_HEADER, panel.left() + 2, panel.top() + 3, 0, 0,
                panel.width() - 4, 23, 203, 38);
        graphics.setColor(1F, 1F, 1F, 1F);

        String employeeState = tr("screen.lobotocraft.monitor.employees");
        if (!candidatePages.isEmpty()) {
            CandidatePage page = candidatePages.get(Mth.clamp(candidatePage, 0, candidatePages.size() - 1));
            employeeState = departmentName(page.departmentId());
        }
        drawCentered(graphics, employeeState, panel.left() + panel.width() / 2,
                panel.top() + 9, withAlpha(COLOR_TEXT, panelAlpha));

        renderWorkCandidates(graphics, panel, mouseX, mouseY, panelAlpha);
        graphics.disableScissor();
    }

    private void renderWorkCandidates(GuiGraphics graphics, ScreenBounds panel,
                                      int mouseX, int mouseY, float alpha) {
        if (selectedWork < 0) return;
        int rowsTop = candidateRowsTop(panel);
        int available = panel.bottom() - 8 - rowsTop;
        int visibleRows = Math.min(CANDIDATES_PER_PAGE, Math.max(0, available / CANDIDATE_ROW_H));

        if (visibleRows <= 0) return;
        if (candidatesLoading) {
            graphics.drawString(this.font, "...", panel.left() + 12, rowsTop + 8,
                    withAlpha(COLOR_TEXT_DIM, alpha), false);
            return;
        }
        if (!candidateError.isEmpty()) {
            drawFittedString(graphics, candidateError, panel.left() + 12, rowsTop + 8,
                    panel.width() - 24, withAlpha(LobotomyUiTheme.ALERT_RED, alpha));
            return;
        }
        candidatePage = Mth.clamp(candidatePage, 0, Math.max(0, candidatePages.size() - 1));
        CandidatePage page = candidatePages.isEmpty() ? null : candidatePages.get(candidatePage);
        if (page == null || page.employees().isEmpty()) {
            graphics.drawString(this.font, "--", panel.left() + 12, rowsTop + 8,
                    withAlpha(LobotomyUiTheme.TEXT_DISABLED, alpha), false);
            if (candidatePages.size() > 1) {
                renderCandidatePageArrows(graphics, panel, mouseX, mouseY, alpha);
            }
            return;
        }
        int rowLeft = panel.left() + 10;
        int rowRight = panel.right() - 10;
        for (int row = 0; row < Math.min(visibleRows, page.employees().size()); row++) {
            RoomEmployeeSnapshot candidate = page.employees().get(row);
            int y = rowsTop + row * CANDIDATE_ROW_H;
            boolean hovered = mouseX >= rowLeft && mouseX < rowRight
                    && mouseY >= y && mouseY < y + CANDIDATE_ROW_H;
            if (hovered) hoveredCandidate = candidate;
            int bg = (row & 1) == 0 ? LobotomyUiTheme.BG_ROW_EVEN : LobotomyUiTheme.BG_ROW_ODD;
            graphics.fill(rowLeft, y, rowRight, y + CANDIDATE_ROW_H - 1, withAlpha(bg, alpha));
            if (hovered) {
                graphics.fill(rowLeft, y, rowRight, y + CANDIDATE_ROW_H - 1,
                        withAlpha(LobotomyUiTheme.BG_ROW_HOVER, alpha));
            }
            int department = candidate.departmentId() == null || candidate.departmentId().isBlank()
                    ? LobotomyUiTheme.ACCENT
                    : LobotomyUiTheme.departmentColor(candidate.departmentId());
            graphics.fill(rowLeft, y, rowLeft + 2, y + CANDIDATE_ROW_H - 1,
                    withAlpha(candidate.eligible() ? department : LobotomyUiTheme.TEXT_DISABLED, alpha));

            int textColor = candidate.eligible() ? COLOR_TEXT : LobotomyUiTheme.TEXT_DISABLED;
            drawFittedString(graphics, candidate.name(), rowLeft + 7, y + 4,
                    rowRight - rowLeft - 56, withAlpha(textColor, alpha));
            String level = "LV " + candidate.employeeLevel();
            graphics.drawString(this.font, level, rowRight - this.font.width(level) - 5, y + 4,
                    withAlpha(candidate.eligible() ? department : LobotomyUiTheme.TEXT_DISABLED, alpha), false);

            int barLeft = rowLeft + 7;
            int barRight = rowLeft + 68;
            int hpY = y + 17;
            graphics.fill(barLeft, hpY, barRight, hpY + 2,
                    withAlpha(LobotomyUiTheme.BAR_TRACK, alpha));
            graphics.fill(barLeft, hpY,
                    barLeft + (int) ((barRight - barLeft) * ratio(candidate.health(), candidate.maxHealth())),
                    hpY + 2, withAlpha(LobotomyUiTheme.BAR_HP, alpha));
            graphics.fill(barLeft, hpY + 5, barRight, hpY + 7,
                    withAlpha(LobotomyUiTheme.BAR_TRACK, alpha));
            graphics.fill(barLeft, hpY + 5,
                    barLeft + (int) ((barRight - barLeft) * ratio(candidate.mental(), candidate.maxMental())),
                    hpY + 7, withAlpha(LobotomyUiTheme.BAR_SP, alpha));

            String preview = candidate.eligible()
                    ? isSuppressionSelection() ? tr("screen.lobotocraft.monitor.dispatch.available")
                    : candidate.successRate() < 0.0F ? "???"
                    : Math.round(candidate.successRate() * 100.0F) + "%  " + candidate.estimatedSeconds() + "s"
                    : candidate.unavailableReason();
            drawFittedString(graphics, preview, barRight + 8, y + 18,
                    rowRight - barRight - 13,
                    withAlpha(candidate.eligible() ? COLOR_TEXT_DIM : LobotomyUiTheme.ALERT_RED, alpha));

            int[] stats = {candidate.fortitude(), candidate.prudence(),
                    candidate.temperance(), candidate.justice()};
            int statX = rowLeft + 7;
            for (int stat = 0; stat < stats.length; stat++) {
                RenderSystem.enableBlend();
                graphics.setColor(1F, 1F, 1F, alpha);
                graphics.blit(STAT_ICONS[stat], statX, y + 29, 0, 0, 8, 8, 8, 8);
                graphics.setColor(1F, 1F, 1F, 1F);
                String value = Integer.toString(stats[stat]);
                graphics.drawString(this.font, value, statX + 9, y + 29,
                        withAlpha(COLOR_TEXT_DIM, alpha), false);
                statX += 34;
            }
        }

        if (candidatePages.size() > 1) {
            renderCandidatePageArrows(graphics, panel, mouseX, mouseY, alpha);
        }
    }

    private void renderCandidatePageArrows(GuiGraphics graphics, ScreenBounds panel,
                                           int mouseX, int mouseY, float alpha) {
        int y = panel.top() + 8;
        int previousX = panel.left() + 7;
        int nextX = panel.right() - 17;
        if (candidatePage > 0) {
            renderPageArrow(graphics, previousX, y,
                    mouseX >= previousX && mouseX < previousX + 10
                            && mouseY >= y && mouseY < y + 10, true, alpha);
        }
        if (candidatePage + 1 < candidatePages.size()) {
            renderPageArrow(graphics, nextX, y,
                    mouseX >= nextX && mouseX < nextX + 10
                            && mouseY >= y && mouseY < y + 10, false, alpha);
        }
    }

    private void renderPageArrow(GuiGraphics graphics, int x, int y, boolean hovered,
                                 boolean mirror, float alpha) {
        ResourceLocation texture = hovered ? PAGE_ARROW_HOVER : PAGE_ARROW;
        int source = hovered ? 32 : 25;
        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, alpha);
        graphics.pose().pushPose();
        if (mirror) {
            graphics.pose().translate(x + 10.0F, y, 0.0F);
            graphics.pose().scale(-1.0F, 1.0F, 1.0F);
            graphics.blit(texture, 0, 0, 0, 0, 10, 10, source, source);
        } else {
            graphics.blit(texture, x, y, 0, 0, 10, 10, source, source);
        }
        graphics.pose().popPose();
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    private int candidateRowsTop(ScreenBounds panel) {
        return panel.top() + 28;
    }

    private static float ratio(float value, float maximum) {
        return maximum <= 0.0F ? 0.0F : Mth.clamp(value / maximum, 0.0F, 1.0F);
    }

    private int detailsWorkIconAt(double mouseX, double mouseY) {
        if (selectedRoom == null || selectedRoom.toolType || selectedRoom.status == RoomEntry.STATUS_UNEXPANDED
                || selectedRoom.status == RoomEntry.STATUS_ESCAPED || isSuppressionSelection()) return -1;
        ScreenBounds panel = detailsBounds();
        int barTop = panel.top() + 14 + 42 + 6;
        int dividerY = barTop + 20;
        int iconsTop = dividerY + 22;
        int iconsWidth = WORK_ICON_SIZE * 2 + WORK_ICON_GAP;
        int iconsLeft = panel.left() + (panel.width() - iconsWidth) / 2;
        for (int i = 0; i < WORK_ICONS.length; i++) {
            int iconX = iconsLeft + (i % 2) * (WORK_ICON_SIZE + WORK_ICON_GAP);
            int iconY = iconsTop + (i / 2) * (WORK_ICON_SIZE + WORK_ICON_GAP);
            if (mouseX >= iconX && mouseX < iconX + WORK_ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + WORK_ICON_SIZE) return i;
        }
        return -1;
    }

    private int candidatePageDeltaAt(double mouseX, double mouseY) {
        if (candidatePages.size() <= 1) return 0;
        ScreenBounds panel = candidateBounds();
        int y = panel.top() + 6;
        if (mouseY < y || mouseY >= y + 14) return 0;
        if (candidatePage > 0
                && mouseX >= panel.left() + 4 && mouseX < panel.left() + 20) return -1;
        if (candidatePage + 1 < candidatePages.size()
                && mouseX >= panel.right() - 20 && mouseX < panel.right() - 4) return 1;
        return 0;
    }

    private RoomEmployeeSnapshot candidateAt(double mouseX, double mouseY) {
        if (candidatePages.isEmpty()) return null;
        ScreenBounds panel = candidateBounds();
        int rowsTop = candidateRowsTop(panel);
        int row = (int) ((mouseY - rowsTop) / CANDIDATE_ROW_H);
        if (mouseY < rowsTop || row < 0 || row >= CANDIDATES_PER_PAGE) return null;
        CandidatePage page = candidatePages.get(Mth.clamp(candidatePage, 0, candidatePages.size() - 1));
        if (row >= page.employees().size()) return null;
        return mouseX >= panel.left() + 10 && mouseX < panel.right() - 10
                ? page.employees().get(row) : null;
    }

    private boolean detailsCloseAt(double mouseX, double mouseY) {
        ScreenBounds panel = detailsBounds();
        return mouseX >= panel.right() - 18 && mouseX < panel.right() - 3
                && mouseY >= panel.top() + 5 && mouseY < panel.top() + 20;
    }

    private EmployeeMapMarker employeeMarkerAt(double mouseX, double mouseY) {
        for (int i = employeeMarkers.size() - 1; i >= 0; i--) {
            EmployeeMapMarker marker = employeeMarkers.get(i);
            MarkerScreenPoint point = markerScreenPoint(marker);
            if (Math.abs(mouseX - point.x()) <= 6.0D
                    && Math.abs(mouseY - point.y()) <= 7.0D) return marker;
        }
        return null;
    }

    private void finishEmployeeBoxSelection() {
        double left = Math.min(selectionStartX, selectionEndX);
        double right = Math.max(selectionStartX, selectionEndX);
        double top = Math.min(selectionStartY, selectionEndY);
        double bottom = Math.max(selectionStartY, selectionEndY);
        if (!hasShiftDown()) selectedEmployees.clear();
        for (EmployeeMapMarker marker : employeeMarkers) {
            MarkerScreenPoint point = markerScreenPoint(marker);
            if (point.x() >= left && point.x() <= right
                    && point.y() >= top && point.y() <= bottom) {
                if (marker.commandable()) selectedEmployees.add(marker.employeeId());
            }
        }
        notifyEmployeeSelectionChanged();
    }

    private void notifyEmployeeSelectionChanged() {
        employeeSelectionListener.accept(Set.copyOf(selectedEmployees));
    }

    // ------------------------------------------------------------ 更改住户选单

    /** 收到服务端送来的候选清单后由网络包调用 */
    public void showAbnormalityPicker(BlockPos roomPos, List<AbnormalityCandidate> candidates) {
        for (RoomEntry entry : rooms) {
            if (entry.pos.equals(roomPos)) {
                if (entry.status == RoomEntry.STATUS_UNEXPANDED) return;
                this.pickerRoom = entry;
                this.pickerCandidates = candidates;
                this.pickerScroll = 0;
                return;
            }
        }
    }

    private void closePicker() {
        pickerRoom = null;
        pickerCandidates = null;
        pickerScroll = 0;
    }

    private int pickerRowCount() {
        return pickerCandidates == null ? 0 : pickerCandidates.size();
    }

    private int pickerVisibleRows() {
        return Math.min(PICKER_MAX_ROWS, Math.max(1, pickerRowCount()));
    }

    private int pickerHeight() {
        return PICKER_HEADER_H + pickerVisibleRows() * PICKER_ROW_H + PICKER_FOOTER_H;
    }

    private int pickerLeft() {
        return (this.width - PICKER_W) / 2;
    }

    private int pickerTop() {
        return (this.height - pickerHeight()) / 2;
    }

    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        // 压暗背景，明确这是个模态选单
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);

        int left = pickerLeft();
        int top = pickerTop();
        int height = pickerHeight();

        graphics.fill(left, top, left + PICKER_W, top + height, 0xF00C1220);
        drawBorder(graphics, left, top, PICKER_W, height, 0xFF5FA8D3);

        // 标题
        graphics.fill(left, top, left + PICKER_W, top + PICKER_HEADER_H, 0xFF16324A);
        String title = tr("screen.lobotocraft.monitor.picker.title",
                this.font.plainSubstrByWidth(pickerRoom.resolveTitle(), 180));
        graphics.drawString(this.font, title, left + 10, top + 9, COLOR_TEXT, false);

        int rowsTop = top + PICKER_HEADER_H;

        if (pickerRowCount() == 0) {
            graphics.drawString(this.font, tr("screen.lobotocraft.monitor.picker.empty"),
                    left + 10, rowsTop + 11, COLOR_TEXT_DIM, false);
        }

        int visible = pickerVisibleRows();
        for (int i = 0; i < visible; i++) {
            int index = i + pickerScroll;
            if (index >= pickerRowCount()) break;
            AbnormalityCandidate candidate = pickerCandidates.get(index);

            int rowTop = rowsTop + i * PICKER_ROW_H;
            boolean hover = mouseX >= left && mouseX <= left + PICKER_W
                    && mouseY >= rowTop && mouseY < rowTop + PICKER_ROW_H;

            if (candidate.alreadyBound) {
                graphics.fill(left + 1, rowTop, left + PICKER_W - 1, rowTop + PICKER_ROW_H, 0x4034D399);
            } else if (hover) {
                graphics.fill(left + 1, rowTop, left + PICKER_W - 1, rowTop + PICKER_ROW_H, 0xFF142236);
            }

            // 立绘
            int px = left + 5;
            int py = rowTop + 4;
            graphics.fill(px, py, px + 22, py + 22, 0xFF05080E);
            if (!candidate.code.isEmpty()) {
                RenderSystem.enableBlend();
                graphics.blit(resolvePortrait(candidate.code), px, py, 0, 0, 22, 22, 22, 22);
            }

            String name = this.font.plainSubstrByWidth(candidate.name, PICKER_W - 50);
            graphics.drawString(this.font, name, left + 33, rowTop + 4, COLOR_TEXT, false);

            int subX = left + 33;
            graphics.drawString(this.font, candidate.code, subX, rowTop + 16, COLOR_TEXT_DIM, false);
            subX += this.font.width(candidate.code) + 6;
            if (!candidate.riskName.isEmpty()) {
                graphics.drawString(this.font, candidate.riskName, subX, rowTop + 16,
                        candidate.riskColor, false);
                subX += this.font.width(candidate.riskName) + 6;
            }
            String where = candidate.inRoom
                    ? tr("screen.lobotocraft.monitor.picker.in_room")
                    : tr("screen.lobotocraft.monitor.picker.distance", candidate.distance);
            graphics.drawString(this.font, where, subX, rowTop + 16,
                    candidate.inRoom ? STATUS_OCCUPIED : COLOR_TEXT_DIM, false);

            if (candidate.alreadyBound) {
                String tag = tr("screen.lobotocraft.monitor.picker.current");
                graphics.drawString(this.font, tag,
                        left + PICKER_W - this.font.width(tag) - 8, rowTop + 10, STATUS_OCCUPIED, false);
            }
        }

        // 滚动提示
        if (pickerRowCount() > PICKER_MAX_ROWS) {
            String more = (pickerScroll + visible) + " / " + pickerRowCount();
            graphics.drawString(this.font, more,
                    left + PICKER_W - this.font.width(more) - 8, top + 9, COLOR_TEXT_DIM, false);
        }

        // 底部按钮
        int footTop = rowsTop + visible * PICKER_ROW_H;
        graphics.fill(left, footTop, left + PICKER_W, footTop + PICKER_FOOTER_H, 0xFF0A1220);

        drawPickerButton(graphics, left + 8, footTop + 4, 120, 20,
                tr("screen.lobotocraft.monitor.picker.clear"), mouseX, mouseY, 0xFFD9A036);
        drawPickerButton(graphics, left + PICKER_W - 88, footTop + 4, 80, 20,
                tr("screen.lobotocraft.common.cancel"), mouseX, mouseY, COLOR_TEXT_DIM);
    }

    private void drawPickerButton(GuiGraphics graphics, int x, int y, int w, int h,
                                  String label, int mouseX, int mouseY, int color) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF1B2E44 : 0xFF111A28);
        drawBorder(graphics, x, y, w, h, color);
        graphics.drawString(this.font, label,
                x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private boolean pickerMouseClicked(double mouseX, double mouseY, int button) {
        int left = pickerLeft();
        int top = pickerTop();
        int height = pickerHeight();

        if (mouseX < left || mouseX > left + PICKER_W || mouseY < top || mouseY > top + height) {
            closePicker();
            return true;
        }
        if (button != 0) return true;

        int rowsTop = top + PICKER_HEADER_H;
        int visible = pickerVisibleRows();
        int footTop = rowsTop + visible * PICKER_ROW_H;

        // 行
        if (mouseY >= rowsTop && mouseY < footTop) {
            int i = (int) ((mouseY - rowsTop) / PICKER_ROW_H) + pickerScroll;
            if (i >= 0 && i < pickerRowCount()) {
                AbnormalityCandidate candidate = pickerCandidates.get(i);
                if (!candidate.alreadyBound) {
                    MessageLoader.getLoader().sendToServer(new BindRoomAbnormalityPacket(
                            pickerRoom.dimension, pickerRoom.pos, candidate.uuid));
                }
                closePicker();
            }
            return true;
        }

        // 清空登记
        if (mouseY >= footTop + 4 && mouseY <= footTop + 24) {
            if (mouseX >= left + 8 && mouseX <= left + 128) {
                MessageLoader.getLoader().sendToServer(new BindRoomAbnormalityPacket(
                        pickerRoom.dimension, pickerRoom.pos, (UUID) null));
                closePicker();
                return true;
            }
            if (mouseX >= left + PICKER_W - 88 && mouseX <= left + PICKER_W - 8) {
                closePicker();
                return true;
            }
        }
        return true;
    }

    private void renderFacilityTemplate(GuiGraphics graphics, float alpha) {
        for (FacilityLayoutTemplate.Area area : facilityLayout.allAreas()) {
            if (!facilityVisibility.isVisible(area)) continue;
            renderFacilityArea(graphics, area, area.equals(hoveredFacilityArea), alpha);
        }
    }

    private void renderFacilityArea(GuiGraphics graphics, FacilityLayoutTemplate.Area area,
                                    boolean hoveredArea, float alpha) {
        FacilityLayoutTemplate.Bounds bounds = area.bounds();
        int left = (int) Math.round(worldToScreenX(templateWorldX(bounds.minX())));
        int top = (int) Math.round(worldToScreenY(templateWorldY(bounds.minY())));
        int right = (int) Math.round(worldToScreenX(templateWorldX(bounds.maxX())));
        int bottom = (int) Math.round(worldToScreenY(templateWorldY(bounds.maxY())));
        if (right < mapLeft() || left > this.width || bottom < HEADER_H || top > this.height) return;

        int width = Math.max(1, right - left);
        int height = Math.max(1, bottom - top);
        int color = LobotomyUiTheme.departmentColor(area.departmentId());
        boolean danger = dangerousAreaIds.contains(area.areaId());
        float fillAlpha;
        float borderAlpha;
        switch (area.role()) {
            case DEPARTMENT_BACKGROUND -> {
                fillAlpha = hoveredArea ? 0.055F : 0.016F;
                borderAlpha = hoveredArea ? 0.48F : 0.0F;
            }
            case CORRIDOR -> {
                fillAlpha = hoveredArea ? 0.15F : 0.065F;
                borderAlpha = hoveredArea ? 0.90F : 0.36F;
            }
            case ELEVATOR_ROOM -> {
                fillAlpha = hoveredArea ? 0.20F : 0.09F;
                borderAlpha = hoveredArea ? 1.00F : 0.64F;
            }
            case ELEVATOR_SHAFT -> {
                fillAlpha = hoveredArea ? 0.13F : 0.035F;
                borderAlpha = hoveredArea ? 1.00F : 0.72F;
            }
            case REST_ROOM, MAIN_HALL -> {
                fillAlpha = hoveredArea ? 0.22F : 0.12F;
                borderAlpha = hoveredArea ? 1.00F : 0.78F;
            }
            default -> {
                fillAlpha = hoveredArea ? 0.16F : 0.045F;
                borderAlpha = hoveredArea ? 1.00F : 0.48F;
            }
        }
        if (danger) {
            float pulse = 0.55F + 0.45F * (float) Math.sin((Util.getMillis() % 900L) / 900.0D * Math.PI * 2.0D);
            color = LobotomyUiTheme.ALERT_RED;
            fillAlpha = Math.max(fillAlpha, 0.16F + pulse * 0.14F);
            borderAlpha = Math.max(borderAlpha, 0.72F + pulse * 0.28F);
        }
        graphics.fill(left, top, right, bottom, withAlpha(color, alpha * fillAlpha));
        if (borderAlpha > 0.0F) {
            drawBorder(graphics, left, top, width, height, withAlpha(color, alpha * borderAlpha));
        }

        if (area.role() == FacilityLayoutTemplate.AreaRole.ELEVATOR_SHAFT && width >= 3) {
            int centerX = left + width / 2;
            graphics.fill(centerX, top + 1, centerX + 1, bottom - 1,
                    withAlpha(color, alpha * 0.74F));
        } else if (area.role() == FacilityLayoutTemplate.AreaRole.DEPARTMENT_BACKGROUND) {
            String label = departmentLabel(area);
            if (width >= this.font.width(label) + 8 && height >= 13) {
                graphics.drawString(this.font, label, left + 4, top + 3,
                        withAlpha(color, alpha * 0.92F), false);
            }
        }
    }

    private void renderFloorBands(GuiGraphics graphics, float alpha) {
        int contentLeft = (int) worldToScreenX(layoutMinX - CARD_W / 2.0D);
        int contentRight = (int) worldToScreenX(layoutMaxX + CARD_W / 2.0D);
        double scaledCardHalfWidth = CARD_W * nodeScale() / 2.0D;

        for (int floorIndex = 0; floorIndex < floors.size(); floorIndex++) {
            Floor floor = floors.get(floorIndex);
            int top = (int) worldToScreenY(floor.layoutTop);
            int bottom = (int) worldToScreenY(floor.layoutBottom);
            if (bottom < HEADER_H || top > this.height) continue;

            int color = floorDepartmentColor(floor);
            graphics.fill(contentLeft, top, contentRight, bottom,
                    withAlpha(color, alpha * (floorIndex == selectedFloor ? 0.10F : 0.055F)));
            graphics.fill(contentLeft, top, contentRight, top + 1, withAlpha(color, alpha * 0.72F));
            graphics.fill(contentLeft, bottom - 1, contentRight, bottom, withAlpha(color, alpha * 0.42F));

            Map<Integer, List<LayoutPoint>> rows = new HashMap<>();
            for (RoomEntry room : floor.rooms) {
                LayoutPoint point = roomLayout.get(room.key());
                if (point != null) {
                    rows.computeIfAbsent((int) Math.round(point.y()), ignored -> new ArrayList<>()).add(point);
                }
            }

            List<Integer> rowKeys = new ArrayList<>(rows.keySet());
            rowKeys.sort(Integer::compareTo);
            int spineX = (int) worldToScreenX(0.0D);
            int firstRowY = Integer.MAX_VALUE;
            int lastRowY = Integer.MIN_VALUE;
            for (Integer rowKey : rowKeys) {
                List<LayoutPoint> points = rows.get(rowKey);
                points.sort(Comparator.comparingDouble(LayoutPoint::x));
                int rowY = (int) worldToScreenY(points.get(0).y());
                firstRowY = Math.min(firstRowY, rowY);
                lastRowY = Math.max(lastRowY, rowY);
                if (points.size() > 1) {
                    int left = (int) (worldToScreenX(points.get(0).x()) + scaledCardHalfWidth);
                    int right = (int) (worldToScreenX(points.get(points.size() - 1).x()) - scaledCardHalfWidth);
                    if (right > left) {
                        graphics.fill(left, rowY, right, rowY + 1, withAlpha(color, alpha * 0.70F));
                    }
                }
            }
            if (lastRowY > firstRowY) {
                graphics.fill(spineX, firstRowY, spineX + 1, lastRowY + 1,
                        withAlpha(color, alpha * 0.70F));
            }
        }
    }

    private void renderCard(GuiGraphics graphics, RoomEntry entry, boolean isHovered, float alpha) {
        Optional<ScreenBounds> containmentBounds = containmentScreenBounds(entry);
        if (containmentBounds.isPresent()) {
            renderContainedRoom(graphics, entry, containmentBounds.get(), isHovered, alpha);
            return;
        }

        int cx = (int) worldToScreenX(cardWorldX(entry));
        int cy = (int) worldToScreenY(cardWorldZ(entry));
        double scale = nodeScale();
        int scaledW = (int) Math.ceil(CARD_W * scale);
        int scaledH = (int) Math.ceil(CARD_H * scale);
        if (cx + scaledW / 2 < mapLeft() || cx - scaledW / 2 > this.width
                || cy + scaledH / 2 < HEADER_H || cy - scaledH / 2 > this.height) {
            return;
        }

        int left = -CARD_W / 2;
        int top = -CARD_H / 2;
        int status = statusColor(entry);
        int department = roomDepartmentColor(entry);
        int border = entry.status == RoomEntry.STATUS_ESCAPED ? STATUS_ESCAPED : department;
        if (entry.status == RoomEntry.STATUS_ESCAPED) {
            float pulse = 0.65F + 0.35F * Mth.sin((Util.getMillis() % 1000L) / 1000.0F
                    * (float) Math.PI * 2F);
            border = withAlpha(border, pulse);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0D);
        graphics.pose().scale((float) scale, (float) scale, 1.0F);

        graphics.fill(left, top, left + CARD_W, top + CARD_H,
                withAlpha(isHovered ? COLOR_CARD_BG_HOVER : COLOR_CARD_BG, alpha));
        graphics.fill(left + 1, top + 1, left + CARD_W - 1, top + CARD_H - 1,
                withAlpha(department, alpha * (isHovered ? 0.10F : 0.045F)));
        drawBorder(graphics, left, top, CARD_W, CARD_H, withAlpha(border, alpha));

        int portraitX = left + 5;
        int portraitY = top + 6;
        graphics.fill(portraitX, portraitY, portraitX + PORTRAIT, portraitY + PORTRAIT,
                withAlpha(LobotomyUiTheme.BG_INSET, alpha));
        if (!entry.abnormalityCode.isEmpty()) {
            RenderSystem.enableBlend();
            graphics.setColor(1F, 1F, 1F, alpha);
            graphics.blit(resolvePortrait(entry.abnormalityCode), portraitX, portraitY, 0, 0,
                    PORTRAIT, PORTRAIT, PORTRAIT, PORTRAIT);
            graphics.setColor(1F, 1F, 1F, 1F);
        } else {
            drawCentered(graphics, "-", portraitX + PORTRAIT / 2, portraitY + 11,
                    withAlpha(COLOR_TEXT_DIM, alpha));
        }

        int textX = left + 40;
        int textWidth = CARD_W - 44;
        String title = this.font.plainSubstrByWidth(entry.resolveTitle(), textWidth);
        graphics.drawString(this.font, title, textX, top + 6, withAlpha(COLOR_TEXT, alpha), false);
        String code = entry.abnormalityCode.isEmpty()
                ? entry.pos.getX() + "," + entry.pos.getZ()
                : entry.abnormalityCode;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(code, textWidth), textX, top + 18,
                withAlpha(COLOR_TEXT_DIM, alpha), false);
        if (!entry.riskName.isEmpty()) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.riskName, textWidth),
                    textX, top + 30, withAlpha(entry.riskColor, alpha), false);
        }

        int barLeft = left + 5;
        int barRight = left + CARD_W - 5;
        int barY = top + 43;
        graphics.fill(barLeft, barY, barRight, barY + 3, withAlpha(LobotomyUiTheme.BAR_TRACK, alpha));
        if (hasActiveOccupant(entry)) {
            int fill = (int) ((barRight - barLeft) * Mth.clamp(entry.healthPercent, 0F, 1F));
            graphics.fill(barLeft, barY, barLeft + fill, barY + 3, withAlpha(status, alpha));
        }

        RoomEmployeeSnapshot activeWork = roomWorkSnapshot(entry);
        if (activeWork != null) {
            renderRoomWorkSlots(graphics, activeWork, left + 5, top + 50, CARD_W - 10, alpha);
        } else if (entry.maxQliphoth > 0) {
            int count = Math.min(entry.maxQliphoth, 8);
            int pipWidth = 4;
            int totalWidth = count * pipWidth + Math.max(0, count - 1) * 2;
            int pipX = left + (CARD_W - totalWidth) / 2;
            for (int i = 0; i < count; i++) {
                graphics.fill(pipX, top + 50, pipX + pipWidth, top + 54,
                        withAlpha(i < entry.qliphoth ? LobotomyUiTheme.GOLD : LobotomyUiTheme.LINE_MID, alpha));
                pipX += pipWidth + 2;
            }
        }

        String statusText = switch (entry.status) {
            case RoomEntry.STATUS_OCCUPIED -> tr("status.lobotocraft.contained");
            case RoomEntry.STATUS_ESCAPED -> tr("status.lobotocraft.escaped_short");
            case RoomEntry.STATUS_LOST -> tr("status.lobotocraft.lost");
            case RoomEntry.STATUS_UNEXPANDED -> tr("status.lobotocraft.unexpanded");
            default -> tr("status.lobotocraft.empty");
        };
        if (entry.playersInside > 0) statusText += "  x" + entry.playersInside;
        drawCentered(graphics, statusText, 0, top + 59, withAlpha(status, alpha));
        renderMeltdownFrame(graphics, entry, left, top, CARD_W, CARD_H, alpha);
        graphics.pose().popPose();
        renderRepeatedWorkPenalty(graphics, entry,
                cx - scaledW / 2, cy - scaledH / 2, scaledW, scaledH, alpha);
    }

    private void renderContainedRoom(GuiGraphics graphics, RoomEntry entry, ScreenBounds bounds,
                                     boolean isHovered, float alpha) {
        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        int width = bounds.width();
        int height = bounds.height();
        if (width < 2 || height < 2 || right < mapLeft() || left > this.width
                || bottom < HEADER_H || top > this.height) {
            return;
        }

        int status = statusColor(entry);
        int department = roomDepartmentColor(entry);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1,
                withAlpha(isHovered ? COLOR_CARD_BG_HOVER : COLOR_CARD_BG, alpha * 0.84F));
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1,
                withAlpha(department, alpha * (isHovered ? 0.12F : 0.05F)));

        int border = entry.status == RoomEntry.STATUS_ESCAPED ? STATUS_ESCAPED : department;
        float borderAlpha = isHovered ? 1.0F : 0.72F;
        if (entry.status == RoomEntry.STATUS_ESCAPED) {
            borderAlpha *= 0.65F + 0.35F * Mth.sin((Util.getMillis() % 1000L) / 1000.0F
                    * (float) Math.PI * 2F);
        }
        drawBorder(graphics, left, top, width, height, withAlpha(border, alpha * borderAlpha));

        int clipLeft = Math.max(mapLeft(), left + 1);
        int clipTop = Math.max(HEADER_H + 1, top + 1);
        int clipRight = Math.min(this.width, right - 1);
        int clipBottom = Math.min(this.height - 18, bottom - 1);
        if (clipRight <= clipLeft || clipBottom <= clipTop) return;

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int inset = width >= 12 && height >= 12 ? 3 : 2;
        int contentLeft = left + inset;
        int contentTop = top + inset;
        int contentRight = right - inset;
        int statusBarHeight = height >= 20 ? 2 : 1;
        int contentBottom = bottom - inset - statusBarHeight - 1;
        int contentWidth = Math.max(0, contentRight - contentLeft);
        RoomEmployeeSnapshot activeWork = roomWorkSnapshot(entry);
        int workSlotsHeight = activeWork == null
                ? 0 : roomWorkSlotsHeight(contentWidth, activeWork.workBoxCapacity());
        if (workSlotsHeight > 0) contentBottom -= workSlotsHeight + 2;

        if (contentWidth > 0) {
            graphics.fill(contentLeft, bottom - inset - statusBarHeight,
                    contentRight, bottom - inset, withAlpha(LobotomyUiTheme.BAR_TRACK, alpha));
            int statusWidth = !hasActiveOccupant(entry)
                    ? contentWidth
                    : Math.max(1, (int) Math.round(contentWidth
                    * Mth.clamp(entry.healthPercent, 0F, 1F)));
            graphics.fill(contentLeft, bottom - inset - statusBarHeight,
                    contentLeft + statusWidth, bottom - inset, withAlpha(status, alpha));
        }

        boolean detailed = zoom >= 1.20D && width >= 68 && height >= 40;
        if (detailed) {
            renderContainedRoomDetails(graphics, entry, contentLeft, contentTop,
                    contentRight, contentBottom, status, alpha);
        } else {
            renderContainedRoomOverview(graphics, entry, contentLeft, contentTop,
                    contentRight, contentBottom, status, alpha);
        }
        if (activeWork != null && workSlotsHeight > 0) {
            renderRoomWorkSlots(graphics, activeWork, contentLeft,
                    contentBottom + 2, contentWidth, alpha);
        }
        graphics.disableScissor();
        renderMeltdownFrame(graphics, entry, left, top, width, height, alpha);
        renderRepeatedWorkPenalty(graphics, entry, left, top, width, height, alpha);
    }

    private void renderRepeatedWorkPenalty(GuiGraphics graphics, RoomEntry entry,
                                           int left, int top, int width, int height,
                                           float alpha) {
        if (entry.status != RoomEntry.STATUS_OCCUPIED
                || entry.repeatedWorkPenaltyPercent <= 0) return;
        String text = "-" + entry.repeatedWorkPenaltyPercent + "%";
        int x = left + width + 3;
        if (x + this.font.width(text) > this.width) {
            x = Math.max(mapLeft() + 2, left + width - this.font.width(text) - 2);
        }
        int y = Math.max(HEADER_H + 1, top + 2);
        graphics.drawString(this.font, text, x, y,
                withAlpha(0xFFFF5555, alpha), true);
    }

    private void renderMeltdownFrame(GuiGraphics graphics, RoomEntry entry,
                                     int left, int top, int width, int height, float alpha) {
        if (!entry.meltdownActive) return;
        float pulse = 0.78F + 0.22F * Mth.sin((Util.getMillis() % 800L) / 800.0F
                * (float) Math.PI * 2.0F);
        int red = withAlpha(0xFFFF3B3B, alpha * pulse);
        drawBorder(graphics, left, top, width, height, red);
        int remainingTicks = remainingMeltdownTicks(entry.meltdownRemainingTicks,
                Util.getMillis() - snapshotReceivedAtMillis);
        int seconds = (remainingTicks + 19) / 20;
        String timer = seconds + "s";
        int timerX = left + width + 3;
        if (timerX + this.font.width(timer) > this.width) {
            timerX = Math.max(left + 2, left + width - this.font.width(timer) - 2);
        }
        int timerY = Math.max(HEADER_H + 1, top + 2);
        graphics.drawString(this.font, timer, timerX, timerY, red, true);
    }

    static int remainingMeltdownTicks(int snapshotTicks, long elapsedMillis) {
        long elapsedTicks = Math.max(0L, elapsedMillis) / 50L;
        return (int) Math.max(0L, (long) Math.max(0, snapshotTicks) - elapsedTicks);
    }

    private RoomEmployeeSnapshot roomWorkSnapshot(RoomEntry room) {
        for (RoomEmployeeSnapshot employee : employees) {
            if (!employee.isWorking() || employee.workRoomDimension() == null
                    || employee.workRoomPosition() == null) continue;
            if (employee.workRoomDimension().equals(room.dimension)
                    && employee.workRoomPosition().equals(room.pos)) return employee;
        }
        return null;
    }

    private static int roomWorkSlotsHeight(int availableWidth, int capacity) {
        if (availableWidth < 2 || capacity <= 0) return 0;
        int slotsWidth = Math.max(2,
                availableWidth - ACTIVE_WORK_ICON_SIZE - ACTIVE_WORK_ICON_GAP);
        int columns = Math.min(capacity, Math.max(1, (slotsWidth + 1) / 3));
        int rows = (capacity + columns - 1) / columns;
        return Math.max(ACTIVE_WORK_ICON_SIZE, rows * 3 - 1);
    }

    private void renderRoomWorkSlots(GuiGraphics graphics, RoomEmployeeSnapshot work,
                                     int left, int top, int availableWidth, float alpha) {
        int capacity = Math.max(0, work.workBoxCapacity());
        if (capacity <= 0 || availableWidth < 2) return;
        int slotsLeft = left;
        int slotsWidth = availableWidth;
        if (work.currentWorkType() != null && availableWidth >= ACTIVE_WORK_ICON_SIZE + 2) {
            renderActiveWorkIcon(graphics, work.currentWorkType(), left, top, alpha);
            slotsLeft += ACTIVE_WORK_ICON_SIZE + ACTIVE_WORK_ICON_GAP;
            slotsWidth -= ACTIVE_WORK_ICON_SIZE + ACTIVE_WORK_ICON_GAP;
        }
        int columns = Math.min(capacity, Math.max(1, (slotsWidth + 1) / 3));
        int totalWidth = columns * 2 + Math.max(0, columns - 1);
        int startX = slotsLeft + Math.max(0, (slotsWidth - totalWidth) / 2);
        int failureCount = Mth.clamp(work.workFailureBoxes(), 0, capacity);
        int successCount = Mth.clamp(work.workSuccessBoxes(), 0, capacity - failureCount);
        for (int slot = 0; slot < capacity; slot++) {
            int row = slot / columns;
            int column = slot % columns;
            int x = startX + column * 3;
            int y = top + row * 3;
            int color;
            if (slot < failureCount) color = 0xFFE35D62;
            else if (slot >= capacity - successCount) color = 0xFF48C979;
            else color = 0xFF353B3C;
            graphics.fill(x, y, x + 2, y + 2, withAlpha(color, alpha));
        }
    }

    private void renderActiveWorkIcon(GuiGraphics graphics, WorkType workType,
                                      int x, int y, float alpha) {
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(WORK_ICONS[workType.ordinal()], x, y, 0, 0,
                ACTIVE_WORK_ICON_SIZE, ACTIVE_WORK_ICON_SIZE,
                ACTIVE_WORK_ICON_SIZE, ACTIVE_WORK_ICON_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderContainedRoomOverview(GuiGraphics graphics, RoomEntry entry,
                                             int left, int top, int right, int bottom,
                                             int status, float alpha) {
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        if (width < 4 || height < 4) return;

        String statusText = roomStatusText(entry);
        boolean showStatusText = height >= 20 && width >= this.font.width(statusText) + 2;
        int portraitBottom = showStatusText ? bottom - this.font.lineHeight - 1 : bottom;
        int portraitSize = Math.min(PORTRAIT,
                Math.min(width, Math.max(0, portraitBottom - top)));
        if (portraitSize >= 4) {
            int portraitX = left + (width - portraitSize) / 2;
            int portraitY = top + Math.max(0, (portraitBottom - top - portraitSize) / 2);
            renderContainedPortrait(graphics, entry, portraitX, portraitY, portraitSize, alpha);
        }
        if (showStatusText) {
            drawCentered(graphics, statusText, left + width / 2,
                    bottom - this.font.lineHeight, withAlpha(status, alpha));
        }
    }

    private void renderContainedRoomDetails(GuiGraphics graphics, RoomEntry entry,
                                            int left, int top, int right, int bottom,
                                            int status, float alpha) {
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        int portraitSize = Math.min(PORTRAIT, Math.min(height, Math.max(12, width / 3)));
        renderContainedPortrait(graphics, entry, left, top, portraitSize, alpha);

        int textX = left + portraitSize + 4;
        int textWidth = right - textX;
        if (textWidth < 18) return;

        int lineY = top;
        drawFittedString(graphics, entry.resolveTitle(), textX, lineY,
                textWidth, withAlpha(COLOR_TEXT, alpha));
        lineY += this.font.lineHeight + 1;

        if (lineY + this.font.lineHeight <= bottom && !entry.riskName.isEmpty()) {
            drawFittedString(graphics, entry.riskName, textX, lineY,
                    textWidth, withAlpha(entry.riskColor, alpha));
            lineY += this.font.lineHeight + 1;
        }

        if (lineY + this.font.lineHeight <= bottom) {
            String values = !hasActiveOccupant(entry)
                    ? roomStatusText(entry)
                    : "HP " + Math.round(entry.healthPercent * 100) + "%";
            if (entry.maxQliphoth > 0) {
                values += "  Q " + entry.qliphoth + "/" + entry.maxQliphoth;
            }
            drawFittedString(graphics, values, textX, lineY,
                    textWidth, withAlpha(status, alpha));
        }
    }

    private void renderContainedPortrait(GuiGraphics graphics, RoomEntry entry,
                                         int x, int y, int size, float alpha) {
        if (size < 2) return;
        graphics.fill(x, y, x + size, y + size, withAlpha(LobotomyUiTheme.BG_INSET, alpha));
        if (!entry.abnormalityCode.isEmpty()) {
            RenderSystem.enableBlend();
            graphics.setColor(1F, 1F, 1F, alpha);
            graphics.blit(resolvePortrait(entry.abnormalityCode), x, y, 0, 0,
                    size, size, size, size);
            graphics.setColor(1F, 1F, 1F, 1F);
        } else if (size >= this.font.lineHeight) {
            drawCentered(graphics, "-", x + size / 2,
                    y + (size - this.font.lineHeight) / 2, withAlpha(COLOR_TEXT_DIM, alpha));
        }
    }

    private void drawFittedString(GuiGraphics graphics, String text, int x, int y,
                                  int maxWidth, int color) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) return;
        String fitted = this.font.plainSubstrByWidth(text, maxWidth);
        if (!fitted.isEmpty()) graphics.drawString(this.font, fitted, x, y, color, false);
    }

    private static String roomStatusText(RoomEntry entry) {
        String statusText = switch (entry.status) {
            case RoomEntry.STATUS_OCCUPIED -> tr("status.lobotocraft.contained");
            case RoomEntry.STATUS_ESCAPED -> tr("status.lobotocraft.escaped_short");
            case RoomEntry.STATUS_LOST -> tr("status.lobotocraft.lost");
            case RoomEntry.STATUS_UNEXPANDED -> tr("status.lobotocraft.unexpanded");
            default -> tr("status.lobotocraft.empty");
        };
        return entry.playersInside > 0 ? statusText + "  x" + entry.playersInside : statusText;
    }

    private void renderLegacyCard(GuiGraphics graphics, RoomEntry entry, boolean isHovered, float alpha) {
        int cx = (int) worldToScreenX(cardWorldX(entry));
        int cy = (int) worldToScreenY(cardWorldZ(entry));
        int left = cx - CARD_W / 2;
        int top = cy - CARD_H / 2;

        if (left > this.width || top > this.height || left + CARD_W < mapLeft() || top + CARD_H < HEADER_H) {
            return;
        }

        boolean escaped = entry.status == RoomEntry.STATUS_ESCAPED;
        int statusColor = statusColor(entry);

        // 出逃 -> 整个边框红色并呼吸闪烁
        int borderColor;
        if (escaped) {
            float pulse = 0.6F + 0.4F * Mth.sin((Util.getMillis() % 1000L) / 1000.0F * (float) Math.PI * 2F);
            borderColor = withAlpha(STATUS_ESCAPED, pulse * alpha);
        } else {
            int base = (entry.riskColor != 0 && hasActiveOccupant(entry))
                    ? entry.riskColor : statusColor;
            borderColor = withAlpha(base, (isHovered ? 1.0F : 0.75F) * alpha);
        }

        graphics.fill(left, top, left + CARD_W, top + CARD_H,
                withAlpha(isHovered ? COLOR_CARD_BG_HOVER : COLOR_CARD_BG, alpha));
        if (escaped) {
            // 出逃的房间底色也压一层红，扫一眼就能看到
            graphics.fill(left, top, left + CARD_W, top + CARD_H, withAlpha(0x30EF4444, alpha));
            drawBorder(graphics, left + 1, top + 1, CARD_W - 2, CARD_H - 2, borderColor);
        }
        drawBorder(graphics, left, top, CARD_W, CARD_H, borderColor);

        // 立绘
        int px = cx - PORTRAIT / 2;
        int py = top + 6;
        graphics.fill(px, py, px + PORTRAIT, py + PORTRAIT, withAlpha(0xFF05080E, alpha));
        if (!entry.abnormalityCode.isEmpty()) {
            RenderSystem.enableBlend();
            graphics.setColor(1F, 1F, 1F, alpha);
            graphics.blit(resolvePortrait(entry.abnormalityCode), px, py, 0, 0,
                    PORTRAIT, PORTRAIT, PORTRAIT, PORTRAIT);
            graphics.setColor(1F, 1F, 1F, 1F);
        } else {
            drawCentered(graphics, tr("status.lobotocraft.empty_short"), cx,
                    py + PORTRAIT / 2 - 4, withAlpha(COLOR_TEXT_DIM, alpha));
        }

        // 房间名
        String title = this.font.plainSubstrByWidth(entry.resolveTitle(), CARD_W - 10);
        drawCentered(graphics, title, cx, top + 75, withAlpha(COLOR_TEXT, alpha));

        // 代码（灰） + 风险等级（按 RiskLevel 的颜色）
        if (entry.abnormalityCode.isEmpty()) {
            String coord = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();
            drawCentered(graphics, coord, cx, top + 87, withAlpha(COLOR_TEXT_DIM, alpha));
        } else {
            String code = entry.abnormalityCode;
            String risk = entry.riskName;
            int codeW = this.font.width(code);
            int riskW = risk.isEmpty() ? 0 : this.font.width(risk) + 6;
            int startX = cx - (codeW + riskW) / 2;
            graphics.drawString(this.font, code, startX, top + 87, withAlpha(COLOR_TEXT_DIM, alpha), false);
            if (!risk.isEmpty()) {
                graphics.drawString(this.font, risk, startX + codeW + 6, top + 87,
                        withAlpha(entry.riskColor, alpha), false);
            }
        }

        if (hasActiveOccupant(entry)) {
            // 血条
            int barLeft = left + 10;
            int barRight = left + CARD_W - 10;
            int barY = top + 100;
            graphics.fill(barLeft, barY, barRight, barY + 5, withAlpha(0xFF1B2434, alpha));
            int filled = (int) ((barRight - barLeft) * Mth.clamp(entry.healthPercent, 0F, 1F));
            graphics.fill(barLeft, barY, barLeft + filled, barY + 5, withAlpha(statusColor, alpha));

            // 计数器
            if (entry.maxQliphoth > 0) {
                int pip = 6;
                int gap = 3;
                int total = Math.min(entry.maxQliphoth, 8);
                int totalWidth = total * pip + (total - 1) * gap;
                int pipX = cx - totalWidth / 2;
                int pipY = top + 109;
                for (int i = 0; i < total; i++) {
                    graphics.fill(pipX, pipY, pipX + pip, pipY + pip,
                            withAlpha(i < entry.qliphoth ? 0xFFE0C46A : 0xFF2A3546, alpha));
                    pipX += pip + gap;
                }
            }
        }

        // 状态
        String statusText = switch (entry.status) {
            case RoomEntry.STATUS_OCCUPIED -> tr("status.lobotocraft.contained");
            case RoomEntry.STATUS_ESCAPED -> entry.escapeDistance > 0
                    ? tr("status.lobotocraft.escaped_distance", entry.escapeDistance)
                    : tr("status.lobotocraft.escaped");
            case RoomEntry.STATUS_LOST -> tr("status.lobotocraft.lost");
            case RoomEntry.STATUS_UNEXPANDED -> tr("status.lobotocraft.unexpanded");
            default -> tr("status.lobotocraft.empty");
        };
        if (entry.playersInside > 0) {
            statusText = tr("screen.lobotocraft.monitor.room.employees_inside", statusText, entry.playersInside);
        }
        drawCentered(graphics, statusText, cx, top + CARD_H - 26, withAlpha(statusColor, alpha));

        long lag = serverGameTime - entry.lastUpdate;
        if (lag > 100) {
            drawCentered(graphics, tr("screen.lobotocraft.monitor.data_delay_short", lag / 20),
                    cx, top + CARD_H - 14,
                    withAlpha(0xFFD9A036, alpha * 0.9F));
        }
    }

    private void renderFloorGutter(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        graphics.fill(0, HEADER_H, GUTTER_W, this.height, withAlpha(COLOR_PANEL, alpha));
        graphics.fill(GUTTER_W - 1, HEADER_H, GUTTER_W, this.height,
                withAlpha(LobotomyUiTheme.LINE_MID, alpha));
        graphics.drawString(this.font, tr("screen.lobotocraft.monitor.floors"), 9, HEADER_H + 6,
                withAlpha(COLOR_TEXT_DIM, alpha), false);

        for (int i = 0; i < floors.size(); i++) {
            Floor floor = floors.get(i);
            int centerY = (int) worldToScreenY(floor.layoutCenter);
            int top = centerY - FLOOR_LABEL_H / 2;
            if (top + FLOOR_LABEL_H < HEADER_H + 16 || top > this.height - 18) continue;

            boolean selected = i == selectedFloor;
            boolean hoveredFloor = mouseX >= 6 && mouseX <= GUTTER_W - 6
                    && mouseY >= top && mouseY <= top + FLOOR_LABEL_H;
            int color = floorDepartmentColor(floor);
            if (selected || hoveredFloor) {
                graphics.fill(6, top, GUTTER_W - 6, top + FLOOR_LABEL_H,
                        withAlpha(color, alpha * (selected ? 0.16F : 0.08F)));
            }
            graphics.fill(6, top, 8, top + FLOOR_LABEL_H,
                    withAlpha(floor.escaped() > 0 ? STATUS_ESCAPED : color, alpha));

            if (renamingFloor == i && renameBox != null) continue;
            String label = this.font.plainSubstrByWidth(floor.label, GUTTER_W - 25);
            graphics.drawString(this.font, label, 13, top + 4,
                    withAlpha(selected ? COLOR_TEXT : COLOR_TEXT_DIM, alpha), false);
            String info = floor.escaped() > 0
                    ? tr("screen.lobotocraft.monitor.floor.rooms_escaped",
                    floor.rooms.size(), floor.escaped())
                    : tr("screen.lobotocraft.monitor.floor.rooms", floor.rooms.size());
            graphics.drawString(this.font, info, 13, top + 16,
                    withAlpha(floor.escaped() > 0 ? STATUS_ESCAPED : COLOR_TEXT_DIM, alpha), false);
        }
    }

    private void renderLegacyFloorTabs(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        int panelRight = TAB_X + TAB_W + 10;
        graphics.fill(0, HEADER_H, panelRight, this.height, withAlpha(COLOR_PANEL, alpha));
        graphics.fill(panelRight, HEADER_H, panelRight + 1, this.height, withAlpha(COLOR_ACCENT, alpha * 0.6F));

        graphics.drawString(this.font, tr("screen.lobotocraft.monitor.floors.rename_hint"),
                TAB_X, HEADER_H + 8,
                withAlpha(COLOR_TEXT_DIM, alpha), false);

        for (int i = 0; i < floors.size(); i++) {
            Floor floor = floors.get(i);
            int top = (int) (TAB_TOP + i * (TAB_H + TAB_GAP) - tabScroll);
            if (top + TAB_H < HEADER_H || top > this.height) continue;

            boolean selected = i == selectedFloor;
            boolean hover = mouseX >= TAB_X && mouseX <= TAB_X + TAB_W
                    && mouseY >= top && mouseY <= top + TAB_H;

            int bg = selected ? 0xFF16324A : (hover ? 0xFF101C2A : 0xFF0A1220);
            graphics.fill(TAB_X, top, TAB_X + TAB_W, top + TAB_H, withAlpha(bg, alpha));

            int escaped = floor.escaped();
            int bar = escaped > 0 ? STATUS_ESCAPED : (selected ? COLOR_ACCENT : 0xFF2A3546);
            graphics.fill(TAB_X, top, TAB_X + 3, top + TAB_H, withAlpha(bar, alpha));

            if (renamingFloor == i && renameBox != null) {
                continue; // 输入框自己会画在这
            }

            String label = this.font.plainSubstrByWidth(floor.label, TAB_W - 16);
            graphics.drawString(this.font, label, TAB_X + 9, top + 5,
                    withAlpha(selected ? COLOR_TEXT : COLOR_TEXT_DIM, alpha), false);

            String info = escaped > 0
                    ? tr("screen.lobotocraft.monitor.floor.rooms_escaped", floor.rooms.size(), escaped)
                    : tr("screen.lobotocraft.monitor.floor.rooms", floor.rooms.size());
            graphics.drawString(this.font, info, TAB_X + 9, top + 18,
                    withAlpha(escaped > 0 ? STATUS_ESCAPED : COLOR_TEXT_DIM, alpha * 0.9F), false);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        graphics.fill(0, 0, this.width, HEADER_H, withAlpha(0xF00A1018, alpha));
        graphics.fill(0, HEADER_H, this.width, HEADER_H + 1, withAlpha(COLOR_ACCENT, alpha));

        String title = tr("screen.lobotocraft.monitor.header");
        graphics.drawString(this.font, title, 12, 11, withAlpha(COLOR_TEXT, alpha), false);
        int x = 12 + this.font.width(title) + 14;

        int escaped = 0;
        int contained = 0;
        for (RoomEntry entry : rooms) {
            if (entry.status == RoomEntry.STATUS_ESCAPED) escaped++;
            else if (entry.status == RoomEntry.STATUS_OCCUPIED) contained++;
        }

        String stats = tr("screen.lobotocraft.monitor.stats",
                rooms.size(), contained, escaped, floors.size());
        ScreenBounds tt2 = tt2ButtonBounds();
        int statsWidth = Math.max(0, tt2.left() - x - 10);
        if (statsWidth > 12) {
            drawFittedString(graphics, stats, x, 11, statsWidth,
                    withAlpha(escaped > 0 ? STATUS_ESCAPED : COLOR_TEXT_DIM, alpha));
        }

        hoveredTt2Button = tt2.contains(mouseX, mouseY);
        int tt2Background = globalTt2Enabled ? 0xFF173A2B : 0xFF202630;
        int tt2Border = globalTt2Enabled ? LobotomyUiTheme.ENERGY_GREEN : LobotomyUiTheme.LINE_MID;
        if (hoveredTt2Button) {
            tt2Background = globalTt2Enabled ? 0xFF20513A : 0xFF303A48;
            tt2Border = LobotomyUiTheme.LINE_BRIGHT;
        }
        graphics.fill(tt2.left(), tt2.top(), tt2.right(), tt2.bottom(),
                withAlpha(tt2Background, alpha));
        drawBorder(graphics, tt2.left(), tt2.top(), tt2.width(), tt2.height(),
                withAlpha(tt2Border, alpha));
        drawCentered(graphics, tr(globalTt2Enabled
                        ? "screen.lobotocraft.monitor.tt2.enabled"
                        : "screen.lobotocraft.monitor.tt2.disabled"),
                tt2.left() + tt2.width() / 2, tt2.top() + 6,
                withAlpha(globalTt2Enabled ? LobotomyUiTheme.ENERGY_GREEN : COLOR_TEXT_DIM, alpha));

        String facilityName = tr("screen.lobotocraft.monitor.facility_name");
        graphics.drawString(this.font, facilityName,
                this.width - this.font.width(facilityName) - 12, 11,
                withAlpha(COLOR_TEXT, alpha), false);

    }

    private ScreenBounds tt2ButtonBounds() {
        int companyLeft = this.width - this.font.width(
                tr("screen.lobotocraft.monitor.facility_name")) - 12;
        int right = companyLeft - 12;
        return new ScreenBounds(right - 94, 6, right, HEADER_H - 6);
    }

    private void renderFooter(GuiGraphics graphics, float alpha) {
        int top = this.height - 18;
        graphics.fill(0, top, this.width, this.height, withAlpha(0xF00A1018, alpha));
        graphics.fill(0, top, this.width, top + 1, withAlpha(LobotomyUiTheme.LINE_FAINT, alpha));
        String hint = tr("screen.lobotocraft.monitor.controls");
        drawFittedString(graphics, hint, 10, top + 5, Math.max(1, this.width - 20),
                withAlpha(COLOR_TEXT_DIM, alpha));
    }

    private List<Component> buildTooltip(RoomEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§f" + entry.resolveTitle()));
        if (entry.floorName != null && !entry.floorName.isEmpty()) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.floor", entry.floorName));
        }
        if (!entry.abnormalityCode.isEmpty()) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.code", entry.abnormalityCode));
            if (!entry.riskName.isEmpty()) {
                lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.risk")
                        .append(Component.literal(entry.riskName)
                                .withStyle(style -> style.withColor(entry.riskColor & 0xFFFFFF))));
            }
        }
        Component status = switch (entry.status) {
            case RoomEntry.STATUS_OCCUPIED -> Component.translatable("status.lobotocraft.contained.colored");
            case RoomEntry.STATUS_ESCAPED -> entry.escapeDistance > 0
                    ? Component.translatable("status.lobotocraft.escaped_distance.colored", entry.escapeDistance)
                    : Component.translatable("status.lobotocraft.escaped.colored");
            case RoomEntry.STATUS_LOST -> Component.translatable("status.lobotocraft.lost.detail");
            case RoomEntry.STATUS_UNEXPANDED -> Component.translatable("status.lobotocraft.unexpanded.colored");
            default -> Component.translatable("status.lobotocraft.empty.colored");
        };
        lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.status").append(status));
        if (hasActiveOccupant(entry)) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.health",
                    Math.round(entry.healthPercent * 100)));
            if (entry.maxQliphoth > 0) {
                lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.qliphoth",
                        entry.qliphoth, entry.maxQliphoth));
            }
        }
        if (entry.meltdownActive) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.meltdown",
                    Math.max(0, (entry.meltdownRemainingTicks + 19) / 20)));
        }
        RoomEmployeeSnapshot activeWork = roomWorkSnapshot(entry);
        if (activeWork != null) {
            String workName = activeWork.currentWorkType() == null
                    ? tr("screen.lobotocraft.common.unknown")
                    : tr(WORK_NAME_KEYS[activeWork.currentWorkType().ordinal()]);
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.work_type", workName));
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.pe_boxes",
                    activeWork.workSuccessBoxes(), activeWork.workFailureBoxes(),
                    activeWork.workBoxCapacity()));
        }
        lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.coordinates",
                entry.pos.getX(), entry.pos.getY(), entry.pos.getZ()));
        if (entry.playersInside > 0) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.employees_inside",
                    entry.playersInside));
        }
        long lag = serverGameTime - entry.lastUpdate;
        if (lag > 100) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.tooltip.data_delay", lag / 20));
        }
        return lines;
    }

    private List<Component> buildEmployeeTooltip(RoomEmployeeSnapshot employee) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§f" + employee.name() + "  §7LV " + employee.employeeLevel()));
        lines.add(Component.literal("§7" + departmentName(employee.departmentId())
                + "  §8" + employeeStatusText(employee.status())));
        lines.add(Component.literal("§cHP " + Math.round(employee.health()) + "/"
                + Math.round(employee.maxHealth()) + "  §fSP " + Math.round(employee.mental())
                + "/" + Math.round(employee.maxMental())));
        lines.add(Component.translatable("screen.lobotocraft.monitor.employee.stats",
                employee.fortitude(), employee.prudence(), employee.temperance(), employee.justice()));
        lines.add(Component.translatable("screen.lobotocraft.monitor.employee.weapon", employee.weaponName()));
        lines.add(Component.translatable("screen.lobotocraft.monitor.employee.armor", employee.armorName()));
        if (employee.eligible() && employee.successRate() >= 0.0F) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.employee.work_preview",
                    Math.round(employee.successRate() * 100.0F), employee.estimatedSeconds()));
        } else if (employee.eligible() && employee.successRate() < 0.0F
                && selectedRoom != null && selectedWork >= 0 && !isSuppressionSelection()) {
            lines.add(Component.translatable("screen.lobotocraft.monitor.employee.work_preview_unknown"));
        } else if (!employee.eligible()) {
            lines.add(Component.literal("§c" + employee.unavailableReason()));
        }
        return lines;
    }

    private static String employeeStatusText(String status) {
        return "CONTROLLED".equals(status) ? tr("status.lobotocraft.controlled") : status;
    }

    private List<Component> buildAreaTooltip(FacilityLayoutTemplate.Area area) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§f" + departmentLabel(area)));
        lines.add(Component.translatable("screen.lobotocraft.monitor.area.type", areaTypeLabel(area.role())));
        worldAreaFor(area.areaId()).ifPresent(world -> lines.add(Component.translatable(
                "screen.lobotocraft.monitor.area.anchor", world.markerPos.getX(),
                world.markerPos.getY(), world.markerPos.getZ())));
        return lines;
    }

    private static String departmentLabel(FacilityLayoutTemplate.Area area) {
        if (area.areaId().startsWith("dept/central/upper/")) {
            return tr("department.lobotocraft.central.upper");
        }
        if (area.areaId().startsWith("dept/central/lower/")) {
            return tr("department.lobotocraft.central.lower");
        }
        String id = area.departmentId() == null || area.departmentId().isBlank()
                ? "unassigned_area" : area.departmentId();
        return tr("department.lobotocraft." + id);
    }

    private static String departmentName(String departmentId) {
        String id = departmentId == null || departmentId.isBlank() ? "unassigned" : departmentId;
        return tr("department.lobotocraft." + id);
    }

    private static String areaTypeLabel(FacilityLayoutTemplate.AreaRole role) {
        return switch (role) {
            case DEPARTMENT_BACKGROUND -> tr("area.lobotocraft.department");
            case CORRIDOR -> tr("area.lobotocraft.corridor");
            case ELEVATOR_ROOM -> tr("area.lobotocraft.elevator_room");
            case ELEVATOR_SHAFT -> tr("area.lobotocraft.elevator_shaft");
            case CONTAINMENT_ROOM -> tr("area.lobotocraft.containment_room");
            case REST_ROOM -> tr("area.lobotocraft.rest_room");
            case MAIN_HALL -> tr("area.lobotocraft.main_hall");
        };
    }

    // ------------------------------------------------------------ 楼层改名

    private void startRename(int index) {
        if (index < 0 || index >= floors.size()) return;
        Floor floor = floors.get(index);
        if (floor.markerPos == null) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable(
                        "message.lobotocraft.monitor.floor_marker_required"), true);
            }
            return;
        }
        cancelRename();

        int top = (int) worldToScreenY(floor.layoutCenter) - FLOOR_LABEL_H / 2;
        renamingFloor = index;
        renameBox = new EditBox(this.font, TAB_X + 5, top + 8, TAB_W - 10, 16,
                Component.translatable("screen.lobotocraft.monitor.floor_name"));
        renameBox.setMaxLength(24);
        renameBox.setValue(floor.label);
        renameBox.setBordered(true);
        renameBox.moveCursorToEnd();
        addRenderableWidget(renameBox);
        setFocused(renameBox);
        renameBox.setFocused(true);
    }

    private void confirmRename() {
        if (renamingFloor < 0 || renameBox == null) return;
        Floor floor = floors.get(renamingFloor);
        if (floor.markerPos != null) {
            MessageLoader.getLoader().sendToServer(
                    new SetFloorNamePacket(dimension, floor.markerPos, renameBox.getValue().trim()));
        }
        cancelRename();
    }

    private void cancelRename() {
        if (renameBox != null) {
            removeWidget(renameBox);
            renameBox = null;
        }
        renamingFloor = -1;
        setFocused(null);
    }

    // ------------------------------------------------------------ 交互

    private int floorAt(double mouseY) {
        for (int i = 0; i < floors.size(); i++) {
            int centerY = (int) worldToScreenY(floors.get(i).layoutCenter);
            int top = centerY - FLOOR_LABEL_H / 2;
            if (mouseY >= top && mouseY <= top + FLOOR_LABEL_H) return i;
        }
        return -1;
    }

    private RoomEntry roomAt(double mouseX, double mouseY) {
        if (mouseX < mapLeft() || mouseY < HEADER_H) return null;
        RoomEntry result = null;
        for (RoomEntry room : visibleRooms()) {
            if (roomScreenBounds(room).contains(mouseX, mouseY)) result = room;
        }
        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 更改住户选单是模态的，优先吃掉所有点击
        if (pickerRoom != null) {
            return pickerMouseClicked(mouseX, mouseY, button);
        }
        // 改名输入框优先
        if (renameBox != null) {
            if (renameBox.mouseClicked(mouseX, mouseY, button)) return true;
            confirmRename();
            return true;
        }
        if (button == 0 && tt2ButtonBounds().contains(mouseX, mouseY)) {
            MessageLoader.getLoader().sendToServer(new ToggleGlobalTT2Packet());
            playUiSound(globalTt2Enabled ? 0.95F : 1.15F);
            return true;
        }
        if (button == 2) {
            cameraPanButton = button;
            return true;
        }
        if (button == 1 && !pointerOnCommandWindow(mouseX, mouseY)) {
            RoomEntry room = roomAt(mouseX, mouseY);
            if (room != null) return handleRoomRightClick(room);
        }
        if (button == 1) {
            cameraPanButton = button;
            moveCommandStartX = mouseX;
            moveCommandStartY = mouseY;
            pendingMoveGroupId = selectedEmployees.isEmpty() || pointerOnCommandWindow(mouseX, mouseY)
                    ? null : moveTargetAreaAt(mouseX, mouseY).orElse(null);
            moveCommandArmed = pendingMoveGroupId != null;
            return true;
        }

        // 楼层条
        if (showTabs() && mouseX < GUTTER_W && mouseY >= HEADER_H) {
            int floorIndex = floorAt(mouseY);
            if (floorIndex >= 0) {
                if (button == 0) {
                    selectFloor(floorIndex);
                }
            }
            return true;
        }

        if (selectedRoom != null && selectedWork >= 0 && candidatePanelProgress() >= 0.95F
                && candidateBounds().contains(mouseX, mouseY)) {
            if (button == 0) {
                int pageDelta = candidatePageDeltaAt(mouseX, mouseY);
                if (pageDelta != 0 && !candidatePages.isEmpty()) {
                    candidatePage = moveCandidatePage(candidatePage, candidatePages.size(), pageDelta);
                    playUiSound(1.2F);
                    return true;
                }
                RoomEmployeeSnapshot candidate = candidateAt(mouseX, mouseY);
                if (candidate != null) {
                    if (!isSuppressionSelection()) selectedEmployees.clear();
                    selectedEmployees.add(candidate.id());
                    notifyEmployeeSelectionChanged();
                    if (candidate.eligible()) {
                        playUiSound(0.9F);
                        if (isSuppressionSelection()) {
                            MessageLoader.getLoader().sendToServer(new AssignRoomEmployeeSuppressionPacket(
                                    selectedRoom.dimension, selectedRoom.pos, candidate.id()));
                        } else {
                            MessageLoader.getLoader().sendToServer(new AssignRoomEmployeeWorkPacket(
                                    selectedRoom.dimension, selectedRoom.pos,
                                    candidate.id(), WorkType.values()[selectedWork]));
                        }
                    } else {
                        playUiSound(0.55F);
                    }
                    return true;
                }
            }
            return true;
        }

        if (selectedRoom != null && detailsBounds().contains(mouseX, mouseY)) {
            if (button == 0 && detailsCloseAt(mouseX, mouseY)) {
                selectedRoom = null;
                selectedWork = -1;
                playUiSound(0.75F);
                return true;
            }
            if (button == 0) {
                int work = detailsWorkIconAt(mouseX, mouseY);
                if (work >= 0) {
                    selectedWork = work;
                    workSelectedAt = Util.getMillis();
                    workCandidates = List.of();
                    candidatePages = List.of();
                    candidatePage = 0;
                    candidateError = "";
                    candidatesLoading = true;
                    playUiSound(1.05F);
                    MessageLoader.getLoader().sendToServer(new RequestRoomWorkCandidatesPacket(
                            selectedRoom.dimension, selectedRoom.pos, WorkType.values()[work]));
                    return true;
                }
            }
            return true;
        }

        if (mouseX < mapLeft() || mouseY < HEADER_H) return false;

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        EmployeeMapMarker employee = employeeMarkerAt(mouseX, mouseY);
        if (employee != null) {
            if (!employee.commandable()) {
                playUiSound(0.55F);
                return true;
            }
            if (!hasShiftDown()) selectedEmployees.clear();
            if (hasShiftDown() && selectedEmployees.contains(employee.employeeId())) {
                selectedEmployees.remove(employee.employeeId());
            } else {
                selectedEmployees.add(employee.employeeId());
            }
            notifyEmployeeSelectionChanged();
            return true;
        }

        RoomEntry room = roomAt(mouseX, mouseY);
        if (room != null) {
            if (room.status == RoomEntry.STATUS_ESCAPED) {
                beginSuppressionSelection(room);
                playUiSound(1.1F);
                return true;
            }
            if (selectedRoom == null || !selectedRoom.key().equals(room.key())) selectedWork = -1;
            selectedRoom = room;
            if (room.toolType && room.status == RoomEntry.STATUS_OCCUPIED) {
                // Tool abnormalities skip the four normal work buttons and open the
                // employee picker immediately. The server dispatch path is TOOL_USE.
                selectedWork = 0;
                workSelectedAt = Util.getMillis();
                workCandidates = List.of();
                candidatePages = List.of();
                candidatePage = 0;
                candidateError = "";
                candidatesLoading = true;
                MessageLoader.getLoader().sendToServer(new RequestRoomWorkCandidatesPacket(
                        room.dimension, room.pos, WorkType.values()[0]));
            }
            playUiSound(0.8F);
            return true;
        }

        selectionArmed = true;
        selectingEmployees = false;
        selectionStartX = selectionEndX = mouseX;
        selectionStartY = selectionEndY = mouseY;
        return true;
    }

    private boolean handleRoomRightClick(RoomEntry room) {
        if (hasControlDown()) {
            MessageLoader.getLoader().sendToServer(
                    new RequestNearbyAbnormalitiesPacket(room.dimension, room.pos));
            playUiSound(1.15F);
            return true;
        }
        if (hasShiftDown()) {
            MessageLoader.getLoader().sendToServer(
                    new RoomTeleportRequestPacket(room.dimension, room.pos));
            playUiSound(1.35F);
            saveCurrentView();
            onClose();
            return true;
        }
        if (!selectedEmployees.isEmpty()
                && room.status != RoomEntry.STATUS_UNEXPANDED
                && room.abnormalityCode != null && !room.abnormalityCode.isEmpty()) {
            String containmentArea = roomAreaIds.get(room.key());
            if (containmentArea != null && !containmentArea.isBlank()) {
                dispatchAreaMove(containmentArea);
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(Component.translatable(
                            "message.lobotocraft.monitor.move_to_containment", room.abnormalityName), true);
                }
                return true;
            }
        }
        if (room.abnormalityCode == null || room.abnormalityCode.isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable(room.status == RoomEntry.STATUS_UNEXPANDED
                                ? "message.lobotocraft.monitor.room_unexpanded"
                                : "message.lobotocraft.monitor.room_empty"), true);
            }
            playUiSound(0.55F);
            return true;
        }
        MessageLoader.getLoader().sendToServer(
                new OpenRoomAbnormalityInfoPacket(room.dimension, room.pos));
        playUiSound(0.95F);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pickerRoom != null) {
            return true;
        }
        if (renameBox != null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == 0 && selectionArmed) {
            selectionEndX = mouseX;
            selectionEndY = mouseY;
            double dx = selectionEndX - selectionStartX;
            double dy = selectionEndY - selectionStartY;
            if (dx * dx + dy * dy >= BOX_SELECT_THRESHOLD_SQ) selectingEmployees = true;
            return true;
        }
        if (button == cameraPanButton && (button == 1 || button == 2)) {
            if (button == 1 && moveCommandArmed) {
                double totalX = mouseX - moveCommandStartX;
                double totalY = mouseY - moveCommandStartY;
                if (totalX * totalX + totalY * totalY >= BOX_SELECT_THRESHOLD_SQ) {
                    moveCommandArmed = false;
                    pendingMoveGroupId = null;
                }
            }
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && selectionArmed) {
            selectionEndX = mouseX;
            selectionEndY = mouseY;
            if (selectingEmployees) {
                finishEmployeeBoxSelection();
            } else if (!hasShiftDown() && !isSuppressionSelection()) {
                selectedRoom = null;
                selectedWork = -1;
                if (!selectedEmployees.isEmpty()) {
                    selectedEmployees.clear();
                    notifyEmployeeSelectionChanged();
                }
            }
            selectionArmed = false;
            selectingEmployees = false;
            return true;
        }
        if (button == 1 && cameraPanButton == 1) {
            String targetGroup = moveCommandArmed ? pendingMoveGroupId : null;
            cameraPanButton = -1;
            moveCommandArmed = false;
            pendingMoveGroupId = null;
            if (targetGroup != null) dispatchAreaMove(targetGroup);
            saveCurrentView();
            return true;
        }
        if (button == cameraPanButton) cameraPanButton = -1;
        saveCurrentView();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private Optional<String> moveTargetAreaAt(double mouseX, double mouseY) {
        FacilityLayoutTemplate.Area selected = hitFacilityArea(mouseX, mouseY).orElse(null);
        if (selected == null || selected.role() == FacilityLayoutTemplate.AreaRole.DEPARTMENT_BACKGROUND) {
            return Optional.empty();
        }
        if (selected.role() == FacilityLayoutTemplate.AreaRole.CONTAINMENT_ROOM
                && isUnexpandedContainment(selected.areaId())) return Optional.empty();
        if (worldAreaFor(selected.areaId()).isPresent()) return Optional.of(selected.areaId());
        if (selected.role() != FacilityLayoutTemplate.AreaRole.ELEVATOR_SHAFT) return Optional.empty();

        return facilityLayout.allConnections().stream()
                .filter(connection -> connection.connects(selected.areaId()))
                .map(connection -> connection.fromAreaId().equals(selected.areaId())
                        ? connection.toAreaId() : connection.fromAreaId())
                .map(areaId -> facilityLayout.areaById(areaId).orElse(null))
                .filter(area -> area != null
                        && area.role() == FacilityLayoutTemplate.AreaRole.ELEVATOR_ROOM
                        && worldAreaFor(area.areaId()).isPresent())
                .min(Comparator.comparingDouble(area -> {
                    double dx = area.center().x() - selected.center().x();
                    double dy = area.center().y() - selected.center().y();
                    return dx * dx + dy * dy;
                }))
                .map(FacilityLayoutTemplate.Area::areaId);
    }

    private boolean isUnexpandedContainment(String areaId) {
        for (RoomEntry room : rooms) {
            if (room.status == RoomEntry.STATUS_UNEXPANDED
                    && areaId.equals(roomAreaIds.get(room.key()))) return true;
        }
        return false;
    }

    private void dispatchAreaMove(String groupId) {
        if (groupId == null || selectedEmployees.isEmpty()) return;
        MessageLoader.getLoader().sendToServer(
                new AssignEmployeesMoveAreaPacket(groupId, selectedEmployees));
        movePulseGroupId = groupId;
        movePulseAt = Util.getMillis();
        playUiSound(1.25F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;

        if (pickerRoom != null) {
            int max = Math.max(0, pickerRowCount() - PICKER_MAX_ROWS);
            pickerScroll = Mth.clamp(pickerScroll - (int) Math.signum(delta), 0, max);
            return true;
        }
        if (selectedRoom != null && selectedWork >= 0 && candidatePanelProgress() >= 0.95F
                && candidateBounds().contains(mouseX, mouseY)) {
            if (selectedWork >= 0 && candidatePages.size() > 1) {
                int moved = moveCandidatePage(candidatePage, candidatePages.size(), delta < 0 ? 1 : -1);
                if (moved != candidatePage) {
                    candidatePage = moved;
                    playUiSound(1.2F);
                }
            }
            return true;
        }
        if (selectedRoom != null && detailsBounds().contains(mouseX, mouseY)) return true;

        double worldX = screenToWorldX(mouseX);
        double worldZ = screenToWorldZ(mouseY);

        zoom = Mth.clamp(zoom * (delta > 0 ? 1.18D : 1 / 1.18D), 0.45D, 4.0D);

        panX += mouseX - worldToScreenX(worldX);
        panY += mouseY - worldToScreenY(worldZ);
        saveCurrentView();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerRoom != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePicker();
                return true;
            }
            return true;
        }
        if (renameBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            return renameBox.keyPressed(keyCode, scanCode, modifiers)
                    || renameBox.canConsumeInput()
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_R -> {
                fitToView();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT -> {
                selectFloor(selectedFloor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> {
                selectFloor(selectedFloor + 1);
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renameBox != null) {
            return renameBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        saveCurrentView();
        RoomMapViewState.save();
        RoomViewClient.requestExit();
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    /** 不暂停世界，异想体照常活动，监控才有意义 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------ 工具

    private int statusColor(RoomEntry entry) {
        return switch (entry.status) {
            case RoomEntry.STATUS_OCCUPIED -> STATUS_OCCUPIED;
            case RoomEntry.STATUS_ESCAPED -> STATUS_ESCAPED;
            case RoomEntry.STATUS_LOST -> STATUS_LOST;
            case RoomEntry.STATUS_UNEXPANDED -> STATUS_UNEXPANDED;
            default -> STATUS_EMPTY;
        };
    }

    private static boolean hasActiveOccupant(RoomEntry entry) {
        return entry.status != RoomEntry.STATUS_EMPTY
                && entry.status != RoomEntry.STATUS_UNEXPANDED;
    }

    /** 立绘路径和图鉴界面保持一致；缺图时退化成问号，不会出现紫黑格 */
    private int floorDepartmentColor(Floor floor) {
        if (floor.rooms.isEmpty()) return LobotomyUiTheme.ACCENT;
        return roomDepartmentColor(floor.rooms.get(0));
    }

    private int roomDepartmentColor(RoomEntry entry) {
        String departmentId = roomDepartmentId(entry);
        return departmentId == null || departmentId.isBlank()
                ? LobotomyUiTheme.ACCENT
                : LobotomyUiTheme.departmentColor(departmentId);
    }

    /** Single adapter point for room ownership; never infer it from coordinates. */
    private String roomDepartmentId(RoomEntry entry) {
        return entry.departmentId;
    }

    private ResourceLocation resolvePortrait(String code) {
        return portraitCache.computeIfAbsent(code, key -> {
            ResourceLocation candidate = ResourceUtil.createInstance(
                    "textures/entities/abnormality/" + key.toLowerCase().replace("-", "_") + ".png");
            if (this.minecraft != null
                    && this.minecraft.getResourceManager().getResource(candidate).isPresent()) {
                return candidate;
            }
            return QUESTION_MARK;
        });
    }

    private void drawCentered(GuiGraphics graphics, String text, int centerX, int y, int color) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void playUiSound(float pitch) {
        if (this.minecraft == null) return;
        this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), pitch, 0.55F));
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int withAlpha(int argb, float multiplier) {
        int a = (argb >>> 24);
        if (a == 0) a = 255;
        a = (int) (a * Mth.clamp(multiplier, 0F, 1F));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}
