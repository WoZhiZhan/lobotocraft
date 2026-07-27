package com.wzz.lobotocraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.room.RoomMapViewState;
import com.wzz.lobotocraft.client.room.RoomViewClient;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.BindRoomAbnormalityPacket;
import com.wzz.lobotocraft.network.packet.OpenRoomAbnormalityInfoPacket;
import com.wzz.lobotocraft.network.packet.RequestNearbyAbnormalitiesPacket;
import com.wzz.lobotocraft.network.packet.RoomTeleportRequestPacket;
import com.wzz.lobotocraft.network.packet.StartRoomWorkPacket;
import com.wzz.lobotocraft.network.packet.SetFloorNamePacket;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.world.data.AbnormalityCandidate;
import com.wzz.lobotocraft.world.data.FloorEntry;
import com.wzz.lobotocraft.world.data.RoomEntry;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 二维设施平面图。
 * <p>
 * 房间按真实世界的 X / Z 坐标排布，所以这张图和你实际建的公司布局是一致的。
 * 左侧楼层列表来自世界里的「楼层标记方块」——每层放一个即可，间距多少都行；
 * 一个楼层标记都没放的话，退化成按 Y 坐标自动聚类分层。
 * <p>
 * 操作：
 * <ul>
 *   <li>左键房间：传送过去</li>
 *   <li>右键房间：打开这只异想体的图鉴</li>
 *   <li>Ctrl + 左键拖房间：调整它在图上的显示位置（只影响 GUI，房间太挤时用），会记住</li>
 *   <li>左键楼层条：切换楼层；右键楼层条：给这层改名</li>
 *   <li>滚轮缩放 / 拖动平移，位置每层单独记忆，下次打开还在原处</li>
 *   <li>空格复位视野，Ctrl+空格清掉所有手动挪动的房间位置</li>
 * </ul>
 */
public class RoomMapScreen extends Screen {

    private static final ResourceLocation QUESTION_MARK =
            ResourceUtil.createInstance("textures/gui/question_mark.png");

    /** 右上角显示的名字。按你的要求写死 */
    private static final String FACILITY_NAME = "公司";

    private static final int HEADER_H = 32;

    private static final int CARD_W = 120;
    private static final int CARD_H = 146;
    private static final int PORTRAIT = 64;

    private static final int TAB_W = 132;
    private static final int TAB_H = 32;
    private static final int TAB_GAP = 4;
    private static final int TAB_X = 10;
    private static final int TAB_TOP = HEADER_H + 22;

    /** 没有楼层标记方块时，自动分层用的 Y 容差 */
    private static final int AUTO_FLOOR_TOLERANCE = 6;

    private static final int COLOR_BG = 0xC8060A12;
    private static final int COLOR_GRID = 0x1A5FA8D3;
    private static final int COLOR_GRID_MAJOR = 0x335FA8D3;
    private static final int COLOR_CARD_BG = 0xE00C1220;
    private static final int COLOR_CARD_BG_HOVER = 0xF0142236;
    private static final int COLOR_PANEL = 0xE0080D16;
    private static final int COLOR_TEXT = 0xFFE8EEF5;
    private static final int COLOR_TEXT_DIM = 0xFF8A99AD;
    private static final int COLOR_ACCENT = 0xFF5FA8D3;

    private static final int STATUS_OCCUPIED = 0xFF4ADE80;
    private static final int STATUS_ESCAPED = 0xFFEF4444;
    private static final int STATUS_EMPTY = 0xFF7A8798;
    private static final int STATUS_LOST = 0xFFD9A036;

    private static final int PICKER_W = 340;
    private static final int PICKER_ROW_H = 30;
    private static final int PICKER_HEADER_H = 26;
    private static final int PICKER_FOOTER_H = 28;
    private static final int PICKER_MAX_ROWS = 8;

    private List<RoomEntry> rooms;
    private List<FloorEntry> floorEntries;
    private long serverGameTime;
    private final ResourceLocation dimension;

