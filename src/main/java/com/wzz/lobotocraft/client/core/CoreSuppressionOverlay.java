package com.wzz.lobotocraft.client.core;

import com.mojang.math.Axis;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class CoreSuppressionOverlay {
    private static final Map<CoreSuppressionType, List<String>> QUOTES = Map.of(
            CoreSuppressionType.MALKUTH, List.of(
                    "这就是你所谓的‘无法控制的局面’吗，我相信你可以的，加油。",
                    "一切都被搞砸了……我们一起把它收拾干净吧！",
                    "接下来会发生什么，你永远预测不到，但是我们会一起面对。",
                    "我们已经做得足够好了。",
                    "我帮不了你，但是我会为你加油的。",
                    "如此的绝望，如此的无力……我们一定能解决好的。",
                    "我相信你，能够引导好事态的发展！",
                    "我们还有不少事情要做，我将它们都记在了记事本上。",
                    "你能听到它与死亡抗争的声音吗？",
                    "仔细想想，主管的工作也没有那么难做，所以坚持下去啊。"),
            CoreSuppressionType.YESOD, List.of(
                    "我相信你能看到得比我更远。",
                    "试着看透这黑暗吧。",
                    "即使什么也看不见，我们也要不断前进。",
                    "站起来，还没到绝望的时候。",
                    "不要让他们的牺牲变得毫无意义。",
                    "这些怪物，是你需要去面对的难关。",
                    "记住那些人用命换来的东西。",
                    "加油，我相信你能记住它们的信息。"),
            CoreSuppressionType.HOD, List.of(
                    "好啦，我们来拍一部新的培训视频吧？",
                    "各位请好好干！所有的新员工都要参考这个培训视频哦。",
                    "大家都看向这边，3，2，1，茄子！",
                    "我想尽我所能地帮助大家。",
                    "我希望大家都需要我帮忙。",
                    "我相信所有人都会理解我的好意。",
                    "即使不被理解，我也会坚持下去。",
                    "一起努力，让更多的人活下去吧。"),
            CoreSuppressionType.NETZACH, List.of(
                    "这种糟糕的人生有什么意义，不如躺下喝点酒。",
                    "我为你准备了威士忌和鸡尾酒，你说你更喜欢果汁？",
                    "其实有时候喝一喝凉白开也是不错的选择。",
                    "喝完睡大觉，上工继续摸鱼，这才叫生活。",
                    "别忘了自己也有选择喝什么酒的权力。",
                    "尽自己全力去做吧，做不到就算了，至少我还陪着你。",
                    "内耗只会让自己难受，生命有限，要学会享受乐趣。",
                    "我早就不再指望什么命运了，现在只想再来一杯冰镇啤酒。",
                    "然后再闭上眼睛好好睡一觉，哪怕只有一次也好。"));

    private CoreSuppressionOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!CoreSuppressionClientState.isActive() || minecraft.player == null
                || minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.screen != null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        renderObjective(graphics, type);
        renderFloatingQuote(graphics, type, minecraft);
    }

    private static void renderObjective(GuiGraphics graphics, CoreSuppressionType type) {
        Minecraft minecraft = Minecraft.getInstance();
        int y = graphics.guiHeight() - 50;
        String title = type.getDisplayName() + " 核心抑制";
        String ordeal = "黎明 " + Math.min(3, CoreSuppressionClientState.getDawnCompleted())
                + "/3  正午 " + Math.min(2, CoreSuppressionClientState.getMiddayCompleted()) + "/2";
        String work = "接取者 " + CoreSuppressionClientState.getOwnerName() + "：工作 "
                + CoreSuppressionClientState.getWorkCompleted() + "/"
                + CoreSuppressionClientState.getWorkRequired();
        int width = Math.max(minecraft.font.width(title), Math.max(minecraft.font.width(ordeal), minecraft.font.width(work))) + 12;
        graphics.fill(7, y - 5, 7 + width, y + 31, 0xB0000000);
        graphics.fill(7, y - 5, 9, y + 31, 0xFF000000 | type.getColor());
        graphics.drawString(minecraft.font, title, 13, y, 0xFF000000 | type.getColor(), false);
        graphics.drawString(minecraft.font, ordeal, 13, y + 10, 0xFFFFFF, false);
        graphics.drawString(minecraft.font, work, 13, y + 20, 0xD0D0D0, false);
        if (CoreSuppressionClientState.requirementsMet()) {
            graphics.drawString(minecraft.font, "目标完成：接取者可以休息了。", 13, y - 15, 0x7CFF8C, true);
        }
    }

    private static void renderFloatingQuote(GuiGraphics graphics, CoreSuppressionType type, Minecraft minecraft) {
        List<String> quotes = QUOTES.get(type);
        if (quotes == null || quotes.isEmpty()) return;

        long gameTime = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        long period = gameTime / 120L;
        long seed = period * 31L + type.ordinal() * 997L + minecraft.player.getUUID().getLeastSignificantBits();
        RandomSource random = RandomSource.create(seed);
        String quote = quotes.get(random.nextInt(quotes.size()));
        int maxX = Math.max(1, graphics.guiWidth() - minecraft.font.width(quote) - 20);
        int x = 10 + random.nextInt(maxX);
        int y = 20 + random.nextInt(Math.max(1, graphics.guiHeight() / 2 - 30));
        float angle = switch (random.nextInt(6)) {
            case 0 -> 90.0F;
            case 1 -> -18.0F;
            case 2 -> 18.0F;
            default -> 0.0F;
        };
        int phase = (int) (gameTime % 120L);
        float alpha = phase < 20 ? phase / 20.0F : phase > 95 ? (120 - phase) / 25.0F : 1.0F;
        int color = (Mth.clamp((int) (alpha * 190), 0, 190) << 24) | (type.getColor() & 0xFFFFFF);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.drawString(minecraft.font, quote, 0, 0, color, true);
        graphics.pose().popPose();
    }
}
