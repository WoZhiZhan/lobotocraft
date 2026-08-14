package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.client.ui.EmployeeDollRenderer;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.AssignEmployeeDepartmentPacket;
import com.wzz.lobotocraft.network.packet.OpenRoomAbnormalityInfoPacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Facility deployment screen rebuilt on LobCorp's original 1920x1080 DeployUI layout.
 * The server remains authoritative; this screen only mirrors a pending assignment while
 * waiting for the refreshed facility snapshot.
 */
public final class DepartmentAssignmentScreen extends Screen {
    private static final int CANVAS_W = 1920;
    private static final int CANVAS_H = 1080;
    private static final int SIDEBAR_X = 1630;
    private static final int DEPARTMENT_W = 443;
    private static final int DEPARTMENT_H = 307;
    private static final int MIDDLE_DEPARTMENT_H = 562;
    private static final int LOWER_DEPARTMENT_H = 290;
    private static final int ARCHITECTURE_W = 850;
    private static final int ARCHITECTURE_DEPARTMENT_H = 307;
    private static final int DOUBLE_CLICK_MS = 350;
    private static final int VISIBLE_POOL_ROWS = 5;
    private static final int DESCRIPTION_VISIBLE_LINES = 4;
    private static final int AREA_TRANSITION_MS = 360;

    private static final int BLACK = 0xFF000000;
    private static final int PANEL_BLACK = 0xF7080908;
    private static final int SIDE_YELLOW = 0xFFFFFF72;
    private static final int SIDE_YELLOW_DIM = 0xFF9C9A50;
    private static final int TEXT_PALE = 0xFFFFFFB0;
    private static final int TEXT_DIM = 0xFFAAA879;
    private static final int BONUS_BLUE = 0xFF52C7F2;

    private static final String[] AREA_NAMES = {"ASIYAH", "BRIAH", "ATZILUTH"};
    private static final EmployeeDepartment[][] AREA_DEPARTMENTS = {
            {EmployeeDepartment.CONTROL, EmployeeDepartment.SAFETY,
                    EmployeeDepartment.INFORMATION, EmployeeDepartment.TRAINING},
            {EmployeeDepartment.WELFARE, EmployeeDepartment.CENTRAL,
                    EmployeeDepartment.DISCIPLINARY},
            {EmployeeDepartment.RECORD, EmployeeDepartment.EXTRACTION,
                    EmployeeDepartment.ARCHITECTURE}
    };

    private static final ResourceLocation BG_UPPER = texture("bg_upper.png");
    private static final ResourceLocation BG_MIDDLE = texture("bg_middle.png");
    private static final ResourceLocation BG_LOWER = texture("bg_lower.png");
    private static final ResourceLocation AGENT_SLOT_EMPTY = texture("agent_slot_empty.png");

    private static UUID rememberedEmployee;
    private static int rememberedArea;

    private CompoundTag snapshot;
    private final List<CompoundTag> employees = new ArrayList<>();
    private final List<CompoundTag> spareEmployees = new ArrayList<>();
    private final List<CompoundTag> abnormalities = new ArrayList<>();
    private final List<DepartmentBox> departmentBoxes = new ArrayList<>();
    private final List<EmployeeHit> employeeHits = new ArrayList<>();
    private final List<AbnormalityHit> abnormalityHits = new ArrayList<>();
    private final Map<String, ResourceLocation> abnormalityPortraits = new HashMap<>();
    private final Set<String> missingAbnormalityPortraits = new HashSet<>();
    private final Map<EmployeeDepartment, PanelState> panelStates =
            new EnumMap<>(EmployeeDepartment.class);
    private final Map<EmployeeDepartment, Integer> descriptionScroll =
            new EnumMap<>(EmployeeDepartment.class);

    private UUID selectedEmployee;
    private int activeArea;
    private int employeeScroll;
    private boolean waitingForServer;
    private long waitStartedAt;
    private String hoveredControl = "";
    private String pressedControl = "";
    private long pressedUntil;
    private EmployeeDepartment flashDepartment;
    private long flashUntil;
    private EmployeeDepartment blockedDepartment;
    private long blockedUntil;
    private UUID lastEmployeeClick;
    private long lastEmployeeClickTime;
    private EmployeeHit dragCandidate;
    private CompoundTag draggedEmployee;
    private boolean dragFromDepartment;
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private long lastAreaScrollTime;
    private long areaTransitionStarted = -1L;
    private int areaTransitionDirection;
    private String hoveredEgoName = "";

    private float canvasScale = 1.0F;
    private float canvasX;
    private float canvasY;

    public DepartmentAssignmentScreen(CompoundTag snapshot) {
        this(snapshot, snapshot.hasUUID("SelectedEmployee")
                ? snapshot.getUUID("SelectedEmployee") : rememberedEmployee);
    }

    public DepartmentAssignmentScreen(CompoundTag snapshot, UUID preferredEmployee) {
        super(Component.translatable("screen.lobotocraft.department_assignment.title"));
        this.snapshot = snapshot.copy();
        rebuildEmployees(preferredEmployee);
        rebuildAbnormalities();
        CompoundTag preferred = findEmployee(selectedEmployee);
        int preferredArea = preferred == null ? -1 : areaFor(preferred.getString("DepartmentId"));
        activeArea = preferredArea >= 0 ? preferredArea : Mth.clamp(rememberedArea, 0, 2);
        rememberedArea = activeArea;
        rebuildDepartmentBoxes();
    }

    /** Applies the authoritative server snapshot without recreating the screen. */
    public void updateSnapshot(CompoundTag updated) {
        UUID keep = updated.hasUUID("SelectedEmployee")
                ? updated.getUUID("SelectedEmployee") : selectedEmployee;
        snapshot = updated.copy();
        waitingForServer = false;
        rebuildEmployees(keep);
        rebuildAbnormalities();
        employeeScroll = Mth.clamp(employeeScroll, 0, maxEmployeeScroll());
    }

    private void rebuildAbnormalities() {
        abnormalities.clear();
        ListTag entries = snapshot.getList("Abnormalities", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            String code = entry.getString("Code");
            String departmentId = entry.getString("DepartmentId");
            if (code.isBlank() || departmentId.isBlank()) continue;
            abnormalities.add(entry.copy());
        }
    }

    @Override
    public void init() {
        updateCanvasTransform();
        rebuildDepartmentBoxes();
    }

    private void rebuildEmployees(UUID preferredEmployee) {
        employees.clear();
        spareEmployees.clear();
        ListTag entries = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag employee = entries.getCompound(index);
            if (employee.hasUUID("Id") && "AI_EMPLOYEE".equals(employee.getString("ActorType"))) {
                CompoundTag copy = employee.copy();
                employees.add(copy);
                if (EmployeeDepartment.UNASSIGNED.id().equals(copy.getString("DepartmentId"))) {
                    spareEmployees.add(copy);
                }
            }
        }
        spareEmployees.sort(Comparator
                .comparingInt((CompoundTag employee) -> employee.getInt("EmployeeLevel")).reversed()
                .thenComparing(employee -> employee.getString("Name"), String.CASE_INSENSITIVE_ORDER));