    private final List<Floor> floors = new ArrayList<>();
    private int selectedFloor = 0;
    private String selectedLabel = null;
    private double tabScroll = 0;

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
    /** Ctrl 拖拽中的房间 */
    private RoomEntry dragging;

    /** "更改住户"选单。null = 没打开 */
    private RoomEntry pickerRoom;
    private List<AbnormalityCandidate> pickerCandidates;
    private int pickerScroll = 0;

    public RoomMapScreen(List<RoomEntry> rooms, List<FloorEntry> floorEntries,
                         long serverGameTime, ResourceLocation dimension) {
        super(Component.literal("设施监控"));
        this.rooms = new ArrayList<>(rooms);
        this.floorEntries = new ArrayList<>(floorEntries);
        this.serverGameTime = serverGameTime;
        this.dimension = dimension;
        this.selectedLabel = RoomMapViewState.getLastFloorLabel();
    }

    @Override
    public void init() {
        super.init();
        buildFloors();
        restoreOrFitView();
    }

    // ------------------------------------------------------------ 楼层分组

    private static class Floor {
        final String label;
        /** 对应的楼层标记方块坐标，null = 自动分层出来的，不能改名 */
        final BlockPos markerPos;
        int topY = Integer.MIN_VALUE;
        final List<RoomEntry> rooms = new ArrayList<>();

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
                Floor other = new Floor("未分层", null);
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
    }

    private List<RoomEntry> visibleRooms() {
        if (floors.isEmpty()) return new ArrayList<>();
        return floors.get(Mth.clamp(selectedFloor, 0, floors.size() - 1)).rooms;
    }

    private boolean showTabs() {
        return floors.size() >= 2;
    }

    private int mapLeft() {
        return showTabs() ? TAB_X + TAB_W + 11 : 0;
    }

    private void selectFloor(int index) {
        if (floors.isEmpty()) return;
        saveCurrentView();
        cancelRename();
        selectedFloor = Mth.clamp(index, 0, floors.size() - 1);
        selectedLabel = floors.get(selectedFloor).label;
        RoomMapViewState.setLastFloorLabel(selectedLabel);
        restoreOrFitView();
    }

    // ------------------------------------------------------------ 视野

    /** 有记住的位置就用记住的，没有就自动适配 */
    private void restoreOrFitView() {
        if (selectedLabel != null) {
            double[] saved = RoomMapViewState.getFloorView(selectedLabel);
            if (saved != null && saved[2] > 0) {
                computeCenter();
                panX = saved[0];
                panY = saved[1];
                zoom = saved[2];
                return;
            }
        }
        fitToView();
    }

