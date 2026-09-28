package com.lobotocraft.facility.client;

import com.lobotocraft.facility.FacilityPatch;
import com.lobotocraft.facility.data.OrdealTier;
import com.lobotocraft.facility.network.FacilityStatePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.client.key.ClientKeyHandler;
import com.wzz.lobotocraft.client.key.ModKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FacilityPatch.MOD_ID, value = Dist.CLIENT)
public final class FacilityHud {
    private static final ResourceLocation ENERGY_FRAME = texture("energy_frame.png");
    private static final ResourceLocation QLIPHOTH = texture("qliphoth_overload.png");
    private static final ResourceLocation REACTOR_ICON = texture("regeneration_reactor_icon.png");
    private static final ResourceLocation ORDEAL_SLOT = texture("ordeal_slot.png");
    private static final ResourceLocation ORDEAL_BASE = texture("ordeal_icon_base.png");
    private static final ResourceLocation DAWN = texture("dawn_a.png");
    private static final ResourceLocation NOON = texture("noon.png");
    private static final ResourceLocation DUSK = texture("dusk.png");
    private static final ResourceLocation MIDNIGHT = texture("midnight.png");
    private static final ResourceLocation WARNING = texture("warning.png");

    /** ALERT_RED 亮档：警报/故障标题文字（HUD 专用，主题表无此档，见 DESIGN_PLAN §2C）。 */
    private static final int ALERT_TEXT_BRIGHT = 0xFFFF9A9A;
    /** ALERT_RED 亮档：熔毁/反应堆倒计时文字。 */
    private static final int ALERT_TIMER_RED = 0xFFFF6464;
    /** 熔毁计量空槽色（qliphoth 贴图原生配色，非主题色）。 */
    private static final int MELTDOWN_SLOT_EMPTY = 0xFF30383B;
    /** 反应堆进度条底色（qliphoth 贴图内嵌槽的暗绿，非主题色）。 */
    private static final int REACTOR_TRACK = 0xFF101B18;

    private static boolean filtersConfigured;
    private static float reactorPanelY = 79.0F;

    // ===== 渲染热路径字符串缓存（避免每帧拼接分配） =====
    private static int dayCache = Integer.MIN_VALUE;
    private static Component dayText = Component.empty();
    private static int energyCache = Integer.MIN_VALUE;
    private static int quotaCache = Integer.MIN_VALUE;
    private static String energyText = "";
    private static int reactorSecondsCache = Integer.MIN_VALUE;
    private static String reactorCountdownText = "";
    private static int meltdownCountCache = Integer.MIN_VALUE;
    private static int meltdownSecondsCache = Integer.MIN_VALUE;
    private static String meltdownTimerText = "";
    private static int warningTypeCache = Integer.MIN_VALUE;
    private static int warningLevelCache = Integer.MIN_VALUE;
    private static Component warningSubtitleText = Component.empty();

