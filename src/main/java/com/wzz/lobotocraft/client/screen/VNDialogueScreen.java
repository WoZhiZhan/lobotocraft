package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@OnlyIn(Dist.CLIENT)
public class VNDialogueScreen extends Screen {
    private String npcName;
    private List<String> fullLines;
    private String soundBaseName;
    public int currentLineIndex = 0;
    private int messageCount;
    private int maxMessageCount;
    private int currentSoundIndex = 1;
    private int currentCharIndex = 0;
    private int tickCounter = 0;
    private String currentLine = "";

    private LivingEntity leftNpcEntity;
    private LivingEntity rightNpcEntity;
    private String leftNpcName;
    private String rightNpcName;
    private boolean dualNpcMode = false;
    private Runnable runnable;

    private Button skipButton;
    private LivingEntity npcEntity;
    public String nextString;
    private boolean hasSound;
    private int soundCount;
    private final String tag;

    public VNDialogueScreen(String npcName, List<String> lines, LivingEntity npcEntity, String tag, Runnable runnable) {
        this(npcName, lines, npcEntity, tag);
        this.runnable = runnable;
    }

    public VNDialogueScreen(String npcName, List<String> lines, LivingEntity npcEntity, String tag, int maxMessageCount, Runnable runnable) {
        this(npcName, lines, npcEntity, tag);
        this.runnable = runnable;
        this.maxMessageCount = maxMessageCount;
    }

    public VNDialogueScreen(String npcName, List<String> lines, LivingEntity npcEntity,  String tag) {
        super(Component.literal(""));
        this.npcName = npcName;
        this.fullLines = lines;
        this.npcEntity = npcEntity;
        AtomicBoolean hasNext = new AtomicBoolean(false);
        if (lines.size() > 1) hasNext.set(true);
        lines.forEach(string -> {
            String cleaned = string.replace("\\n", "\n");
            if (cleaned.contains("\n\n")) {
                hasNext.set(true);
            }
        });
        this.nextString = hasNext.get() ? "下一步" : "关闭";
        this.tag = tag;
        this.minecraft = Minecraft.getInstance();
        this.font = minecraft.font;
        this.init();
    }

    public VNDialogueScreen(String leftNpcName, String rightNpcName, List<String> lines, LivingEntity leftNpcEntity, LivingEntity rightNpcEntity,
                            String tag, Runnable runnable) {
        this(leftNpcName, rightNpcName, lines, leftNpcEntity, rightNpcEntity, tag);
        this.runnable = runnable;
    }

    public VNDialogueScreen(String leftNpcName, String rightNpcName, List<String> lines, LivingEntity leftNpcEntity, LivingEntity rightNpcEntity,
                            String tag) {
        super(Component.literal(""));
        this.leftNpcEntity = leftNpcEntity;
        this.rightNpcEntity = rightNpcEntity;
        this.leftNpcName = leftNpcName;
        this.rightNpcName = rightNpcName;
        this.fullLines = lines;
        this.dualNpcMode = true;
        this.minecraft = Minecraft.getInstance();
        this.font = minecraft.font;
        AtomicBoolean hasNext = new AtomicBoolean(false);
        if (lines.size() > 1) hasNext.set(true);
        lines.forEach(string -> {
            String cleaned = string.replace("\\n", "\n");
            if (cleaned.contains("\n\n")) {
                hasNext.set(true);
            }
        });
        this.nextString = hasNext.get() ? "下一步" : "关闭";
        this.tag = tag;
        this.init();
    }

    public VNDialogueScreen(String npcName, List<String> lines, LivingEntity npcEntity, String soundName, int count, int offset,
                            String tag) {
        this(npcName, lines, npcEntity, tag);
        this.soundBaseName = soundName;
        this.soundCount = count;
        this.currentSoundIndex += offset;
        this.hasSound = true;
    }

    @Override
    public void init() {
        super.init();
        skipButton = new Button(width - 80, height - 40, 70, 20, Component.literal(this.nextString), btn -> {
            if (currentCharIndex < fullLines.get(currentLineIndex).length()) {
                currentCharIndex = fullLines.get(currentLineIndex).length();
                currentLine = fullLines.get(currentLineIndex);
            } else {
                currentLineIndex++;
                if (this.runnable != null && maxMessageCount == 0)
                    this.doRunnable();
                if (hasSound && currentSoundIndex < soundCount) {
                    if (minecraft != null && minecraft.level != null && minecraft.level.isClientSide) {
                        SoundUtil.playSoundWithClient(minecraft.level, soundBaseName + currentSoundIndex, minecraft.player);
                    }
                    currentSoundIndex += 1;
                }

                if (currentLineIndex + 1 >= fullLines.size()) {
                    this.nextString = "关闭";
                    this.skipButton.setMessage(Component.literal(this.nextString));
                }
                if (currentLineIndex >= fullLines.size()) {
                    onClose();
                } else {
                    currentCharIndex = 0;
                    currentLine = "";
                }
            }
        }, Button.DEFAULT_NARRATION);
        addRenderableWidget(skipButton);
    }

    public int getMaxMessageCount() {
        return maxMessageCount;
    }

