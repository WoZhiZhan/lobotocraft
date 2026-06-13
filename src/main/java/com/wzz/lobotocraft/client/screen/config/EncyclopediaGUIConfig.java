package com.wzz.lobotocraft.client.screen.config;

/**
 * 异想体图鉴GUI配置类
 * 用于解决硬编码问题，集中管理所有GUI相关的常量
 */
public class EncyclopediaGUIConfig {

    // ==================== 颜色配置 ====================
    public static class Colors {
        // 主题色
        public static final int THEME_ORANGE = 0xFFFFAA00;
        public static final int THEME_YELLOW = 0xFFFFFF00;

        // 背景色
        public static final int BACKGROUND_BLACK = 0xFF000000;
        public static final int BACKGROUND_DARK = 0xFF1A1A1A;
        public static final int BACKGROUND_SEMI = 0xCC000000;
        public static final int BACKGROUND_OVERLAY = 0x80000000;

        // 文本色
        public static final int TEXT_WHITE = 0xFFFFFFFF;
        public static final int TEXT_GRAY = 0xFFAAAAAA;
        public static final int TEXT_DARK_GRAY = 0xFF444444;
        public static final int TEXT_HIGHLIGHT = 0xFFFFCC66;
        public static final int TEXT_WARNING = 0xFFFF6666;

        // 状态色
        public static final int STATUS_GREEN = 0xFF00FF00;
        public static final int STATUS_RED = 0xFFFF0000;
        public static final int STATUS_YELLOW = 0xFFFFFF00;
        public static final int STATUS_ORANGE = 0xFFFFA500;

        // 滚动条
        public static final int SCROLLBAR_BACKGROUND = 0x40FFFFFF;
        public static final int SCROLLBAR_HANDLE = 0xFFFFAA00;

        // 伤害类型颜色
        public static int getDamageTypeColor(String damageType) {
            return switch (damageType) {
                case "WHITE" -> 0xFFFFFFFF;
                case "RED" -> 0xFFFF0000;
                case "BLACK" -> 0xFFAA00AA;
                case "PALE" -> 0xFF00FFFF;
                default -> 0xFFCCCCCC;
            };
        }

        // 工作效率颜色
        public static int getWorkRateColor(float rate) {
            if (rate >= 0.7f) return 0xFF00FF00;     // 高效率 - 绿色
            if (rate >= 0.5f) return 0xFFFFFF00;     // 中效率 - 黄色
            if (rate >= 0.3f) return 0xFFFFA500;     // 低效率 - 橙色
            return 0xFFFF0000;                       // 极低 - 红色
        }
    }

    // ==================== 布局配置 ====================
    public static class Layout {
        // 顶部栏
        public static final int TOP_BAR_HEIGHT = 35;
        public static final int TOP_BAR_TITLE_X = 20;
        public static final int TOP_BAR_TITLE_Y = 12;
        public static final int TOP_BAR_RISK_OFFSET_X = 80;

        // 底部栏
        public static final int BOTTOM_BAR_HEIGHT = 25;
        public static final int BOTTOM_BAR_TEXT_X = 20;
        public static final int BOTTOM_BAR_TEXT_Y = 8;

        // 滚动设置
        public static final int CONTENT_HEIGHT = 1200;
        public static final int SCROLL_SPEED = 30;
        public static final int SCROLLBAR_WIDTH = 6;
        public static final int SCROLLBAR_OFFSET_X = 8;
        public static final int SCROLLBAR_MIN_HEIGHT = 20;

        // 左侧列
        public static class LeftColumn {
            public static final int X = 20;
            public static final int BONUS_LINE_SPACING = 20;
            public static final int OBSERVATION_LEVEL_Y_OFFSET = 100;
            public static final float OBSERVATION_LEVEL_SCALE = 1.5f;
            public static final int OBSERVATION_LEVEL_X_OFFSET = 70;
        }

        // 中间列（异想体信息）
        public static class CenterColumn {
            public static final int X = 200;
            public static final int TITLE_SPACING = 15;

            // 异想体图片
            public static final int IMAGE_SIZE = 180;
            public static final int IMAGE_BORDER_WIDTH = 2;

            // 基础信息区域
            public static final int BASIC_INFO_Y_OFFSET = 200;
            public static final int INFO_LINE_SPACING = 15;
            public static final int INFO_ICON_SIZE = 16;

            // 工作偏好区域
            public static final int WORK_PREF_Y_OFFSET = 320;
            public static final int WORK_PREF_ICON_SIZE = 20;
            public static final int WORK_PREF_BAR_WIDTH = 100;
            public static final int WORK_PREF_BAR_HEIGHT = 8;

            public static final int PE_RANGES_Y_OFFSET = 20;
        }

        // 右侧列（图鉴条目）
        public static class RightColumn {
            public static final int OFFSET_FROM_RIGHT = 380;
            public static final int START_Y_OFFSET = 20;

            // 条目框
            public static final int ENTRY_WIDTH = 360;
            public static final int ENTRY_HEIGHT = 60;
            public static final int ENTRY_SPACING = 15;

            // 文本位置
            public static final int TEXT_PADDING_X = 8;
            public static final int TEXT_PADDING_Y = 8;
            public static final int UNLOCK_TEXT_OFFSET_Y = 25;
            public static final int CLICK_HINT_OFFSET_Y = 40;
            public static final int REQUIREMENT_OFFSET_Y = 25;
        }
    }

    // ==================== 解锁配置 ====================
    public static class UnlockCosts {
        public static final int SENSITIVE_INFO_LEVEL = 1;   // 敏感信息需要等级1
    }

    // ==================== 文本配置 ====================
    public static class Text {
        public static final String TITLE_BASIC_INFO = "异想体的基础信息";
        public static final String TITLE_WORK_PREF = "工作偏好";
        public static final String TITLE_PE_OUTPUT = "PE-BOX 产量";
        public static final String TITLE_OBSERVATION_LEVEL = "观察等级";

        public static final String LABEL_RISK_LEVEL = "风险等级";
        public static final String LABEL_DAMAGE_TYPE = "伤害类型";
        public static final String LABEL_MAX_OUTPUT = "最大产量";

        public static final String WORK_TYPE_INSTINCT = "本能";
        public static final String WORK_TYPE_INSIGHT = "洞察";
        public static final String WORK_TYPE_ATTACHMENT = "沟通";
        public static final String WORK_TYPE_REPRESSION = "压迫";

        public static final String PE_RANGE_GOOD = "优";
        public static final String PE_RANGE_NORMAL = "良";
        public static final String PE_RANGE_BAD = "差";

        public static final String UNLOCK_HINT = ">>> 点击解锁 <<<";
        public static final String REQUIRE_LEVEL = "需要观察等级 %d";
        public static final String UNLOCK_COST = "消耗 %d PE-BOX 解锁";
        public static final String REQUIRE_PEBOX = "需要 %d 个 %s 的PE-BOX解锁";  // %s = 异想体名称

        public static final String AVAILABLE_PEBOX = "可用 PE-BOX: %d";
        public static final String CONTROLS_HINT = "[ ESC 关闭 | 滚轮滚动 ]";

        public static final String PLACEHOLDER_IMAGE = "[异想体图片]";
        public static final String LOCKED_CONTENT = "■■■■■";
    }

    // ==================== 动画配置 ====================
    public static class Animation {
        public static final double SCROLL_SMOOTHNESS = 0.3;
        public static final int DASH_LENGTH = 8;
    }
}