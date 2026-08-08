package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ConfirmDepartmentExpansionPacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Full-screen, server-authoritative department expansion selector. */
public final class DepartmentExpansionScreen extends Screen {
    private static final int BACKGROUND = 0xFF020303;
    private static final int TEXT = 0xFFF1EFCB;
    private static final int MUTED = 0xFF8E907D;
    private static final int LOCKED = 0xFF686868;
    private static final int OPENABLE = 0xFFFFE86C;
    private static final int COMPLETE = 0xFF64DBE8;
    private static final int ALERT = 0xFFD94C4C;

    private static final EmployeeDepartment[] UPPER = {
            EmployeeDepartment.CONTROL, EmployeeDepartment.INFORMATION,
            EmployeeDepartment.TRAINING, EmployeeDepartment.SAFETY
    };
    private static final EmployeeDepartment[] MIDDLE = {
            EmployeeDepartment.WELFARE, EmployeeDepartment.CENTRAL,
            EmployeeDepartment.DISCIPLINARY
    };
    private static final EmployeeDepartment[] LOWER = {
            EmployeeDepartment.RECORD, EmployeeDepartment.ARCHITECTURE,
            EmployeeDepartment.EXTRACTION
    };

    private CompoundTag snapshot;
    private final Map<EmployeeDepartment, Node> nodes = new EnumMap<>(EmployeeDepartment.class);
    private final EnumSet<EmployeeDepartment> eligible = EnumSet.noneOf(EmployeeDepartment.class);
    private EmployeeDepartment selected;
    private boolean waiting;
    private long pressedUntil;
    private long resolvedAt;

    public DepartmentExpansionScreen(CompoundTag snapshot) {
        super(Component.literal("部门扩建"));
        this.snapshot = snapshot.copy();
        readSnapshot();
    }

    public void updateSnapshot(CompoundTag updated) {
        boolean wasPending = pendingDay() > 0;
        snapshot = updated.copy();
        waiting = false;
        readSnapshot();
        if (wasPending && pendingDay() == 0) {
            resolvedAt = Util.getMillis();
            play(SoundEvents.PLAYER_LEVELUP, 0.65F, 1.15F);
        }
    }

    private void readSnapshot() {
        eligible.clear();
        ListTag ids = snapshot.getList("EligibleDepartments", Tag.TAG_STRING);
        for (int index = 0; index < ids.size(); index++) {
            EmployeeDepartment department = EmployeeDepartment.byId(ids.getString(index));
            if (department != EmployeeDepartment.UNASSIGNED) eligible.add(department);
        }
        if (selected != null && !eligible.contains(selected)) selected = null;
    }