    public String getTag() {
        return this.tag;
    }

    public boolean isTag(String tag) {
        return this.tag.equals(tag);
    }

    @Override
    public void onClose() {
        if (this.nextString.equals("下一步"))
            return;
        super.onClose();
    }

    private void doRunnable() {
        if (this.runnable != null) this.runnable.run();
    }

    public int getMessageCount() {
        return messageCount;
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (currentLineIndex < fullLines.size()) {
            String targetLine = fullLines.get(currentLineIndex);
            if (currentCharIndex < targetLine.length()) {
                currentCharIndex++;
                currentLine = targetLine.substring(0, currentCharIndex);
            }
        }
        if (currentLine.contains("\n")) {
            currentLine = currentLine.replace("\n", " ");
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics);
        if (this.width != graphics.guiWidth() || this.height != graphics.guiHeight()) {
            this.width = graphics.guiWidth();
            this.height = graphics.guiHeight();
            this.init();
        }
        float phase = (float) Math.sin(tickCounter / 20.0f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(tickCounter / 10.0f);
        int baseBlue1 = 0xFF4DA6FF;
        int baseBlue2 = 0xFF0066CC;
        float blendFactor = 0.5f + 0.5f * phase;
        int dynamicBlue = blendColors(baseBlue1, baseBlue2, blendFactor);
        int alpha2 = 0x88 + (int)(0x22 * pulse);
        int finalColor = (alpha2 << 24) | (dynamicBlue & 0xFFFFFF);

            // 使用原来的实体渲染
            if (dualNpcMode) {
                boolean leftTalking = currentLineIndex < fullLines.size() && fullLines.get(currentLineIndex).startsWith(leftNpcName + "：");
                boolean rightTalking = currentLineIndex < fullLines.size() && fullLines.get(currentLineIndex).startsWith(rightNpcName + "：");

                if (leftNpcEntity != null) {
                    int scale = 80;

                    renderEntityOnScreen(graphics, width / 4, height / 2, scale, leftNpcEntity);
                }

                if (rightNpcEntity != null) {
                    int scale = 80;

                    renderEntityOnScreen(graphics, width * 3 / 4, height / 2, scale, rightNpcEntity);
                }
                String currentSpeaker = leftTalking ? leftNpcName : rightTalking ? rightNpcName : npcName;
                graphics.drawString(font, currentSpeaker, 20, height / 2 + 20, 0xFFFFFF);
            } else {

                    renderEntityOnScreen(graphics, width / 2, height / 2, 80, npcEntity);

                graphics.drawString(font, npcName, 20, height / 2 + 20, 0xFFFFFF);

        }

        graphics.fillGradient(0, height / 2, width, height,
                finalColor,
                finalColor & 0x80FFFFFF);

        List<String> lines = wrapText(currentLine, width - 40);
        int yPosition = height / 2 + 50;
        for (String line : lines) {
            graphics.drawString(font, line, width / 2 - font.width(line) / 2, yPosition, 0xFFFFFF);
            yPosition += font.lineHeight;
        }

        float alpha = 0.5f + 0.5f * (float) Math.sin(tickCounter / 10.0f);
        int borderColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        graphics.drawCenteredString(font, skipButton.getMessage(),
                skipButton.getX() + skipButton.getWidth() / 2,
                skipButton.getY() + 6,
                borderColor
        );
        int x1 = skipButton.getX();
        int y1 = skipButton.getY();
        int x2 = x1 + skipButton.getWidth();
        int y2 = y1 + skipButton.getHeight();
        int thickness = 1;
        graphics.fill(x1, y1, x2, y1 + thickness, borderColor);
        graphics.fill(x1, y2 - thickness, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + thickness, y2, borderColor);
        graphics.fill(x2 - thickness, y1, x2, y2, borderColor);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> wrappedLines = new ArrayList<>();
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            StringBuilder currentLine = new StringBuilder();
            int currentWidth = 0;
            for (int i = 0; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                int charWidth = font.width(String.valueOf(c));
                if (currentWidth + charWidth > maxWidth && !currentLine.isEmpty()) {
                    wrappedLines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    currentWidth = 0;
                }
                currentLine.append(c);
                currentWidth += charWidth;
            }
            if (!currentLine.isEmpty()) {
                wrappedLines.add(currentLine.toString());
            }
        }
        return wrappedLines;
    }

    private int blendColors(int color1, int color2, float ratio) {
        ratio = Math.min(1, Math.max(0, ratio));
        int r = (int)((color1 >> 16 & 0xFF) * (1-ratio) + (color2 >> 16 & 0xFF) * ratio);
        int g = (int)((color1 >> 8 & 0xFF) * (1-ratio) + (color2 >> 8 & 0xFF) * ratio);
        int b = (int)((color1 & 0xFF) * (1-ratio) + (color2 & 0xFF) * ratio);
        return 0xFF << 24 | r << 16 | g << 8 | b;
    }

    public void renderEntityOnScreen(GuiGraphics graphics, int x, int y, int scale, LivingEntity entity) {
        if (entity == null) return;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y, scale, 0, 0, entity);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}