package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.base.IEgoArmor;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exercises production selection, retained-target and hit entrances, not just a test predicate. */
@GameTestHolder(RedShoesRootRegressionGameTests.NAMESPACE)
public final class RedShoesRootRegressionGameTests {
    static final String NAMESPACE = "lobotocraft_red_shoes_root";
    private static final String TARGET = "lobotocraft_red_shoes_target";
    private static final String COOLDOWN = "lobotocraft_red_shoes_attack_cooldown";

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyBodyUsesApprovedCandidateDefense(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var army = f.spawn(ModEntities.army_in_black.get(), 4, 1, 4);
            var attributes = List.of(ModAttributes.RED_DAMAGE_RESISTANCE.get(),
                    ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), ModAttributes.BLACK_DAMAGE_RESISTANCE.get(),
                    ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
            double[] expected = {1.2, 0.6, 1.0, 0.8};
            for (int i = 0; i < 4; i++)
                helper.assertTrue(Math.abs(army.getAttributeValue(attributes.get(i)) - expected[i]) < 0.00001,
                        "body defense mismatch at channel " + i);
            helper.assertTrue(army.getEGOArmorData().paleResistance() == 1.0F,
                    "body change accidentally retuned armor");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyActualFourChannelDamageUsesNewBodyDefense(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var army = f.spawn(ModEntities.army_in_black.get(), 6, 1, 4);
            var attacker = f.spawn(ModEntities.nothing_there.get(), 4, 1, 4);
            army.setEscape(true);
            String[] channels = {"red", "white", "black", "blue"};
            float[] expected = {12, 6, 10, 8};
            for (int i = 0; i < channels.length; i++) {
                army.setHealth(army.getMaxHealth());
                EntityUtil.clearHurtTime(army);
                float before = army.getHealth();
                helper.assertTrue(army.hurt(DamageHelper.getDamage(attacker, channels[i]), 10),
                        "four-color entry rejected " + channels[i]);
                helper.assertTrue(Math.abs(before - army.getHealth() - expected[i]) < 0.001F,
                        "real damage uses old resistance for " + channels[i]);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void armyOldSaveDefaultsMigrateButCustomBasesAndModifiersSurvive(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var army = f.spawn(ModEntities.army_in_black.get(), 4, 1, 4);
            var red = army.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
            var black = army.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
            var pale = army.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
            red.setBaseValue(0.8);
            black.setBaseValue(0.6);
            pale.setBaseValue(1.2);
            var modifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    UUID.randomUUID(), "migration-regression", 0.1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
            red.addPermanentModifier(modifier);
            var saved = new net.minecraft.nbt.CompoundTag();
            army.addAdditionalSaveData(saved);
            army.readAdditionalSaveData(saved);
            helper.assertTrue(red.getBaseValue() == 1.2 && black.getBaseValue() == 1.0
                    && pale.getBaseValue() == 0.8 && red.getModifier(modifier.getId()) != null,
                    "legacy default tuple or modifier failed migration");
            red.setBaseValue(0.9);
            black.setBaseValue(0.6);
            pale.setBaseValue(1.2);
            saved = new net.minecraft.nbt.CompoundTag();
            army.addAdditionalSaveData(saved);
            army.readAdditionalSaveData(saved);
            helper.assertTrue(red.getBaseValue() == 0.9 && black.getBaseValue() == 0.6
                    && pale.getBaseValue() == 1.2, "custom saved tuning overwritten");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void normalClerkRemainsBlockedAndControlledPersonnelRemainAttackable(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 2, 1, 4);
            var employee = f.employee(4, 1, 4, shoes);
            var bird = f.spawn(ModEntities.large_bird.get(), 6, 1, 4);
            bird.setEscape(true);
            var normal = f.spawn(ModEntities.clerk.get(), 4, 1, 7);
            helper.assertFalse(bird.hurt(DamageHelper.getDamage(normal, "red"), 9),
                    "ordinary clerk gained protected-combatant damage permission");
            var controlled = f.spawn(ModEntities.red_shoes_clerk.get(), 6, 1, 4);
            bird.discard();
            normal.discard();
            float before = controlled.getHealth();
            tick(employee);
            helper.assertTrue(controlled.getUUID().equals(employee.getPersistentData().getUUID(TARGET)),
                    "controlled personnel wrongly excluded from original attackable-unit rule");
            helper.assertTrue(controlled.getHealth() < before, "controlled positive target was not hit");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void redShoesGiftAndArmorDisplayUseActualEquipment(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 4, 1, 4);
            helper.assertTrue("口部".equals(shoes.getEGOGiftData().part()), "gift does not say mouth");
            var shown = shoes.getEGOArmorData();
            for (var item : List.of(ModItems.RED_SHOES_CHESTPLATE.get(), ModItems.RED_SHOES_LEGGINGS.get(),
                    ModItems.RED_SHOES_BOOTS.get())) {
                var armor = (IEgoArmor) item;
                helper.assertTrue(shown.redResistance() == 0.5F && shown.whiteResistance() == 1.2F
                        && shown.blackResistance() == 0.8F && shown.paleResistance() == 1.5F,
                        "encyclopedia retained stale armor tuning");
                helper.assertTrue(shown.redResistance() == armor.getRedResistance()
                        && shown.whiteResistance() == armor.getWhiteResistance()
                        && shown.blackResistance() == armor.getBlackResistance()
                        && shown.paleResistance() == armor.getPaleResistance(), "display/actual armor drift");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void controlledEmployeeSkipsContainedBodyAndShoesAtRealTick(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 3, 1, 4);
            var attacker = f.employee(4, 1, 4, shoes);
            var contained = f.spawn(ModEntities.large_bird.get(), 5, 1, 4);
            var person = f.spawn(ModEntities.clerk.get(), 6, 1, 4);
            attacker.getPersistentData().putUUID(TARGET, contained.getUUID());
            float before = person.getHealth();
            tick(attacker);
            helper.assertTrue(person.getUUID().equals(attacker.getPersistentData().getUUID(TARGET)),
                    "contained or source body won selection over attackable personnel");
            helper.assertTrue(person.getHealth() < before, "positive personnel target was not hit");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void specialClerkRejectsContainedAtAssignmentAndHit(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var attacker = f.spawn(ModEntities.red_shoes_clerk.get(), 4, 1, 4);
            var contained = f.spawn(ModEntities.large_bird.get(), 6, 1, 4);
            attacker.setTarget(contained);
            helper.assertTrue(attacker.getTarget() == null, "clerk assignment accepts contained abnormality");
            helper.assertFalse(attacker.doHurtTarget(contained), "clerk hit accepts contained abnormality");
            var shoes = f.spawn(ModEntities.red_shoes.get(), 6, 1, 6);
            attacker.setTarget(shoes);
            helper.assertTrue(attacker.getTarget() == null, "clerk attacks noncombat source body");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void bothControlledEntrancesCanHitEscapedBirdAndHero(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 2, 1, 4);
            var employee = f.employee(4, 1, 4, shoes);
            var bird = f.spawn(ModEntities.large_bird.get(), 6, 1, 4);
            bird.setEscape(true);
            float before = bird.getHealth();
            tick(employee);
            helper.assertTrue(bird.getHealth() < before, "employee cannot hit escaped passive bird");
            employee.kill();
            var clerk = f.spawn(ModEntities.red_shoes_clerk.get(), 4, 1, 4);
            clerk.setTarget(bird);
            helper.assertTrue(clerk.getTarget() == bird && clerk.doHurtTarget(bird),
                    "clerk lacks targetability or shared damage admission");
            bird.setEscape(false);
            helper.assertFalse(clerk.canAttack(bird) || clerk.doHurtTarget(bird),
                    "clerk continuation or delayed hit accepts a returned target");
            bird.discard();
            var hero = f.spawn(ModEntities.queen_of_hatred.get(), 6, 1, 4);
            hero.setHeroDamageStateForTest(false);
            clerk.setTarget(hero);
            helper.assertTrue(clerk.getTarget() == hero, "HERO combat selection exception was lost");
            helper.assertTrue(clerk.doHurtTarget(hero), "HERO hit rejects controlled clerk identity");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void closestTargetIsReselectedAndReturnedTargetRejectedAtHit(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 2, 1, 4);
            var employee = f.employee(4, 1, 4, shoes);
            var far = f.spawn(ModEntities.clerk.get(), 10, 1, 4);
            var near = f.spawn(ModEntities.large_bird.get(), 6, 1, 4);
            near.setEscape(true);
            employee.getPersistentData().putUUID(TARGET, far.getUUID());
            tick(employee);
            helper.assertTrue(near.getUUID().equals(employee.getPersistentData().getUUID(TARGET)),
                    "valid cached target masks a nearer attackable target");
            near.setEscape(false);
            float before = near.getHealth();
            employee.getPersistentData().putInt(COOLDOWN, 0);
            hit(employee, near, shoes);
            helper.assertTrue(near.getHealth() == before
                    && employee.getPersistentData().getInt(COOLDOWN) == 0,
                    "delayed hit uses a returned/invalid target");
            tick(employee);
            helper.assertTrue(far.getUUID().equals(employee.getPersistentData().getUUID(TARGET)),
                    "returned target was retained");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void selectionUsesCurrentFacilityAreaNotAdjacentRoom(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 2, 1, 4);
            var employee = f.employee(4, 1, 4, shoes);
            var otherRoom = f.spawn(ModEntities.clerk.get(), 5, 1, 4);
            var samePassage = f.spawn(ModEntities.clerk.get(), 4, 1, 7);
            f.area(1, 4, FacilityAreaType.CORRIDOR);
            f.area(5, 16, FacilityAreaType.CONTAINMENT);
            tick(employee);
            helper.assertTrue(samePassage.getUUID().equals(employee.getPersistentData().getUUID(TARGET)),
                    "adjacent room wins over current passage");
            var clerk = f.spawn(ModEntities.red_shoes_clerk.get(), 4, 1, 4);
            clerk.setTarget(otherRoom);
            helper.assertTrue(clerk.getTarget() == null, "clerk ignores passage boundaries");
            clerk.setTarget(samePassage);
            helper.assertTrue(clerk.getTarget() == samePassage, "same passage positive control rejected");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void samePassageCuboidsShareIdentityButContainmentAndOtherFloorDoNot(GameTestHelper helper)
            throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 2, 1, 4);
            var employee = f.employee(4, 1, 4, shoes);
            var neighbor = f.spawn(ModEntities.clerk.get(), 5, 1, 4);
            String passage = "shared-shoes-passage-" + UUID.randomUUID();
            f.area(1, 4, FacilityAreaType.CORRIDOR, passage);
            f.area(5, 8, FacilityAreaType.CORRIDOR, passage);
            f.area(9, 16, FacilityAreaType.CONTAINMENT, passage);
            tick(employee);
            helper.assertTrue(employee.getPersistentData().hasUUID(TARGET)
                            && neighbor.getUUID().equals(employee.getPersistentData().getUUID(TARGET)),
                    "one passage split into geometry boxes became separate combat identities");
            f.move(neighbor, 9, 1, 4);
            var clerk = f.spawn(ModEntities.red_shoes_clerk.get(), 4, 1, 4);
            clerk.setTarget(neighbor);
            helper.assertTrue(clerk.getTarget() == null, "room sharing a monitor group is treated as same passage");
            f.move(neighbor, 5, 4, 4);
            clerk.setTarget(neighbor);
            helper.assertTrue(clerk.getTarget() == null, "vertical group alias leaks to another floor");
            helper.succeed();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void unmeasuredWorldDoesNotSelectOtherFloorOrHitThroughWall(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var shoes = f.spawn(ModEntities.red_shoes.get(), 2, 1, 4);
            var employee = f.employee(4, 1, 4, shoes);
            var upstairs = f.spawn(ModEntities.clerk.get(), 4, 4, 4);
            var sameFloor = f.spawn(ModEntities.clerk.get(), 6, 1, 4);
            employee.getPersistentData().putUUID(TARGET, upstairs.getUUID());
            tick(employee);
            helper.assertTrue(sameFloor.getUUID().equals(employee.getPersistentData().getUUID(TARGET)),
                    "unmapped radius selects another floor");
            for (int y = 1; y <= 5; y++) for (int z = 1; z <= 10; z++)
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            float before = sameFloor.getHealth();
            employee.getPersistentData().putInt(COOLDOWN, 0);
            hit(employee, sameFloor, shoes);
            helper.assertTrue(sameFloor.getHealth() == before, "employee hits through wall");
            var clerk = f.spawn(ModEntities.red_shoes_clerk.get(), 4, 1, 4);
            helper.assertFalse(clerk.doHurtTarget(sameFloor), "clerk hits through wall");
            helper.succeed();
        }
    }

    private static void tick(LivingEntity employee) throws Exception {
        invoke("tickBloodlust", new Class<?>[]{LivingEntity.class}, employee);
    }

    private static void hit(LivingEntity employee, LivingEntity target, EntityRedShoes source) throws Exception {
        invoke("tryBloodlustAttack", new Class<?>[]{LivingEntity.class, LivingEntity.class, EntityRedShoes.class},
                employee, target, source);
    }

    private static void invoke(String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = EntityRedShoes.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        method.invoke(null, args);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final List<LivingEntity> entities = new ArrayList<>();
        final List<String> areaKeys = new ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            for (int x = 1; x <= 16; x++) for (int z = 1; z <= 10; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        <T extends Mob> T spawn(EntityType<T> type, int x, int y, int z) {
            T mob = type.create(helper.getLevel());
            if (mob == null) throw new IllegalStateException("missing entity " + type);
            move(mob, x, y, z);
            mob.setNoAi(true);
            helper.getLevel().addFreshEntity(mob);
            entities.add(mob);
            return mob;
        }

        EmployeeServerPlayer employee(int x, int y, int z, EntityRedShoes shoes) throws Exception {
            var record = new EmployeeRecord(UUID.randomUUID(), "shoes_root", EmployeeDepartment.CONTROL);
            var player = new EmployeeServerPlayer(helper.getLevel(), new GameProfile(record.id(), record.name()), record);
            move(player, x, y, z);
            helper.getLevel().addNewPlayer(player);
            var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
            player.getAbilities().invulnerable = false;
            player.getPersistentData().putBoolean("lobotocraft_red_shoes_bloodlust", true);
            player.getPersistentData().putUUID("lobotocraft_red_shoes_source", shoes.getUUID());
            player.getPersistentData().putBoolean("lobotocraft_red_shoes_teleported", true);
            entities.add(player);
            return player;
        }

        void move(LivingEntity entity, int x, int y, int z) {
            var pos = helper.absolutePos(new BlockPos(x, y, z));
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        }

        void area(int minX, int maxX, FacilityAreaType type) {
            area(minX, maxX, type, "shoes-area-" + UUID.randomUUID());
        }

        void area(int minX, int maxX, FacilityAreaType type, String group) {
            String key = "shoes-root-" + UUID.randomUUID();
            var min = helper.absolutePos(new BlockPos(minX, 1, 1));
            var max = helper.absolutePos(new BlockPos(maxX, 6, 10));
            CompanyRoomData.get(helper.getLevel()).putArea(new FacilityAreaEntry(
                    helper.getLevel().dimension().location(), key, min, min, max, "", type, group));
            areaKeys.add(key);
        }

        @Override public void close() {
            for (String key : areaKeys) CompanyRoomData.get(helper.getLevel()).removeArea(key);
            for (LivingEntity entity : entities) {
                if (entity instanceof EmployeeServerPlayer player) {
                    EntityRedShoes.clearControlForRecovery(player);
                    player.kill();
                } else entity.discard();
            }
        }
    }
}