    @Override
    public void init() {
        rebuildNodes();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        rebuildNodes();
        renderHeader(graphics);
        renderConnections(graphics);
        for (Node node : nodes.values()) renderNode(graphics, node, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        renderClose(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        int day = snapshot.getInt("CurrentDay");
        drawLargeText(graphics, "DAY " + day, 24, 18, 2, OPENABLE);
        graphics.drawString(font, "部门扩建", 24, 43, TEXT, false);
        String state;
        int color;
        if (pendingDay() > 0) {
            String pendingPrefix = pendingDay() < day ? "补办 DAY " + pendingDay() + " · " : "";
            state = pendingPrefix + (snapshot.getBoolean("CanConfirm")
                    ? "选择一个可扩建部门。本日仅接受一次确认。"
                    : "等待服务器管理员确认本日扩建。");
            color = MUTED;
        } else if (snapshot.getInt("LastExpandedDay") == day) {
            EmployeeDepartment expanded = EmployeeDepartment.byId(
                    snapshot.getString("LastExpandedDepartment"));
            state = "本日扩建完成" + (expanded == EmployeeDepartment.UNASSIGNED
                    ? "" : "：" + expanded.displayName());
            color = COMPLETE;
        } else {
            state = "当前没有待处理的扩建决策。";
            color = MUTED;
        }
        graphics.drawString(font, state, 130, 45, color, false);
        graphics.fill(24, 65, width - 24, 66, 0xFF55563F);
    }

    private void rebuildNodes() {
        nodes.clear();
        int margin = Math.max(18, width / 40);
        int gap = Mth.clamp(width / 55, 10, 24);
        int upperWidth = Math.min(190, Math.max(104,
                (width - margin * 2 - gap * 3) / 4));
        int otherWidth = Math.min(205, Math.max(120,
                (width - margin * 2 - gap * 2) / 3));
        int nodeHeight = Mth.clamp((height - 135) / 3, 38, 86);
        int firstY = Math.max(72, height / 6);
        int usableBottom = height - 50;
        int rowGap = Math.max(nodeHeight + 8,
                (usableBottom - firstY - nodeHeight) / 2);

        layoutRow(UPPER, upperWidth, nodeHeight, firstY, gap);
        layoutRow(MIDDLE, otherWidth, nodeHeight, firstY + rowGap, gap);
        layoutRow(LOWER, otherWidth, nodeHeight, firstY + rowGap * 2, gap);
    }

    private void layoutRow(EmployeeDepartment[] departments, int nodeWidth, int nodeHeight,
                           int y, int gap) {
        int total = departments.length * nodeWidth + (departments.length - 1) * gap;
        int x = (width - total) / 2;
        for (EmployeeDepartment department : departments) {
            nodes.put(department, new Node(department, x, y, nodeWidth, nodeHeight));
            x += nodeWidth + gap;
        }
    }

    private void renderConnections(GuiGraphics graphics) {
        Node central = nodes.get(EmployeeDepartment.CENTRAL);
        int upperBusY = central.y - Math.max(10, (central.y - bottom(nodes.get(UPPER[0]))) / 2);
        int upperLeft = centerX(nodes.get(UPPER[0]));
        int upperRight = centerX(nodes.get(UPPER[UPPER.length - 1]));
        drawLine(graphics, upperLeft, upperBusY, upperRight, upperBusY, 0xFF46483C);
        for (EmployeeDepartment department : UPPER) {
            Node node = nodes.get(department);
            drawLine(graphics, centerX(node), bottom(node), centerX(node), upperBusY, dim(department));
        }
        drawLine(graphics, centerX(central), upperBusY, centerX(central), central.y,
                dim(EmployeeDepartment.CENTRAL));

        Node welfare = nodes.get(EmployeeDepartment.WELFARE);
        Node discipline = nodes.get(EmployeeDepartment.DISCIPLINARY);
        int middleBottom = Math.max(bottom(welfare), Math.max(bottom(central), bottom(discipline)));
        Node record = nodes.get(EmployeeDepartment.RECORD);
        int lowerBusY = record.y - Math.max(10, (record.y - middleBottom) / 2);
        drawLine(graphics, centerX(welfare), lowerBusY, centerX(discipline), lowerBusY, 0xFF46483C);
        for (EmployeeDepartment department : MIDDLE) {
            Node node = nodes.get(department);
            drawLine(graphics, centerX(node), bottom(node), centerX(node), lowerBusY, dim(department));
        }
        for (EmployeeDepartment department : new EmployeeDepartment[]{
                EmployeeDepartment.RECORD, EmployeeDepartment.EXTRACTION}) {
            Node node = nodes.get(department);
            drawLine(graphics, centerX(node), lowerBusY, centerX(node), node.y, dim(department));
        }
        Node architecture = nodes.get(EmployeeDepartment.ARCHITECTURE);
        drawLine(graphics, centerX(record), centerY(record), centerX(architecture),
                centerY(architecture), dim(EmployeeDepartment.ARCHITECTURE));
        drawLine(graphics, centerX(architecture), centerY(architecture),
                centerX(nodes.get(EmployeeDepartment.EXTRACTION)), centerY(architecture),
                dim(EmployeeDepartment.ARCHITECTURE));
    }

    private void renderNode(GuiGraphics graphics, Node node, int mouseX, int mouseY) {
        int level = level(node.department);
        boolean complete = level >= 5;
        boolean openable = pendingDay() > 0 && eligible.contains(node.department);
        boolean hovered = node.contains(mouseX, mouseY);
        boolean chosen = node.department == selected;
        int baseColor = 0xFF000000 | node.department.textColorRgb();
        int color = complete ? COMPLETE : openable ? OPENABLE : level > 0 ? baseColor : LOCKED;
        if (hovered && (openable || level > 0)) color = 0xFFFFFFFF;

        if (chosen || (hovered && openable)) {
            graphics.fill(node.x - 3, node.y - 3, node.x + node.width + 3,
                    node.y + node.height + 3, chosen ? 0x24FFE86C : 0x14FFFFFF);
        }
        fillClippedPanel(graphics, node, chosen ? 0xF00D0F09 : 0xED050605);
        outlineClippedPanel(graphics, node, color);

        boolean compact = node.height < 56;
        graphics.drawString(font, node.department.displayName(), node.x + 10,
                node.y + (compact ? 6 : 9), color, false);
        String levelText = level == 0 ? "LOCKED" : "LEVEL " + roman(level);
        graphics.drawString(font, levelText,
                node.x + node.width - 10 - font.width(levelText), node.y + (compact ? 17 : 9),
                level == 0 ? LOCKED : color, false);

        if (!compact) {
            int stripeY = node.y + 25;
            for (int x = node.x + 7; x < node.x + node.width - 7; x += 8) {
                graphics.fill(x, stripeY, Math.min(x + 4, node.x + node.width - 7),
                        stripeY + 2, (x / 8 & 1) == 0 ? color : 0xFF24251F);
            }

            String status = nodeStatus(node.department, level, complete, openable);
            int statusColor = openable ? OPENABLE : complete ? COMPLETE : level > 0 ? TEXT : MUTED;
            drawFittedText(graphics, status, node.x + 10, node.y + 35,
                    node.width - 20, statusColor);
        }

        int segmentGap = 3;
        int segmentWidth = Math.max(6, (node.width - 20 - segmentGap * 4) / 5);
        int barY = node.y + node.height - 13;
        for (int index = 0; index < 5; index++) {
            int x = node.x + 10 + index * (segmentWidth + segmentGap);
            int segmentColor = index < level ? color : 0xFF292A25;
            graphics.fill(x, barY, x + segmentWidth, barY + 4, segmentColor);
        }
    }

    private String nodeStatus(EmployeeDepartment department, int level,
                              boolean complete, boolean openable) {
        if (complete) return "扩建完成";
        if (openable) return level == 0 ? "可开放" : "可继续扩建";
        if (level > 0) {
            if (department == EmployeeDepartment.CENTRAL && level < 3) {
                return "下部于 LEVEL III 开放";
            }
            return "等待后续扩建";
        }
        return requirement(department);
    }

    private String requirement(EmployeeDepartment department) {
        return switch (department) {
            case CONTROL -> "初始部门";
            case INFORMATION -> "需要控制部 LEVEL V";
            case TRAINING, SAFETY -> "需要情报部 LEVEL V";
            case CENTRAL -> "需要上层全部 LEVEL V";
            case WELFARE, DISCIPLINARY -> "需要中央本部 LEVEL V";
            case RECORD, EXTRACTION -> "需要中层全部 LEVEL V";
            case ARCHITECTURE -> "需要记录部与研发部 LEVEL V";
            default -> "不可开放";
        };
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonWidth = Math.min(240, Math.max(150, width / 4));
        int buttonHeight = 28;
        int x = (width - buttonWidth) / 2;
        int y = height - buttonHeight - 14;
        boolean canConfirm = snapshot.getBoolean("CanConfirm") && pendingDay() > 0
                && selected != null && eligible.contains(selected) && !waiting;
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        int color = canConfirm ? (hovered ? 0xFFFFFFFF : OPENABLE) : LOCKED;
        int offset = Util.getMillis() < pressedUntil ? 1 : 0;
        graphics.fill(x + offset, y + offset, x + buttonWidth + offset,
                y + buttonHeight + offset, 0xF20A0B08);
        outline(graphics, x + offset, y + offset, buttonWidth, buttonHeight, color);
        String label;
        if (waiting) label = "正在提交...";
        else if (pendingDay() == 0) label = "本日扩建已完成";
        else if (!snapshot.getBoolean("CanConfirm")) label = "只读：等待管理员确认";
        else if (selected == null) label = "选择一个可扩建部门";
        else label = "确认扩建 " + selected.displayName();
        graphics.drawString(font, label, x + (buttonWidth - font.width(label)) / 2,
                y + 10, color, false);

        if (resolvedAt > 0 && Util.getMillis() - resolvedAt < 1300L) {
            long elapsed = Util.getMillis() - resolvedAt;
            int alpha = (int) (255 * (1.0F - elapsed / 1300.0F));
            String complete = "EXPANSION COMPLETE";
            graphics.drawString(font, complete, (width - font.width(complete)) / 2,
                    y - 17, (alpha << 24) | 0x64DBE8, false);
        }
    }

    private void renderClose(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = width - 31;
        int y = 18;
        boolean hovered = inside(mouseX, mouseY, x, y, 15, 15);
        int color = hovered ? 0xFFFFFFFF : MUTED;
        outline(graphics, x, y, 15, 15, color);
        drawLine(graphics, x + 4, y + 4, x + 10, y + 10, color);
        drawLine(graphics, x + 10, y + 4, x + 4, y + 10, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, width - 31, 18, 15, 15)) {
            play(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 1.1F);
            onClose();
            return true;
        }
        for (Node node : nodes.values()) {
            if (!node.contains(mouseX, mouseY) || pendingDay() == 0) continue;
            if (eligible.contains(node.department)) {
                selected = node.department;
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.30F, 1.42F);
            } else {
                play(SoundEvents.VILLAGER_NO, 0.28F, 0.75F);
            }
            return true;
        }

        int buttonWidth = Math.min(240, Math.max(150, width / 4));
        int x = (width - buttonWidth) / 2;
        int y = height - 42;
        if (inside(mouseX, mouseY, x, y, buttonWidth, 28)
                && snapshot.getBoolean("CanConfirm") && pendingDay() > 0
                && selected != null && eligible.contains(selected) && !waiting) {
            waiting = true;
            pressedUntil = Util.getMillis() + 130L;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 0.45F, 0.92F);
            MessageLoader.getLoader().sendToServer(new ConfirmDepartmentExpansionPacket(
                    pendingDay(), selected.id()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int pendingDay() {
        return snapshot.getInt("PendingDay");
    }

    private int level(EmployeeDepartment department) {
        return snapshot.getCompound("Levels").getInt(department.id());
    }

    private int dim(EmployeeDepartment department) {
        int level = level(department);
        int rgb = level > 0 ? department.textColorRgb() : 0x46483C;
        return 0xFF000000 | rgb;
    }

    private void drawFittedText(GuiGraphics graphics, String value, int x, int y,
                                int maxWidth, int color) {
        int textWidth = font.width(value);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, value, x, y, color, false);
            return;
        }
        float scale = Math.max(0.62F, maxWidth / (float) Math.max(1, textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, value, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static String roman(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "I";
        };
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft.player != null) minecraft.player.playSound(sound, volume, pitch);
    }

    private void drawLargeText(GuiGraphics graphics, String text, int x, int y,
                               int scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void fillClippedPanel(GuiGraphics graphics, Node node, int color) {
        int cut = 13;
        for (int row = 0; row < cut; row++) {
            graphics.fill(node.x + cut - row, node.y + row,
                    node.x + node.width, node.y + row + 1, color);
        }
        graphics.fill(node.x, node.y + cut, node.x + node.width,
                node.y + node.height, color);
    }

    private static void outlineClippedPanel(GuiGraphics graphics, Node node, int color) {
        int cut = 13;
        drawLine(graphics, node.x + cut, node.y, node.x + node.width - 1, node.y, color);
        drawLine(graphics, node.x, node.y + cut, node.x + cut, node.y, color);
        drawLine(graphics, node.x, node.y + cut, node.x, node.y + node.height - 1, color);
        drawLine(graphics, node.x, node.y + node.height - 1,
                node.x + node.width - 1, node.y + node.height - 1, color);
        drawLine(graphics, node.x + node.width - 1, node.y,
                node.x + node.width - 1, node.y + node.height - 1, color);
    }

    private static void outline(GuiGraphics graphics, int x, int y,
                                int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0,
                                 int x1, int y1, int color) {
        if (x0 == x1) {
            graphics.fill(x0, Math.min(y0, y1), x0 + 1, Math.max(y0, y1) + 1, color);
            return;
        }
        if (y0 == y1) {
            graphics.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + 1, color);
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

    private static int centerX(Node node) { return node.x + node.width / 2; }
    private static int centerY(Node node) { return node.y + node.height / 2; }
    private static int bottom(Node node) { return node.y + node.height; }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Node(EmployeeDepartment department, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }

    @Override
    public void onClose() {
        boolean returnToFacility = minecraft.level != null
                && minecraft.level.dimension() == ModDimensions.LOBOTO_KEY;
        super.onClose();
        if (returnToFacility) {
            MessageLoader.getLoader().sendToServer(RequestFacilityManagementPacket.returnToFacility());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
