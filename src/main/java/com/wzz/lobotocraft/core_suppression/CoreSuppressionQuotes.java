package com.wzz.lobotocraft.core_suppression;

import com.wzz.lobotocraft.entity.EntityText;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

public final class CoreSuppressionQuotes {
    private static final int SPAWN_INTERVAL = 120; // 每 120 tick 生成一句
    private static final int LIFE_TICKS = 100;     // 每句存活时长

    public static final Map<CoreSuppressionType, List<String>> QUOTES = Map.of(
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

    private CoreSuppressionQuotes() {}

    /** 每 tick 调用一次，内部自己按间隔生成 */
    public static void tick(ServerLevel level, Player owner, CoreSuppressionType type) {
        if (level.getGameTime() % SPAWN_INTERVAL != 0L) return;
        spawnRandomQuote(level, owner, type);
    }

    public static void spawnRandomQuote(ServerLevel level, Player owner, CoreSuppressionType type) {
        List<String> quotes = QUOTES.get(type);
        if (quotes == null || quotes.isEmpty()) return;

        RandomSource random = level.random;
        String quote = quotes.get(random.nextInt(quotes.size()));

        // 玩家周围随机一圈的位置
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = 2.0 + random.nextDouble() * 3.0;
        double x = owner.getX() + Math.cos(angle) * dist;
        double z = owner.getZ() + Math.sin(angle) * dist;
        double y = owner.getEyeY() + (random.nextDouble() - 0.3) * 2.5;

        EntityText entity = ModEntities.text.get().create(level);
        if (entity == null) return;
        entity.setPos(x, y, z);
        entity.setText(quote);
        entity.setColor(type.getColor());
        entity.setLifeTick(LIFE_TICKS);
        float r = switch (random.nextInt(6)) {
            case 0 -> 90.0f;
            case 1 -> -18.0f;
            case 2 -> 18.0f;
            default -> 0.0f;
        };
        entity.setRotation(r);
        level.addFreshEntity(entity);
    }
}