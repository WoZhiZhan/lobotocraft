package com.lobotocraft.facility.client;

import com.lobotocraft.facility.FacilityPatch;
import com.lobotocraft.facility.network.PlayerStatusPacket;
import com.lobotocraft.facility.player.PlayerStatusEvents;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FacilityPatch.MOD_ID, value = Dist.CLIENT)
public final class PlayerStatusClientState {
    private static final Map<UUID, PlayerStatusPacket.Entry> ENTRIES = new HashMap<>();
    /** TAB 每帧查询：缓存已构建的 Component，收到新状态包时整体失效。 */
    private static final Map<UUID, Component> DISPLAY_CACHE = new HashMap<>();

    // ===== 主题色 Style（LobotomyUiTheme，替换原版 ChatFormatting 16 色） =====
    private static final Style STYLE_WORKING = themed(LobotomyUiTheme.GOLD);
    private static final Style STYLE_NAVIGATING = themed(LobotomyUiTheme.WARN_YELLOW);
    private static final Style STYLE_COMBAT = themed(LobotomyUiTheme.ALERT_RED);
    private static final Style STYLE_IDLE = themed(LobotomyUiTheme.TEXT_MUTED);
    /** 恐慌紫，与设施管理界面员工行的 PANIC 状态色保持一致。 */
    private static final Style STYLE_PANIC = themed(0xFFB05CD6);
    private static final Style STYLE_CONTROLLED = themed(0xFFE05A2B);
    private static final Style STYLE_HP_LABEL = themed(LobotomyUiTheme.ALERT_RED_DIM);
    private static final Style STYLE_HP_VALUE = themed(LobotomyUiTheme.BAR_HP);
    private static final Style STYLE_SP_LABEL = themed(LobotomyUiTheme.TEXT_MUTED);
    private static final Style STYLE_SP_VALUE = themed(LobotomyUiTheme.BAR_SP);
    private static final Style STYLE_SP_LOW = themed(LobotomyUiTheme.BAR_SP_LOW);

    private PlayerStatusClientState() {
    }

    public static void update(List<PlayerStatusPacket.Entry> entries) {
        ENTRIES.clear();
        DISPLAY_CACHE.clear();
        for (PlayerStatusPacket.Entry entry : entries) ENTRIES.put(entry.playerId(), entry);
    }

    public static Component display(UUID playerId) {
        PlayerStatusPacket.Entry entry = ENTRIES.get(playerId);
        if (entry == null) return Component.empty();
        Component cached = DISPLAY_CACHE.get(playerId);
        if (cached != null) return cached;
        Component built = build(entry);
        DISPLAY_CACHE.put(playerId, built);
        return built;
    }

    private static Component build(PlayerStatusPacket.Entry entry) {
        MutableComponent result = Component.literal("  ");
        if (entry.state() == PlayerStatusEvents.CONTROLLED) {
            result.append(Component.translatable("status.lobotocraft.controlled").withStyle(STYLE_CONTROLLED));
        } else if (entry.state() == PlayerStatusEvents.PANIC) {
            result.append(Component.translatable("status.lobotocraft.panic").withStyle(STYLE_PANIC));
        } else if (entry.state() == PlayerStatusEvents.WORKING) {
            result.append(Component.translatable("status.lobotocraft.working").withStyle(STYLE_WORKING));
        } else if (entry.state() == PlayerStatusEvents.NAVIGATING) {
            result.append(Component.translatable("status.lobotocraft.navigating", entry.workTarget())
                    .withStyle(STYLE_NAVIGATING));
        } else if (entry.state() == PlayerStatusEvents.COMBAT) {
            result.append(Component.translatable("status.lobotocraft.combat").withStyle(STYLE_COMBAT));
        } else {
            result.append(Component.translatable("status.lobotocraft.idle").withStyle(STYLE_IDLE));
        }
        result.append(Component.literal("  HP ").withStyle(STYLE_HP_LABEL));
        result.append(Component.literal(number(entry.health()) + "/" + number(entry.maxHealth()))
            .withStyle(STYLE_HP_VALUE));
        boolean spLow = entry.maxMental() > 0.0F && entry.mental() < entry.maxMental() * 0.25F;
        result.append(Component.literal("  SP ").withStyle(STYLE_SP_LABEL));
        result.append(Component.literal(number(entry.mental()) + "/" + number(entry.maxMental()))
            .withStyle(spLow ? STYLE_SP_LOW : STYLE_SP_VALUE));
        return result;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ENTRIES.clear();
        DISPLAY_CACHE.clear();
    }

    private static Style themed(int argb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }

    private static String number(float value) {
        int rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01F
            ? Integer.toString(rounded)
            : String.format(Locale.ROOT, "%.1f", value);
    }
}
