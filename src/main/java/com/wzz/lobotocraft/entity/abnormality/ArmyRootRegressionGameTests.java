package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.base.IEgoArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(ArmyRootRegressionGameTests.NAMESPACE)
public final class ArmyRootRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_army_root";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void nothingThereSelectsAndActuallyHitsBothPassiveBirds(GameTestHelper helper) throws Exception {
        for (var type : java.util.List.of(ModEntities.large_bird.get(), ModEntities.punishing_bird.get())) {
            var attacker = spawn(helper, ModEntities.nothing_there.get(), 4);
            var bird = spawn(helper, type, 7);
            try {
                attacker.setEscape(true);
                bird.setEscape(true);
                for (int i = 0; i < 21 && attacker.getTarget() != bird; i++)
                    invoke(attacker, "customServerAiStep", new Class<?>[0]);
                helper.assertTrue(attacker.getTarget() == bird, "real common selection missed " + bird.getAbnormalityCode());
                float before = bird.getHealth();
                for (int i = 0; i < 5; i++) invoke(attacker, "tickEscapePhases", new Class<?>[0]);
                helper.assertTrue(bird.getHealth() < before, "real phase-one hit missed " + bird.getAbnormalityCode());
            } finally { attacker.discard(); bird.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyArmorEncyclopediaAndAllPiecesUseOriginalPaleOne(GameTestHelper helper) {
        var army = spawn(helper, ModEntities.army_in_black.get(), 4);
        try {
            var shown = army.getEGOArmorData();
            for (var item : java.util.List.of(ModItems.ARMY_IN_BLACK_CHESTPLATE.get(),
                    ModItems.ARMY_IN_BLACK_LEGGINGS.get(), ModItems.ARMY_IN_BLACK_BOOTS.get())) {
                IEgoArmor armor = (IEgoArmor) item;
                helper.assertTrue(armor.getPaleResistance() == 1.0F && shown.paleResistance() == 1.0F,
                        "armor actual/display PALE must both equal 1.0");
                helper.assertTrue(shown.redResistance() == armor.getRedResistance()
                        && shown.whiteResistance() == armor.getWhiteResistance()
                        && shown.blackResistance() == armor.getBlackResistance(), "display duplicates stale armor values");
            }
            helper.succeed();
        } finally { army.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyGiftTextAndRealEquipTagsAgreeOnHeadwear(GameTestHelper helper) {
        var army = spawn(helper, ModEntities.army_in_black.get(), 4);
        try {
            var stack = ModItems.ARMY_IN_BLACK_CURIO.get().getDefaultInstance();
            helper.assertTrue("头饰".equals(army.getEGOGiftData().part()), "gift still says head");
            helper.assertTrue(stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:headwear"))),
                    "real gift slot not headwear");
            helper.assertFalse(stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:head"))),
                    "gift remains equippable in wrong head slot");
            helper.succeed();
        } finally { army.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyWorkAndNormalWeaponDamageTypesAreWhite(GameTestHelper helper) {
        var army = spawn(helper, ModEntities.army_in_black.get(), 4);
        try {
            helper.assertTrue("WHITE".equals(army.getDamageType()), "work/display damage type incorrectly BLACK");
            helper.assertTrue("WHITE".equals(army.getEGOWeaponData().damageType()), "weapon normal mode changed");
            helper.succeed();
        } finally { army.discard(); }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyRetainsChosenReactorInsteadOfSwitchingToPassingOne(GameTestHelper helper) throws Exception {
        try (var fixture = new ReactorFixture(helper)) {
            BlockPos chosen = new BlockPos(195, 271, 45), closer = new BlockPos(195, 271, 52);
            fixture.block(chosen, ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            fixture.block(closer, ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            var army = ModEntities.army_in_black.get().create(fixture.level);
            if (army == null) throw new IllegalStateException("missing army");
            try {
                army.moveTo(195.5, 271, 54.5, 0, 0);
                army.setEscape(true);
                army.tickCount = 100;
                set(army, "targetReactorPos", chosen);
                invoke(army, "tickEscapedState", new Class<?>[]{ServerLevel.class}, fixture.level);
                helper.assertTrue(chosen.equals(get(army, "targetReactorPos")), "timed nearest scan replaced stable destination");
                helper.succeed();
            } finally { army.discard(); }
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyTargetSelectionUsesBirthDepartmentNotClosestOtherDepartment(GameTestHelper helper)
            throws Exception {
        try (var fixture = new ReactorFixture(helper)) {
            BlockPos safety = new BlockPos(102, 201, 49), wrong = new BlockPos(160, 240, 51);
            fixture.block(safety, ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            fixture.block(wrong, ModBlocks.REGENERATION_REACTOR.get().defaultBlockState());
            var army = ModEntities.army_in_black.get().create(fixture.level);
            if (army == null) throw new IllegalStateException("missing army");
            try {
                BlockPos birth = new BlockPos(128, 236, 51);
                Object selected = invoke(army, "findNearestReactor",
                        new Class<?>[]{ServerLevel.class, BlockPos.class}, fixture.level, birth);
                helper.assertTrue(safety.equals(selected), "closest other department replaced birth department's main hall");
                helper.succeed();
            } finally { army.discard(); }
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void auditOtherEncyclopediaArmorAndGiftDefinitionsWithoutChangingBalance(GameTestHelper helper) {
        int checked = 0, mismatches = 0;
        var logger = com.mojang.logging.LogUtils.getLogger();
        for (var registration : ModEntities.ENTITIES.getEntries()) {
            var entity = registration.get().create(helper.getLevel());
            if (entity == null) continue;
            try {
                if (!(entity instanceof AbstractAbnormality abnormality)) continue;
                var data = abnormality.getEGOArmorData();
                if (data != null) {
                    String setId = data.armorId().replaceFirst("_armor$", "");
                    var armor = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues().stream()
                            .filter(item -> item instanceof IEgoArmor value && value.getSetId().equals(setId))
                            .map(item -> (IEgoArmor) item).findFirst().orElse(null);
                    if (armor != null) {
                        checked++;
                        if (data.redResistance() != armor.getRedResistance()
                                || data.whiteResistance() != armor.getWhiteResistance()
                                || data.blackResistance() != armor.getBlackResistance()
                                || data.paleResistance() != armor.getPaleResistance()) {
                            mismatches++;
                            logger.info("Armor display audit mismatch {} {}: display={}/{}/{}/{}, actual={}/{}/{}/{}",
                                    abnormality.getAbnormalityCode(), setId, data.redResistance(),
                                    data.whiteResistance(), data.blackResistance(), data.paleResistance(),
                                    armor.getRedResistance(), armor.getWhiteResistance(),
                                    armor.getBlackResistance(), armor.getPaleResistance());
                        }
                    }
                }
                var gift = abnormality.getEGOGiftData();
                if (gift == null) continue;
                String slot = switch (gift.part()) {
                    case "头部" -> "head"; case "头饰" -> "headwear";
                    case "眼部" -> "eyes"; case "口部" -> "mouth";
                    case "口罩" -> "mask"; case "面部" -> "face";
                    case "颈部" -> "neck"; case "右背" -> "right_boby";
                    case "手部" -> "hands"; case "胸部" -> "chest"; case "脸颊" -> "cheek";
                    default -> null;
                };
                if (slot == null) continue;
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        ResourceLocation.parse("lobotocraft:" + gift.itemId()));
                if (item == null) continue;
                if (!item.getDefaultInstance().is(TagKey.create(Registries.ITEM,
                        ResourceLocation.parse("curios:" + slot))))
                    logger.info("Gift slot audit mismatch {} {}: display={}, missing tag=curios:{}",
                            abnormality.getAbnormalityCode(), gift.itemId(), gift.part(), slot);
            } finally { entity.discard(); }
        }
        helper.assertTrue(checked >= 20, "too few actual armor/display pairs audited: " + checked);
        logger.info("Armor display audit completed: checked={}, mismatches={}; read-only, not full balance acceptance",
                checked, mismatches);
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyRouteOriginPersistsAndAttackReturnRemovalCancelNavigation(GameTestHelper helper)
            throws Exception {
        var army = spawn(helper, ModEntities.army_in_black.get(), 4);
        var reloaded = spawn(helper, ModEntities.army_in_black.get(), 12);
        try {
            BlockPos origin = new BlockPos(128, 236, 51), reactor = new BlockPos(102, 201, 49);
            set(army, "reactorOriginPos", origin);
            set(army, "targetReactorPos", reactor);
            CompoundTag saved = new CompoundTag();
            army.addAdditionalSaveData(saved);
            reloaded.readAdditionalSaveData(saved);
            helper.assertTrue(origin.equals(get(reloaded, "reactorOriginPos"))
                    && reactor.equals(get(reloaded, "targetReactorPos")), "reload lost stable birth/target binding");
            var navigator = (com.wzz.lobotocraft.entity.ai.FacilityMobNavigator) get(army, "reactorNavigator");
            navigator.moveTo(reactor);
            set(army, "reactorWalkPos", reactor);
            army.getMoveControl().setWantedPosition(reactor.getX(), reactor.getY(), reactor.getZ(), 1);
            invoke(army, "beginEscapeAttack", new Class<?>[0]);
            helper.assertTrue(get(navigator, "target") == null && get(army, "reactorWalkPos") == null,
                    "attack did not cancel facility route");
            helper.assertTrue(army.getMoveControl().getWantedX() == army.getX()
                    && army.getMoveControl().getWantedZ() == army.getZ(),
                    "attack retained the executor's old movement request");
            navigator.moveTo(reactor);
            set(army, "reactorWalkPos", reactor);
            army.stopEscape();
            helper.assertTrue(get(navigator, "target") == null, "containment return did not cancel route");
            navigator.moveTo(reactor);
            set(army, "reactorWalkPos", reactor);
            army.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(get(navigator, "target") == null, "chunk unload did not cancel route");
            helper.succeed();
        } finally { army.discard(); reloaded.discard(); }
    }

    static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        var method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(object, args);
    }
    static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    static Object get(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static <T extends AbstractAbnormality> T spawn(GameTestHelper helper, EntityType<T> type, int x) {
        for (int dx = 1; dx <= 16; dx++) for (int dz = 1; dz <= 11; dz++)
            helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
        T entity = type.create(helper.getLevel());
        if (entity == null) throw new IllegalStateException("missing entity");
        var pos = helper.absolutePos(new BlockPos(x, 1, 5));
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, -90, 0);
        helper.getLevel().addFreshEntity(entity);
        entity.recoverForNewFacilityDay();
        entity.setNoAi(true);
        return entity;
    }

    private static final class ReactorFixture implements AutoCloseable {
        final ServerLevel level;
        final CompoundTag previousProgress;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Map<BlockPos, CompoundTag> tags = new LinkedHashMap<>();
        ReactorFixture(GameTestHelper helper) {
            level = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (level == null) throw new IllegalStateException("missing facility");
            var progress = DepartmentProgressData.get(level.getServer());
            previousProgress = progress.save(new CompoundTag());
            for (var department : EmployeeDepartment.values())
                if (department != EmployeeDepartment.UNASSIGNED) progress.setLevel(department, 5);
        }
        void block(BlockPos pos, BlockState state) {
            blocks.putIfAbsent(pos, level.getBlockState(pos));
            if (level.getBlockEntity(pos) != null) tags.putIfAbsent(pos, level.getBlockEntity(pos).saveWithFullMetadata());
            level.setBlockAndUpdate(pos, state);
        }
        public void close() {
            blocks.forEach(level::setBlockAndUpdate);
            tags.forEach((pos, tag) -> { if (level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(tag); });
            level.getServer().overworld().getDataStorage().set("lobotocraft_department_progress",
                    DepartmentProgressData.load(previousProgress));
        }
    }
}
