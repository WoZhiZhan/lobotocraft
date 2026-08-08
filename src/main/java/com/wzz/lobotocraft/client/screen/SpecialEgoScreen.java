package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.network.packet.SetSpecialEgoPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Player-only special E.G.O allocation screen in the LobCorp deployment/inventory style. */
public final class SpecialEgoScreen extends Screen {
    private static final int BG = 0xF8000000;
    private static final int PANEL = 0xE6050807;
    private static final int ROW_A = 0xD00A0D0C;
    private static final int ROW_B = 0xD00D100F;
    private static final int TEXT = 0xFFF0F0B0;
    private static final int TEXT_DIM = 0xFFA9AA78;
    private static final int LINE = 0xFFB7B876;
    private static final int LINE_DIM = 0x806B6C48;
    private static final int WEAPON_RED = 0xFFFF3B3B;
    private static final int ARMOR_BLUE = 0xFF53A5FF;
    private static final int SELECTED = 0xFF66BFCD;
    private static final int CANNOT = 0xFFE05A5A;
    private static final int HEADER_H = 58;

    private final Screen parent;
    private CompoundTag snapshot;
    private String equipmentType = "weapon";
    private int selectedIndex;
    private boolean waiting;

    private int riskX;
    private int riskW;
    private int inventoryX;
    private int inventoryW;
    private int detailX;
    private int detailW;
    private int contentBottom;
    private LobotomyButton actionButton;

    public SpecialEgoScreen(CompoundTag snapshot, Screen parent) {
        super(Component.literal("特殊 E.G.O 分配"));
        this.snapshot = snapshot.copy();
        this.parent = parent;
    }

    public void updateSnapshot(CompoundTag updated) {
        snapshot = updated.copy();
        waiting = false;
        selectedIndex = Mth.clamp(selectedIndex, 0, Math.max(0, entries().size() - 1));
        updateActionButton();
    }

    @Override
    public void init() {
        updateLayout();
        actionButton = LobotomyButton.create(Component.empty(), button -> toggleAssignment())
                .bounds(detailX + Math.max(0, detailW - 132), contentBottom - 29, 124, 20)
                .build();
        addRenderableWidget(actionButton);
        addRenderableWidget(LobotomyButton.create(Component.literal("返回"), button -> onClose())
                .bounds(width - 54, height - 25, 44, 18).build());
        updateActionButton();
    }

