package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.EmployeeDollRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.AssignAiEgoPacket;
import com.wzz.lobotocraft.network.packet.OpenEmployeeInventoryPacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** AI E.G.O inventory, following LobCorp's InventoryUI controller and slot semantics. */
public final class EgoAssignmentScreen extends Screen {
    private static final int BG = 0xF6000000;
    private static final int PANEL = 0xDD050807;
    private static final int ROW_A = 0xC80A0D0C;
    private static final int ROW_B = 0xC80D100F;
    private static final int TEXT = 0xFFF0F0B0;
    private static final int TEXT_DIM = 0xFFA9AA78;
    private static final int LINE = 0xFFB7B876;
    private static final int LINE_DIM = 0x806B6C48;
    private static final int WEAPON_RED = 0xFFFF3B3B;
    private static final int ARMOR_BLUE = 0xFF53A5FF;
    private static final int SELECTED = 0xFF66BFCD;
    private static final int CANNOT = 0xFFE05A5A;

    private static final int HEADER_H = 58;
    private static final int TAB_H = 34;
    private static final int ITEM_HEADER_H = 22;
    private static final int ITEM_ROW_H = 46;
    private static final int DEPARTMENT_HEADER_H = 18;
    private static final int EMPLOYEE_SLOT_H = 58;
    private static final int MAX_VISIBLE_STOCK_SLOTS = 6;
    private static final int ASSIGNED_WEAPON_H = 45;
    private static final int ASSIGNED_ARMOR_H = 57;

    private static final ResourceLocation INVENTORY_ICON = texture("inventory_icon.png");
    private static final ResourceLocation RISK_SLOT = texture("risk_slot.png");
    private static final ResourceLocation AGENT_SLOT = texture("agent_slot.png");
    private static final ResourceLocation DEPARTMENT_BAR = texture("department_bar.png");
    private static final ResourceLocation[] DAMAGE_ICONS = {
            texture("damage_red.png"), texture("damage_white.png"),
            texture("damage_black.png"), texture("damage_pale.png")
    };

    private static final String[] AREA_NAMES = {"ASIYAH", "BRIAH", "ATZILUTH"};
    private static final String[][] AREA_DEPARTMENTS = {
            {"control", "information", "training", "safety", "unassigned"},
            {"central", "welfare", "disciplinary"},
            {"record", "extraction", "architecture"}
    };

    private static UUID rememberedEmployee;
    private static String rememberedType = "weapon";
    private static RiskLevel rememberedRisk;
    private static int rememberedArea;

    private CompoundTag snapshot;
    private final List<CompoundTag> employees = new ArrayList<>();
    private final List<CompoundTag> stocks = new ArrayList<>();
    private final List<CompoundTag> visibleStocks = new ArrayList<>();

    private UUID selectedEmployee;
    private String equipmentType = rememberedType;
    private RiskLevel riskFilter = rememberedRisk;
    private int areaIndex = rememberedArea;
    private int itemScroll;
    private int employeeScroll;
    private boolean waitingForServer;
    private String expandedStockKey = "";
    private String hoveredControl = "";
    private String pressedControl = "";
    private long pressedUntil;

    private int riskX;
    private int riskY;
    private int riskWidth;
    private int riskStep;
    private int riskSlotHeight;
    private int inventoryX;
    private int inventoryWidth;
    private int inventoryTop;
    private int itemListTop;
    private int contentBottom;
    private int employeeX;
    private int employeeWidth;
    private int employeeTop;
    private int detailX;
    private int detailWidth;
    private final Screen returnScreen;

    public EgoAssignmentScreen(CompoundTag snapshot) {
        this(snapshot, null);
    }

    public EgoAssignmentScreen(CompoundTag snapshot, Screen returnScreen) {
        super(Component.literal("E.G.O 分配"));
        this.snapshot = snapshot.copy();
        this.returnScreen = returnScreen;
        readSnapshot();
        restoreSelection();
        rebuildVisibleStocks();
    }

    public void updateSnapshot(CompoundTag updated) {
        UUID keepEmployee = selectedEmployee;
        String keepExpanded = expandedStockKey;
        snapshot = updated.copy();
        waitingForServer = false;
        readSnapshot();
        selectedEmployee = employee(keepEmployee) == null ? null : keepEmployee;
        if (selectedEmployee == null) restoreSelection();
        expandedStockKey = stocks.stream().anyMatch(stock -> stockKey(stock).equals(keepExpanded))
                ? keepExpanded : "";
        rebuildVisibleStocks();
        itemScroll = Mth.clamp(itemScroll, 0, maxItemScroll());
        employeeScroll = Mth.clamp(employeeScroll, 0, maxEmployeeScroll());
    }

    @Override
    public void init() {
        updateLayout();
        LobotomyButton refresh = LobotomyButton.create(Component.literal("刷新"), button -> {
                    waitingForServer = true;
                    MessageLoader.getLoader().sendToServer(new RequestFacilityManagementPacket(true));
                }).bounds(width - 104, height - 25, 44, 18).build();
        addRenderableWidget(refresh);
        LobotomyButton back = LobotomyButton.create(Component.literal("返回"), button ->
                        returnToPreviousScreen())
                .bounds(width - 54, height - 25, 44, 18).build();
        addRenderableWidget(back);
    }

    private void readSnapshot() {
        employees.clear();
        stocks.clear();
        ListTag employeeTags = snapshot.getList("Employees", Tag.TAG_COMPOUND);
        for (int i = 0; i < employeeTags.size(); i++) {
            CompoundTag employee = employeeTags.getCompound(i);
            if ("AI_EMPLOYEE".equals(employee.getString("ActorType")) && employee.hasUUID("Id")) {
                employees.add(employee.copy());
            }
        }
        employees.sort(Comparator.comparingInt((CompoundTag entry) ->
                        departmentOrder(entry.getString("DepartmentId")))
                .thenComparing(entry -> entry.getString("Name"), String.CASE_INSENSITIVE_ORDER));

        ListTag stockTags = snapshot.getList("AiEgo", Tag.TAG_COMPOUND);
        for (int i = 0; i < stockTags.size(); i++) stocks.add(stockTags.getCompound(i).copy());
        stocks.sort(Comparator.comparingInt((CompoundTag entry) -> risk(entry).ordinal())
                .thenComparing(entry -> entry.getString("DisplayName"), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.getString("Code")));
    }

    private void restoreSelection() {
        if (rememberedEmployee == null) return;
        if (employee(rememberedEmployee) != null) selectedEmployee = rememberedEmployee;
        else rememberedEmployee = null;
    }

