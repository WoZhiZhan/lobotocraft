package com.wzz.lobotocraft.item.ego.must_open_path;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.ego.MustOpenPathRewardService;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

@GameTestHolder(ModMain.MODID + "_must_open_path")
public final class MustOpenPathGameTests {
    private static final String TEST_NAMESPACE = ModMain.MODID + "_must_open_path";
    private MustOpenPathGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "must_open_path")
    public static void suppressionRewardIsUniqueAndAiDoesNotReceiveCurio(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, "must_open_path_reward");
        setStats(employee, 110, 110, 110, 110);

        AiEgoRepositoryData repository = AiEgoRepositoryData.get(helper.getLevel().getServer());
        MustOpenPathRewardService.grantNormalSuppression(helper.getLevel().getServer(), null);
        MustOpenPathRewardService.grantNormalSuppression(helper.getLevel().getServer(), null);
        helper.assertTrue(repository.total(MustOpenPathRewardService.SKADI_CODE, "weapon") == 1,
                "normal suppression must grant at most one global AI weapon");
        helper.assertTrue(repository.total(MustOpenPathRewardService.SKADI_CODE, "armor") == 1,
                "normal suppression must grant at most one global AI armor allocation");

        helper.assertTrue(repository.assign(employee.getUUID(), MustOpenPathRewardService.SKADI_CODE, "armor"),
                "AI armor allocation could not be assigned");
        helper.assertTrue(repository.assign(employee.getUUID(), MustOpenPathRewardService.SKADI_CODE, "weapon"),
                "AI weapon allocation could not be assigned");
        helper.assertTrue(EgoLoadoutService.equipAi(employee, repository,
                MustOpenPathRewardService.SKADI_CODE, "armor"), "AI armor projection failed");
        helper.assertTrue(EgoLoadoutService.equipAi(employee, repository,
                MustOpenPathRewardService.SKADI_CODE, "weapon"), "AI weapon projection failed");
        employee.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(data -> data.lockArmor());

        helper.assertTrue(!EgoArmorHelper.isFullEGOWithWeaponAnywhere(
                employee, MustOpenPathWeapon.SET_ID),
                "AI research must not project the player-only suppression curio");
        helper.assertTrue(MustOpenPathRewardService.armorReward().size() == 3
                        && MustOpenPathRewardService.armorReward().stream()
                        .noneMatch(stack -> stack.is(ModItems.MUST_OPEN_PATH_CURIO.get())),
                "AI armor stock still contains the player-only curio");
        employee.kill();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "must_open_path")
    public static void chargeGateAndSpecialAreaMatchDesign(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, "must_open_path_charge");
        employee.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(ModItems.MUST_OPEN_PATH_WEAPON.get()));
        employee.getPersistentData().putLong(BaseEgoWeapon.PARTIAL_ATTACK_TICK_TAG,
                employee.level().getGameTime());
        employee.getPersistentData().putFloat("lobotocraft_ego_attack_multiplier", 0.99F);
        helper.assertTrue(!MustOpenPathMechanics.shouldCountOutgoingAttack(employee),
                "partial left click was accepted as a full-charge boundary attack");
        employee.getPersistentData().putFloat("lobotocraft_ego_attack_multiplier", 1.0F);
        helper.assertTrue(MustOpenPathMechanics.shouldCountOutgoingAttack(employee),
                "fully charged left click was rejected by the boundary gate");

        employee.getPersistentData().remove(BaseEgoWeapon.PARTIAL_ATTACK_TICK_TAG);
        MustOpenPathMechanics.markFullChargeAttack(employee);
        helper.assertTrue(MustOpenPathMechanics.shouldCountOutgoingAttack(employee),
                "fully charged anchor impact marker was rejected by the boundary gate");
        MustOpenPathMechanics.clearFullChargeAttack(employee);

        var area = MustOpenPathMechanics.specialAttackArea(employee);
        helper.assertTrue(Math.abs(area.getXsize() - 7.0D) < 0.001D
                        && Math.abs(area.getYsize() - 3.0D) < 0.001D
                        && Math.abs(area.getZsize() - 7.0D) < 0.001D,
                "anchor special attack area is not 7x3x7");
        helper.assertTrue(MustOpenPathMechanics.BOUNDARY_INVULNERABLE_TICKS == 15,
                "boundary must reduce vanilla 20-tick invulnerability by only one quarter");
        employee.kill();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "must_open_path")
    public static void curioRecoveryClearsServerPanicState(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, "must_open_path_panic");
        employee.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setMentalValue(0.0F);
            mental.setPanicType(PanicType.BERSERK);
        });
        helper.assertTrue(MentalValueUtil.isPanic(employee), "panic fixture did not enter panic");
        helper.assertTrue(MustOpenPathCurio.recoverFromPanic(employee),
                "curio recovery left the server-owned panic state active");
        helper.assertTrue(!MentalValueUtil.isPanic(employee),
                "player still reports panic after curio recovery");
        employee.kill();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "must_open_path")
    public static void skadiResearchContainsWeaponAndArmorButNotCurio(GameTestHelper helper) {
        EntityDarkSkadi skadi = ModEntities.skadi_corrupted.get().create(helper.getLevel());
        helper.assertTrue(skadi != null, "could not create Dark Skadi research fixture");
        helper.assertTrue(skadi.getEGOWeaponStacks().size() == 1
                        && skadi.getEGOWeaponStacks().get(0).is(ModItems.MUST_OPEN_PATH_WEAPON.get()),
                "Skadi weapon research does not produce Must Open Path");
        helper.assertTrue(skadi.getEGOArmorStacks().size() == 3
                        && skadi.getEGOArmorStacks().stream()
                        .noneMatch(stack -> stack.is(ModItems.MUST_OPEN_PATH_CURIO.get())),
                "Skadi armor research contains the suppression-only curio");
        helper.assertTrue(skadi.getGiftProbability() < 0.0F,
                "Skadi curio can still be obtained from ordinary work");
        skadi.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "must_open_path")
    public static void exRequirementsAndOffhandWeaponAreRecognized(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, "must_open_path_ex");
        MustOpenPathWeapon weapon = (MustOpenPathWeapon) ModItems.MUST_OPEN_PATH_WEAPON.get();
        setStats(employee, 110, 99, 110, 99);
        helper.assertTrue(!weapon.canUseItem(employee),
                "V stats must not satisfy an EX equipment requirement");
        setStats(employee, 110, 100, 110, 100);
        helper.assertTrue(weapon.canUseItem(employee),
                "raw stat 100 must satisfy an EX equipment requirement");

        employee.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.MUST_OPEN_PATH_CHESTPLATE.get()));
        employee.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.MUST_OPEN_PATH_LEGGINGS.get()));
        employee.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.MUST_OPEN_PATH_BOOTS.get()));
        employee.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.MUST_OPEN_PATH_WEAPON.get()));
        CuriosApi.getCuriosInventory(employee).resolve().ifPresent(handler ->
                handler.getCurios().get("headwear").getStacks().setStackInSlot(
                        0, new ItemStack(ModItems.MUST_OPEN_PATH_CURIO.get())));
        employee.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(data -> data.lockArmor());
        helper.assertTrue(EgoArmorHelper.isFullEGOWithWeaponAnywhere(
                employee, MustOpenPathWeapon.SET_ID),
                "offhand weapon must satisfy this set's weapon-presence requirement");
        employee.kill();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "must_open_path")
    public static void returningStartsCooldownWithoutTouchingNoAi(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, "must_open_path_anchor");
        setStats(employee, 110, 110, 110, 110);
        var zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 3, 2, 3);
        zombie.setNoAi(false);
        zombie.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                MustOpenPathMechanics.STUN_TICKS, 255));
        MustOpenPathMechanics.activateAt(employee, helper.getLevel().dimension(),
                employee.position(), employee.position().add(1.0D, 0.0D, 0.0D));
        helper.assertTrue(MustOpenPathMechanics.isDeployed(employee), "anchor deployment was not recorded");
        helper.assertTrue(!zombie.isNoAi(), "anchor stun must never modify NoAI");
        helper.assertTrue(MustOpenPathMechanics.returnToOrigin(employee), "anchor return failed");
        helper.assertTrue(!MustOpenPathMechanics.isDeployed(employee), "anchor deployment survived return");
        helper.assertTrue(MustOpenPathMechanics.remainingCooldown(employee) > 0,
                "40-second cooldown did not begin after return");
        employee.kill();
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 80, required = true, batch = "must_open_path")
    public static void recordSuppressionGrantsNeitherPlayerNorAiReward(GameTestHelper helper) {
        EmployeeServerPlayer employee = employee(helper, "must_open_path_record");
        var repository = AiEgoRepositoryData.get(helper.getLevel().getServer());
        int weaponBefore = repository.total(MustOpenPathRewardService.SKADI_CODE, "weapon");
        int armorBefore = repository.total(MustOpenPathRewardService.SKADI_CODE, "armor");
        employee.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SPECIAL_RECORD.get()));
        var isharmla = ModEntities.isharmla.get().create(helper.getLevel());
        helper.assertTrue(isharmla != null, "could not create Isharmla for record test");
        isharmla.moveTo(employee.getX() + 1.0D, employee.getY(), employee.getZ(), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(isharmla);

        ModItems.SPECIAL_RECORD.get().use(helper.getLevel(), employee, InteractionHand.MAIN_HAND);
        helper.assertTrue(repository.total(MustOpenPathRewardService.SKADI_CODE, "weapon") == weaponBefore,
                "record suppression granted global AI weapon stock");
        helper.assertTrue(repository.total(MustOpenPathRewardService.SKADI_CODE, "armor") == armorBefore,
                "record suppression granted global AI armor stock");
        helper.assertTrue(countRewardItems(employee) == 0,
                "record suppression granted E.G.O items to its user");
        employee.kill();
        helper.succeed();
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, String name) {
        UUID id = UUID.randomUUID();
        EmployeeRecord record = new EmployeeRecord(id, name, EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(id, name), record);
        helper.getLevel().addNewPlayer(employee);
        return employee;
    }

    private static void setStats(EmployeeServerPlayer employee, int fortitude, int prudence,
                                 int temperance, int justice) {
        employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(fortitude);
            stats.setPrudence(prudence);
            stats.setTemperance(temperance);
            stats.setJustice(justice);
        });
    }

    private static int countRewardItems(EmployeeServerPlayer employee) {
        int count = 0;
        for (ItemStack stack : employee.getInventory().items) {
            if (stack.is(ModItems.MUST_OPEN_PATH_WEAPON.get())
                    || stack.is(ModItems.MUST_OPEN_PATH_CHESTPLATE.get())
                    || stack.is(ModItems.MUST_OPEN_PATH_LEGGINGS.get())
                    || stack.is(ModItems.MUST_OPEN_PATH_BOOTS.get())
                    || stack.is(ModItems.MUST_OPEN_PATH_CURIO.get())) count += stack.getCount();
        }
        return count;
    }
}