    private void updateLayout() {
        contentBottom = height - 32;
        riskX = Math.max(8, (width - 900) / 2 + 8);
        riskW = Mth.clamp(width * 65 / 1000, 50, 62);
        inventoryX = riskX + riskW + 8;
        inventoryW = Mth.clamp(width * 43 / 100, 210, 430);
        detailX = inventoryX + inventoryW + 8;
        detailW = Math.max(160, width - detailX - 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        renderBackdrop(graphics);
        renderHeader(graphics);
        renderRiskColumn(graphics);
        renderInventory(graphics, mouseX, mouseY);
        renderDetail(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, BG);
        for (int y = HEADER_H + 18; y < contentBottom; y += 30) {
            graphics.fill(0, y, width, y + 1, 0x1600B9A8);
        }
        int typeColor = typeColor();
        graphics.fill(0, HEADER_H - 1, width, HEADER_H + 1, typeColor);
        graphics.fill(0, contentBottom, width, contentBottom + 1, LINE_DIM);
    }

    private void renderHeader(GuiGraphics graphics) {
        int typeColor = typeColor();
        graphics.fill(0, 0, Math.min(280, inventoryX + 130), HEADER_H - 1, 0xFF100505);
        graphics.fill(0, 0, Math.min(280, inventoryX + 130), 3, typeColor);
        graphics.drawString(font, "特殊 E.G.O 分配", 18, 15, typeColor, false);
        graphics.drawString(font, "SPECIAL E.G.O INVENTORY", 18, 32,
                "weapon".equals(equipmentType) ? 0xFFB92E2E : 0xFF3578B9, false);
        graphics.drawString(font, "武器与护甲席位相互独立，可由不同玩家同时使用", 295, 22,
                TEXT_DIM, false);
        if (waiting) graphics.drawString(font, "同步中", width - 92, 22, TEXT_DIM, false);
    }

    private void renderRiskColumn(GuiGraphics graphics) {
        int top = HEADER_H + 54;
        graphics.drawString(font, "风险等级", riskX + 2, top - 18, TEXT_DIM, false);
        String[] risks = {"ZAYIN", "TETH", "HE", "WAW", "ALEPH"};
        for (int i = 0; i < risks.length; i++) {
            int y = top + i * 36;
            boolean selected = "ALEPH".equals(risks[i]);
            graphics.fill(riskX, y, riskX + riskW, y + 28,
                    selected ? 0xD02B0707 : 0xA6080A09);
            drawOutline(graphics, riskX, y, riskW, 28, selected ? WEAPON_RED : LINE_DIM);
            graphics.drawCenteredString(font, risks[i], riskX + riskW / 2, y + 10,
                    selected ? WEAPON_RED : TEXT_DIM);
        }
    }

    private void renderInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = HEADER_H + 8;
        int tabW = inventoryW / 2;
        drawTab(graphics, inventoryX, top, tabW, "武器", "weapon".equals(equipmentType), WEAPON_RED);
        drawTab(graphics, inventoryX + tabW, top, inventoryW - tabW,
                "护甲", "armor".equals(equipmentType), ARMOR_BLUE);

        int headerY = top + 34;
        graphics.fill(inventoryX, headerY, inventoryX + inventoryW, headerY + 22, PANEL);
        graphics.drawString(font, "E.G.O 名称", inventoryX + 10, headerY + 7, TEXT_DIM, false);
        graphics.drawString(font, "全服席位", inventoryX + inventoryW - 66,
                headerY + 7, TEXT_DIM, false);

        ListTag entries = entries();
        int rowY = headerY + 22;
        if (entries.isEmpty()) {
            graphics.fill(inventoryX, rowY, inventoryX + inventoryW, contentBottom, PANEL);
            graphics.drawCenteredString(font, "暂无特殊 E.G.O 记录",
                    inventoryX + inventoryW / 2, rowY + 40, TEXT_DIM);
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            int y = rowY + index * 52;
            if (y + 50 > contentBottom) break;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= inventoryX && mouseX < inventoryX + inventoryW
                    && mouseY >= y && mouseY < y + 50;
            graphics.fill(inventoryX, y, inventoryX + inventoryW, y + 50,
                    selected ? 0xD0122927 : index % 2 == 0 ? ROW_A : ROW_B);
            if (selected || hovered) drawOutline(graphics, inventoryX, y, inventoryW, 50,
                    selected ? SELECTED : LINE_DIM);

            List<ItemStack> items = itemStacks(entry.getList(prefix() + "Items", Tag.TAG_STRING));
            if (!items.isEmpty()) graphics.renderItem(items.get(0), inventoryX + 10, y + 17);
            graphics.drawString(font, entry.getString("Name"), inventoryX + 34, y + 10,
                    selected ? TEXT : TEXT_DIM, false);
            graphics.drawString(font, fit(entry.getString("Subtitle"), inventoryW - 118),
                    inventoryX + 34, y + 28, TEXT_DIM, false);

            boolean inUse = entry.getBoolean(prefix() + "InUse");
            boolean viewer = entry.getBoolean(prefix() + "UsedByViewer");
            String status = viewer ? "你正在使用" : inUse ? "0 / 1" : "1 / 1";
            int statusColor = viewer ? SELECTED : inUse ? CANNOT : typeColor();
            graphics.drawString(font, status,
                    inventoryX + inventoryW - font.width(status) - 12, y + 20,
                    statusColor, false);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        int top = HEADER_H + 8;
        int height = Math.max(1, contentBottom - top);
        graphics.fill(detailX, top, detailX + detailW, top + height, PANEL);
        drawOutline(graphics, detailX, top, detailW, height, LINE_DIM);
        CompoundTag entry = entry();
        if (entry.isEmpty()) return;

        int x = detailX + 14;
        int innerW = Math.max(10, detailW - 28);
        graphics.drawString(font, entry.getString("Risk"), x, top + 13, WEAPON_RED, false);
        drawScaledText(graphics, entry.getString("Name"), x + 48, top + 8, 1.55F, TEXT);
        graphics.drawString(font, fit(entry.getString("Subtitle"), innerW), x, top + 31,
                TEXT_DIM, false);
        graphics.fill(x, top + 49, x + innerW, top + 50, LINE_DIM);

        String typeName = "weapon".equals(equipmentType) ? "WEAPON / 武器" : "SUIT / 护甲";
        graphics.drawString(font, typeName, x, top + 61, typeColor(), false);
        List<ItemStack> items = itemStacks(entry.getList(prefix() + "Items", Tag.TAG_STRING));
        int iconY = top + 79;
        for (int index = 0; index < items.size(); index++) {
            renderItemScaled(graphics, items.get(index), x + index * 38, iconY,
                    items.size() == 1 ? 2.0F : 1.65F);
        }

        int stateY = top + 127;
        graphics.fill(x, stateY, x + innerW, stateY + 42, ROW_A);
        graphics.fill(x, stateY, x + 3, stateY + 42, typeColor());
        boolean unlocked = entry.getBoolean("Unlocked");
        boolean inUse = entry.getBoolean(prefix() + "InUse");
        boolean viewer = entry.getBoolean(prefix() + "UsedByViewer");
        String user = entry.getString(prefix() + "User");
        graphics.drawString(font, "解锁状态", x + 10, stateY + 7, TEXT_DIM, false);
        graphics.drawString(font, unlocked ? "已解锁" : "未解锁", x + 72, stateY + 7,
                unlocked ? SELECTED : CANNOT, false);
        graphics.drawString(font, "当前使用者", x + 10, stateY + 24, TEXT_DIM, false);
        String userText = viewer ? user + "（你）" : inUse ? user : "空闲";
        graphics.drawString(font, fit(userText.isBlank() ? "未知" : userText, innerW - 82),
                x + 82, stateY + 24, viewer ? SELECTED : inUse ? CANNOT : TEXT, false);

        int infoY = stateY + 55;
        if ("weapon".equals(equipmentType)) {
            graphics.drawString(font, "伤害  " + entry.getString("Damage"), x, infoY, TEXT, false);
            graphics.drawString(font, "攻速  " + entry.getString("AttackSpeed")
                    + "    范围  " + entry.getString("AttackRange"), x, infoY + 16, TEXT_DIM, false);
        } else {
            graphics.drawString(font, "抗性   R " + format(entry.getFloat("RedResistance"))
                    + "   W " + format(entry.getFloat("WhiteResistance")), x, infoY, TEXT, false);
            graphics.drawString(font, "       B " + format(entry.getFloat("BlackResistance"))
                    + "   P " + format(entry.getFloat("PaleResistance")), x, infoY + 16,
                    TEXT_DIM, false);
        }

        int requirementY = infoY + 45;
        boolean requirementsMet = entry.getBoolean(prefix() + "RequirementsMet");
        graphics.drawString(font, "属性要求", x, requirementY, TEXT_DIM, false);
        graphics.drawString(font, fit(entry.getString(prefix() + "Requirements"), innerW),
                x, requirementY + 15, requirementsMet ? TEXT : CANNOT, false);
        if (!requirementsMet) {
            String missing = String.join(" / ", strings(
                    entry.getList(prefix() + "Missing", Tag.TAG_STRING)));
            graphics.drawString(font, fit("不足：" + missing, innerW), x, requirementY + 30,
                    CANNOT, false);
        }

        List<String> lore = strings(entry.getList("Lore", Tag.TAG_STRING));
        int loreY = Math.min(contentBottom - 67, requirementY + 55);
        graphics.fill(x, loreY - 8, x + innerW, loreY - 7, LINE_DIM);
        for (int index = 0; index < Math.min(2, lore.size()); index++) {
            graphics.drawString(font, fit(lore.get(index), innerW), x, loreY + index * 14,
                    TEXT_DIM, false);
        }
    }

    private void drawTab(GuiGraphics graphics, int x, int y, int w, String label,
                         boolean selected, int color) {
        graphics.fill(x, y, x + w, y + 31, selected ? 0xD0180A0A : 0xB6070908);
        graphics.fill(x, y + 30, x + w, y + 32, selected ? color : LINE_DIM);
        graphics.drawCenteredString(font, label, x + w / 2, y + 11,
                selected ? color : TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = HEADER_H + 8;
        int tabW = inventoryW / 2;
        if (button == 0 && mouseY >= top && mouseY < top + 34
                && mouseX >= inventoryX && mouseX < inventoryX + inventoryW) {
            equipmentType = mouseX < inventoryX + tabW ? "weapon" : "armor";
            updateActionButton();
            return true;
        }
        int rowY = top + 56;
        if (button == 0 && mouseX >= inventoryX && mouseX < inventoryX + inventoryW
                && mouseY >= rowY) {
            int index = (int) ((mouseY - rowY) / 52);
            if (index >= 0 && index < entries().size()) {
                selectedIndex = index;
                updateActionButton();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleAssignment() {
        CompoundTag entry = entry();
        if (waiting || entry.isEmpty()) return;
        waiting = true;
        updateActionButton();
        MessageLoader.getLoader().sendToServer(new SetSpecialEgoPacket(
                entry.getString("Id"), equipmentType,
                !entry.getBoolean(prefix() + "UsedByViewer")));
    }

    private void updateActionButton() {
        if (actionButton == null) return;
        CompoundTag entry = entry();
        String typeName = "weapon".equals(equipmentType) ? "武器" : "护甲";
        boolean viewer = entry.getBoolean(prefix() + "UsedByViewer");
        boolean inUse = entry.getBoolean(prefix() + "InUse");
        boolean requirementsMet = entry.getBoolean(prefix() + "RequirementsMet");
        actionButton.setMessage(Component.literal(viewer ? "释放" + typeName + "席位"
                : "申请" + typeName + "席位"));
        actionButton.setSelected(viewer);
        actionButton.active = !waiting && !entry.isEmpty() && entry.getBoolean("Unlocked")
                && (viewer || !inUse && requirementsMet);
        String tooltip = !entry.getBoolean("Unlocked") ? "击败对应特殊事件后解锁"
                : viewer ? "卸下并释放给其他玩家"
                : inUse ? "该席位正由其他玩家使用"
                : requirementsMet ? "占用全服唯一的当前使用席位" : "属性等级不足";
        actionButton.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private ListTag entries() {
        return snapshot.getList("SpecialEgos", Tag.TAG_COMPOUND);
    }

    private CompoundTag entry() {
        ListTag entries = entries();
        return entries.isEmpty() ? new CompoundTag()
                : entries.getCompound(Mth.clamp(selectedIndex, 0, entries.size() - 1));
    }

    private String prefix() {
        return "weapon".equals(equipmentType) ? "Weapon" : "Armor";
    }

    private int typeColor() {
        return "weapon".equals(equipmentType) ? WEAPON_RED : ARMOR_BLUE;
    }

    private List<ItemStack> itemStacks(ListTag itemIds) {
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < itemIds.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(itemIds.getString(index));
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item != null) result.add(new ItemStack(item));
        }
        return result;
    }

    private static List<String> strings(ListTag values) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) result.add(values.getString(index));
        return result;
    }

    private void renderItemScaled(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 80.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y,
                                float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private String fit(String text, int width) {
        return LobotomyUiRenderer.fitText(font, text, Math.max(8, width));
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        if (parent instanceof FacilityManagementScreen) {
            MessageLoader.getLoader().sendToServer(RequestFacilityManagementPacket.returnToFacility());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y,
                                    int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