    private void updateLayout() {
        contentBottom = height - 32;
        riskWidth = Mth.clamp(width * 65 / 1000, 50, 62);
        employeeWidth = Mth.clamp(width / 5, width < 560 ? 116 : 150, 195);
        riskX = Math.max(8, (width - 870) / 2 + 8);
        int normalRiskY = HEADER_H + TAB_H + 25;
        boolean compactRisk = contentBottom - normalRiskY < RiskLevel.values().length * 36 + 26;
        riskY = compactRisk ? HEADER_H + 15 : normalRiskY;
        riskStep = compactRisk
                ? Mth.clamp((contentBottom - riskY - 2) / (RiskLevel.values().length + 1), 14, 36)
                : 36;
        riskSlotHeight = Math.min(28, riskStep - 2);
        employeeX = width - employeeWidth - 8;
        employeeTop = HEADER_H + 8;
        inventoryX = riskX + riskWidth;
        int targetInventoryWidth = Mth.clamp(width / 2, 210, 490);
        inventoryWidth = Math.max(104, Math.min(targetInventoryWidth, employeeX - inventoryX - 8));
        detailX = inventoryX + inventoryWidth + 7;
        detailWidth = Math.min(280, Math.max(0, employeeX - detailX - 7));
        inventoryTop = HEADER_H + 8;
        itemListTop = inventoryTop + TAB_H + ITEM_HEADER_H;
        itemScroll = Mth.clamp(itemScroll, 0, maxItemScroll());
        employeeScroll = Mth.clamp(employeeScroll, 0, maxEmployeeScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        updateHoverFeedback(mouseX, mouseY);
        renderBackdrop(graphics);
        renderHeader(graphics);
        renderRiskFilters(graphics, mouseX, mouseY);
        renderInventory(graphics, mouseX, mouseY);
        renderEmployeeDetail(graphics, mouseX, mouseY);
        renderEmployees(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, BG);
        for (int y = HEADER_H + 18; y < contentBottom; y += 30) {
            graphics.fill(0, y, width, y + 1, 0x1600B9A8);
        }
        graphics.fill(0, HEADER_H - 1, width, HEADER_H + 1, WEAPON_RED);
        graphics.fill(0, contentBottom, width, contentBottom + 1, LINE_DIM);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.fill(0, 0, Math.min(220, inventoryX + 90), HEADER_H - 1, 0xFF100505);
        graphics.fill(0, 0, Math.min(220, inventoryX + 90), 3, WEAPON_RED);
        tint(graphics, WEAPON_RED, 1.0F,
                () -> LobotomyUiRenderer.blitScaled(graphics, INVENTORY_ICON, 13, 7, 37, 42, 95, 107));
        graphics.drawString(font, "E.G.O 分配", 57, 16, WEAPON_RED, false);
        graphics.drawString(font, "E.G.O INVENTORY", 57, 32, 0xFFB92E2E, false);

        CompoundTag selected = employee(selectedEmployee);
        String selection = selected == null ? "未选择员工" : selected.getString("Name")
                + "  ·  " + selected.getString("Department") + "  ·  等级 "
                + roman(selected.getInt("EmployeeLevel"));
        graphics.drawString(font, fit(selection, Math.max(0, employeeX - 244)), 232, 23,
                selected == null ? TEXT_DIM : SELECTED, false);
        if (waitingForServer) {
            graphics.drawString(font, "同步中", Math.max(232, employeeX - 43), 23, TEXT_DIM, false);
        }
    }

    private void renderRiskFilters(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "风险等级", riskX + 2, riskY - 17, TEXT_DIM, false);
        RiskLevel[] levels = RiskLevel.values();
        for (int i = 0; i < levels.length; i++) {
            RiskLevel level = levels[i];
            int y = riskY + i * riskStep;
            boolean selected = riskFilter == level;
            boolean hovered = inside(mouseX, mouseY, riskX, y, riskWidth, riskSlotHeight);
            int color = level.getColor();
            tint(graphics, color, selected ? 1.0F : hovered ? 0.82F : 0.58F,
                    () -> LobotomyUiRenderer.blitScaled(graphics, RISK_SLOT, riskX, y,
                            riskWidth, riskSlotHeight, 121, 45));
            if (selected) graphics.fill(riskX + 3, y + 2, riskX + 5,
                    y + riskSlotHeight - 2, color);
            if (isPressed("risk:" + level.name())) {
                drawOutline(graphics, riskX + 2, y + 2, riskWidth - 4,
                        Math.max(1, riskSlotHeight - 4), 0xFFFFFFFF);
            }
            String label = level.name();
            graphics.drawCenteredString(font, label, riskX + riskWidth / 2,
                    y + Math.max(2, (riskSlotHeight - 8) / 2), selected || hovered ? color : TEXT);
        }
        int allY = riskY + levels.length * riskStep;
        boolean all = riskFilter == null;
        drawOutline(graphics, riskX, allY, riskWidth, riskSlotHeight, all ? SELECTED : LINE_DIM);
        if (isPressed("risk:all")) {
            drawOutline(graphics, riskX + 2, allY + 2, riskWidth - 4,
                    Math.max(1, riskSlotHeight - 4), 0xFFFFFFFF);
        }
        graphics.drawCenteredString(font, "全部", riskX + riskWidth / 2,
                allY + Math.max(2, (riskSlotHeight - 8) / 2),
                all ? SELECTED : TEXT_DIM);
    }

    private void renderInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(inventoryX, inventoryTop, inventoryX + inventoryWidth, contentBottom, PANEL);
        drawOutline(graphics, inventoryX, inventoryTop, inventoryWidth, contentBottom - inventoryTop, LINE_DIM);
        renderEquipmentTabs(graphics, mouseX, mouseY);

        int headerY = inventoryTop + TAB_H;
        graphics.fill(inventoryX + 1, headerY, inventoryX + inventoryWidth - 1,
                headerY + ITEM_HEADER_H, 0xE00A0B09);
        graphics.drawString(font, "外观", inventoryX + 7, headerY + 7, TEXT_DIM, false);
        graphics.drawString(font, "等级和名称", inventoryX + 38, headerY + 7, TEXT_DIM, false);
        if (inventoryWidth >= 340) {
            graphics.drawString(font, "装备属性", inventoryX + Math.min(160, inventoryWidth / 3),
                    headerY + 7, TEXT_DIM, false);
        }
        graphics.drawString(font, "分配槽", inventoryX + inventoryWidth - 52,
                headerY + 7, TEXT_DIM, false);

