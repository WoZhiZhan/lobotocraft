package com.wzz.lobotocraft.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Random;

/**
 * 奇怪的工牌Tooltip处理器
 * 处理恐慌状态下的乱码→文字渐变效果
 */
@OnlyIn(Dist.CLIENT)
public class StrangeBadgeTooltipHandler {

    private static final String TARGET_TEXT_KEY = "tooltip.lobotocraft.strange_badge.panic";
    private static final Random RANDOM = new Random();

    // 乱码字符池
    private static final char[] GARBLED_CHARS = {
            '█', '▓', '▒', '░', '▀', '▄', '■', '□', '▪', '▫',
            '☰', '☱', '☲', '☳', '☴', '☵', '☶', '☷',
            '◢', '◣', '◤', '◥', '◆', '◇', '○', '●', '◎', '◉',
            '※', '＊', '＃', '＄', '％', '＆'
    };

    /**
     * 添加恐慌状态的Tooltip
     */
    public static void addPanicTooltip(List<Component> tooltip) {
        // 从客户端追踪器获取悬停进度
        float progress = StrangeBadgeClientTracker.getHoverProgress();

        String displayText = getDisplayText(progress);

        // 根据进度决定是否混淆
        boolean obfuscated = progress < 0.95f;

        tooltip.add(Component.literal(displayText)
                .withStyle(ChatFormatting.DARK_RED)
                .withStyle(style -> obfuscated
                        ? style.withObfuscated(true)
                        : style));
    }

    /**
     * 根据进度获取显示文本
     * @param progress 0.0-1.0
     */
    private static String getDisplayText(float progress) {
        String targetText = I18n.get(TARGET_TEXT_KEY);
        if (progress >= 1.0f) {
            // 完全显示
            return targetText;
        } else if (progress >= 0.8f) {
            // 80%-100%: 逐字显示
            int revealCount = (int) ((progress - 0.8f) / 0.2f * targetText.length());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < targetText.length(); i++) {
                if (i < revealCount) {
                    sb.append(targetText.charAt(i));
                } else {
                    sb.append(getGarbledChar());
                }
            }
            return sb.toString();
        } else {
            // 0%-80%: 完全乱码，但长度逐渐接近目标
            int length = Math.max(5, (int) (progress / 0.8f * targetText.length()));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(getGarbledChar());
            }
            return sb.toString();
        }
    }

    /**
     * 获取随机乱码字符
     */
    private static char getGarbledChar() {
        return GARBLED_CHARS[RANDOM.nextInt(GARBLED_CHARS.length)];
    }
}
