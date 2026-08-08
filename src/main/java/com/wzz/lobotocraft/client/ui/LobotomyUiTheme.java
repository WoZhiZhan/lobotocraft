package com.wzz.lobotocraft.client.ui;

import com.wzz.lobotocraft.employee.EmployeeDepartment;

/**
 * 脑叶公司统一视觉主题 —— 纯常量类。
 * 颜色数值与 DESIGN_PLAN.md 1.1 完全一致；间距常量见 1.6/1.7。
 * 所有 GUI/HUD 前端代码只允许引用本类常量，不得复制字面量。
 */
public final class LobotomyUiTheme {

    // ===== 背景 =====
    public static final int BG_SCRIM        = 0xB0000000; // 全屏遮罩（Screen 背后）
    public static final int BG_PANEL        = 0xF20A0A0F; // 面板主底（95% 不透明，深近黑蓝）
    public static final int BG_PANEL_SOLID  = 0xFF0E0E14; // 不允许透底的面板（HUD 面板降级底）
    public static final int BG_INSET        = 0xFF14141C; // 内嵌区域/列表容器/输入框底（比面板亮一档）
    public static final int BG_ROW_EVEN     = 0xFF101017; // 列表偶数行
    public static final int BG_ROW_ODD      = 0xFF0C0C12; // 列表奇数行
    public static final int BG_ROW_HOVER    = 0x229ADFE0; // 行悬停叠加（青白 13% alpha）
    public static final int BG_ROW_SELECTED = 0x339ADFE0; // 行选中叠加

    // ===== 线条 =====
    public static final int LINE_BRIGHT     = 0xFFE8E8E4; // 白描边（面板外框、强调分隔）
    public static final int LINE_MID        = 0xFF585862; // 灰描边（次级框、按钮默认边）
    public static final int LINE_FAINT      = 0xFF26262E; // 行分隔线、暗分隔
    public static final int LINE_ACCENT     = 0xFF66BFCD; // 灰青装饰线（切角、选中框）

    // ===== 强调色 =====
    // 原游戏默认高亮色：源码 SefiraConversationController 等处以 <color=#66bfcd> 硬编码
    // 高亮员工名/异想体名，是脑叶「无部门归属」时的基准强调色。
    public static final int ACCENT          = 0xFF66BFCD; // 默认高亮（交互焦点、选中、数据高亮）
    public static final int ACCENT_DIM      = 0xFF3E7A85; // 默认高亮降饱和（次级高亮、图标描边）
    public static final int GOLD            = 0xFFE6C56C; // 脑叶金（仅限：DAY 计数、能源/PE 数值、考验等级罗马数字）
    public static final int ENERGY_GREEN    = 0xFF1FFFAE; // 能源绿（能源条填充，与 energy_frame 原色一致）
    public static final int ENERGY_GREEN_HI = 0xFF7DFFD5; // 能源绿顶部 1px 提亮
    public static final int ALERT_RED       = 0xFFCC3333; // 警戒红（错误、警报标题、不足）
    public static final int ALERT_RED_DIM   = 0xFF7A2222; // 警戒红暗（警报底、故障条纹）
    public static final int WARN_YELLOW     = 0xFFF3E482; // 熔毁计量/倒计时黄（沿用现有 HUD 值）

    // ===== 文字 =====
    public static final int TEXT_TITLE      = 0xFFF2F2E6; // 标题/主要数据
    public static final int TEXT_BODY       = 0xFFC8CCC8; // 正文
    public static final int TEXT_MUTED      = 0xFF8A9095; // 次要说明
    public static final int TEXT_DISABLED   = 0xFF565C60; // 禁用
    public static final int TEXT_ON_ACCENT  = 0xFF06090A; // 青白底上的反色文字

    // ===== 状态条 =====
    public static final int BAR_TRACK       = 0xFF1C1C24; // 条底
    public static final int BAR_HP          = 0xFFD14B4B; // HP 红（脑叶：生命=红）
    public static final int BAR_SP          = 0xFFF0F0E8; // SP 白（脑叶：精神=白）
    public static final int BAR_SP_LOW      = 0xFFCC3333; // SP < 25% 时转警戒红（濒临恐慌）

    // ===== 四维属性（沿用本模组既有 红/蓝/绿/金 惯例） =====
    public static final int STAT_FORTITUDE  = 0xFFC94F4F; // 勇气
    public static final int STAT_PRUDENCE   = 0xFF6C9CE0; // 谨慎
    public static final int STAT_TEMPERANCE = 0xFF6CC98A; // 自律
    public static final int STAT_JUSTICE    = 0xFFE0C063; // 正义

    // ===== 部门色（Sephirah） =====
    // 脑叶的 UI 底图（SefiraPanel_Boss_Norm、SefiraStateBar 等）本身是无色白图，
    // 运行时经 UIColorManager.GetSefiraColor() 按当前部门染色；每个部门的专属美术
    // 则烘死了自己的色。以下数值取自原游戏部门贴图采样（*_Frame.png / 徽章），
    // 按深底可读性做了亮度归一。无部门归属处一律回落 ACCENT(#66BFCD)。
    public static final int DEPT_CONTROL      = 0xFFD7AF3E; // Malkuth   控制部   金
    public static final int DEPT_INFORMATION  = 0xFF8E5FC6; // Yesod     情报部   紫
    public static final int DEPT_TRAINING     = 0xFFD67500; // Hod       培训部   橙
    public static final int DEPT_SAFETY       = 0xFF21C784; // Netzach   安保部   绿
    public static final int DEPT_CENTRAL      = 0xFFE27F02; // Tiphereth 中央本部 琥珀
    public static final int DEPT_WELFARE      = 0xFF4C63B0; // Chesed    福利部   蓝
    public static final int DEPT_DISCIPLINARY = 0xFFE33B22; // Gebura    惩戒部   红
    public static final int DEPT_RECORD       = 0xFFC8CCD0; // Hokma     记录部   白灰
    public static final int DEPT_EXTRACTION   = 0xFFC99A55; // Binah     研发部   黄铜
    public static final int DEPT_ARCHITECTURE = 0xFFE8E8E4; // Keter     构筑部   白

    /**
     * 取部门强调色；未分配/未知部门回落 {@link #ACCENT}（即原游戏默认 #66BFCD）。
     *
     * @param departmentId {@code EmployeeDepartment.id()}
     */
    public static int departmentColor(String departmentId) {
        return 0xFF000000 | EmployeeDepartment.byId(departmentId).textColorRgb();
    }

    // ===== 间距 / 尺寸（DESIGN_PLAN 1.6 / 1.7） =====
    public static final int PAD          = 16; // 面板内边距
    public static final int HEADER_H     = 40; // 标准面板头高
    public static final int BTN_H        = 20; // 标准按钮高
    public static final int ROW_EMPLOYEE = 36; // 员工紧凑行高
    public static final int ROW_EGO      = 58; // E.G.O 行高
    public static final int CORNER_LEN   = 8;  // 角括号装饰线长度

    private LobotomyUiTheme() {
    }
}