    private FacilityHud() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientFacilityState.tick();
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
            || minecraft.player.level().dimension() != ModDimensions.LOBOTO_KEY) return;

        configureTextureFilters(minecraft);
        GuiGraphics graphics = event.getGuiGraphics();
        FacilityStatePacket state = ClientFacilityState.get();
        renderEnergy(graphics, minecraft, state);
        renderPauseState(graphics, minecraft, state);
        renderDayTransition(graphics, minecraft, state);
        renderMeltdown(graphics, minecraft, state);
        renderOrdeal(graphics, minecraft, state);
        renderReactor(graphics, minecraft, state);
        if (ClientFacilityState.getWarningTicks() > 0) renderWarning(graphics, minecraft, state);
    }

    private static void renderReactor(GuiGraphics graphics, Minecraft minecraft,
                                      FacilityStatePacket state) {
        float visibility = ClientReactorState.visibility();
        if (visibility <= 0.01F) return;

        int x = 8;
        float targetY = hasUpcomingOrdeal(state) ? 117.0F : 79.0F;
        reactorPanelY = Mth.lerp(0.16F, reactorPanelY, targetY);
        int y = Math.round(reactorPanelY - (1.0F - easeOut(visibility)) * 7.0F);
        int width = 253;
        float progress = ClientReactorState.progress();
        boolean failed = ClientReactorState.failed();
        int trackLeft = x + 53;
        int trackRight = x + 199;
        int trackTop = y + 14;
        int fillRight = trackLeft + Math.round((trackRight - trackLeft) * progress);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, visibility);
        LobotomyUiRenderer.blitScaled(graphics, QLIPHOTH, x, y, width, 22, 405, 35);
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (failed) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, visibility);
            LobotomyUiRenderer.drawBrokenBar(graphics, trackLeft, trackTop, trackRight - trackLeft, 3);
            graphics.flush();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            graphics.fill(trackLeft, trackTop, trackRight, trackTop + 3,
                LobotomyUiRenderer.withAlpha(REACTOR_TRACK, visibility));
            if (fillRight > trackLeft) {
                graphics.fill(trackLeft, trackTop, fillRight, trackTop + 3,
                    LobotomyUiRenderer.withAlpha(LobotomyUiTheme.ENERGY_GREEN, visibility));
                graphics.fill(trackLeft, trackTop, fillRight, trackTop + 1,
                    LobotomyUiRenderer.withAlpha(LobotomyUiTheme.ENERGY_GREEN_HI, visibility));
            }
        }

        if (visibility > 0.45F) {
            LobotomyUiRenderer.blitScaled(graphics, REACTOR_ICON, x + 18, y + 3, 16, 16, 16, 16);
        }

        Component status = Component.translatable(failed
                ? "hud.lobotocraft.facility.reactor.failed"
                : "hud.lobotocraft.facility.reactor.active");
        graphics.drawCenteredString(minecraft.font, status, x + 126, y + 4,
            LobotomyUiRenderer.withAlpha(failed ? ALERT_TEXT_BRIGHT : LobotomyUiTheme.TEXT_TITLE, visibility));
        int seconds = failed ? -1 : (int) Math.ceil((1.0F - progress) * 10.0F);
        if (seconds != reactorSecondsCache) {
            reactorSecondsCache = seconds;
            reactorCountdownText = seconds < 0 ? "--" : seconds + "s";
        }
        graphics.drawCenteredString(minecraft.font, reactorCountdownText, x + 226, y + 7,
            LobotomyUiRenderer.withAlpha(failed ? ALERT_TIMER_RED : LobotomyUiTheme.WARN_YELLOW, visibility));
        RenderSystem.disableBlend();
    }

    private static void renderEnergy(GuiGraphics graphics, Minecraft minecraft, FacilityStatePacket state) {
        int x = 8;
        int y = 8;
        int width = 260;
        int fillWidth = Math.round(219.0F * state.energy() / Math.max(1, state.quota()));
        RenderSystem.enableBlend();
        // Ratios come from the original GameStatusUI prefab: 864.8x74.1 fill in a 1028x169 frame.
        graphics.fill(x + 15, y + 16, x + 234, y + 35, 0xE6000000);
        if (fillWidth > 0) graphics.fill(x + 15, y + 16, x + 15 + fillWidth, y + 35, LobotomyUiTheme.ENERGY_GREEN);
        LobotomyUiRenderer.blitScaled(graphics, ENERGY_FRAME, x, y, width, 43, 521, 87);
        if (state.day() != dayCache) {
            dayCache = state.day();
            dayText = Component.translatable("hud.lobotocraft.facility.day", dayCache);
        }
        graphics.drawString(minecraft.font, dayText, x + 12, y + 4, LobotomyUiTheme.GOLD, true);
        if (state.energy() != energyCache || state.quota() != quotaCache) {
            energyCache = state.energy();
            quotaCache = state.quota();
            energyText = energyCache + " / " + quotaCache;
        }
        graphics.drawCenteredString(minecraft.font, energyText, x + 124, y + 21, 0xFFFFFFFF);
        RenderSystem.disableBlend();
    }

    private static void renderPauseState(GuiGraphics graphics, Minecraft minecraft,
                                         FacilityStatePacket state) {
        if (!state.paused()) return;
        Component label = Component.translatable("hud.lobotocraft.facility.paused");
        int x = 8;
        int y = hasUpcomingOrdeal(state) ? 117 : 79;
        int width = minecraft.font.width(label) + 12;
        graphics.fill(x, y, x + width, y + 16, 0xE7132434);
        graphics.fill(x, y, x + width, y + 1, 0xFF69BEEB);
        graphics.fill(x, y + 15, x + width, y + 16, 0xFF69BEEB);
        graphics.drawString(minecraft.font, label, x + 6, y + 4, 0xFFEAF8FF, false);
    }

    private static void renderDayTransition(GuiGraphics graphics, Minecraft minecraft,
                                            FacilityStatePacket state) {
        if (!state.dayReady() && !state.voteActive()) return;
        int width = state.voteActive() ? 236 : 190;
        int height = state.voteActive() ? 32 : 24;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 7;
        int border = 0xFF69BEEB;
        graphics.fill(x, y, x + width, y + height, 0xE7132434);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);

        if (!state.voteActive()) {
            Component label = Component.translatable("hud.lobotocraft.facility.end_day_hold",
                    ModKeyMappings.END_DAY.getTranslatedKeyMessage());
            graphics.drawCenteredString(minecraft.font, label, x + width / 2, y + 7, 0xFFEAF8FF);
            int progress = Math.round((width - 4) * ClientKeyHandler.endDayHoldProgress());
            if (progress > 0) graphics.fill(x + 2, y + height - 4, x + 2 + progress,
                    y + height - 2, 0xFF69D4FF);
            return;
        }

        int seconds = (state.voteRemainingTicks() + 19) / 20;
        Component title = Component.translatable("hud.lobotocraft.facility.end_day_vote", seconds);
        graphics.drawCenteredString(minecraft.font, title, x + width / 2, y + 5, 0xFFEAF8FF);
        Component detail = Component.translatable("hud.lobotocraft.facility.end_day_vote_detail",
                ModKeyMappings.END_DAY.getTranslatedKeyMessage(), state.voteApprovals(),
                ModKeyMappings.REJECT_DAY_VOTE.getTranslatedKeyMessage(), state.voteRejects(),
                state.voteRequired());
        int detailColor = state.ownVote() > 0 ? 0xFF84E7B0
                : state.ownVote() < 0 ? 0xFFFF8E8E : 0xFFBFD3DF;
        graphics.drawCenteredString(minecraft.font, detail, x + width / 2, y + 18, detailColor);
    }

    private static void renderMeltdown(GuiGraphics graphics, Minecraft minecraft, FacilityStatePacket state) {
        int x = 8;
        int y = 52;
        int width = 253;
        LobotomyUiRenderer.blitScaled(graphics, QLIPHOTH, x, y, width, 22, 405, 35);
        int slots = Math.max(1, state.threshold());
        int available = 146;
        int gap = 2;
        int slotWidth = Math.max(3, (available - gap * (slots - 1)) / slots);
        for (int i = 0; i < slots; i++) {
            int color = i < state.gauge() ? LobotomyUiTheme.WARN_YELLOW : MELTDOWN_SLOT_EMPTY;
            int sx = x + 53 + i * (slotWidth + gap);
            graphics.fill(sx, y + 8, Math.min(x + 199, sx + slotWidth), y + 15, color);
        }
        graphics.drawCenteredString(minecraft.font, roman(state.meltdownLevel()), x + 13, y + 7, 0xFFF0E5B5);
        if (state.activeMeltdowns() > 0) {
            int seconds = ClientFacilityState.meltdownSeconds(state.earliestRemainingTicks());
            if (state.activeMeltdowns() != meltdownCountCache || seconds != meltdownSecondsCache) {
                meltdownCountCache = state.activeMeltdowns();
                meltdownSecondsCache = seconds;
                meltdownTimerText = meltdownCountCache + "  " + formatTime(seconds);
            }
            graphics.drawString(minecraft.font, meltdownTimerText,
                x + width - minecraft.font.width(meltdownTimerText), y + 24, ALERT_TIMER_RED, true);
        }
    }

    private static void renderOrdeal(GuiGraphics graphics, Minecraft minecraft, FacilityStatePacket state) {
        OrdealTier tier = OrdealTier.byId(state.nextOrdeal());
        int scheduledStage = state.meltdownLevel();
        if (tier == OrdealTier.NONE) {
            for (int stage = Math.max(1, state.meltdownLevel() + 1); stage <= 9; stage++) {
                OrdealTier scheduled = state.scheduledTier(stage);
                if (scheduled == OrdealTier.NONE) continue;
                tier = scheduled;
                scheduledStage = stage;
                break;
            }
        }
        if (tier == OrdealTier.NONE) return;
        int x = 8;
        int y = 79;
        LobotomyUiRenderer.blitScaled(graphics, ORDEAL_SLOT, x, y, 164, 35, 328, 70);
        int scheduledColor = scheduledStage == state.meltdownLevel()
                ? state.nextOrdealColor() : state.scheduledColor(scheduledStage);
        int ordealColor = ordealColor(tier, scheduledColor);
        RenderSystem.setShaderColor(red(ordealColor), green(ordealColor), blue(ordealColor), 1.0F);
        LobotomyUiRenderer.blitScaled(graphics, ORDEAL_BASE, x + 4, y + 3, 28, 28, 56, 56);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        ResourceLocation icon = switch (tier) {
            case DAWN -> DAWN;
            case NOON -> NOON;
            case DUSK -> DUSK;
            case MIDNIGHT -> MIDNIGHT;
            default -> ORDEAL_BASE;
        };
        int sourceWidth = switch (tier) {
            case DAWN -> 56;
            case NOON -> 61;
            case DUSK -> 54;
            case MIDNIGHT -> 41;
            default -> 56;
        };
        int sourceHeight = switch (tier) {
            case DAWN -> 42;
            case NOON -> 63;
            case DUSK -> 53;
            case MIDNIGHT -> 35;
            default -> 56;
        };
        LobotomyUiRenderer.blitScaled(graphics, icon, x + 8, y + 7, 20, 20, sourceWidth, sourceHeight);
        Component name = switch (tier) {
            case DAWN -> Component.translatable("hud.lobotocraft.facility.ordeal.dawn");
            case NOON -> Component.translatable("hud.lobotocraft.facility.ordeal.noon");
            case DUSK -> Component.translatable("hud.lobotocraft.facility.ordeal.dusk");
            case MIDNIGHT -> Component.translatable("hud.lobotocraft.facility.ordeal.midnight");
            default -> Component.empty();
        };
        graphics.drawString(minecraft.font, name, x + 40, y + 8, 0xFFF0E5D0, true);
        Component timing = Component.translatable("hud.lobotocraft.facility.ordeal.timing",
                roman(scheduledStage));
        graphics.drawString(minecraft.font, timing, x + 40, y + 20,
                LobotomyUiTheme.TEXT_MUTED, true);
    }

    private static boolean hasUpcomingOrdeal(FacilityStatePacket state) {
        if (OrdealTier.byId(state.nextOrdeal()) != OrdealTier.NONE) return true;
        for (int stage = Math.max(1, state.meltdownLevel() + 1); stage <= 9; stage++) {
            if (state.scheduledTier(stage) != OrdealTier.NONE) return true;
        }
        return false;
    }

    private static void renderWarning(GuiGraphics graphics, Minecraft minecraft, FacilityStatePacket state) {
        // Meltdown is already identified by text and countdown in the upper-left
        // status area; do not add the generic centered warning artwork.
        if (!shouldRenderWarning(state.notificationType())) return;
        int screenWidth = graphics.guiWidth();
        int center = screenWidth / 2;
        int y = 28;
        int left = center - 180;
        int right = center + 180;
        graphics.fill(left, y, right, y + 64, LobotomyUiRenderer.withAlpha(LobotomyUiTheme.BG_PANEL_SOLID, 0.88F));
        graphics.fill(left, y, right, y + 1, LobotomyUiTheme.ALERT_RED);
        graphics.fill(left, y + 63, right, y + 64, LobotomyUiTheme.ALERT_RED);
        LobotomyUiRenderer.drawCorners(graphics, left, y, 360, 64, LobotomyUiTheme.CORNER_LEN, LobotomyUiTheme.ALERT_RED);
        LobotomyUiRenderer.blitScaled(graphics, WARNING, left + 4, y, 20, 64, 40, 127);
        LobotomyUiRenderer.blitScaled(graphics, WARNING, right - 24, y, 20, 64, 40, 127);
        Component title = Component.translatable(state.notificationType() == 1
                ? "hud.lobotocraft.facility.warning.ordeal"
                : state.notificationType() == 3
                ? "hud.lobotocraft.facility.warning.meltdown_failed"
                : "hud.lobotocraft.facility.warning.qliphoth");
        if (state.notificationType() != warningTypeCache || state.meltdownLevel() != warningLevelCache) {
            warningTypeCache = state.notificationType();
            warningLevelCache = state.meltdownLevel();
            warningSubtitleText = warningTypeCache == 3
                ? Component.translatable("hud.lobotocraft.facility.warning.energy_damaged")
                : Component.translatable("hud.lobotocraft.facility.warning.level",
                        roman(Math.max(1, warningLevelCache - 1)));
        }
        graphics.drawCenteredString(minecraft.font, title, center, y + 17, ALERT_TEXT_BRIGHT);
        graphics.drawCenteredString(minecraft.font, warningSubtitleText, center, y + 35, LobotomyUiTheme.TEXT_TITLE);
    }

    static boolean shouldRenderWarning(int notificationType) {
        // Type 3 is the post-failure notification; the failure is already visible
        // in the reactor status and must not create a second top-screen alert.
        return notificationType != 2 && notificationType != 3;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(FacilityPatch.ASSET_NAMESPACE, "textures/gui/" + name);
    }

    private static void configureTextureFilters(Minecraft minecraft) {
        if (filtersConfigured) return;
        ResourceLocation[] textures = {
            ENERGY_FRAME, QLIPHOTH, ORDEAL_SLOT, ORDEAL_BASE,
            DAWN, NOON, DUSK, WARNING
        };
        for (ResourceLocation texture : textures) {
            minecraft.getTextureManager().getTexture(texture).setFilter(false, false);
        }
        filtersConfigured = true;
    }

    private static String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; default -> "X";
        };
    }

    private static float red(int color) { return ((color >> 16) & 255) / 255.0F; }
    private static float green(int color) { return ((color >> 8) & 255) / 255.0F; }
    private static float blue(int color) { return (color & 255) / 255.0F; }

    private static float easeOut(float value) {
        float inverse = 1.0F - Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static int ordealColor(OrdealTier tier, int id) {
        return switch (tier) {
            case DAWN -> switch (id) {
                case 1 -> 0xFF3B3B;
                case 2 -> 0x6CFF4E;
                case 3 -> 0xC77BFF;
                default -> 0xFFB347;
            };
            case NOON -> switch (id) {
                case 1 -> 0x5AB4FF;
                case 2 -> 0xC77BFF;
                case 3 -> 0x6CFF4E;
                default -> 0xFF3B3B;
            };
            case DUSK, MIDNIGHT -> switch (id) {
                case 1 -> 0xFF3B3B;
                case 2 -> 0x6CFF4E;
                case 3 -> 0xC77BFF;
                default -> 0xFFB347;
            };
            default -> 0xFFFFFF;
        };
    }
}