    private void computeCenter() {
        List<RoomEntry> visible = visibleRooms();
        if (visible.isEmpty()) return;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (RoomEntry entry : visible) {
            double[] off = RoomMapViewState.getRoomOffset(entry.key());
            double x = entry.pos.getX() + off[0];
            double z = entry.pos.getZ() + off[1];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        centerWorldX = (minX + maxX) / 2.0D;
        centerWorldZ = (minZ + maxZ) / 2.0D;
    }

    private void fitToView() {
        List<RoomEntry> visible = visibleRooms();
        if (visible.isEmpty()) return;

        computeCenter();

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (RoomEntry entry : visible) {
            double[] off = RoomMapViewState.getRoomOffset(entry.key());
            minX = Math.min(minX, entry.pos.getX() + off[0]);
            maxX = Math.max(maxX, entry.pos.getX() + off[0]);
            minZ = Math.min(minZ, entry.pos.getZ() + off[1]);
            maxZ = Math.max(maxZ, entry.pos.getZ() + off[1]);
        }

        double spanX = Math.max(1, maxX - minX);
        double spanZ = Math.max(1, maxZ - minZ);
        double usableW = (this.width - mapLeft()) - CARD_W - 40;
        double usableH = (this.height - HEADER_H) - CARD_H - 50;

        this.zoom = Mth.clamp(Math.min(usableW / spanX, usableH / spanZ), 0.35D, 6.0D);
        this.panX = 0;
        this.panY = 0;
        saveCurrentView();
    }

    private void saveCurrentView() {
        if (selectedLabel != null) {
            RoomMapViewState.setFloorView(selectedLabel, panX, panY, zoom);
        }
    }

    public void onDataRefreshed(List<RoomEntry> newRooms, List<FloorEntry> newFloors, long newServerGameTime) {
        this.rooms = new ArrayList<>(newRooms);
        this.floorEntries = new ArrayList<>(newFloors);
        this.serverGameTime = newServerGameTime;
        buildFloors();
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

    /** 房间在图上的位置 = 真实坐标 + 手动偏移 */
    private double cardWorldX(RoomEntry entry) {
        return entry.pos.getX() + RoomMapViewState.getRoomOffset(entry.key())[0];
    }

    private double cardWorldZ(RoomEntry entry) {
        return entry.pos.getZ() + RoomMapViewState.getRoomOffset(entry.key())[1];
    }

    // ------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float alpha = Mth.clamp((Util.getMillis() - openedAt) / 260.0F, 0F, 1F);

        graphics.fill(0, 0, this.width, this.height, withAlpha(COLOR_BG, alpha));
        renderGrid(graphics, alpha);

        List<RoomEntry> visible = visibleRooms();

        this.hovered = null;
        if (mouseX >= mapLeft() && mouseY >= HEADER_H) {
            for (RoomEntry entry : visible) {
                int left = (int) (worldToScreenX(cardWorldX(entry)) - CARD_W / 2.0D);
                int top = (int) (worldToScreenY(cardWorldZ(entry)) - CARD_H / 2.0D);
                if (mouseX >= left && mouseX <= left + CARD_W && mouseY >= top && mouseY <= top + CARD_H) {
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

        if (showTabs()) {
            renderFloorTabs(graphics, mouseX, mouseY, alpha);
        }
        renderHeader(graphics, alpha);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (pickerRoom != null) {
            renderPicker(graphics, mouseX, mouseY, alpha);
        } else if (hovered != null && dragging == null) {
            graphics.renderComponentTooltip(this.font, buildTooltip(hovered), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------ 更改住户选单

    /** 收到服务端送来的候选清单后由网络包调用 */
    public void showAbnormalityPicker(BlockPos roomPos, List<AbnormalityCandidate> candidates) {
        for (RoomEntry entry : rooms) {
            if (entry.pos.equals(roomPos)) {
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
        String title = "更改住户 · " + this.font.plainSubstrByWidth(pickerRoom.resolveTitle(), 180);
        graphics.drawString(this.font, title, left + 10, top + 9, COLOR_TEXT, false);

        int rowsTop = top + PICKER_HEADER_H;

        if (pickerRowCount() == 0) {
            graphics.drawString(this.font, "附近 64 格内没有找到异想体",
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
            String where = candidate.inRoom ? "房间内" : (candidate.distance + " 格外");
            graphics.drawString(this.font, where, subX, rowTop + 16,
                    candidate.inRoom ? STATUS_OCCUPIED : COLOR_TEXT_DIM, false);

            if (candidate.alreadyBound) {
                String tag = "当前";
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

        drawPickerButton(graphics, left + 8, footTop + 4, 120, 20, "清空登记", mouseX, mouseY, 0xFFD9A036);
        drawPickerButton(graphics, left + PICKER_W - 88, footTop + 4, 80, 20, "取消", mouseX, mouseY, COLOR_TEXT_DIM);
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

    private void renderGrid(GuiGraphics graphics, float alpha) {
        double step = 16 * zoom;
        if (step < 8) return;

        int minor = withAlpha(COLOR_GRID, alpha);
        int major = withAlpha(COLOR_GRID_MAJOR, alpha);
        int left = mapLeft();

        double worldLeft = screenToWorldX(left);
        double worldRight = screenToWorldX(this.width);
        double worldTop = screenToWorldZ(HEADER_H);
        double worldBottom = screenToWorldZ(this.height);

        int startX = (int) (Math.floor(worldLeft / 16) * 16);
        for (int wx = startX; wx <= worldRight; wx += 16) {
            int sx = (int) worldToScreenX(wx);
            if (sx < left) continue;
            graphics.fill(sx, HEADER_H, sx + 1, this.height, (wx % 64 == 0) ? major : minor);
        }
        int startZ = (int) (Math.floor(worldTop / 16) * 16);
        for (int wz = startZ; wz <= worldBottom; wz += 16) {
            int sy = (int) worldToScreenY(wz);
            if (sy < HEADER_H) continue;
            graphics.fill(left, sy, this.width, sy + 1, (wz % 64 == 0) ? major : minor);
        }
    }

    private void renderCard(GuiGraphics graphics, RoomEntry entry, boolean isHovered, float alpha) {
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
            int base = (entry.riskColor != 0 && entry.status != RoomEntry.STATUS_EMPTY)
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
            drawCentered(graphics, "空", cx, py + PORTRAIT / 2 - 4, withAlpha(COLOR_TEXT_DIM, alpha));
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

        if (entry.status != RoomEntry.STATUS_EMPTY) {
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
            case RoomEntry.STATUS_OCCUPIED -> "收容中";
            case RoomEntry.STATUS_ESCAPED -> entry.escapeDistance > 0
                    ? "出逃中 " + entry.escapeDistance + "格" : "出逃中";
            case RoomEntry.STATUS_LOST -> "失联";
            default -> "空置";
        };
        if (entry.playersInside > 0) {
            statusText += "  员工x" + entry.playersInside;
        }
        drawCentered(graphics, statusText, cx, top + CARD_H - 26, withAlpha(statusColor, alpha));

        long lag = serverGameTime - entry.lastUpdate;
        if (lag > 100) {
            drawCentered(graphics, "数据延迟 " + (lag / 20) + "s", cx, top + CARD_H - 14,
                    withAlpha(0xFFD9A036, alpha * 0.9F));
        }
    }

    private void renderFloorTabs(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        int panelRight = TAB_X + TAB_W + 10;
        graphics.fill(0, HEADER_H, panelRight, this.height, withAlpha(COLOR_PANEL, alpha));
        graphics.fill(panelRight, HEADER_H, panelRight + 1, this.height, withAlpha(COLOR_ACCENT, alpha * 0.6F));

        graphics.drawString(this.font, "楼层  §8右键改名", TAB_X, HEADER_H + 8,
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

            String info = floor.rooms.size() + " 间" + (escaped > 0 ? "   出逃 " + escaped : "");
            graphics.drawString(this.font, info, TAB_X + 9, top + 18,
                    withAlpha(escaped > 0 ? STATUS_ESCAPED : COLOR_TEXT_DIM, alpha * 0.9F), false);
        }
    }

    private void renderHeader(GuiGraphics graphics, float alpha) {
        graphics.fill(0, 0, this.width, HEADER_H, withAlpha(0xF00A1018, alpha));
        graphics.fill(0, HEADER_H, this.width, HEADER_H + 1, withAlpha(COLOR_ACCENT, alpha));

        String title = "收容设施监控";
        graphics.drawString(this.font, title, 12, 11, withAlpha(COLOR_TEXT, alpha), false);
        int x = 12 + this.font.width(title) + 14;

        int escaped = 0;
        int contained = 0;
        for (RoomEntry entry : rooms) {
            if (entry.status == RoomEntry.STATUS_ESCAPED) escaped++;
            else if (entry.status == RoomEntry.STATUS_OCCUPIED) contained++;
        }

        String stats = "收容间 " + rooms.size() + "   收容中 " + contained
                + "   出逃 " + escaped + "   楼层 " + floors.size();
        graphics.drawString(this.font, stats, x, 11,
                withAlpha(escaped > 0 ? STATUS_ESCAPED : COLOR_TEXT_DIM, alpha), false);

        graphics.drawString(this.font, FACILITY_NAME,
                this.width - this.font.width(FACILITY_NAME) - 12, 11,
                withAlpha(COLOR_TEXT, alpha), false);

        String hint = "空格开始工作   左键传送   右键查资料   Ctrl+左键拖动   Ctrl+右键改住户   ↑↓ 换层   R 复位   ESC 返回";
        graphics.drawString(this.font, hint,
                (this.width - this.font.width(hint)) / 2, this.height - 16,
                withAlpha(COLOR_TEXT_DIM, alpha), false);
    }

    private List<Component> buildTooltip(RoomEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§f" + entry.resolveTitle()));
        if (entry.floorName != null && !entry.floorName.isEmpty()) {
            lines.add(Component.literal("§7楼层：§f" + entry.floorName));
        }
        if (!entry.abnormalityCode.isEmpty()) {
            lines.add(Component.literal("§7编号：§f" + entry.abnormalityCode));
            if (!entry.riskName.isEmpty()) {
                lines.add(Component.literal("§7风险等级：")
                        .append(Component.literal(entry.riskName)
                                .withStyle(style -> style.withColor(entry.riskColor & 0xFFFFFF))));
            }
        }
        lines.add(Component.literal("§7状态：" + switch (entry.status) {
            case RoomEntry.STATUS_OCCUPIED -> "§a收容中";
            case RoomEntry.STATUS_ESCAPED -> "§c出逃中"
                    + (entry.escapeDistance > 0 ? " §7(离房间 §c" + entry.escapeDistance + " §7格)" : "");
            case RoomEntry.STATUS_LOST -> "§6失联 §7(被回收 / 死亡待复活 / 区块未加载)";
            default -> "§8空置";
        }));
        if (entry.status != RoomEntry.STATUS_EMPTY) {
            lines.add(Component.literal("§7生命：§f" + Math.round(entry.healthPercent * 100) + "%"));
            if (entry.maxQliphoth > 0) {
                lines.add(Component.literal("§7计数器：§f" + entry.qliphoth + " / " + entry.maxQliphoth));
            }
        }
        lines.add(Component.literal("§7坐标：§f"
                + entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ()));
        if (entry.playersInside > 0) {
            lines.add(Component.literal("§7房间内员工：§f" + entry.playersInside));
        }
        long lag = serverGameTime - entry.lastUpdate;
        if (lag > 100) {
            lines.add(Component.literal("§6数据延迟 " + (lag / 20) + " 秒（区块未加载）"));
        }
        lines.add(Component.literal("§8空格开工   左键传送   右键查资料   Ctrl+左键拖动   Ctrl+右键改住户"));
        return lines;
    }

    // ------------------------------------------------------------ 楼层改名

    private void startRename(int index) {
        if (index < 0 || index >= floors.size()) return;
        Floor floor = floors.get(index);
        if (floor.markerPos == null) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal(
                        "§7这一层是按 Y 坐标自动分的，放一个楼层标记方块才能改名。"), true);
            }
            return;
        }
        cancelRename();

        int top = (int) (TAB_TOP + index * (TAB_H + TAB_GAP) - tabScroll);
        renamingFloor = index;
        renameBox = new EditBox(this.font, TAB_X + 5, top + 8, TAB_W - 10, 16, Component.literal("楼层名"));
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

        // 楼层条
        if (showTabs() && mouseX <= TAB_X + TAB_W && mouseY >= HEADER_H) {
            for (int i = 0; i < floors.size(); i++) {
                int top = (int) (TAB_TOP + i * (TAB_H + TAB_GAP) - tabScroll);
                if (mouseX >= TAB_X && mouseY >= top && mouseY <= top + TAB_H) {
                    if (button == 1) {
                        startRename(i);
                    } else if (button == 0) {
                        selectFloor(i);
                    }
                    return true;
                }
            }
            return true;
        }

        if (hovered != null) {
            // Ctrl + 左键 = 抓住这张卡片挪位置
            if (button == 0 && hasControlDown()) {
                dragging = hovered;
                return true;
            }
            // 左键 = 传送
            if (button == 0) {
                MessageLoader.getLoader().sendToServer(
                        new RoomTeleportRequestPacket(hovered.dimension, hovered.pos));
                saveCurrentView();
                onClose();
                return true;
            }
            // Ctrl + 右键 = 更改这个房间登记的异想体
            if (button == 1 && hasControlDown()) {
                MessageLoader.getLoader().sendToServer(
                        new RequestNearbyAbnormalitiesPacket(hovered.dimension, hovered.pos));
                return true;
            }
            // 右键 = 打开异想体图鉴
            if (button == 1) {
                if (hovered.abnormalityCode.isEmpty()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                Component.literal("§7这个收容间是空的。"), true);
                    }
                } else {
                    MessageLoader.getLoader().sendToServer(
                            new OpenRoomAbnormalityInfoPacket(hovered.dimension, hovered.pos));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pickerRoom != null) {
            return true;
        }
        if (renameBox != null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        // Ctrl 拖房间：偏移量按世界坐标存，缩放时相对关系不会乱
        if (dragging != null) {
            double[] off = RoomMapViewState.getRoomOffset(dragging.key());
            RoomMapViewState.setRoomOffset(dragging.key(),
                    off[0] + dragX / zoom, off[1] + dragY / zoom);
            return true;
        }
        if ((button == 0 || button == 2) && mouseX >= mapLeft()) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            RoomMapViewState.save();
            return true;
        }
        saveCurrentView();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;

        if (pickerRoom != null) {
            int max = Math.max(0, pickerRowCount() - PICKER_MAX_ROWS);
            pickerScroll = Mth.clamp(pickerScroll - (int) Math.signum(delta), 0, max);
            return true;
        }

        // 楼层列表上滚 -> 滚动列表
        if (showTabs() && mouseX <= TAB_X + TAB_W + 10) {
            double totalHeight = floors.size() * (TAB_H + TAB_GAP);
            double available = this.height - TAB_TOP - 20;
            double max = Math.max(0, totalHeight - available);
            tabScroll = Mth.clamp(tabScroll - delta * 24, 0, max);
            cancelRename();
            return true;
        }

        double worldX = screenToWorldX(mouseX);
        double worldZ = screenToWorldZ(mouseY);

        zoom = Mth.clamp(zoom * (delta > 0 ? 1.18D : 1 / 1.18D), 0.15D, 24.0D);

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
            case GLFW.GLFW_KEY_SPACE -> {
                startWorkOnHovered();
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                if (hasControlDown()) {
                    RoomMapViewState.clearRoomOffsets();
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                Component.literal("§7已清除所有手动调整的房间位置。"), true);
                    }
                }
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

    /**
     * 空格：对鼠标指着的房间直接开工。
     * <p>
     * 服务端会做全部检查（工具类 / 已在工作 / 精神状态 / 异想体自己的回调），
     * 并且因为 WorkManager 全程要求 10 格以内，必要时会先把你传送到那个房间。
     * 之后打开的就是原本那个工作选择界面。
     */
    private void startWorkOnHovered() {
        if (hovered == null) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.literal("§7把鼠标指到一个房间上再按空格。"), true);
            }
            return;
        }
        if (hovered.abnormalityCode.isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.literal("§7这个收容间是空的。"), true);
            }
            return;
        }
        MessageLoader.getLoader().sendToServer(
                new StartRoomWorkPacket(hovered.dimension, hovered.pos));
        saveCurrentView();
        onClose();
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
            default -> STATUS_EMPTY;
        };
    }

    /** 立绘路径和图鉴界面保持一致；缺图时退化成问号，不会出现紫黑格 */
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
}
