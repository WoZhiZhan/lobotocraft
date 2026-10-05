package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.employee.*;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.event.definition.work.WorkPreExtractionEvent;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.*;

@GameTestHolder(WorkArchiveRegressionGameTests.NAMESPACE)
public final class WorkArchiveRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_work_archive";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void leticiaGiftOnlyUsesHeadwearAndBeeDisplayDoesNotRetuneItsHairSlot(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var leticia = f.mob(ModEntities.leticia.get(), 4);
            var bee = f.mob(ModEntities.queen_bee.get(), 8);
            var leticiaStack = ModItems.LETICIA_CURIO.get().getDefaultInstance();
            helper.assertTrue(leticiaStack.is(slot("headwear")) && !leticiaStack.is(slot("head")),
                    "leticia still equips on wrong head slot");
            helper.assertTrue("头饰".equals(leticia.getEGOGiftData().part()), "leticia display does not agree");
            var beeStack = ModItems.QUEEN_BEE_CURIO.get().getDefaultInstance();
            helper.assertTrue("头部".equals(bee.getEGOGiftData().part()) && beeStack.is(slot("headwear"))
                    && !beeStack.is(slot("head")), "bee display fix unexpectedly retunes original hair slot");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void oneSinGiftAlreadyUsesOnlyHeadNotASecondPart(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var one = f.mob(ModEntities.onebad.get(), 4);
            helper.assertTrue("头部".equals(one.getEGOGiftData().part()), "one sin gift display not head");
            var stack = ModItems.REPENTANCE_CURIO.get().getDefaultInstance();
            helper.assertTrue(stack.is(slot("head")) && !stack.is(slot("headwear"))
                    && !stack.is(slot("eyes")) && !stack.is(slot("boxing")), "gift has a wrong extra slot");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void helperInsightKeepsOriginalSignedValues(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var robot = f.mob(ModEntities.helper.get(), 4);
            float[] expected = {0, 0, -0.3F, -0.6F, -0.9F};
            helper.assertTrue(Arrays.equals(robot.getFullWorkPreferences()[WorkType.INSIGHT.ordinal()], expected),
                    "helper original negative insight probabilities were altered or clamped");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void nothingThereMultipliesWholeProbabilityByAllFiveFortitudeGrades(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var nothing = f.mob(ModEntities.nothing_there.get(), 4);
            var player = f.player(8);
            int[] values = {20, 30, 45, 65, 85};
            for (int i = 0; i < values.length; i++) {
                f.fortitude(player, values[i]);
                float result = nothing.modifyWorkSuccessRate(player, WorkType.ATTACHMENT, 0.6F);
                helper.assertTrue(Math.abs(result - 0.6F * (i + 1) * 0.2F) < 0.00001F,
                        "fortitude multiplier is additive or missing at grade " + (i + 1));
            }
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void nothingThereWithoutWorkerKeepsOriginalProbability(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var nothing = f.mob(ModEntities.nothing_there.get(), 4);
            helper.assertTrue(nothing.modifyWorkSuccessRate(null, WorkType.ATTACHMENT, 0.6F) == 0.6F,
                    "workerless query changes or crashes original rate");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void realExtractionRereadsFortitudeWithoutMultiplyingItsPriorResult(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var nothing = f.mob(ModEntities.nothing_there.get(), 4);
            var player = f.player(8);
            f.fortitude(player, 20);
            var session = new WorkManager.WorkSession(player, nothing, WorkType.ATTACHMENT, false);
            var field = WorkManager.class.getDeclaredField("activeWorks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var works = (Map<UUID, WorkManager.WorkSession>) field.get(null);
            works.put(player.getUUID(), session);
            var probe = new ExtractionProbe(player, nothing);
            MinecraftForge.EVENT_BUS.register(probe);
            var extract = WorkManager.class.getDeclaredMethod("performExtraction", WorkManager.WorkSession.class);
            extract.setAccessible(true);
            try {
                extract.invoke(null, session);
                float low = probe.rate;
                f.fortitude(player, 85);
                extract.invoke(null, session);
                float high = probe.rate;
                f.fortitude(player, 20);
                extract.invoke(null, session);
                helper.assertTrue(Math.abs(high - low * 5) < 0.00001F
                        && Math.abs(probe.rate - low) < 0.00001F,
                        "actual extraction freezes modifier or compounds previously modified rate: "
                                + low + "/" + high + "/" + probe.rate);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(probe);
                works.remove(player.getUUID());
            }
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void sharedLiveModifierAlwaysStartsBeforeBlueStarAndQueenBonuses(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var star = f.mob(ModEntities.blue_star.get(), 4);
            var queen = f.mob(ModEntities.queen_of_hatred.get(), 8);
            var player = f.player(12);
            queen.setQliphothCounter(1);
            for (var target : List.of(star, queen)) {
                var session = new WorkManager.WorkSession(player, target, WorkType.INSIGHT, false);
                for (int i = 0; i < 5; i++)
                    helper.assertTrue(Math.abs(session.currentSuccessRate() - session.actualSuccessRate) < 0.00001,
                            "repeated modifier compounds a previous entity modifier");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void oneSinManualsDescribeExistingModNotEnergyOrDepartmentHealing(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var one = f.mob(ModEntities.onebad.get(), 4);
            var worker = f.player(8);
            var neighbor = f.player(12);
            worker.getCapability(com.wzz.lobotocraft.capability.MentalValueProvider.MENTAL_VALUE)
                    .ifPresent(mental -> mental.setMaxMentalValue(100));
            neighbor.getCapability(com.wzz.lobotocraft.capability.MentalValueProvider.MENTAL_VALUE)
                    .ifPresent(mental -> mental.setMaxMentalValue(100));
            MentalValueUtil.setMentalValue(worker, 30);
            MentalValueUtil.setMentalValue(neighbor, 30);
            one.onGoodWork(worker);
            helper.assertTrue(Math.abs(MentalValueUtil.getMentalValue(worker) - 40) < 0.001
                    && Math.abs(MentalValueUtil.getMentalValue(neighbor) - 30) < 0.001,
                    "existing good-work individual recovery changed unexpectedly");
            one.onNormalWork(worker);
            helper.assertTrue(Math.abs(MentalValueUtil.getMentalValue(worker) - 45) < 0.001,
                    "existing normal recovery changed");
            String first = AbnormalityEncyclopediaData.getManual("O-03-03", 0, "zh_cn");
            String second = AbnormalityEncyclopediaData.getManual("O-03-03", 1, "zh_cn");
            helper.assertTrue(first.contains("精神值") && first.contains("10") && first.contains("5")
                    && !first.contains("情绪值") && second.contains("祈祷") && second.contains("精神值"),
                    "Chinese manuals still misdescribe the existing recovery mechanics");
            String english = AbnormalityEncyclopediaData.getManual("O-03-03", 1, "en_us");
            helper.assertTrue(english.contains("Poor") && !english.contains("every employee in the department"),
                    "English manuals still promise an unimplemented original department effect");
            helper.succeed();
        }
    }

    public static final class ExtractionProbe {
        final EmployeeServerPlayer player;
        final EntityNothingThere target;
        float rate = Float.NaN;
        ExtractionProbe(EmployeeServerPlayer player, EntityNothingThere target) { this.player=player; this.target=target; }
        @SubscribeEvent public void before(WorkPreExtractionEvent event) {
            if (event.getPlayer() == player && event.getAbnormality() == target) {
                rate=event.getSuccessRate();
                event.setCanceled(true); // deterministic signal at the real pre-roll seam
            }
        }
    }
    private static TagKey<net.minecraft.world.item.Item> slot(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:" + id));
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final List<Entity> entities = new ArrayList<>();
        Fixture(GameTestHelper helper) {
            this.helper=helper;
            for (int x=1;x<=16;x++) for (int z=1;z<=11;z++) helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        }
        <T extends Mob> T mob(EntityType<T> type,int x) {
            var mob=type.create(helper.getLevel());
            if(mob==null)throw new IllegalStateException("missing entity");
            var pos=helper.absolutePos(new BlockPos(x,1,5));
            mob.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            mob.setNoAi(true); helper.getLevel().addFreshEntity(mob); entities.add(mob);
            return mob;
        }
        EmployeeServerPlayer player(int x) {
            var record=new EmployeeRecord(UUID.randomUUID(),"work_archive",EmployeeDepartment.CONTROL);
            var player=new EmployeeServerPlayer(helper.getLevel(),new GameProfile(record.id(),record.name()),record);
            var pos=helper.absolutePos(new BlockPos(x,1,5));
            player.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            helper.getLevel().addNewPlayer(player);
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats->{
                stats.setFortitude(20);stats.setPrudence(20);stats.setTemperance(20);stats.setJustice(20);
            });
            entities.add(player);return player;
        }
        void fortitude(EmployeeServerPlayer player,int value) {
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats->stats.setFortitude(value));
        }
        @Override public void close() { for(var entity:entities) if(entity instanceof EmployeeServerPlayer p) p.kill();else entity.discard(); }
    }
}