        graphics.enableScissor(inventoryX + 1, itemListTop,
                inventoryX + inventoryWidth - 1, contentBottom - 1);
        if (visibleStocks.isEmpty()) {
            graphics.drawCenteredString(font, "暂无已研发的 E.G.O",
                    inventoryX + inventoryWidth / 2, itemListTop + 35, TEXT_DIM);
        }
        int y = itemListTop - itemScroll;
        for (int i = 0; i < visibleStocks.size(); i++) {
            CompoundTag stock = visibleStocks.get(i);
            if (y + ITEM_ROW_H > itemListTop && y < contentBottom) {
                renderStockRow(graphics, stock, i, y, mouseX, mouseY);
            }
            if (isExpanded(stock)) {
                int detailsHeight = detailHeight(stock);
                if (y + ITEM_ROW_H + detailsHeight > itemListTop && y + ITEM_ROW_H < contentBottom) {
                    renderStockDetails(graphics, stock, y + ITEM_ROW_H, detailsHeight);
                }
                y += detailsHeight;
            }
            y += ITEM_ROW_H;
        }
        graphics.disableScissor();
        LobotomyUiRenderer.drawScrollbar(graphics, inventoryX + inventoryWidth - 3,
                itemListTop, contentBottom, itemContentHeight(), itemScroll);
    }

    private void renderEquipmentTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int gap = 3;
        int half = (inventoryWidth - gap) / 2;
        int weaponColor = "weapon".equals(equipmentType) ? WEAPON_RED : 0xFF702929;
        int armorColor = "armor".equals(equipmentType) ? ARMOR_BLUE : 0xFF294A70;
        boolean weaponHover = inside(mouseX, mouseY, inventoryX, inventoryTop, half, TAB_H);
        boolean armorHover = inside(mouseX, mouseY, inventoryX + half + gap, inventoryTop,
                inventoryWidth - half - gap, TAB_H);
        drawEquipmentTab(graphics, inventoryX, inventoryTop, half, TAB_H, weaponColor,
                "weapon".equals(equipmentType), weaponHover, isPressed("tab:weapon"));
        drawEquipmentTab(graphics, inventoryX + half + gap, inventoryTop,
                inventoryWidth - half - gap, TAB_H, armorColor,
                "armor".equals(equipmentType), armorHover, isPressed("tab:armor"));
        graphics.drawCenteredString(font, "E.G.O 武器", inventoryX + half / 2,
                inventoryTop + 12, weaponColor);
        graphics.drawCenteredString(font, "E.G.O 护甲",
                inventoryX + half + gap + (inventoryWidth - half - gap) / 2,
                inventoryTop + 12, armorColor);
    }

    private void drawEquipmentTab(GuiGraphics graphics, int x, int y, int width, int height,
                                  int color, boolean selected, boolean hovered, boolean pressed) {
        if (selected || hovered) {
            graphics.fill(x + 10, y + 3, x + width - 10, y + height - 2,
                    selected ? (color & 0x00FFFFFF) | 0x22000000
                            : (color & 0x00FFFFFF) | 0x12000000);
        }
        int inset = pressed ? 1 : 0;
        int leftSlope = x + 13 + inset;
        int rightSlope = x + width - 13 - inset;
        int top = y + 2 + inset;
        int bottom = y + height - 2;
        drawCrispLine(graphics, x + inset, bottom, x + 5 + inset, bottom, color);
        drawCrispLine(graphics, x + 5 + inset, bottom, leftSlope, top, color);
        drawCrispLine(graphics, leftSlope, top, rightSlope, top, color);
        drawCrispLine(graphics, rightSlope, top, x + width - 5 - inset, bottom, color);
        drawCrispLine(graphics, x + width - 5 - inset, bottom, x + width - inset, bottom, color);
        if (selected) graphics.fill(x + 10, bottom - 1, x + width - 10, bottom + 1, color);
    }

    private void renderStockRow(GuiGraphics graphics, CompoundTag stock, int index, int y,
                                int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, inventoryX + 2, y + 1,
                inventoryWidth - 4, ITEM_ROW_H - 2);
        graphics.fill(inventoryX + 2, y + 1, inventoryX + inventoryWidth - 2,
                y + ITEM_ROW_H - 1, index % 2 == 0 ? ROW_A : ROW_B);
        if (hovered) graphics.fill(inventoryX + 2, y + 1, inventoryX + inventoryWidth - 2,
                y + ITEM_ROW_H - 1, 0x302CCFC3);
        if (isPressed("stock:" + stockKey(stock))) {
            graphics.fill(inventoryX + 3, y + 2, inventoryX + 5, y + ITEM_ROW_H - 2,
                    "weapon".equals(stock.getString("Type")) ? WEAPON_RED : ARMOR_BLUE);
        }
        drawOutline(graphics, inventoryX + 2, y + 1, inventoryWidth - 4,
                ITEM_ROW_H - 2, hovered ? LINE : LINE_DIM);

        ItemStack icon = iconStack(stock);
        if (!icon.isEmpty()) renderItem(graphics, icon, inventoryX + 8, y + 17);

        RiskLevel risk = risk(stock);
        int nameX = inventoryX + 37;
        int slots = visualSlotCount(stock);
        int slotWidth = slotWidth(slots);
        int slotsX = inventoryX + inventoryWidth - 6 - slots * slotWidth;
        int detailsWidth = inventoryWidth >= 390 ? 52 : 0;
        int infoEnd = Math.max(nameX + 35, slotsX - detailsWidth - 4);
        String name = displayName(stock);
        graphics.drawString(font, risk.name(), nameX, y + 8, risk.getColor(), false);
        int riskTextWidth = font.width(risk.name()) + 5;
        drawMarqueeText(graphics, name, nameX + riskTextWidth, y + 8,
                Math.max(0, infoEnd - nameX - riskTextWidth), TEXT);

        if (inventoryWidth >= 250) {
            renderStockStats(graphics, stock, nameX, y + 21,
                    Math.max(0, infoEnd - nameX));
        }
        if (detailsWidth > 0) {
            int detailColor = isExpanded(stock) || hoveredControl.equals("detail:" + stockKey(stock))
                    ? SELECTED : TEXT;
            graphics.drawCenteredString(font, "装备资料", infoEnd + 2 + detailsWidth / 2,
                    y + 20, detailColor);
            graphics.fill(infoEnd + 5, y + 32, infoEnd + detailsWidth - 3, y + 33, LINE_DIM);
        }
        renderOwnerSlots(graphics, stock, slotsX, y + 3, slotWidth);
    }

    private void renderStockDetails(GuiGraphics graphics, CompoundTag stock, int y, int height) {
        int accent = "weapon".equals(stock.getString("Type")) ? WEAPON_RED : ARMOR_BLUE;
        graphics.fill(inventoryX + 3, y, inventoryX + inventoryWidth - 3, y + height,
                0xF20B0D0C);
        graphics.fill(inventoryX + 3, y, inventoryX + 5, y + height, accent);
        graphics.fill(inventoryX + 5, y, inventoryX + inventoryWidth - 3, y + 1, LINE_DIM);
        List<FormattedCharSequence> lines = detailLines(stock);
        int textY = y + 5;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, inventoryX + 10, textY, TEXT, false);
            textY += 10;
        }
    }

    private void renderStockStats(GuiGraphics graphics, CompoundTag stock, int x, int y, int width) {
        if ("weapon".equals(stock.getString("Type"))) {
            String damage = blankAsUnknown(stock.getString("DamageType"));
            String power = blankAsUnknown(stock.getString("AttackPower"));
            String speed = blankAsUnknown(stock.getString("AttackSpeed"));
            String range = blankAsUnknown(stock.getString("AttackRange"));
            int iconIndex = damageIndex(damage);
            if (iconIndex >= 0 && width >= 90) {
                LobotomyUiRenderer.blitScaled(graphics, DAMAGE_ICONS[iconIndex], x, y, 10, 10,
                        iconTextureWidth(iconIndex), iconTextureHeight(iconIndex));
                x += 13;
                width -= 13;
            }
            graphics.drawString(font, fit(damage + "  " + power + "  " + speed + "  " + range, width),
                    x, y + 1, damageColor(damage), false);
        } else if (stock.contains("RedResistance", Tag.TAG_FLOAT)) {
            renderArmorResistances(graphics, stock, x, y, width);
        } else {
            graphics.drawString(font, fit(requirementText(stock), width), x, y + 1, TEXT_DIM, false);
        }
    }

    private void renderArmorResistances(GuiGraphics graphics, CompoundTag stock, int x, int y, int width) {
        float[] values = {stock.getFloat("RedResistance"), stock.getFloat("WhiteResistance"),
                stock.getFloat("BlackResistance"), stock.getFloat("PaleResistance")};
        int columns = 4;
        int cellWidth = Math.max(18, width / columns);
        int[] colors = {0xFFFF4141, 0xFFF2F0D0, 0xFFC363D8, 0xFF55BFFF};
        for (int i = 0; i < columns; i++) {
            int cellX = x + i * cellWidth;
            LobotomyUiRenderer.blitScaled(graphics, DAMAGE_ICONS[i], cellX, y, 10, 10,
                    iconTextureWidth(i), iconTextureHeight(i));
            String value = String.format(Locale.ROOT, "%.1f", values[i]);
            graphics.drawString(font, fit(value, cellWidth - 12), cellX + 12, y, colors[i], false);
            graphics.drawString(font, fit(resistanceLabel(values[i]), cellWidth - 12),
                    cellX + 12, y + 10, TEXT_DIM, false);
        }
    }

    private void renderOwnerSlots(GuiGraphics graphics, CompoundTag stock, int x, int y, int slotWidth) {
        List<CompoundTag> owners = owners(stock);
        int total = Math.max(stock.getInt("Total"), owners.size());
        int shown = Math.min(total, visualSlotCount(stock));
        CompoundTag selected = employee(selectedEmployee);
        boolean eligible = selected != null && selectedMeetsRequirements(stock)
                && selected.getBoolean("Deployed") && !selected.getBoolean("Panicking");
        for (int i = 0; i < shown; i++) {
            int sx = x + i * slotWidth;
            int sw = Math.max(16, slotWidth - 2);
            CompoundTag owner = i < owners.size() ? owners.get(i) : null;
            if (owner != null) {
                int color = owner.hasUUID("Id") && owner.getUUID("Id").equals(selectedEmployee)
                        ? SELECTED : departmentColor(owner);
                tint(graphics, color, 1.0F, () -> LobotomyUiRenderer.blitScaled(graphics,
                        AGENT_SLOT, sx, y, sw, 41, 78, 111));
                renderEmployeeFace(graphics, owner, sx + (sw - 18) / 2, y + 7, 18);
                graphics.drawCenteredString(font, fit(owner.getString("Name"), sw - 2),
                        sx + sw / 2, y + 29, color);
            } else {
                int color = selected == null ? LINE_DIM : eligible ? LINE : CANNOT;
                tint(graphics, color, 0.95F, () -> LobotomyUiRenderer.blitScaled(graphics,
                        AGENT_SLOT, sx, y, sw, 41, 78, 111));
                graphics.drawCenteredString(font, "要求", sx + sw / 2, y + 7, color);
                graphics.drawCenteredString(font, requirementBadge(stock), sx + sw / 2,
                        y + 25, eligible || selected == null ? TEXT_DIM : CANNOT);
            }
        }
        if (total > shown) {
            graphics.drawString(font, "+" + (total - shown), x + shown * slotWidth - 1,
                    y + 30, TEXT_DIM, false);
        }
    }

    private void renderEmployees(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(employeeX, employeeTop, employeeX + employeeWidth, contentBottom, PANEL);
        drawOutline(graphics, employeeX, employeeTop, employeeWidth,
                contentBottom - employeeTop, LINE_DIM);
        int tabWidth = employeeWidth / AREA_NAMES.length;
        for (int i = 0; i < AREA_NAMES.length; i++) {
            int x = employeeX + i * tabWidth;
            boolean selected = areaIndex == i;
            boolean hovered = inside(mouseX, mouseY, x, employeeTop, tabWidth, 25);
            int color = selected ? TEXT : hovered ? LINE : TEXT_DIM;
            drawOutline(graphics, x, employeeTop, tabWidth - 1, 25, selected ? LINE : LINE_DIM);
            if (isPressed("area:" + i)) {
                graphics.fill(x + 3, employeeTop + 22, x + tabWidth - 4, employeeTop + 24, color);
            }
            graphics.drawCenteredString(font, fit(AREA_NAMES[i], tabWidth - 6),
                    x + (tabWidth - 1) / 2, employeeTop + 9, color);
        }

        int viewTop = employeeTop + 29;
        graphics.enableScissor(employeeX + 1, viewTop, employeeX + employeeWidth - 1, contentBottom - 1);
        int y = viewTop - employeeScroll;
        for (String departmentId : AREA_DEPARTMENTS[areaIndex]) {
            y = renderDepartment(graphics, departmentId, y, mouseX, mouseY);
        }
        graphics.disableScissor();
        LobotomyUiRenderer.drawScrollbar(graphics, employeeX + employeeWidth - 3,
                viewTop, contentBottom, departmentContentHeight(), employeeScroll);
    }

    private int renderDepartment(GuiGraphics graphics, String departmentId, int y,
                                 int mouseX, int mouseY) {
        int color = LobotomyUiTheme.departmentColor(departmentId);
        int headerY = y;
        drawOutline(graphics, employeeX + 4, y, employeeWidth - 8,
                DEPARTMENT_HEADER_H, color);
        graphics.fill(employeeX + 5, y + 2, employeeX + 7, y + DEPARTMENT_HEADER_H - 2, color);
        graphics.drawCenteredString(font, departmentName(departmentId),
                employeeX + employeeWidth / 2, y + 5, color);
        y += DEPARTMENT_HEADER_H + 3;

        List<CompoundTag> group = employeesInDepartment(departmentId);
        int columns = employeeColumns();
        int gap = 3;
        int slotWidth = (employeeWidth - 10 - gap * (columns - 1)) / columns;
        int rows = Math.max(1, (group.size() + columns - 1) / columns);
        for (int i = 0; i < group.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int x = employeeX + 5 + column * (slotWidth + gap);
            int sy = y + row * EMPLOYEE_SLOT_H;
            renderEmployeeSlot(graphics, group.get(i), x, sy, slotWidth, color, mouseX, mouseY);
        }
        return y + rows * EMPLOYEE_SLOT_H + 7;
    }

    private void renderEmployeeSlot(GuiGraphics graphics, CompoundTag employee, int x, int y,
                                    int slotWidth, int departmentColor, int mouseX, int mouseY) {
        boolean selected = employee.hasUUID("Id") && employee.getUUID("Id").equals(selectedEmployee);
        boolean hovered = inside(mouseX, mouseY, x, y, slotWidth, EMPLOYEE_SLOT_H - 3);
        int color = selected ? SELECTED : departmentColor;
        tint(graphics, color, selected ? 1.0F : hovered ? 0.88F : 0.68F,
                () -> LobotomyUiRenderer.blitScaled(graphics, AGENT_SLOT, x, y,
                        slotWidth, EMPLOYEE_SLOT_H - 3, 78, 111));
        if (isPressed("employee:" + employee.getUUID("Id"))) {
            drawOutline(graphics, x + 2, y + 2, slotWidth - 4, EMPLOYEE_SLOT_H - 7,
                    0xFFFFFFFF);
        }
        graphics.drawCenteredString(font, roman(employee.getInt("EmployeeLevel")),
                x + slotWidth / 2, y + 4, color);
        renderEmployeeFace(graphics, employee, x + (slotWidth - 24) / 2, y + 17, 24);
        graphics.drawCenteredString(font, fit(employee.getString("Name"), slotWidth - 4),
                x + slotWidth / 2, y + 46, employee.getBoolean("Deployed") ? TEXT : TEXT_DIM);
        if (!employee.getBoolean("Deployed")) {
            graphics.fill(x + slotWidth - 7, y + 5, x + slotWidth - 4, y + 8, CANNOT);
        }
    }

    private void renderEmployeeDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!showsEmployeeDetail()) {
            renderCompactInventoryButton(graphics, mouseX, mouseY);
            return;
        }
        int detailBottom = employeeDetailBottom();
        graphics.fill(detailX, employeeTop, detailX + detailWidth, detailBottom, PANEL);
        drawOutline(graphics, detailX, employeeTop, detailWidth,
                detailBottom - employeeTop, LINE_DIM);
        CompoundTag employee = employee(selectedEmployee);
        if (employee == null) {
            graphics.drawCenteredString(font, "未选择员工",
                    detailX + detailWidth / 2, employeeTop + 30, TEXT_DIM);
            return;
        }

        int centerX = detailX + detailWidth / 2;
        int departmentColor = departmentColor(employee);
        graphics.fill(detailX + 4, employeeTop + 4, detailX + detailWidth - 4,
                employeeTop + 5, departmentColor);
        graphics.drawCenteredString(font, fit(employee.getString("Name"), detailWidth - 12),
                centerX, employeeTop + 10, TEXT);
        graphics.drawCenteredString(font,
                fit(employee.getString("Department") + " · 等级 "
                        + roman(employee.getInt("EmployeeLevel")), detailWidth - 12),
                centerX, employeeTop + 23, departmentColor);

        int dollBottom = employeeTop + 129;
        graphics.fill(detailX + 6, employeeTop + 36, detailX + detailWidth - 6,
                dollBottom + 4, 0xA0060807);
        drawOutline(graphics, detailX + 6, employeeTop + 36, detailWidth - 12,
                dollBottom - employeeTop - 32, departmentColor);
        EmployeeDollRenderer.render(graphics, employee, centerX, dollBottom,
                Mth.clamp(detailWidth / 5, 18, 28));

        int weaponY = dollBottom + 10;
        int armorY = weaponY + ASSIGNED_WEAPON_H + 4;
        renderAssignedEgoCard(graphics, employee, "weapon", weaponY, ASSIGNED_WEAPON_H);
        renderAssignedEgoCard(graphics, employee, "armor", armorY, ASSIGNED_ARMOR_H);

        int statsY = employeeInventoryButtonY() + 36;
        int statWidth = detailWidth - 12;
        renderVitalDetailStat(graphics, employee, detailX + 6, statsY, statWidth,
                "勇气", "FortitudeLevel", "Fortitude", "最大生命",
                "BaseMaxHealth", "HealthBonus", "Health", "MaxHealth", 0xFFE05A5A);
        renderVitalDetailStat(graphics, employee, detailX + 6, statsY + 32, statWidth,
                "谨慎", "PrudenceLevel", "Prudence", "最大精神",
                "BaseMaxMental", "MentalBonus", "Mental", "MaxMental", 0xFFF2F0D0);
        renderDerivedDetailStat(graphics, employee, detailX + 6, statsY + 64, statWidth,
                "自律", "TemperanceLevel", "Temperance",
                "成功率", "WorkSuccessBase", "WorkSuccessBonus",
                "工作速度", "WorkSpeedBase", "WorkSpeedBonus", 0xFFC363D8);
        renderDerivedDetailStat(graphics, employee, detailX + 6, statsY + 96, statWidth,
                "正义", "JusticeLevel", "Justice",
                "攻击速度", "AttackSpeedBase", "AttackSpeedBonus",
                "移动速度", "MovementSpeedBase", "MovementSpeedBonus", 0xFF55BFFF);

        int buttonY = employeeInventoryButtonY();
        boolean enabled = employee.getBoolean("Deployed") && snapshot.getBoolean("CanManageAi");
        boolean hovered = enabled && inside(mouseX, mouseY, detailX + 6, buttonY,
                detailWidth - 12, 30);
        int buttonColor = enabled ? hovered ? SELECTED : LINE : LINE_DIM;
        graphics.fill(detailX + 6, buttonY, detailX + detailWidth - 6,
                buttonY + 30, hovered ? 0x402CCFC3 : 0xE0080A08);
        drawOutline(graphics, detailX + 6, buttonY, detailWidth - 12, 30, buttonColor);
        graphics.drawCenteredString(font, enabled ? "点击此处打开假人物品栏" : "员工未部署",
                centerX, buttonY + 6, buttonColor);
        graphics.drawCenteredString(font, "管理背包与饰品", centerX, buttonY + 18, TEXT_DIM);
    }

    private void renderAssignedEgoCard(GuiGraphics graphics, CompoundTag employee,
                                       String type, int y, int height) {
        int accent = "weapon".equals(type) ? WEAPON_RED : ARMOR_BLUE;
        int x = detailX + 6;
        int width = detailWidth - 12;
        graphics.fill(x, y, x + width, y + height, 0xD0080A09);
        drawOutline(graphics, x, y, width, height, accent);
        CompoundTag stock = assignedStock(employee, type);
        if (stock != null) {
            ItemStack icon = iconStack(stock);
            if (!icon.isEmpty()) graphics.renderItem(icon, x + 5, y + 11);
        }
        String nameKey = "weapon".equals(type) ? "WeaponEgoName" : "ArmorEgoName";
        graphics.drawString(font, "weapon".equals(type) ? "E.G.O 武器" : "E.G.O 防具",
                x + 27, y + 5, accent, false);
        String riskName = stock == null ? "" : risk(stock).name();
        int nameX = x + 27;
        if (!riskName.isBlank()) {
            graphics.drawString(font, riskName, nameX, y + 20, risk(stock).getColor(), false);
            nameX += font.width(riskName) + 5;
        }
        drawMarqueeText(graphics, blankAsNone(employee.getString(nameKey)), nameX, y + 20,
                Math.max(1, x + width - 5 - nameX), TEXT);

        if (stock == null) {
            graphics.drawString(font, "无装备数据", x + 5, y + 33, TEXT_DIM, false);
        } else if ("weapon".equals(type)) {
            String damageType = blankAsUnknown(stock.getString("DamageType"));
            String power = blankAsUnknown(stock.getString("AttackPower"));
            String speed = blankAsUnknown(stock.getString("AttackSpeed"));
            String range = blankAsUnknown(stock.getString("AttackRange"));
            String details = damageType + " " + power + "  ·  " + speed + "  ·  " + range;
            drawMarqueeText(graphics, details, x + 5, y + 33,
                    width - 10, damageColor(damageType));
        } else if (stock.contains("RedResistance")) {
            renderResistancePair(graphics, stock, x + 5, y + 33, width - 10,
                    "RED", "RedResistance", "WHITE", "WhiteResistance");
            renderResistancePair(graphics, stock, x + 5, y + 45, width - 10,
                    "BLACK", "BlackResistance", "PALE", "PaleResistance");
        } else {
            graphics.drawString(font, "悬停查看装备介绍", x + 5, y + 38, TEXT_DIM, false);
        }
    }

    private void renderResistancePair(GuiGraphics graphics, CompoundTag stock, int x, int y,
                                      int width, String firstLabel, String firstKey,
                                      String secondLabel, String secondKey) {
        int half = width / 2;
        String first = firstLabel + " " + compactNumber(stock.getFloat(firstKey));
        String second = secondLabel + " " + compactNumber(stock.getFloat(secondKey));
        graphics.drawString(font, first, x, y, damageColor(firstLabel), false);
        graphics.drawString(font, second, x + half, y, damageColor(secondLabel), false);
    }

    private void renderVitalDetailStat(GuiGraphics graphics, CompoundTag employee,
                                       int x, int y, int width, String label,
                                       String levelKey, String valueKey, String metricLabel,
                                       String baseKey, String bonusKey, String currentKey,
                                       String maximumKey, int color) {
        drawOutline(graphics, x, y, width, 31, LobotomyUiRenderer.withAlpha(color, 0.68F));
        graphics.drawString(font, label + " " + statGrade(employee, levelKey)
                + "  " + employee.getInt(valueKey), x + 5, y + 3, color, false);
        renderDetailMetric(graphics, employee, x, y + 13, width,
                metricLabel, baseKey, bonusKey);
        String current = "当前 " + compactNumber(employee.getFloat(currentKey)) + "/"
                + compactNumber(employee.getFloat(maximumKey));
        graphics.drawString(font, current, x + 5, y + 22,
                employee.getBoolean("Deployed") ? TEXT_DIM : LINE_DIM, false);
    }

    private void renderDerivedDetailStat(GuiGraphics graphics, CompoundTag employee,
                                         int x, int y, int width, String label,
                                         String levelKey, String valueKey,
                                         String firstLabel, String firstBase, String firstBonus,
                                         String secondLabel, String secondBase, String secondBonus,
                                         int color) {
        drawOutline(graphics, x, y, width, 31, LobotomyUiRenderer.withAlpha(color, 0.68F));
        graphics.drawString(font, label + " " + statGrade(employee, levelKey)
                + "  " + employee.getInt(valueKey), x + 5, y + 3, color, false);
        renderDetailMetric(graphics, employee, x, y + 13, width,
                firstLabel, firstBase, firstBonus);
        renderDetailMetric(graphics, employee, x, y + 22, width,
                secondLabel, secondBase, secondBonus);
    }

    private void renderDetailMetric(GuiGraphics graphics, CompoundTag employee,
                                    int x, int y, int width, String label,
                                    String baseKey, String bonusKey) {
        graphics.drawString(font, label, x + 5, y, TEXT_DIM, false);
        String base = compactNumber(employee.getFloat(baseKey));
        float bonusValue = employee.getFloat(bonusKey);
        String bonus = Math.abs(bonusValue) < 0.05F ? "" : signedNumber(bonusValue);
        int valuesWidth = font.width(base) + (bonus.isEmpty() ? 0 : font.width(bonus) + 3);
        int valueX = Math.max(x + 5 + font.width(label) + 4, x + width - 5 - valuesWidth);
        graphics.drawString(font, base, valueX, y, TEXT, false);
        if (!bonus.isEmpty()) {
            graphics.drawString(font, bonus, valueX + font.width(base) + 3,
                    y, 0xFF55BFFF, false);
        }
    }

    private CompoundTag assignedStock(CompoundTag employee, String type) {
        String field = "weapon".equals(type) ? "WeaponEgo" : "ArmorEgo";
        String code = employee.getString(field);
        if (code.isBlank()) return null;
        for (CompoundTag stock : stocks) {
            if (type.equals(stock.getString("Type")) && code.equals(stock.getString("Code"))) {
                return stock;
            }
        }
        return null;
    }

    private boolean showsEmployeeDetail() {
        return detailWidth >= 120 && employeeDetailBottom() - employeeTop >= 405;
    }

    private int employeeDetailBottom() {
        return height - 7;
    }

    private void renderCompactInventoryButton(GuiGraphics graphics, int mouseX, int mouseY) {
        CompoundTag employee = employee(selectedEmployee);
        int x = 8;
        int y = height - 25;
        int width = 88;
        boolean enabled = employee != null && employee.getBoolean("Deployed")
                && snapshot.getBoolean("CanManageAi");
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, 18);
        int color = enabled ? hovered ? SELECTED : LINE : LINE_DIM;
        graphics.fill(x, y, x + width, y + 18, hovered ? 0x402CCFC3 : 0xE0080A08);
        drawOutline(graphics, x, y, width, 18, color);
        graphics.drawCenteredString(font, "员工物品栏", x + width / 2, y + 5, color);
    }

    private boolean insideEmployeeInventoryButton(double mouseX, double mouseY) {
        if (showsEmployeeDetail()) {
            return inside(mouseX, mouseY, detailX + 6, employeeInventoryButtonY(),
                    detailWidth - 12, 30);
        }
        return inside(mouseX, mouseY, 8, height - 25, 88, 18);
    }

    private int employeeInventoryButtonY() {
        return employeeTop + 249;
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        CompoundTag selected = employee(selectedEmployee);
        if (selected != null && insideEmployeeInventoryButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(selected.getBoolean("Deployed")
                    ? "打开 " + selected.getString("Name") + " 的背包与饰品槽"
                    : "员工未部署，无法打开物品栏"), mouseX, mouseY);
            return;
        }
        CompoundTag assigned = assignedEgoAt(mouseX, mouseY);
        if (assigned != null) {
            List<Component> lines = new ArrayList<>();
            ItemStack stack = iconStack(assigned);
            if (!stack.isEmpty()) {
                lines.addAll(stack.getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL));
            }
            if (lines.isEmpty()) lines.add(Component.literal(displayName(assigned)));
            if ("weapon".equals(assigned.getString("Type"))) {
                lines.add(Component.literal(blankAsUnknown(assigned.getString("DamageType")) + "  "
                        + blankAsUnknown(assigned.getString("AttackPower")) + "  ·  攻速 "
                        + blankAsUnknown(assigned.getString("AttackSpeed")) + "  ·  距离 "
                        + blankAsUnknown(assigned.getString("AttackRange"))));
            } else if (assigned.contains("RedResistance")) {
                lines.add(Component.literal("RED " + compactNumber(assigned.getFloat("RedResistance"))
                        + "  ·  WHITE " + compactNumber(assigned.getFloat("WhiteResistance"))));
                lines.add(Component.literal("BLACK " + compactNumber(assigned.getFloat("BlackResistance"))
                        + "  ·  PALE " + compactNumber(assigned.getFloat("PaleResistance"))));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        CompoundTag stock = stockAt(mouseX, mouseY);
        if (stock != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(displayName(stock) + " · " + risk(stock).name()));
            lines.add(Component.literal(requirementText(stock)));
            lines.add(Component.literal("库存 " + stock.getInt("Total") + "/" + stock.getInt("Maximum")
                    + " · 已分配 " + stock.getInt("Assigned")));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        CompoundTag employee = employeeAt(mouseX, mouseY);
        if (employee != null) {
            List<Component> lines = List.of(
                    Component.literal(employee.getString("Name") + " · " + employee.getString("Department")),
                    Component.literal("勇气 " + statGrade(employee, "FortitudeLevel")
                            + " · 谨慎 " + statGrade(employee, "PrudenceLevel")),
                    Component.literal("自律 " + statGrade(employee, "TemperanceLevel")
                            + " · 正义 " + statGrade(employee, "JusticeLevel")),
                    Component.literal("武器 " + blankAsNone(employee.getString("WeaponEgoName"))
                            + " · 防具 " + blankAsNone(employee.getString("ArmorEgoName")))
            );
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private CompoundTag assignedEgoAt(double mouseX, double mouseY) {
        if (!showsEmployeeDetail()) return null;
        CompoundTag employee = employee(selectedEmployee);
        if (employee == null) return null;
        int weaponY = employeeTop + 139;
        int armorY = weaponY + ASSIGNED_WEAPON_H + 4;
        if (inside(mouseX, mouseY, detailX + 6, weaponY,
                detailWidth - 12, ASSIGNED_WEAPON_H)) {
            return assignedStock(employee, "weapon");
        }
        if (inside(mouseX, mouseY, detailX + 6, armorY,
                detailWidth - 12, ASSIGNED_ARMOR_H)) {
            return assignedStock(employee, "armor");
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (waitingForServer) return true;

        int gap = 3;
        int half = (inventoryWidth - gap) / 2;
        if (inside(mouseX, mouseY, inventoryX, inventoryTop, half, TAB_H)) {
            press("tab:weapon");
            setEquipmentType("weapon");
            return true;
        }
        if (inside(mouseX, mouseY, inventoryX + half + gap, inventoryTop,
                inventoryWidth - half - gap, TAB_H)) {
            press("tab:armor");
            setEquipmentType("armor");
            return true;
        }
        for (int i = 0; i < RiskLevel.values().length; i++) {
            int y = riskY + i * riskStep;
            if (inside(mouseX, mouseY, riskX, y, riskWidth, riskSlotHeight)) {
                RiskLevel clicked = RiskLevel.values()[i];
                press("risk:" + clicked.name());
                playClick(1.15F);
                riskFilter = riskFilter == clicked ? null : clicked;
                rememberedRisk = riskFilter;
                itemScroll = 0;
                rebuildVisibleStocks();
                return true;
            }
        }
        int allY = riskY + RiskLevel.values().length * riskStep;
        if (inside(mouseX, mouseY, riskX, allY, riskWidth, riskSlotHeight)) {
            press("risk:all");
            playClick(1.15F);
            riskFilter = null;
            rememberedRisk = null;
            itemScroll = 0;
            rebuildVisibleStocks();
            return true;
        }
        int areaTabWidth = employeeWidth / AREA_NAMES.length;
        if (inside(mouseX, mouseY, employeeX, employeeTop, employeeWidth, 25)) {
            areaIndex = Mth.clamp((int) ((mouseX - employeeX) / areaTabWidth), 0, AREA_NAMES.length - 1);
            press("area:" + areaIndex);
            playClick(0.92F);
            rememberedArea = areaIndex;
            employeeScroll = 0;
            return true;
        }

        CompoundTag selected = employee(selectedEmployee);
        if (selected != null && selected.getBoolean("Deployed")
                && snapshot.getBoolean("CanManageAi")
                && insideEmployeeInventoryButton(mouseX, mouseY)) {
            press("employee_inventory");
            playClick(1.28F);
            MessageLoader.getLoader().sendToServer(
                    new OpenEmployeeInventoryPacket(selected.getUUID("Id")));
            return true;
        }

        CompoundTag clickedEmployee = employeeAt(mouseX, mouseY);
        if (clickedEmployee != null) {
            selectedEmployee = clickedEmployee.getUUID("Id");
            rememberedEmployee = selectedEmployee;
            press("employee:" + selectedEmployee);
            playClick(1.32F);
            return true;
        }

        CompoundTag stock = stockAt(mouseX, mouseY);
        if (stock != null) {
            press("stock:" + stockKey(stock));
            handleStockClick(stock, mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void handleStockClick(CompoundTag stock, double mouseX, double mouseY) {
        if (isDetailButton(stock, mouseX, mouseY)) {
            expandedStockKey = isExpanded(stock) ? "" : stockKey(stock);
            press("detail:" + stockKey(stock));
            playClick(isExpanded(stock) ? 1.38F : 0.96F);
            itemScroll = Mth.clamp(itemScroll, 0, maxItemScroll());
            return;
        }
        CompoundTag selected = employee(selectedEmployee);
        if (selected == null || !snapshot.getBoolean("CanManageAi") || !selected.getBoolean("Deployed")
                || selected.getBoolean("Panicking") || !selectedMeetsRequirements(stock)) {
            playClick(0.62F);
            return;
        }

        List<CompoundTag> owners = owners(stock);
        int slots = visualSlotCount(stock);
        int slotWidth = slotWidth(slots);
        int slotsX = inventoryX + inventoryWidth - 6 - slots * slotWidth;
        if (mouseX >= slotsX) {
            int slot = Mth.floor((mouseX - slotsX) / slotWidth);
            if (slot >= 0 && slot < owners.size()) {
                CompoundTag ownerEntry = owners.get(slot);
                if (ownerEntry.getBoolean("Panicking")) {
                    playClick(0.62F);
                    return;
                }
                UUID owner = ownerEntry.getUUID("Id");
                sendAssignment(stock, owner.equals(selectedEmployee) ? selectedEmployee : owner);
                return;
            }
        }
        if (stock.getInt("Available") > 0) sendAssignment(stock, null);
    }

    private void sendAssignment(CompoundTag stock, UUID sourceOwner) {
        waitingForServer = true;
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.38F,
                    sourceOwner != null && sourceOwner.equals(selectedEmployee) ? 0.82F : 1.45F);
        }
        if (sourceOwner != null && sourceOwner.equals(selectedEmployee)) {
            MessageLoader.getLoader().sendToServer(new AssignAiEgoPacket(
                    selectedEmployee, "", stock.getString("Type")));
        } else {
            MessageLoader.getLoader().sendToServer(new AssignAiEgoPacket(selectedEmployee,
                    stock.getString("Code"), stock.getString("Type"), sourceOwner));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int direction = -(int) Math.signum(delta);
        if (inside(mouseX, mouseY, employeeX, employeeTop, employeeWidth,
                contentBottom - employeeTop)) {
            employeeScroll = Mth.clamp(employeeScroll + direction * EMPLOYEE_SLOT_H,
                    0, maxEmployeeScroll());
            return true;
        }
        if (inside(mouseX, mouseY, inventoryX, itemListTop, inventoryWidth,
                contentBottom - itemListTop)) {
            itemScroll = Mth.clamp(itemScroll + direction * ITEM_ROW_H, 0, maxItemScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private CompoundTag stockAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, inventoryX + 1, itemListTop,
                inventoryWidth - 2, contentBottom - itemListTop)) return null;
        int y = itemListTop - itemScroll;
        for (CompoundTag stock : visibleStocks) {
            if (mouseY >= y && mouseY < y + ITEM_ROW_H) return stock;
            y += ITEM_ROW_H + (isExpanded(stock) ? detailHeight(stock) : 0);
        }
        return null;
    }

    private CompoundTag employeeAt(double mouseX, double mouseY) {
        int viewTop = employeeTop + 29;
        if (!inside(mouseX, mouseY, employeeX + 1, viewTop,
                employeeWidth - 2, contentBottom - viewTop)) return null;
        int y = viewTop - employeeScroll;
        int columns = employeeColumns();
        int gap = 3;
        int slotWidth = (employeeWidth - 10 - gap * (columns - 1)) / columns;
        for (String departmentId : AREA_DEPARTMENTS[areaIndex]) {
            y += DEPARTMENT_HEADER_H + 3;
            List<CompoundTag> group = employeesInDepartment(departmentId);
            int rows = Math.max(1, (group.size() + columns - 1) / columns);
            for (int i = 0; i < group.size(); i++) {
                int x = employeeX + 5 + (i % columns) * (slotWidth + gap);
                int sy = y + (i / columns) * EMPLOYEE_SLOT_H;
                if (inside(mouseX, mouseY, x, sy, slotWidth, EMPLOYEE_SLOT_H - 3)) return group.get(i);
            }
            y += rows * EMPLOYEE_SLOT_H + 7;
        }
        return null;
    }

    private void setEquipmentType(String type) {
        playClick("weapon".equals(type) ? 0.88F : 1.05F);
        if (type.equals(equipmentType)) return;
        equipmentType = type;
        rememberedType = type;
        itemScroll = 0;
        rebuildVisibleStocks();
    }

    private void rebuildVisibleStocks() {
        visibleStocks.clear();
        for (CompoundTag stock : stocks) {
            if (!equipmentType.equals(stock.getString("Type"))) continue;
            if (riskFilter != null && risk(stock) != riskFilter) continue;
            visibleStocks.add(stock);
        }
    }

    private List<CompoundTag> owners(CompoundTag stock) {
        List<CompoundTag> result = new ArrayList<>();
        String field = "weapon".equals(stock.getString("Type")) ? "WeaponEgo" : "ArmorEgo";
        String code = stock.getString("Code");
        for (CompoundTag employee : employees) {
            if (code.equals(employee.getString(field))) result.add(employee);
        }
        return result;
    }

    private List<CompoundTag> employeesInDepartment(String departmentId) {
        List<CompoundTag> result = new ArrayList<>();
        for (CompoundTag employee : employees) {
            if (departmentId.equals(employee.getString("DepartmentId"))) result.add(employee);
        }
        return result;
    }

    private CompoundTag employee(UUID id) {
        if (id == null) return null;
        for (CompoundTag employee : employees) {
            if (employee.hasUUID("Id") && id.equals(employee.getUUID("Id"))) return employee;
        }
        return null;
    }

    private boolean selectedMeetsRequirements(CompoundTag stock) {
        CompoundTag employee = employee(selectedEmployee);
        if (employee == null) return false;
        for (String key : new String[]{"FortitudeLevel", "PrudenceLevel", "TemperanceLevel",
                "JusticeLevel", "EmployeeLevel"}) {
            int required = stock.getInt("Required" + key);
            int actual = employee.getInt(key);
            if (!"EmployeeLevel".equals(key) && key.endsWith("Level")) {
                String rawKey = key.substring(0, key.length() - "Level".length());
                if (employee.contains(rawKey)) {
                    actual = EmployeeLevelCalculator.levelForEquipmentRequirement(
                            employee.getInt(rawKey), actual, required);
                }
            }
            if (actual < required) return false;
        }
        return true;
    }

    private String requirementText(CompoundTag stock) {
        List<String> requirements = new ArrayList<>();
        addRequirement(requirements, stock, "FortitudeLevel", "勇气");
        addRequirement(requirements, stock, "PrudenceLevel", "谨慎");
        addRequirement(requirements, stock, "TemperanceLevel", "自律");
        addRequirement(requirements, stock, "JusticeLevel", "正义");
        addRequirement(requirements, stock, "EmployeeLevel", "员工");
        return requirements.isEmpty() ? "无等级限制" : "要求 " + String.join(" · ", requirements);
    }

    private void addRequirement(List<String> result, CompoundTag stock, String key, String name) {
        int value = stock.getInt("Required" + key);
        if (value > 0) result.add(name + " " + roman(value));
    }

    private String requirementBadge(CompoundTag stock) {
        int required = 0;
        for (String key : new String[]{"FortitudeLevel", "PrudenceLevel", "TemperanceLevel",
                "JusticeLevel", "EmployeeLevel"}) {
            required = Math.max(required, stock.getInt("Required" + key));
        }
        return required <= 0 ? "无" : roman(required);
    }

    private int maxItemScroll() {
        return Math.max(0, itemContentHeight() - Math.max(0, contentBottom - itemListTop));
    }

    private int itemContentHeight() {
        int result = visibleStocks.size() * ITEM_ROW_H;
        for (CompoundTag stock : visibleStocks) {
            if (isExpanded(stock)) result += detailHeight(stock);
        }
        return result;
    }

    private boolean isExpanded(CompoundTag stock) {
        return stockKey(stock).equals(expandedStockKey);
    }

    private int detailHeight(CompoundTag stock) {
        return Math.max(28, 10 + detailLines(stock).size() * 10);
    }

    private List<FormattedCharSequence> detailLines(CompoundTag stock) {
        List<Component> components = new ArrayList<>();
        ItemStack stack = iconStack(stock);
        if (!stack.isEmpty()) {
            components.addAll(stack.getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL));
        }
        if (components.isEmpty()) components.add(Component.literal(displayName(stock)));
        components.add(Component.literal(displayName(stock) + " · " + risk(stock).name()));
        components.add(Component.literal(requirementText(stock)));
        List<FormattedCharSequence> result = new ArrayList<>();
        int maxWidth = Math.max(40, inventoryWidth - 24);
        for (Component component : components) result.addAll(font.split(component, maxWidth));
        return result;
    }

    private boolean isDetailButton(CompoundTag stock, double mouseX, double mouseY) {
        if (inventoryWidth < 390) return false;
        int rowY = stockRowY(stock);
        if (rowY == Integer.MIN_VALUE || mouseY < rowY || mouseY >= rowY + ITEM_ROW_H) return false;
        int slots = visualSlotCount(stock);
        int slotsX = inventoryX + inventoryWidth - 6 - slots * slotWidth(slots);
        int detailX = slotsX - 56;
        return mouseX >= detailX && mouseX < slotsX - 2;
    }

    private int stockRowY(CompoundTag wanted) {
        int y = itemListTop - itemScroll;
        for (CompoundTag stock : visibleStocks) {
            if (stock == wanted) return y;
            y += ITEM_ROW_H + (isExpanded(stock) ? detailHeight(stock) : 0);
        }
        return Integer.MIN_VALUE;
    }

    private int maxEmployeeScroll() {
        return Math.max(0, departmentContentHeight() - Math.max(0, contentBottom - employeeTop - 29));
    }

    private int departmentContentHeight() {
        int height = 0;
        int columns = employeeColumns();
        for (String department : AREA_DEPARTMENTS[areaIndex]) {
            int count = employeesInDepartment(department).size();
            int rows = Math.max(1, (count + columns - 1) / columns);
            height += DEPARTMENT_HEADER_H + 3 + rows * EMPLOYEE_SLOT_H + 7;
        }
        return height;
    }

    private int employeeColumns() {
        return employeeWidth >= 190 ? 5 : 3;
    }

    private int visualSlotCount(CompoundTag stock) {
        int total = Math.max(stock.getInt("Total"), owners(stock).size());
        if (inventoryWidth < 260) return 1;
        return Math.max(1, Math.min(total, MAX_VISIBLE_STOCK_SLOTS));
    }

    private int slotWidth(int slots) {
        int budget = Math.max(18, Math.min(inventoryWidth * 38 / 100, 210));
        return Mth.clamp(budget / Math.max(1, slots), 18, 36);
    }

    private ItemStack iconStack(CompoundTag stock) {
        ResourceLocation id = ResourceLocation.tryParse(stock.getString("IconItem"));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void renderItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 20.0F);
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private RiskLevel risk(CompoundTag entry) {
        try {
            return RiskLevel.valueOf(entry.getString("RiskLevel"));
        } catch (IllegalArgumentException ignored) {
            return RiskLevel.ZAYIN;
        }
    }

    private String displayName(CompoundTag stock) {
        String value = stock.getString("DisplayName");
        return value.isBlank() ? "无" : value;
    }

    private void renderEmployeeFace(GuiGraphics graphics, CompoundTag employee,
                                    int x, int y, int size) {
        UUID id = employee.hasUUID("Id") ? employee.getUUID("Id") : new UUID(0L, 0L);
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(id);
        if (minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null) skin = info.getSkinLocation();
        }
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private static String stockKey(CompoundTag stock) {
        return stock.getString("Code") + "|" + stock.getString("Type");
    }

    private void updateHoverFeedback(int mouseX, int mouseY) {
        String next = hoveredControlAt(mouseX, mouseY);
        if (!next.equals(hoveredControl) && !next.isEmpty() && !waitingForServer) {
            playClick(1.65F, 0.12F);
        }
        hoveredControl = next;
    }

    private String hoveredControlAt(double mouseX, double mouseY) {
        int gap = 3;
        int half = (inventoryWidth - gap) / 2;
        if (inside(mouseX, mouseY, inventoryX, inventoryTop, half, TAB_H)) return "tab:weapon";
        if (inside(mouseX, mouseY, inventoryX + half + gap, inventoryTop,
                inventoryWidth - half - gap, TAB_H)) return "tab:armor";
        for (int i = 0; i < RiskLevel.values().length; i++) {
            int y = riskY + i * riskStep;
            if (inside(mouseX, mouseY, riskX, y, riskWidth, riskSlotHeight)) {
                return "risk:" + RiskLevel.values()[i].name();
            }
        }
        int allY = riskY + RiskLevel.values().length * riskStep;
        if (inside(mouseX, mouseY, riskX, allY, riskWidth, riskSlotHeight)) return "risk:all";
        if (inside(mouseX, mouseY, employeeX, employeeTop, employeeWidth, 25)) {
            int tabWidth = employeeWidth / AREA_NAMES.length;
            return "area:" + Mth.clamp((int) ((mouseX - employeeX) / tabWidth),
                    0, AREA_NAMES.length - 1);
        }
        CompoundTag selected = employee(selectedEmployee);
        if (selected != null && selected.getBoolean("Deployed")
                && insideEmployeeInventoryButton(mouseX, mouseY)) return "employee_inventory";
        CompoundTag employee = employeeAt(mouseX, mouseY);
        if (employee != null) return "employee:" + employee.getUUID("Id");
        CompoundTag stock = stockAt(mouseX, mouseY);
        if (stock == null) return "";
        return isDetailButton(stock, mouseX, mouseY)
                ? "detail:" + stockKey(stock)
                : "stock:" + stockKey(stock);
    }

    private void press(String control) {
        pressedControl = control;
        pressedUntil = System.currentTimeMillis() + 120L;
    }

    private boolean isPressed(String control) {
        return control.equals(pressedControl) && System.currentTimeMillis() < pressedUntil;
    }

    private void playClick(float pitch) {
        playClick(pitch, 0.42F);
    }

    private void playClick(float pitch, float volume) {
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), volume, pitch);
        }
    }

    private int departmentColor(CompoundTag employee) {
        return LobotomyUiTheme.departmentColor(employee.getString("DepartmentId"));
    }

    private static int departmentOrder(String id) {
        int index = 0;
        for (String[] area : AREA_DEPARTMENTS) {
            for (String department : area) {
                if (department.equals(id)) return index;
                index++;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String departmentName(String id) {
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department.id().equals(id)) return department.displayName();
        }
        return EmployeeDepartment.UNASSIGNED.displayName();
    }

    private static int damageColor(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "RED" -> 0xFFFF4141;
            case "WHITE" -> 0xFFF2F0D0;
            case "BLACK" -> 0xFFC363D8;
            case "PALE", "BLUE" -> 0xFF55BFFF;
            default -> TEXT_DIM;
        };
    }

    private static int damageIndex(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "RED" -> 0;
            case "WHITE" -> 1;
            case "BLACK" -> 2;
            case "PALE", "BLUE" -> 3;
            default -> -1;
        };
    }

    private static int iconTextureWidth(int index) {
        return switch (index) {
            case 0 -> 122;
            case 1, 3 -> 116;
            default -> 124;
        };
    }

    private static int iconTextureHeight(int index) {
        return switch (index) {
            case 0, 3 -> 120;
            case 1 -> 122;
            default -> 123;
        };
    }

    private static String resistanceLabel(float value) {
        if (value <= 0.0F) return "免疫";
        if (value <= 0.4F) return "抗性极高";
        if (value <= 0.7F) return "抗性较高";
        if (value <= 1.0F) return "抗性一般";
        if (value <= 1.5F) return "抗性较低";
        return "抗性极低";
    }

    private static String roman(int value) {
        return EmployeeLevelCalculator.levelGrade(value);
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

    private static String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "???" : value;
    }

    private static String blankAsNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String fit(String value, int maxWidth) {
        return LobotomyUiRenderer.fitText(font, value, Math.max(0, maxWidth));
    }

    private void drawMarqueeText(GuiGraphics graphics, String text, int x, int y,
                                  int maxWidth, int color) {
        if (maxWidth <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        int overflow = textWidth - maxWidth;
        long pauseMs = 650L;
        long moveMs = Math.max(450L, overflow * 32L);
        long phase = Util.getMillis() % (pauseMs * 2L + moveMs * 2L);
        float offset;
        if (phase < pauseMs) offset = 0.0F;
        else if (phase < pauseMs + moveMs) {
            offset = overflow * (phase - pauseMs) / (float) moveMs;
        } else if (phase < pauseMs * 2L + moveMs) offset = overflow;
        else {
            offset = overflow * (1.0F
                    - (phase - pauseMs * 2L - moveMs) / (float) moveMs);
        }
        graphics.enableScissor(x, y, x + maxWidth, y + font.lineHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(-offset, 0.0F, 1.0F);
        graphics.drawString(font, text, x, y, color, false);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private static ResourceLocation texture(String name) {
        return ResourceUtil.createInstance("textures/gui/inventory/" + name);
    }

    private static void tint(GuiGraphics graphics, int color, float alpha, Runnable draw) {
        try {
            graphics.setColor(((color >> 16) & 0xFF) / 255.0F,
                    ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, alpha);
            draw.run();
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static void drawCrispLine(GuiGraphics graphics, int x0, int y0,
                                      int x1, int y1, int color) {
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        returnToPreviousScreen();
    }

    private void returnToPreviousScreen() {
        if (returnScreen instanceof DepartmentAssignmentScreen deployment) {
            deployment.updateSnapshot(snapshot);
            minecraft.setScreen(deployment);
        } else if (returnScreen instanceof FacilityManagementScreen management) {
            management.updateSnapshot(snapshot);
            minecraft.setScreen(management);
        } else {
            minecraft.setScreen(new FacilityManagementScreen(snapshot));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