        selectedEmployee = findEmployee(preferredEmployee) == null ? null : preferredEmployee;
        if (selectedEmployee == null && !spareEmployees.isEmpty()) {
            selectedEmployee = spareEmployees.get(0).getUUID("Id");
        } else if (selectedEmployee == null && !employees.isEmpty()) {
            selectedEmployee = employees.get(0).getUUID("Id");
        }
        rememberedEmployee = selectedEmployee;
    }

    private void updateCanvasTransform() {
        canvasScale = Math.min((float) width / CANVAS_W, (float) height / CANVAS_H);
        canvasX = (width - CANVAS_W * canvasScale) * 0.5F;
        canvasY = (height - CANVAS_H * canvasScale) * 0.5F;
    }

    private void rebuildDepartmentBoxes() {
        departmentBoxes.clear();
        if (activeArea == 0) {
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.CONTROL, 591, 228,
                    DEPARTMENT_W, DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.SAFETY, 41, 586,
                    DEPARTMENT_W, DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.INFORMATION, 592, 586,
                    DEPARTMENT_W, DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.TRAINING, 1141, 586,
                    DEPARTMENT_W, DEPARTMENT_H));
        } else if (activeArea == 1) {
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.WELFARE, 40, 346,
                    DEPARTMENT_W, MIDDLE_DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.CENTRAL, 591, 346,
                    DEPARTMENT_W, MIDDLE_DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.DISCIPLINARY, 1142, 346,
                    DEPARTMENT_W, MIDDLE_DEPARTMENT_H));
        } else {
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.RECORD, 39, 190,
                    DEPARTMENT_W, LOWER_DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.EXTRACTION, 1142, 190,
                    DEPARTMENT_W, LOWER_DEPARTMENT_H));
            departmentBoxes.add(new DepartmentBox(EmployeeDepartment.ARCHITECTURE,
                    (SIDEBAR_X - ARCHITECTURE_W) / 2, 640,
                    ARCHITECTURE_W, ARCHITECTURE_DEPARTMENT_H));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, BLACK);
        updateCanvasTransform();
        double logicalMouseX = toLogicalX(mouseX);
        double logicalMouseY = toLogicalY(mouseY);
        employeeHits.clear();
        abnormalityHits.clear();
        hoveredEgoName = "";

        graphics.pose().pushPose();
        graphics.pose().translate(canvasX, canvasY, 0.0F);
        graphics.pose().scale(canvasScale, canvasScale, 1.0F);
        renderOriginalBackground(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, areaTransitionOffset(), 0.0F);
        renderLayerDecorations(graphics);
        for (DepartmentBox box : departmentBoxes) {
            renderDepartmentPanel(graphics, box, logicalMouseX, logicalMouseY);
        }
        renderAreaNavigation(graphics, logicalMouseX, logicalMouseY);
        graphics.pose().popPose();
        renderAreaTransitionOverlay(graphics);
        renderSidebar(graphics, logicalMouseX, logicalMouseY);
        renderDraggedEmployee(graphics, logicalMouseX, logicalMouseY);
        graphics.pose().popPose();

        updateHoverFeedback(logicalMouseX, logicalMouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        AbnormalityHit hoveredAbnormality = abnormalityAt(logicalMouseX, logicalMouseY);
        if (hoveredAbnormality != null) {
            graphics.renderComponentTooltip(font, abnormalityTooltip(hoveredAbnormality.abnormality),
                    mouseX, mouseY);
        } else if (!hoveredEgoName.isBlank()) {
            graphics.renderTooltip(font, Component.literal(hoveredEgoName), mouseX, mouseY);
        }
    }

    private void renderOriginalBackground(GuiGraphics graphics) {
        ResourceLocation background = switch (activeArea) {
            case 1 -> BG_MIDDLE;
            case 2 -> BG_LOWER;
            default -> BG_UPPER;
        };
        LobotomyUiRenderer.blitScaled(graphics, background, 0, 0, CANVAS_W, CANVAS_H,
                activeArea == 1 ? 1622 : activeArea == 2 ? 1921 : 1920,
                activeArea == 1 ? 1082 : activeArea == 2 ? 1081 : 1080);
        graphics.fill(SIDEBAR_X, 0, CANVAS_W, CANVAS_H, 0xF9000000);
    }

    private float areaTransitionOffset() {
        if (areaTransitionStarted < 0L) return 0.0F;
        float progress = Mth.clamp((Util.getMillis() - areaTransitionStarted)
                / (float) AREA_TRANSITION_MS, 0.0F, 1.0F);
        float remaining = 1.0F - progress;
        return areaTransitionDirection * 42.0F * remaining * remaining;
    }

    private void renderAreaTransitionOverlay(GuiGraphics graphics) {
        if (areaTransitionStarted < 0L) return;
        float progress = Mth.clamp((Util.getMillis() - areaTransitionStarted)
                / (float) AREA_TRANSITION_MS, 0.0F, 1.0F);
        if (progress >= 1.0F) {
            areaTransitionStarted = -1L;
            return;
        }
        int alpha = Mth.clamp(Math.round((1.0F - progress) * 72.0F), 0, 72);
        graphics.fill(0, 0, SIDEBAR_X, CANVAS_H, alpha << 24);
    }

    private void renderLayerDecorations(GuiGraphics graphics) {
        if (activeArea == 0) {
            int color = LobotomyUiRenderer.withAlpha(
                    departmentColor(EmployeeDepartment.CONTROL), 0.76F);
            drawPolyline(graphics, color,
                    619, 226, 683, 164, 1017, 164, 1080, 101, 1455, 101, 1511, 45);
            drawPolyline(graphics, color,
                    636, 226, 695, 178, 1032, 178, 1093, 116, 1469, 116, 1524, 60);
        }
    }

    private void renderAreaNavigation(GuiGraphics graphics, double mouseX, double mouseY) {
        int color = switch (activeArea) {
            case 0 -> 0xFF050806;
            case 1 -> 0xFF20F0B3;
            default -> 0xFFF4F4F0;
        };
        if (activeArea == 2) {
            drawScaledText(graphics, AREA_NAMES[activeArea], 48, 1000, 5.0F, color, 420);
            boolean hovered = inside(mouseX, mouseY, 47, 947, 90, 50);
            drawUpTriangle(graphics, 58, 963, 66, 23, hovered ? 0xFFFFFFFF : color);
        } else {
            drawScaledText(graphics, AREA_NAMES[activeArea], activeArea == 0 ? 10 : 24,
                    48, 5.0F, color, 420);
            if (activeArea == 1) {
                boolean upHovered = inside(mouseX, mouseY, 55, 4, 94, 42);
                drawUpTriangle(graphics, 69, 14, 66, 23,
                        upHovered ? 0xFFFFFFFF : color);
            }
            boolean downHovered = inside(mouseX, mouseY, 55, 104, 94, 48);
            drawDownTriangle(graphics, 69, 114, 66, 23,
                    downHovered ? 0xFFFFFFFF : color);
        }
    }

    private void renderDepartmentPanel(GuiGraphics graphics, DepartmentBox box,
                                       double mouseX, double mouseY) {
        int color = departmentColor(box.department);
        boolean selectedHere = selectedInDepartment(box.department);
        boolean flashed = box.department == flashDepartment && Util.getMillis() < flashUntil;
        boolean blocked = box.department == blockedDepartment && Util.getMillis() < blockedUntil;
        int x = box.x;
        int y = box.y;
        int frameColor = blocked ? LobotomyUiTheme.ALERT_RED : color;

        graphics.fill(x + 1, y + 1, x + box.width - 1, y + box.height - 1, PANEL_BLACK);
        if (selectedHere || flashed || blocked) {
            graphics.fill(x + 1, y + 1, x + box.width - 1, y + box.height - 1,
                    LobotomyUiRenderer.withAlpha(frameColor,
                            blocked ? 0.14F : selectedHere ? 0.075F : 0.035F));
        }
        drawOutline(graphics, x, y, box.width, box.height,
                selectedHere || flashed ? frameColor
                        : LobotomyUiRenderer.withAlpha(frameColor, 0.76F));
        for (int stripe = 0; stripe < 3; stripe++) {
            int stripeX = x + 10 + stripe * 23;
            drawCrispLine(graphics, stripeX, y + 38, stripeX + 20, y + 4,
                    LobotomyUiRenderer.withAlpha(frameColor, 0.72F));
        }
        if (isDepartmentOpen(box.department)) {
            renderDepartmentTabs(graphics, box, mouseX, mouseY, frameColor);
        }
        graphics.fill(x + 7, y + 42, x + box.width - 7, y + 43,
                LobotomyUiRenderer.withAlpha(frameColor, 0.68F));

        int nameX = x + box.width - 204;
        int nameY = y - 42;
        drawDepartmentNamePlate(graphics, nameX, nameY, 204, 38, frameColor);
        drawCenteredScaledText(graphics, departmentName(box.department), nameX + 110,
                nameY + 6, 3.0F, frameColor, 176);

        if (!isDepartmentOpen(box.department)) {
            renderLockedDepartment(graphics, box, x, y, frameColor);
            return;
        }

        switch (panelState(box.department)) {
            case FUNCTION -> renderDepartmentFunction(graphics, box, x, y, frameColor);
            case DESCRIPTION -> renderDepartmentDescription(graphics, box, x, y, frameColor);
            case RESEARCH -> renderDepartmentResearch(graphics, box, x, y, frameColor);
            default -> renderDepartmentMain(graphics, box, x, y, frameColor, mouseX, mouseY);
        }
    }

    private void renderDepartmentTabs(GuiGraphics graphics, DepartmentBox box,
                                       double mouseX, double mouseY, int color) {
        String[] labels = {
                tr("screen.lobotocraft.department_assignment.tab.function"),
                tr("screen.lobotocraft.department_assignment.tab.description"),
                tr("screen.lobotocraft.department_assignment.tab.research")
        };
        PanelState[] states = {PanelState.FUNCTION, PanelState.DESCRIPTION, PanelState.RESEARCH};
        PanelState current = panelState(box.department);
        if (current != PanelState.MAIN) {
            String label = tabLabel(current);
            drawScaledText(graphics, label, box.x + 89, box.y + 13,
                    2.0F, color, Math.max(96, box.width - 180));
            int closeX = box.x + box.width - 30;
            boolean closeHovered = inside(mouseX, mouseY, closeX - 4, box.y + 6, 25, 25);
            drawDepartmentClose(graphics, closeX, box.y + 9,
                    closeHovered ? 0xFFFFFFFF : color);
            return;
        }

        if (box.department == EmployeeDepartment.ARCHITECTURE) {
            int tabX = box.x + box.width - 199;
            boolean hovered = inside(mouseX, mouseY, tabX, box.y + 1, 184, 40);
            if (hovered) {
                graphics.fill(tabX + 2, box.y + 2, tabX + 182, box.y + 40,
                        LobotomyUiRenderer.withAlpha(color, 0.07F));
            }
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.department_assignment.tab.description"),
                    tabX + 92, box.y + 13,
                    2.0F, hovered ? 0xFFFFFFFF : color, 164);
            return;
        }

        int tabAreaX = box.x + 72;
        int tabAreaW = box.width - 79;
        int tabW = tabAreaW / states.length;
        for (int index = 0; index < states.length; index++) {
            int tabX = tabAreaX + index * tabW;
            int currentTabW = index == states.length - 1 ? tabAreaW - tabW * index : tabW;
            boolean hovered = inside(mouseX, mouseY, tabX, box.y + 1, currentTabW, 40);
            boolean active = current == states[index];
            if (active || hovered) {
                graphics.fill(tabX + 2, box.y + 2, tabX + currentTabW - 2, box.y + 40,
                        LobotomyUiRenderer.withAlpha(color, active ? 0.18F : 0.07F));
            }
            if (active) {
                graphics.fill(tabX + 5, box.y + 39, tabX + currentTabW - 5, box.y + 41, color);
            }
            drawCenteredScaledText(graphics, labels[index], tabX + currentTabW / 2, box.y + 13,
                    2.0F, hovered ? 0xFFFFFFFF : color, currentTabW - 12);
        }
    }

    private void renderDepartmentFunction(GuiGraphics graphics, DepartmentBox box,
                                          int x, int y, int color) {
        drawScaledText(graphics,
                tr("screen.lobotocraft.department_assignment.function.clerk_bonus"),
                x + 18, y + 53, 2.0F, color, 120);
        drawWrappedScaledText(graphics, departmentOfficerBonus(box.department),
                x + 18, y + 76, 2.0F, TEXT_PALE, box.width - 36, 2);
        graphics.fill(x + 18, y + 105, x + box.width - 18, y + 106,
                LobotomyUiRenderer.withAlpha(color, 0.34F));
        drawScaledText(graphics,
                tr("screen.lobotocraft.department_assignment.function.tenure_bonus"),
                x + 18, y + 115, 2.0F, color, 170);
        drawWrappedScaledText(graphics, departmentTenureBonus(box.department),
                x + 18, y + 138, 2.0F, TEXT_PALE, box.width - 36, 2);
    }

    private void renderDepartmentDescription(GuiGraphics graphics, DepartmentBox box,
                                              int x, int y, int color) {
        drawScaledText(graphics, sephirah(box.department) + " / " + departmentName(box.department),
                x + 18, y + 55, 2.0F, color, box.width - 58);
        graphics.fill(x + 18, y + 75, x + box.width - 18, y + 76,
                LobotomyUiRenderer.withAlpha(color, 0.34F));
        List<net.minecraft.util.FormattedCharSequence> lines = wrapScaledText(
                departmentDescription(box.department), 2.0F, box.width - 52);
        int visibleLines = Math.max(DESCRIPTION_VISIBLE_LINES, (box.height - 108) / 22);
        int maximum = Math.max(0, lines.size() - visibleLines);
        int offset = Mth.clamp(descriptionScroll.getOrDefault(box.department, 0), 0, maximum);
        descriptionScroll.put(box.department, offset);
        drawWrappedScaledLines(graphics, lines, offset, x + 18, y + 88,
                2.0F, TEXT_PALE, visibleLines);
        if (maximum > 0) {
            int trackX = x + box.width - 12;
            int trackY = y + 88;
            int trackH = box.height - 108;
            graphics.fill(trackX, trackY, trackX + 1, trackY + trackH,
                    LobotomyUiRenderer.withAlpha(color, 0.30F));
            int thumbH = Math.max(14,
                    trackH * visibleLines / Math.max(1, lines.size()));
            int thumbY = trackY + (trackH - thumbH) * offset / maximum;
            graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbH, color);
        }
    }

    private void renderDepartmentResearch(GuiGraphics graphics, DepartmentBox box,
                                          int x, int y, int color) {
        drawCenteredScaledText(graphics,
                tr("screen.lobotocraft.department_assignment.tab.research"),
                x + box.width / 2, y + 64,
                2.0F, color, box.width - 40);
        graphics.fill(x + 86, y + 91, x + box.width - 86, y + 92,
                LobotomyUiRenderer.withAlpha(color, 0.35F));
        drawCenteredScaledText(graphics,
                tr("screen.lobotocraft.department_assignment.research.empty"),
                x + box.width / 2, y + 116,
                2.0F, TEXT_DIM, box.width - 40);
    }

    private void renderDepartmentMain(GuiGraphics graphics, DepartmentBox box,
                                      int x, int y, int color,
                                      double mouseX, double mouseY) {
        List<CompoundTag> assigned = employeesInDepartment(box.department);
        if (box.department == EmployeeDepartment.ARCHITECTURE) {
            renderArchitectureMain(graphics, box, assigned, x, y, color, mouseX, mouseY);
            return;
        }

        List<CompoundTag> upperAssigned = box.department == EmployeeDepartment.CENTRAL
                ? employeesInCentralSection(0) : assigned;
        renderEmployeeRow(graphics, upperAssigned, 0, x + 14, y + 50,
                78, 111, 6, employeeCapacity(box.department, 0), color, mouseX, mouseY);
        renderAbnormalities(graphics, box.department, 0, x + 14, y + 172,
                box.width - 28, 104, 4, color, mouseX, mouseY);
        graphics.fill(x + 14, y + 290, x + box.width - 14, y + 291,
                LobotomyUiRenderer.withAlpha(color, 0.48F));

        if (box.department == EmployeeDepartment.CENTRAL) {
            if (isCentralLowerOpen()) {
                renderEmployeeRow(graphics, employeesInCentralSection(1), 0, x + 14, y + 311,
                        78, 111, 6, employeeCapacity(box.department, 1), color, mouseX, mouseY);
                renderAbnormalities(graphics, box.department, 1, x + 14, y + 433,
                        box.width - 28, 104, 4, color, mouseX, mouseY);
            } else {
                renderLockedSection(graphics, x + 14, y + 311,
                        box.width - 28, box.height - 325, color,
                        tr("screen.lobotocraft.department_assignment.central_lower_locked"));
            }
        }
    }

    private void renderLockedDepartment(GuiGraphics graphics, DepartmentBox box,
                                        int x, int y, int color) {
        graphics.fill(x + 1, y + 42, x + box.width - 1, y + box.height - 1, 0xD8030403);
        drawOutline(graphics, x, y, box.width, box.height,
                LobotomyUiRenderer.withAlpha(LobotomyUiTheme.LINE_FAINT, 0.55F));
        drawCenteredScaledText(graphics,
                tr("screen.lobotocraft.department_assignment.locked"),
                x + box.width / 2, y + box.height / 2 - 20,
                2.5F, LobotomyUiRenderer.withAlpha(color, 0.82F), box.width - 50);
        drawCenteredScaledText(graphics, departmentUnlockRequirement(box.department),
                x + box.width / 2, y + box.height / 2 + 16, 1.8F,
                TEXT_DIM, box.width - 55);
    }

    private void renderLockedSection(GuiGraphics graphics, int x, int y,
                                     int width, int height, int color, String text) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xD8030403);
        drawOutline(graphics, x, y, width, height,
                LobotomyUiRenderer.withAlpha(color, 0.42F));
        drawCenteredScaledText(graphics, text, x + width / 2, y + Math.max(18, height / 2 - 10),
                1.8F, LobotomyUiRenderer.withAlpha(color, 0.82F), width - 20);
    }

    private String departmentUnlockRequirement(EmployeeDepartment department) {
        return switch (department) {
            case INFORMATION -> tr("screen.lobotocraft.department_assignment.requirement.department",
                    departmentName(EmployeeDepartment.CONTROL));
            case TRAINING, SAFETY -> tr("screen.lobotocraft.department_assignment.requirement.department",
                    departmentName(EmployeeDepartment.INFORMATION));
            case CENTRAL -> tr("screen.lobotocraft.department_assignment.requirement.upper");
            case WELFARE, DISCIPLINARY -> tr("screen.lobotocraft.department_assignment.requirement.department",
                    departmentName(EmployeeDepartment.CENTRAL));
            case RECORD, EXTRACTION -> tr("screen.lobotocraft.department_assignment.requirement.middle");
            case ARCHITECTURE -> tr("screen.lobotocraft.department_assignment.requirement.departments",
                    departmentName(EmployeeDepartment.RECORD),
                    departmentName(EmployeeDepartment.EXTRACTION));
            default -> "";
        };
    }

    private void renderArchitectureMain(GuiGraphics graphics, DepartmentBox box,
                                        List<CompoundTag> assigned,
                                        int x, int y, int color,
                                        double mouseX, double mouseY) {
        int ordealColor = 0xFFA42A2A;
        int ordealW = 405;
        int employeeX = x + ordealW + 25;
        graphics.fill(x + 12, y + 51, x + 12 + ordealW, y + 157, 0xE6090505);
        drawOutline(graphics, x + 11, y + 50, ordealW + 2, 108, ordealColor);
        graphics.fill(x + 18, y + 57, x + 12 + ordealW - 6, y + 59,
                LobotomyUiRenderer.withAlpha(ordealColor, 0.62F));

        renderEmployeeRow(graphics, assigned, 0, employeeX, y + 50,
                78, 111, 6, employeeCapacity(box.department, 0), color, mouseX, mouseY);
        renderAbnormalities(graphics, box.department, 0, x + 11, y + 172,
                box.width - 22, 104, 8, color, mouseX, mouseY);
    }

    private void renderEmployeeRow(GuiGraphics graphics, List<CompoundTag> assigned,
                                   int firstEmployee, int startX, int startY,
                                   int slotW, int slotH, int gap, int capacity,
                                   int color, double mouseX, double mouseY) {
        int hoveredIndex = -1;
        for (int column = 0; column < 5; column++) {
            int employeeIndex = firstEmployee + column;
            int slotX = startX + column * (slotW + gap);
            boolean slotHovered = inside(mouseX, mouseY, slotX, startY, slotW, slotH);
            if (column >= capacity) {
                renderUnavailableSlot(graphics, slotX, startY, slotW, slotH, color);
            } else if (employeeIndex < assigned.size()) {
                CompoundTag employee = assigned.get(employeeIndex);
                employeeHits.add(new EmployeeHit(employee, slotX, startY, slotW, slotH, true));
                if (isDragged(employee)) {
                    renderVacatedSlot(graphics, slotX, startY, slotW, slotH, color);
                    continue;
                }
                if (slotHovered) {
                    hoveredIndex = employeeIndex;
                    continue;
                }
                boolean selected = employee.getUUID("Id").equals(selectedEmployee);
                renderAgentSlot(graphics, employee, slotX, startY, slotW, slotH,
                        color, false, selected, true);
            } else {
                renderEmptySlot(graphics, slotX, startY, slotW, slotH, color, slotHovered);
            }
        }
        if (hoveredIndex >= 0) {
            CompoundTag employee = assigned.get(hoveredIndex);
            int column = hoveredIndex - firstEmployee;
            int slotX = startX + column * (slotW + gap);
            renderAgentSlot(graphics, employee, slotX, startY, slotW, slotH, color, true,
                    employee.getUUID("Id").equals(selectedEmployee), true);
        }
    }

    private void renderAbnormalities(GuiGraphics graphics, EmployeeDepartment department,
                                     int centralSection, int x, int y, int width, int height,
                                     int count, int departmentColor,
                                     double mouseX, double mouseY) {
        List<CompoundTag> assigned = abnormalitiesInDepartment(department, centralSection);
        int gap = count >= 8 ? 4 : 7;
        int slotW = (width - gap * (count - 1)) / count;
        for (int index = 0; index < count; index++) {
            int slotX = x + index * (slotW + gap);
            if (index < assigned.size()) {
                CompoundTag abnormality = assigned.get(index);
                abnormalityHits.add(new AbnormalityHit(abnormality, slotX, y, slotW, height));
                renderAbnormalityCard(graphics, abnormality, slotX, y, slotW, height,
                        inside(mouseX, mouseY, slotX, y, slotW, height));
                continue;
            }
            graphics.fill(slotX + 1, y + 1, slotX + slotW - 1, y + height - 1,
                    0xE5070908);
            drawOutline(graphics, slotX, y, slotW, height,
                    LobotomyUiRenderer.withAlpha(departmentColor, 0.68F));
            drawCenteredScaledText(graphics, "?", slotX + slotW / 2,
                    y + Math.max(10, height / 2 - 10), 3.0F,
                    LobotomyUiRenderer.withAlpha(departmentColor, 0.70F), slotW - 8);
            graphics.fill(slotX + 5, y + height - 13, slotX + slotW - 5,
                    y + height - 11, LobotomyUiRenderer.withAlpha(departmentColor, 0.52F));
        }
    }

    private void renderAbnormalityCard(GuiGraphics graphics, CompoundTag abnormality,
                                       int x, int y, int width, int height, boolean hovered) {
        String code = abnormality.getString("Code");
        String risk = abnormality.getString("Risk");
        int color = abnormalityRiskColor(abnormality);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xF2050706);
        if (hovered) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    LobotomyUiRenderer.withAlpha(color, 0.14F));
        }
        drawOutline(graphics, x, y, width, height,
                LobotomyUiRenderer.withAlpha(color, hovered ? 1.0F : 0.82F));
        drawCenteredScaledText(graphics, risk.isBlank() ? "UNKNOWN" : risk,
                x + width / 2, y + 4, 2.0F, color, width - 8);
        graphics.fill(x + 5, y + 22, x + width - 5, y + 23,
                LobotomyUiRenderer.withAlpha(color, 0.42F));

        int portraitSize = Math.max(24, Math.min(width - 14, height - 42));
        int portraitX = x + (width - portraitSize) / 2;
        int portraitY = y + 26;
        graphics.fill(portraitX, portraitY, portraitX + portraitSize,
                portraitY + portraitSize, 0xFF020403);
        ResourceLocation portrait = abnormalityPortrait(code);
        if (portrait != null) {
            graphics.blit(portrait, portraitX, portraitY, 0, 0,
                    portraitSize, portraitSize, portraitSize, portraitSize);
        } else {
            drawCenteredScaledText(graphics, "?", x + width / 2,
                    portraitY + portraitSize / 2 - 10, 3.0F,
                    LobotomyUiRenderer.withAlpha(color, 0.75F), portraitSize - 6);
        }
        drawCenteredScaledText(graphics, code, x + width / 2,
                y + height - 14, 1.6F, hovered ? 0xFFFFFFFF : TEXT_DIM, width - 8);
    }

    private List<CompoundTag> abnormalitiesInDepartment(EmployeeDepartment department,
                                                         int centralSection) {
        List<CompoundTag> result = new ArrayList<>();
        for (CompoundTag abnormality : abnormalities) {
            if (!department.id().equals(abnormality.getString("DepartmentId"))) continue;
            if (department == EmployeeDepartment.CENTRAL
                    && abnormality.getInt("CentralSection") != centralSection) continue;
            result.add(abnormality);
        }
        return result;
    }

    private ResourceLocation abnormalityPortrait(String code) {
        if (code == null || code.isBlank() || missingAbnormalityPortraits.contains(code)) {
            return null;
        }
        ResourceLocation cached = abnormalityPortraits.get(code);
        if (cached != null) return cached;
        ResourceLocation candidate = ResourceUtil.createInstance(
                "textures/entities/abnormality/" + code.toLowerCase().replace("-", "_") + ".png");
        if (minecraft != null && minecraft.getResourceManager().getResource(candidate).isPresent()) {
            abnormalityPortraits.put(code, candidate);
            return candidate;
        }
        missingAbnormalityPortraits.add(code);
        return null;
    }

    private static int abnormalityRiskColor(CompoundTag abnormality) {
        int color = abnormality.getInt("RiskColor");
        return color == 0 ? riskColor(abnormality.getString("Risk")) : color;
    }

    private static List<Component> abnormalityTooltip(CompoundTag abnormality) {
        String name = abnormality.getString("Name");
        if (name.isBlank()) name = abnormality.getString("Code");
        return List.of(
                Component.literal(name),
                Component.literal(abnormality.getString("Code") + "  "
                        + abnormality.getString("Risk"))
        );
    }

    private void renderSidebar(GuiGraphics graphics, double mouseX, double mouseY) {
        renderSpareAgentList(graphics, mouseX, mouseY);
        renderSidebarHeader(graphics, mouseX, mouseY);
    }

    private void renderSidebarHeader(GuiGraphics graphics, double mouseX, double mouseY) {
        int x = 1631;
        graphics.fill(x, 8, 1912, 400, 0xFB020301);
        drawHazardFrame(graphics, x, 8, 281, 392, SIDE_YELLOW);

        int egoButtonX = 1641;
        int buttonY = 16;
        int buttonW = 126;
        int buttonH = 48;
        renderTopActionButton(graphics, egoButtonX, buttonY, buttonW, buttonH,
                tr("screen.lobotocraft.ego_assignment.title"), "ego", mouseX, mouseY);
        renderTopActionButton(graphics, 1776, buttonY, buttonW, buttonH,
                tr("screen.lobotocraft.department_assignment.finish"),
                "return", mouseX, mouseY);

        EmployeeHit hoveredEmployee = employeeAt(mouseX, mouseY);
        CompoundTag detail = hoveredEmployee == null ? selectedEmployee() : hoveredEmployee.employee;
        renderEmployeeDetail(graphics, detail, mouseX, mouseY);
        if (waitingForServer) {
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.department_assignment.deploying"), 1771, 380,
                    2.0F, LobotomyUiTheme.WARN_YELLOW, 240);
        } else if (Util.getMillis() < blockedUntil) {
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.department_assignment.department_full"), 1771, 380,
                    2.0F, LobotomyUiTheme.ALERT_RED, 240);
        }
    }

    private void renderTopActionButton(GuiGraphics graphics, int x, int y,
                                       int width, int height, String label, String control,
                                       double mouseX, double mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        int offset = isPressed(control) ? 2 : 0;
        graphics.fill(x + offset, y + offset, x + width + offset,
                y + height + offset, 0xFF080A06);
        drawHazardFrame(graphics, x + offset, y + offset, width, height,
                hovered ? 0xFFFFFFFF : SIDE_YELLOW);
        drawCenteredScaledText(graphics, label, x + width / 2 + offset,
                y + 12 + offset, 2.0F, hovered ? 0xFFFFFFFF : TEXT_PALE, width - 14);
    }

    private void renderEmployeeDetail(GuiGraphics graphics, CompoundTag employee,
                                      double mouseX, double mouseY) {
        graphics.fill(1644, 72, 1899, 73, SIDE_YELLOW_DIM);
        if (employee == null) {
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.ego_assignment.no_employee"), 1771, 156,
                    3.0F, TEXT_DIM, 240);
            return;
        }

        drawCenteredScaledText(graphics, employee.getString("Name"), 1771, 79,
                3.0F, TEXT_PALE, 246);
        drawCenteredScaledText(graphics,
                displayDepartment(employee) + "  /  LV "
                        + roman(employee.getInt("EmployeeLevel")),
                1771, 102, 2.0F, TEXT_DIM, 246);

        drawAgentSlotShell(graphics, 1644, 122, 78, 92, SIDE_YELLOW_DIM, 0.04F);
        drawInventoryDoll(graphics, employee, 1683, 128, 64, 80);
        if (!employee.contains("Health") || !employee.contains("MaxHealth")) {
            drawCenteredScaledText(graphics, tr("status.lobotocraft.offline"), 1683, 216,
                    2.0F, TEXT_DIM, 92);
        }

        renderEgoDetailCard(graphics, employee, "weapon", 1730, 122, 168, 45,
                mouseX, mouseY);
        renderEgoDetailCard(graphics, employee, "armor", 1730, 174, 168, 45,
                mouseX, mouseY);

        renderVitalStat(graphics, employee, 1634, 236, tr("stat.lobotocraft.fortitude"), "FortitudeLevel",
                "HP", "BaseMaxHealth", "HealthBonus", "Health", "MaxHealth",
                LobotomyUiTheme.STAT_FORTITUDE);
        renderVitalStat(graphics, employee, 1772, 236, tr("stat.lobotocraft.prudence"), "PrudenceLevel",
                "SP", "BaseMaxMental", "MentalBonus", "Mental", "MaxMental",
                LobotomyUiTheme.STAT_PRUDENCE);
        renderDerivedStat(graphics, employee, 1634, 305, tr("stat.lobotocraft.temperance"), "TemperanceLevel",
                "Temperance",
                tr("screen.lobotocraft.ego_assignment.success_rate"),
                "WorkSuccessBase", "WorkSuccessBonus", "",
                tr("screen.lobotocraft.ego_assignment.work_speed"),
                "WorkSpeedBase", "WorkSpeedBonus", "",
                LobotomyUiTheme.STAT_TEMPERANCE);
        renderDerivedStat(graphics, employee, 1772, 305, tr("stat.lobotocraft.justice"), "JusticeLevel",
                "Justice",
                tr("screen.lobotocraft.ego_assignment.attack_speed"),
                "AttackSpeedBase", "AttackSpeedBonus", "",
                tr("screen.lobotocraft.ego_assignment.movement_speed"),
                "MovementSpeedBase", "MovementSpeedBonus", "",
                LobotomyUiTheme.STAT_JUSTICE);
    }

    private void renderVitalStat(GuiGraphics graphics, CompoundTag employee,
                                 int x, int y, String label, String levelKey,
                                 String valueLabel, String baseKey, String bonusKey,
                                 String currentKey, String maximumKey, int color) {
        drawOutline(graphics, x, y, 134, 62, LobotomyUiRenderer.withAlpha(color, 0.72F));
        drawScaledText(graphics, label + " " + statGrade(employee, levelKey),
                x + 6, y + 3, 2.0F, color, 122);
        renderMetricLine(graphics, employee, x, y + 21, valueLabel,
                baseKey, bonusKey, "");
        if (employee.contains(currentKey) && employee.contains(maximumKey)) {
            drawScaledText(graphics,
                    tr("screen.lobotocraft.ego_assignment.current_value",
                            compactNumber(employee.getFloat(currentKey)),
                            compactNumber(employee.getFloat(maximumKey))),
                    x + 6, y + 42, 2.0F, TEXT_DIM, 122);
        }
    }

    private void renderDerivedStat(GuiGraphics graphics, CompoundTag employee,
                                   int x, int y, String label, String levelKey,
                                   String valueKey,
                                   String firstLabel, String firstBase, String firstBonus,
                                   String firstSuffix, String secondLabel, String secondBase,
                                   String secondBonus, String secondSuffix, int color) {
        drawOutline(graphics, x, y, 134, 67, LobotomyUiRenderer.withAlpha(color, 0.72F));
        drawScaledText(graphics, label + " " + statGrade(employee, levelKey)
                        + " " + employee.getInt(valueKey),
                x + 6, y + 3, 2.0F, color, 122);
        renderMetricLine(graphics, employee, x, y + 23,
                firstLabel, firstBase, firstBonus, firstSuffix);
        renderMetricLine(graphics, employee, x, y + 44,
                secondLabel, secondBase, secondBonus, secondSuffix);
    }

    private void renderMetricLine(GuiGraphics graphics, CompoundTag employee,
                                   int x, int y, String label,
                                   String baseKey, String bonusKey, String suffix) {
        float base = employee.getFloat(baseKey);
        drawUnclippedScaledText(graphics, label, x + 6, y, 2.0F, TEXT_PALE);
        String baseText = compactNumber(base) + suffix;
        float bonus = employee.getFloat(bonusKey);
        String bonusText = Math.abs(bonus) >= 0.05F ? signedNumber(bonus) + suffix : "";
        int gap = bonusText.isEmpty() ? 0 : 4;
        int valuesWidth = scaledTextWidth(baseText, 2.0F)
                + gap + scaledTextWidth(bonusText, 2.0F);
        int valuesX = x + 128 - valuesWidth;
        int labelEnd = x + 6 + scaledTextWidth(label, 2.0F);
        valuesX = Math.max(labelEnd + 4, valuesX);
        drawUnclippedScaledText(graphics, baseText, valuesX, y, 2.0F, TEXT_PALE);
        if (Math.abs(bonus) >= 0.05F) {
            drawUnclippedScaledText(graphics, bonusText,
                    valuesX + scaledTextWidth(baseText, 2.0F) + gap,
                    y, 2.0F, BONUS_BLUE);
        }
    }

    private void renderEgoDetailCard(GuiGraphics graphics, CompoundTag employee,
                                     String type, int x, int y, int width, int height,
                                     double mouseX, double mouseY) {
        int accent = "weapon".equals(type) ? LobotomyUiTheme.STAT_FORTITUDE
                : LobotomyUiTheme.BAR_SP;
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xE5080908);
        drawOutline(graphics, x, y, width, height,
                LobotomyUiRenderer.withAlpha(accent, 0.72F));
        CompoundTag stock = egoStock(employee, type);
        drawUnclippedScaledText(graphics,
                tr("weapon".equals(type)
                        ? "equipment.lobotocraft.ego_weapon"
                        : "equipment.lobotocraft.ego_armor"),
                x + 43, y + 4, 2.0F, accent);
        if (stock == null) {
            drawScaledText(graphics, tr("screen.lobotocraft.common.none"), x + 43, y + 24,
                    2.0F, TEXT_DIM, width - 50);
            return;
        }

        ItemStack icon = egoIcon(stock);
        if (!icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 5, y + 7, 5.0F);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
        }
        String name = stock.getString("DisplayName");
        if (name.isBlank()) name = tr("screen.lobotocraft.common.none");
        String riskName = stock.getString("RiskLevel");
        drawUnclippedScaledText(graphics, riskName, x + 43, y + 23,
                2.0F, riskColor(riskName));
        int nameX = x + 43 + scaledTextWidth(riskName, 2.0F) + 8;
        drawScrollingScaledText(graphics, name, nameX, y + 23,
                2.0F, TEXT_PALE, Math.max(1, x + width - 4 - nameX));
        if (inside(mouseX, mouseY, x, y, width, height)) hoveredEgoName = name;
    }

    private CompoundTag egoStock(CompoundTag employee, String type) {
        String field = "weapon".equals(type) ? "WeaponEgo" : "ArmorEgo";
        String code = employee.getString(field);
        if (code == null || code.isBlank()) return null;
        ListTag stocks = snapshot.getList("AiEgo", Tag.TAG_COMPOUND);
        for (int index = 0; index < stocks.size(); index++) {
            CompoundTag stock = stocks.getCompound(index);
            if (code.equals(stock.getString("Code")) && type.equals(stock.getString("Type"))) {
                return stock;
            }
        }
        return null;
    }

    private static ItemStack egoIcon(CompoundTag stock) {
        ResourceLocation id = ResourceLocation.tryParse(stock.getString("IconItem"));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int riskColor(String risk) {
        return switch (risk) {
            case "TETH" -> 0xFF48C9F2;
            case "HE" -> 0xFFE9E743;
            case "WAW" -> 0xFFB04DF0;
            case "ALEPH" -> 0xFFFF4545;
            default -> 0xFF53E36B;
        };
    }

    private void renderSpareAgentList(GuiGraphics graphics, double mouseX, double mouseY) {
        int panelX = 1630;
        int panelY = 402;
        int panelW = 282;
        int panelH = 666;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFB010201);
        drawHazardFrame(graphics, panelX, panelY, panelW, panelH, SIDE_YELLOW_DIM);
        drawCenteredScaledText(graphics,
                tr("screen.lobotocraft.department_assignment.unassigned_employees",
                        spareEmployees.size()),
                panelX + panelW / 2, panelY + 9, 2.0F,
                waitingForServer ? TEXT_DIM : TEXT_PALE, panelW - 42);
        graphics.fill(panelX + 7, panelY + 30, panelX + panelW - 7, panelY + 31,
                SIDE_YELLOW_DIM);

        int firstIndex = employeeScroll * 3;
        int slotW = 88;
        int slotH = 116;
        int gapX = 4;
        int gapY = 6;
        int startX = panelX + 6;
        int startY = panelY + 39;
        int end = Math.min(spareEmployees.size(), firstIndex + VISIBLE_POOL_ROWS * 3);
        int hoveredIndex = -1;
        for (int index = firstIndex; index < end; index++) {
            int local = index - firstIndex;
            int column = local % 3;
            int row = local / 3;
            int x = startX + column * (slotW + gapX);
            int y = startY + row * (slotH + gapY);
            CompoundTag employee = spareEmployees.get(index);
            boolean hovered = inside(mouseX, mouseY, x, y, slotW, slotH);
            boolean selected = employee.getUUID("Id").equals(selectedEmployee);
            employeeHits.add(new EmployeeHit(employee, x, y, slotW, slotH, false));
            if (isDragged(employee)) {
                renderVacatedSlot(graphics, x, y, slotW, slotH, SIDE_YELLOW);
            } else if (hovered) {
                hoveredIndex = index;
            } else {
                renderAgentSlot(graphics, employee, x, y, slotW, slotH,
                        SIDE_YELLOW, false, selected, false);
            }
        }
        if (hoveredIndex >= 0) {
            int local = hoveredIndex - firstIndex;
            int x = startX + local % 3 * (slotW + gapX);
            int y = startY + local / 3 * (slotH + gapY);
            CompoundTag employee = spareEmployees.get(hoveredIndex);
            renderAgentSlot(graphics, employee, x, y, slotW, slotH, SIDE_YELLOW, true,
                    employee.getUUID("Id").equals(selectedEmployee), false);
        }

        if (employeeScroll > 0) {
            boolean hovered = inside(mouseX, mouseY, 1861, 406, 42, 25);
            drawUpTriangle(graphics, 1868, 413, 28, 10,
                    hovered ? 0xFFFFFFFF : SIDE_YELLOW);
        }
        if (employeeScroll < maxEmployeeScroll()) {
            boolean hovered = inside(mouseX, mouseY, 1740, 1043, 72, 27);
            drawDownTriangle(graphics, 1753, 1050, 46, 15,
                    hovered ? 0xFFFFFFFF : SIDE_YELLOW);
        }
        if (spareEmployees.isEmpty()) {
            drawCenteredScaledText(graphics,
                    tr("screen.lobotocraft.department_assignment.all_deployed"),
                    panelX + panelW / 2,
                    panelY + 92, 2.0F, TEXT_DIM, panelW - 36);
        }
    }

    private void renderAgentSlot(GuiGraphics graphics, CompoundTag employee,
                                 int x, int y, int width, int height, int color,
                                 boolean hovered, boolean selected, boolean inDepartment) {
        if (hovered) {
            int growX = Math.max(2, width / 22);
            int growY = Math.max(3, height / 24);
            x -= growX;
            y -= growY;
            width += growX * 2;
            height += growY * 2;
        }
        int slotColor = selected ? LobotomyUiTheme.ACCENT : color;
        drawAgentSlotShell(graphics, x, y, width, height, slotColor,
                selected ? 0.20F : hovered ? 0.10F : 0.035F);

        drawCenteredScaledText(graphics, roman(employee.getInt("EmployeeLevel")),
                x + width / 2, y + 3, 2.0F,
                slotColor, width - 8);
        drawCenteredScaledText(graphics, employee.getString("Name"),
                x + width / 2, y + 25, 2.0F,
                selected ? LobotomyUiTheme.ACCENT : TEXT_PALE, width - 7);
        drawInventoryDoll(graphics, employee, x + width / 2, y + 43,
                width - 18, height - 48);
        if (inDepartment && selected) {
            graphics.fill(x + 5, y + height - 6, x + width - 5, y + height - 3,
                    LobotomyUiTheme.ACCENT);
        }
    }

    private void renderEmptySlot(GuiGraphics graphics, int x, int y, int width, int height,
                                 int color, boolean hovered) {
        drawAgentSlotShell(graphics, x, y, width, height,
                LobotomyUiRenderer.withAlpha(color, hovered ? 0.88F : 0.56F),
                hovered ? 0.07F : 0.02F);
        drawCenteredScaledText(graphics, "EMPTY", x + width / 2, y + 4,
                2.0F, LobotomyUiRenderer.withAlpha(color, hovered ? 1.0F : 0.65F),
                width - 8);
        int emptyW = Math.min(43, Math.max(18, width - 10));
        int emptyH = Math.min(67, Math.max(34, height - 42));
        drawTinted(graphics, AGENT_SLOT_EMPTY, x + (width - emptyW) / 2,
                y + 37, emptyW, emptyH, 43, 67, color, hovered ? 0.74F : 0.42F);
    }

    private void renderVacatedSlot(GuiGraphics graphics, int x, int y, int width, int height,
                                   int color) {
        drawAgentSlotShell(graphics, x, y, width, height,
                LobotomyUiRenderer.withAlpha(color, 0.30F), 0.01F);
    }

    private void renderUnavailableSlot(GuiGraphics graphics, int x, int y,
                                       int width, int height, int color) {
        drawAgentSlotShell(graphics, x, y, width, height,
                LobotomyUiRenderer.withAlpha(color, 0.18F), 0.01F);
        drawCenteredScaledText(graphics, "LOCKED", x + width / 2, y + 5,
                1.6F, LobotomyUiRenderer.withAlpha(color, 0.46F), width - 8);
        drawCrispLine(graphics, x + 12, y + 42, x + width - 12, y + height - 13,
                LobotomyUiRenderer.withAlpha(color, 0.24F));
        drawCrispLine(graphics, x + width - 12, y + 42, x + 12, y + height - 13,
                LobotomyUiRenderer.withAlpha(color, 0.24F));
    }

    private void renderDraggedEmployee(GuiGraphics graphics, double mouseX, double mouseY) {
        if (!dragging || draggedEmployee == null) return;
        int x = Mth.floor(mouseX) - 44;
        int y = Mth.floor(mouseY) - 63;
        EmployeeDepartment origin = EmployeeDepartment.byId(
                draggedEmployee.getString("DepartmentId"));
        int color = origin == EmployeeDepartment.UNASSIGNED
                ? SIDE_YELLOW : departmentColor(origin);
        renderAgentSlot(graphics, draggedEmployee, x, y, 88, 126,
                color, false, true, false);
        drawOutline(graphics, x - 2, y - 2, 92, 130, 0xFFFFFFFF);
    }

    private void drawInventoryDoll(GuiGraphics graphics, CompoundTag employee,
                                   int centerX, int y, int maxWidth, int maxHeight) {
        int scale = Mth.clamp(Math.min(maxWidth * 9 / 20, maxHeight * 9 / 20), 12, 38);
        EmployeeDollRenderer.render(graphics, employee, centerX, y + maxHeight, scale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        double logicalMouseX = toLogicalX(mouseX);
        double logicalMouseY = toLogicalY(mouseY);

        if (button == 1) {
            AbnormalityHit abnormality = abnormalityAt(logicalMouseX, logicalMouseY);
            if (abnormality == null) return false;
            ResourceLocation dimension = ResourceLocation.tryParse(
                    abnormality.abnormality.getString("Dimension"));
            if (dimension == null || !abnormality.abnormality.contains("Pos")) return true;
            MessageLoader.getLoader().sendToServer(new OpenRoomAbnormalityInfoPacket(
                    dimension, BlockPos.of(abnormality.abnormality.getLong("Pos"))));
            playClick(0.95F, 0.55F);
            return true;
        }
        if (button != 0) return false;

        if (inside(logicalMouseX, logicalMouseY, 1641, 16, 126, 48)) {
            if (!waitingForServer || Util.getMillis() - waitStartedAt >= 2500L) {
                press("ego");
                playClick(1.08F, 0.55F);
                waitingForServer = true;
                waitStartedAt = Util.getMillis();
                MessageLoader.getLoader().sendToServer(
                        new RequestFacilityManagementPacket(true));
            }
            return true;
        }
        if (inside(logicalMouseX, logicalMouseY, 1776, 16, 126, 48)) {
            press("return");
            playClick(0.92F, 0.55F);
            onClose();
            return true;
        }
        if (activeArea > 0 && ((activeArea == 1
                && inside(logicalMouseX, logicalMouseY, 55, 4, 94, 42))
                || (activeArea == 2
                && inside(logicalMouseX, logicalMouseY, 47, 947, 90, 50)))) {
            changeArea(activeArea - 1);
            return true;
        }
        if (activeArea < 2 && inside(logicalMouseX, logicalMouseY, 55, 104, 94, 48)) {
            changeArea(activeArea + 1);
            return true;
        }
        if (inside(logicalMouseX, logicalMouseY, 1861, 406, 42, 25)
                && employeeScroll > 0) {
            employeeScroll--;
            press("pool:up");
            playClick(1.25F, 0.35F);
            return true;
        }
        if (inside(logicalMouseX, logicalMouseY, 1740, 1043, 72, 27)
                && employeeScroll < maxEmployeeScroll()) {
            employeeScroll++;
            press("pool:down");
            playClick(0.95F, 0.35F);
            return true;
        }

        DepartmentTabHit tab = departmentTabAt(logicalMouseX, logicalMouseY);
        if (tab != null) {
            dragCandidate = null;
            if (panelState(tab.box.department) == tab.state) return true;
            panelStates.put(tab.box.department, tab.state);
            press("tab:" + tab.box.department.id() + ":" + tab.state.name());
            playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.18F, 1.62F);
            return true;
        }
        DepartmentBox closeBox = departmentCloseAt(logicalMouseX, logicalMouseY);
        if (closeBox != null) {
            panelStates.put(closeBox.department, PanelState.MAIN);
            press("close:" + closeBox.department.id());
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.22F, 1.28F);
            return true;
        }

        EmployeeHit hit = employeeAt(logicalMouseX, logicalMouseY);
        if (hit != null) {
            UUID id = hit.employee.getUUID("Id");
            long now = Util.getMillis();
            boolean doubleClick = hit.inDepartment && id.equals(lastEmployeeClick)
                    && id.equals(selectedEmployee) && now - lastEmployeeClickTime < DOUBLE_CLICK_MS;
            selectedEmployee = id;
            rememberedEmployee = id;
            lastEmployeeClick = id;
            lastEmployeeClickTime = now;
            press("employee:" + id);
            playClick(doubleClick ? 0.88F : 1.35F, doubleClick ? 0.52F : 0.38F);
            if (doubleClick) {
                dragCandidate = null;
                deploy(EmployeeDepartment.UNASSIGNED, 0);
            } else {
                dragCandidate = hit;
                dragStartX = logicalMouseX;
                dragStartY = logicalMouseY;
            }
            return true;
        }

        DepartmentBox box = departmentAt(logicalMouseX, logicalMouseY);
        if (box != null) {
            dragCandidate = null;
            return false;
        }
        dragCandidate = null;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && dragCandidate != null) {
            double logicalMouseX = toLogicalX(mouseX);
            double logicalMouseY = toLogicalY(mouseY);
            if (!dragging && Math.hypot(logicalMouseX - dragStartX,
                    logicalMouseY - dragStartY) >= 7.0D) {
                dragging = true;
                draggedEmployee = dragCandidate.employee;
                dragFromDepartment = dragCandidate.inDepartment;
                selectedEmployee = draggedEmployee.getUUID("Id");
                rememberedEmployee = selectedEmployee;
                playClick(1.18F, 0.42F);
            }
            return dragging;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging && draggedEmployee != null) {
            double logicalMouseX = toLogicalX(mouseX);
            double logicalMouseY = toLogicalY(mouseY);
            DepartmentBox target = departmentAt(logicalMouseX, logicalMouseY);
            if (target != null) {
                deploy(target.department, centralSectionForDrop(target, logicalMouseY));
            } else if (dragFromDepartment) {
                deploy(EmployeeDepartment.UNASSIGNED, 0);
            } else {
                playClick(0.72F, 0.30F);
            }
            clearDrag();
            return true;
        }
        if (button == 0) clearDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double logicalMouseX = toLogicalX(mouseX);
        double logicalMouseY = toLogicalY(mouseY);
        if (inside(logicalMouseX, logicalMouseY, SIDEBAR_X, 402,
                CANVAS_W - SIDEBAR_X, 666)) {
            employeeScroll = Mth.clamp(employeeScroll - (int) Math.signum(delta),
                    0, maxEmployeeScroll());
            return true;
        }
        DepartmentBox textBox = departmentAt(logicalMouseX, logicalMouseY);
        if (textBox != null && panelState(textBox.department) == PanelState.DESCRIPTION
                && logicalMouseY >= textBox.y + 43) {
            List<net.minecraft.util.FormattedCharSequence> lines = wrapScaledText(
                    departmentDescription(textBox.department), 2.0F, textBox.width - 52);
            int visibleLines = Math.max(DESCRIPTION_VISIBLE_LINES, (textBox.height - 108) / 22);
            int maximum = Math.max(0, lines.size() - visibleLines);
            int current = descriptionScroll.getOrDefault(textBox.department, 0);
            int next = Mth.clamp(current - (int) Math.signum(delta), 0, maximum);
            descriptionScroll.put(textBox.department, next);
            return true;
        }
        if (logicalMouseX >= 0 && logicalMouseX < SIDEBAR_X
                && logicalMouseY >= 0 && logicalMouseY < CANVAS_H) {
            long now = Util.getMillis();
            if (now - lastAreaScrollTime < 500L) return true;
            int next = activeArea + (delta < 0 ? 1 : -1);
            if (next >= 0 && next < AREA_NAMES.length) {
                lastAreaScrollTime = now;
                changeArea(next);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void changeArea(int area) {
        area = Mth.clamp(area, 0, AREA_NAMES.length - 1);
        if (area == activeArea) {
            playClick(1.12F, 0.25F);
            return;
        }
        areaTransitionDirection = area > activeArea ? -1 : 1;
        areaTransitionStarted = Util.getMillis();
        activeArea = area;
        rememberedArea = activeArea;
        press("area:" + area);
        playSound(SoundEvents.PLAYER_LEVELUP, 0.16F, 1.45F + area * 0.12F);
        rebuildDepartmentBoxes();
    }

    private void clearDrag() {
        dragCandidate = null;
        draggedEmployee = null;
        dragging = false;
        dragFromDepartment = false;
    }

    private void deploy(EmployeeDepartment department, int centralSection) {
        CompoundTag employee = selectedEmployee();
        if (employee == null) {
            playClick(0.70F, 0.25F);
            return;
        }
        int targetSection = department == EmployeeDepartment.CENTRAL
                ? Mth.clamp(centralSection, 0, 1) : 0;
        if (!isDepartmentOpen(department)
                || (department == EmployeeDepartment.CENTRAL && targetSection == 1
                && !isCentralLowerOpen())) {
            blockedDepartment = department;
            blockedUntil = Util.getMillis() + 900L;
            playClick(0.55F, 0.45F);
            return;
        }
        String control = "department:" + department.id();
        press(control);
        boolean sameDepartment = department.id().equals(employee.getString("DepartmentId"));
        if (sameDepartment && (department != EmployeeDepartment.CENTRAL
                || employee.getInt("CentralSection") == targetSection)) {
            playClick(1.02F, 0.25F);
            return;
        }
        int assigned = department == EmployeeDepartment.CENTRAL
                ? employeesInCentralSection(targetSection).size()
                : employeesInDepartment(department).size();
        int capacity = employeeCapacity(department, targetSection);
        if (department != EmployeeDepartment.UNASSIGNED && assigned >= capacity) {
            blockedDepartment = department;
            blockedUntil = Util.getMillis() + 900L;
            playClick(0.55F, 0.45F);
            return;
        }
        if (waitingForServer && Util.getMillis() - waitStartedAt < 2500L) return;

        employee.putString("DepartmentId", department.id());
        employee.putString("Department", department.displayName());
        employee.putInt("CentralSection", targetSection);
        spareEmployees.removeIf(entry -> entry.getUUID("Id").equals(selectedEmployee));
        if (department == EmployeeDepartment.UNASSIGNED) {
            spareEmployees.add(employee);
            spareEmployees.sort(Comparator
                    .comparingInt((CompoundTag entry) -> entry.getInt("EmployeeLevel")).reversed()
                    .thenComparing(entry -> entry.getString("Name"), String.CASE_INSENSITIVE_ORDER));
        }
        employeeScroll = Mth.clamp(employeeScroll, 0, maxEmployeeScroll());
        waitingForServer = true;
        waitStartedAt = Util.getMillis();
        flashDepartment = department;
        flashUntil = Util.getMillis() + 450L;
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.32F, 1.05F);
        MessageLoader.getLoader().sendToServer(
                new AssignEmployeeDepartmentPacket(selectedEmployee, department.id(),
                        targetSection, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (waitingForServer && Util.getMillis() - waitStartedAt >= 3000L) {
            waitingForServer = false;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new FacilityManagementScreen(snapshot));
    }

    private void updateHoverFeedback(double mouseX, double mouseY) {
        String next = hoveredControlAt(mouseX, mouseY);
        if (!next.equals(hoveredControl) && !next.isEmpty()) playClick(1.62F, 0.09F);
        hoveredControl = next;
    }

    private String hoveredControlAt(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, 1641, 16, 126, 48)) return "ego";
        if (inside(mouseX, mouseY, 1776, 16, 126, 48)) return "return";
        EmployeeHit employee = employeeAt(mouseX, mouseY);
        if (employee != null) return "employee:" + employee.employee.getUUID("Id");
        DepartmentTabHit tab = departmentTabAt(mouseX, mouseY);
        if (tab != null) return "tab:" + tab.box.department.id() + ":" + tab.state.name();
        DepartmentBox closeBox = departmentCloseAt(mouseX, mouseY);
        if (closeBox != null) return "close:" + closeBox.department.id();
        if (activeArea > 0 && ((activeArea == 1 && inside(mouseX, mouseY, 55, 4, 94, 42))
                || (activeArea == 2 && inside(mouseX, mouseY, 47, 947, 90, 50)))) {
            return "area:up";
        }
        if (activeArea < 2 && inside(mouseX, mouseY, 55, 104, 94, 48)) {
            return "area:down";
        }
        return "";
    }

    private void press(String control) {
        pressedControl = control;
        pressedUntil = Util.getMillis() + 115L;
    }

    private boolean isPressed(String control) {
        return control.equals(pressedControl) && Util.getMillis() < pressedUntil;
    }

    private void playClick(float pitch, float volume) {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), volume, pitch);
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (minecraft.player != null) minecraft.player.playSound(sound, volume, pitch);
    }

    private CompoundTag selectedEmployee() {
        return findEmployee(selectedEmployee);
    }

    private CompoundTag findEmployee(UUID id) {
        if (id == null) return null;
        for (CompoundTag employee : employees) {
            if (id.equals(employee.getUUID("Id"))) return employee;
        }
        return null;
    }

    private boolean isDragged(CompoundTag employee) {
        return dragging && draggedEmployee != null
                && employee.hasUUID("Id")
                && employee.getUUID("Id").equals(draggedEmployee.getUUID("Id"));
    }

    private boolean selectedInDepartment(EmployeeDepartment department) {
        CompoundTag employee = selectedEmployee();
        return employee != null && department.id().equals(employee.getString("DepartmentId"));
    }

    private boolean isDepartmentOpen(EmployeeDepartment department) {
        if (department == EmployeeDepartment.UNASSIGNED) return true;
        CompoundTag progress = snapshot.getCompound("DepartmentProgress");
        return progress.getCompound("Levels").getInt(department.id()) > 0;
    }

    private boolean isCentralLowerOpen() {
        return snapshot.getCompound("DepartmentProgress").getBoolean("CentralLowerOpen");
    }

    private int employeeCapacity(EmployeeDepartment department, int centralSection) {
        if (department == EmployeeDepartment.UNASSIGNED) return Integer.MAX_VALUE;
        CompoundTag capacities = snapshot.getCompound("DepartmentProgress")
                .getCompound("EmployeeCapacities");
        return capacities.getInt(department == EmployeeDepartment.CENTRAL && centralSection > 0
                ? "central_lower" : department.id());
    }

    private List<CompoundTag> employeesInDepartment(EmployeeDepartment department) {
        List<CompoundTag> result = new ArrayList<>();
        for (CompoundTag employee : employees) {
            if (department.id().equals(employee.getString("DepartmentId"))) result.add(employee);
        }
        result.sort(Comparator.comparing(employee -> employee.getString("Name"),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private List<CompoundTag> employeesInCentralSection(int section) {
        List<CompoundTag> result = new ArrayList<>();
        for (CompoundTag employee : employees) {
            if (EmployeeDepartment.CENTRAL.id().equals(employee.getString("DepartmentId"))
                    && employee.getInt("CentralSection") == section) {
                result.add(employee);
            }
        }
        result.sort(Comparator.comparing(employee -> employee.getString("Name"),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static int centralSectionForDrop(DepartmentBox box, double mouseY) {
        if (box.department != EmployeeDepartment.CENTRAL) return 0;
        return mouseY < box.y + 291 ? 0 : 1;
    }

    private PanelState panelState(EmployeeDepartment department) {
        return panelStates.getOrDefault(department, PanelState.MAIN);
    }

    private DepartmentTabHit departmentTabAt(double mouseX, double mouseY) {
        PanelState[] states = {PanelState.FUNCTION, PanelState.DESCRIPTION, PanelState.RESEARCH};
        for (DepartmentBox box : departmentBoxes) {
            if (!isDepartmentOpen(box.department)) continue;
            if (panelState(box.department) != PanelState.MAIN) continue;
            if (box.department == EmployeeDepartment.ARCHITECTURE) {
                int tabX = box.x + box.width - 199;
                if (inside(mouseX, mouseY, tabX, box.y + 1, 184, 40)) {
                    return new DepartmentTabHit(box, PanelState.DESCRIPTION);
                }
                continue;
            }
            int tabAreaX = box.x + 72;
            int tabAreaW = box.width - 79;
            int tabW = tabAreaW / states.length;
            for (int index = 0; index < states.length; index++) {
                int tabX = tabAreaX + index * tabW;
                int currentTabW = index == states.length - 1
                        ? tabAreaW - tabW * index : tabW;
                if (inside(mouseX, mouseY, tabX, box.y + 1, currentTabW, 40)) {
                    return new DepartmentTabHit(box, states[index]);
                }
            }
        }
        return null;
    }

    private DepartmentBox departmentCloseAt(double mouseX, double mouseY) {
        for (DepartmentBox box : departmentBoxes) {
            if (!isDepartmentOpen(box.department)) continue;
            if (panelState(box.department) == PanelState.MAIN) continue;
            if (inside(mouseX, mouseY, box.x + box.width - 34, box.y + 5, 29, 29)) return box;
        }
        return null;
    }

    private DepartmentBox departmentAt(double mouseX, double mouseY) {
        for (DepartmentBox box : departmentBoxes) {
            if (box.contains(mouseX, mouseY)) return box;
        }
        return null;
    }

    private EmployeeHit employeeAt(double mouseX, double mouseY) {
        for (int index = employeeHits.size() - 1; index >= 0; index--) {
            EmployeeHit hit = employeeHits.get(index);
            if (hit.contains(mouseX, mouseY)) return hit;
        }
        return null;
    }

    private AbnormalityHit abnormalityAt(double mouseX, double mouseY) {
        for (int index = abnormalityHits.size() - 1; index >= 0; index--) {
            AbnormalityHit hit = abnormalityHits.get(index);
            if (hit.contains(mouseX, mouseY)) return hit;
        }
        return null;
    }

    private int maxEmployeeScroll() {
        int rows = (spareEmployees.size() + 2) / 3;
        return Math.max(0, rows - VISIBLE_POOL_ROWS);
    }

    private String displayDepartment(CompoundTag employee) {
        String id = employee.getString("DepartmentId");
        if (id != null && !id.isBlank()) return departmentName(EmployeeDepartment.byId(id));
        String value = employee.getString("Department");
        return value == null || value.isBlank()
                ? departmentName(EmployeeDepartment.UNASSIGNED) : value;
    }

    private double toLogicalX(double screenX) {
        return (screenX - canvasX) / Math.max(0.0001F, canvasScale);
    }

    private double toLogicalY(double screenY) {
        return (screenY - canvasY) / Math.max(0.0001F, canvasScale);
    }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y,
                                float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + x * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        String fitted = LobotomyUiRenderer.fitText(font, text,
                Math.max(1, screenMaxWidth / snappedScale));
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 2.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, fitted, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawUnclippedScaledText(GuiGraphics graphics, String text, int x, int y,
                                         float scale, int color) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + x * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 2.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScrollingScaledText(GuiGraphics graphics, String text, int x, int y,
                                         float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + x * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        int textWidth = font.width(text) * snappedScale;
        if (textWidth <= screenMaxWidth) {
            drawUnclippedScaledText(graphics, text, x, y, scale, color);
            return;
        }

        int overflow = textWidth - screenMaxWidth;
        long pauseMs = 700L;
        long moveMs = Math.max(500L, overflow * 28L);
        long phase = Util.getMillis() % (pauseMs * 2L + moveMs * 2L);
        float offset;
        if (phase < pauseMs) {
            offset = 0.0F;
        } else if (phase < pauseMs + moveMs) {
            offset = overflow * (phase - pauseMs) / (float) moveMs;
        } else if (phase < pauseMs * 2L + moveMs) {
            offset = overflow;
        } else {
            offset = overflow * (1.0F
                    - (phase - pauseMs * 2L - moveMs) / (float) moveMs);
        }

        graphics.enableScissor(screenX, screenY,
                screenX + screenMaxWidth, screenY + font.lineHeight * snappedScale);
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX - offset, screenY - canvasY, 2.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private void drawCenteredScaledText(GuiGraphics graphics, String text, int centerX, int y,
                                        float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + centerX * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        String fitted = LobotomyUiRenderer.fitText(font, text,
                Math.max(1, screenMaxWidth / snappedScale));
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 2.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, fitted, -font.width(fitted) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawRightAlignedScaledText(GuiGraphics graphics, String text, int rightX, int y,
                                            float scale, int color, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenX = Math.round(canvasX + rightX * canvasScale);
        int screenY = Math.round(canvasY + y * canvasScale);
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        String fitted = LobotomyUiRenderer.fitText(font, text,
                Math.max(1, screenMaxWidth / snappedScale));
        graphics.pose().pushPose();
        graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
        graphics.pose().translate(screenX - canvasX, screenY - canvasY, 2.0F);
        graphics.pose().scale(snappedScale, snappedScale, 1.0F);
        graphics.drawString(font, fitted, -font.width(fitted), 0, color, false);
        graphics.pose().popPose();
    }

    private int scaledTextWidth(String text, float scale) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        return Math.max(1, Mth.ceil(font.width(text) * snappedScale
                / Math.max(0.0001F, canvasScale)));
    }

    private void drawWrappedScaledText(GuiGraphics graphics, String text, int x, int y,
                                       float scale, int color, int maxWidth, int maxLines) {
        drawWrappedScaledLines(graphics, wrapScaledText(text, scale, maxWidth),
                0, x, y, scale, color, maxLines);
    }

    private List<net.minecraft.util.FormattedCharSequence> wrapScaledText(
            String text, float scale, int maxWidth) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int screenMaxWidth = Math.max(1, Math.round(maxWidth * canvasScale));
        int wrapWidth = Math.max(1, screenMaxWidth / snappedScale);
        return font.split(Component.literal(text), wrapWidth);
    }

    private void drawWrappedScaledLines(GuiGraphics graphics,
                                        List<net.minecraft.util.FormattedCharSequence> lines,
                                        int lineOffset, int x, int y, float scale,
                                        int color, int maxLines) {
        int snappedScale = Math.max(1, Mth.floor(scale * canvasScale + 0.001F));
        int lineAdvance = Math.max(12,
                Math.round((font.lineHeight + 2) * snappedScale / canvasScale));
        int first = Mth.clamp(lineOffset, 0, lines.size());
        int count = Math.min(maxLines, lines.size() - first);
        int screenX = Math.round(canvasX + x * canvasScale);
        for (int index = 0; index < count; index++) {
            int screenY = Math.round(canvasY + (y + index * lineAdvance) * canvasScale);
            graphics.pose().pushPose();
            graphics.pose().scale(1.0F / canvasScale, 1.0F / canvasScale, 1.0F);
            graphics.pose().translate(screenX - canvasX, screenY - canvasY, 2.0F);
            graphics.pose().scale(snappedScale, snappedScale, 1.0F);
            graphics.drawString(font, lines.get(first + index), 0, 0, color, false);
            graphics.pose().popPose();
        }
    }

    private static void drawAgentSlotShell(GuiGraphics graphics, int x, int y,
                                           int width, int height, int color, float fillAlpha) {
        int cut = Mth.clamp(width / 8, 6, 12);
        int diagonalStart = height - cut;
        fillAgentSlotShape(graphics, x, y, width, height, diagonalStart, 0xEC050705);
        if (fillAlpha > 0.0F) {
            fillAgentSlotShape(graphics, x, y, width, height, diagonalStart,
                    LobotomyUiRenderer.withAlpha(color, fillAlpha));
        }
        drawCrispLine(graphics, x, y, x + width - 1, y, color);
        drawCrispLine(graphics, x, y, x, y + height - 1, color);
        drawCrispLine(graphics, x + width - 1, y,
                x + width - 1, y + diagonalStart, color);
        drawCrispLine(graphics, x + width - 1, y + diagonalStart,
                x + width - cut, y + height - 1, color);
        drawCrispLine(graphics, x + width - cut, y + height - 1,
                x, y + height - 1, color);
        graphics.fill(x + 1, y + 21, x + width - 2, y + 22,
                LobotomyUiRenderer.withAlpha(color, 0.42F));
    }

    private static void fillAgentSlotShape(GuiGraphics graphics, int x, int y,
                                           int width, int height, int diagonalStart, int color) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + diagonalStart + 1, color);
        for (int row = diagonalStart + 1; row < height - 1; row++) {
            int inset = row - diagonalStart + 1;
            graphics.fill(x + 1, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void drawPolyline(GuiGraphics graphics, int color, int... points) {
        for (int index = 0; index + 3 < points.length; index += 2) {
            drawCrispLine(graphics, points[index], points[index + 1],
                    points[index + 2], points[index + 3], color);
        }
    }

    private static void drawHazardFrame(GuiGraphics graphics, int x, int y,
                                        int width, int height, int color) {
        drawOutline(graphics, x, y, width, height, color);
        int faint = LobotomyUiRenderer.withAlpha(color, 0.52F);
        int corner = Math.min(28, Math.max(10, width / 8));
        graphics.fill(x + 5, y + 5, x + 5 + corner, y + 6, faint);
        graphics.fill(x + width - 5 - corner, y + 5, x + width - 5, y + 6, faint);
        graphics.fill(x + 5, y + height - 6, x + 5 + corner, y + height - 5, faint);
        graphics.fill(x + width - 5 - corner, y + height - 6,
                x + width - 5, y + height - 5, faint);
    }

    private static void drawDepartmentNamePlate(GuiGraphics graphics, int x, int y,
                                                 int width, int height, int color) {
        int slope = 20;
        graphics.fill(x + slope, y + 1, x + width - 1, y + height - 1, 0xF7000000);
        for (int row = 1; row < height - 1; row++) {
            int left = x + slope - row * slope / Math.max(1, height - 2);
            graphics.fill(left, y + row, x + slope, y + row + 1, 0xF7000000);
        }
        drawCrispLine(graphics, x, y + height - 1, x + slope, y, color);
        drawCrispLine(graphics, x + slope, y, x + width - 1, y, color);
        drawCrispLine(graphics, x + width - 1, y, x + width - 1, y + height - 1, color);
        drawCrispLine(graphics, x + width - 1, y + height - 1, x, y + height - 1, color);
    }

    private static void drawDepartmentClose(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 3, y - 3, x + 15, y + 15,
                LobotomyUiRenderer.withAlpha(color, 0.09F));
        drawOutline(graphics, x - 2, y - 2, 16, 16,
                LobotomyUiRenderer.withAlpha(color, 0.72F));
        drawCrispLine(graphics, x + 2, y + 2, x + 10, y + 10, color);
        drawCrispLine(graphics, x + 10, y + 2, x + 2, y + 10, color);
    }

    private static void drawCrispLine(GuiGraphics graphics, int x0, int y0,
                                      int x1, int y1, int color) {
        if (y0 == y1) {
            graphics.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + 1, color);
            return;
        }
        if (x0 == x1) {
            graphics.fill(x0, Math.min(y0, y1), x0 + 1, Math.max(y0, y1) + 1, color);
            return;
        }
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) return;
            int twice = error * 2;
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static void drawDownTriangle(GuiGraphics graphics, int x, int y,
                                         int width, int height, int color) {
        for (int row = 0; row < height; row++) {
            int inset = row * width / (height * 2);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void drawUpTriangle(GuiGraphics graphics, int x, int y,
                                       int width, int height, int color) {
        for (int row = 0; row < height; row++) {
            int inset = (height - row - 1) * width / (height * 2);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void drawTinted(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height,
                                   int textureWidth, int textureHeight,
                                   int color, float alpha) {
        try {
            setColor(graphics, color, alpha);
            LobotomyUiRenderer.blitScaled(graphics, texture, x, y, width, height,
                    textureWidth, textureHeight);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void setColor(GuiGraphics graphics, int color, float alpha) {
        graphics.setColor(((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, Mth.clamp(alpha, 0.0F, 1.0F));
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y,
                                    int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static int departmentColor(EmployeeDepartment department) {
        return department == EmployeeDepartment.UNASSIGNED ? LobotomyUiTheme.ACCENT
                : LobotomyUiTheme.departmentColor(department.id());
    }

    private static int areaFor(String departmentId) {
        for (int area = 0; area < AREA_DEPARTMENTS.length; area++) {
            for (EmployeeDepartment department : AREA_DEPARTMENTS[area]) {
                if (department.id().equals(departmentId)) return area;
            }
        }
        return -1;
    }

    private static String departmentOfficerBonus(EmployeeDepartment department) {
        return tr("screen.lobotocraft.department_assignment.officer_bonus." + department.id());
    }

    private static String departmentTenureBonus(EmployeeDepartment department) {
        return tr("screen.lobotocraft.department_assignment.tenure_bonus." + department.id());
    }

    private static String departmentDescription(EmployeeDepartment department) {
        return tr("screen.lobotocraft.department_assignment.description." + department.id());
    }

    private static String sephirah(EmployeeDepartment department) {
        return switch (department) {
            case CONTROL -> "MALKUTH";
            case INFORMATION -> "YESOD";
            case TRAINING -> "HOD";
            case SAFETY -> "NETZACH";
            case CENTRAL -> "TIPHERETH";
            case WELFARE -> "CHESED";
            case DISCIPLINARY -> "GEBURA";
            case RECORD -> "HOKMA";
            case EXTRACTION -> "BINAH";
            case ARCHITECTURE -> "KETER";
            case UNASSIGNED -> "NONE";
        };
    }

    private static String tabLabel(PanelState state) {
        return switch (state) {
            case FUNCTION -> tr("screen.lobotocraft.department_assignment.tab.function");
            case DESCRIPTION -> tr("screen.lobotocraft.department_assignment.tab.description");
            case RESEARCH -> tr("screen.lobotocraft.department_assignment.tab.research");
            default -> "";
        };
    }

    private static String departmentName(EmployeeDepartment department) {
        return tr("department.lobotocraft." + department.id());
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private static String roman(int level) {
        return switch (Mth.clamp(level, 1, 5)) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "I";
        };
    }

    private static String statGrade(CompoundTag employee, String levelKey) {
        String valueKey = levelKey != null && levelKey.endsWith("Level")
                ? levelKey.substring(0, levelKey.length() - "Level".length()) : "";
        return !valueKey.isEmpty() && employee.contains(valueKey)
                ? EmployeeLevelCalculator.statGrade(employee.getInt(valueKey))
                : roman(employee.getInt(levelKey));
    }

    private static String compactNumber(float value) {
        float rounded = Math.round(value * 10.0F) / 10.0F;
        if (Math.abs(rounded - Math.round(rounded)) < 0.001F) {
            return Integer.toString(Math.round(rounded));
        }
        return Float.toString(rounded);
    }

    private static String signedNumber(float value) {
        return (value > 0.0F ? "+" : "") + compactNumber(value);
    }

    private static ResourceLocation texture(String name) {
        return ResourceUtil.createInstance("textures/gui/deploy/" + name);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum PanelState {
        MAIN,
        FUNCTION,
        DESCRIPTION,
        RESEARCH
    }

    private static final class DepartmentTabHit {
        final DepartmentBox box;
        final PanelState state;

        DepartmentTabHit(DepartmentBox box, PanelState state) {
            this.box = box;
            this.state = state;
        }
    }

    private static final class DepartmentBox {
        final EmployeeDepartment department;
        final int x;
        final int y;
        final int width;
        final int height;

        DepartmentBox(EmployeeDepartment department, int x, int y, int width, int height) {
            this.department = department;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y - 54, width + 10, height + 54);
        }
    }

    private static final class EmployeeHit {
        final CompoundTag employee;
        final int x;
        final int y;
        final int width;
        final int height;
        final boolean inDepartment;

        EmployeeHit(CompoundTag employee, int x, int y, int width, int height,
                    boolean inDepartment) {
            this.employee = employee;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.inDepartment = inDepartment;
        }

        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }

    private static final class AbnormalityHit {
        final CompoundTag abnormality;
        final int x;
        final int y;
        final int width;
        final int height;

        AbnormalityHit(CompoundTag abnormality, int x, int y, int width, int height) {
            this.abnormality = abnormality;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }
}
