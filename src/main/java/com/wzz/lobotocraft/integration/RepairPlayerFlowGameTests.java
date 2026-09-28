package com.wzz.lobotocraft.integration;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import com.wzz.lobotocraft.event.ForgeModEvent;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenDawn;
import com.wzz.lobotocraft.event.listener.PlayerControlLock;
import com.wzz.lobotocraft.exhibition.ExhibitionSafetyService;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.item.ego.nothing_there.NothingThereWeapon;
import com.wzz.lobotocraft.util.BuffUtil;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.MovementLockUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import top.theillusivec4.curios.api.CuriosApi;

@GameTestHolder(RepairPlayerFlowGameTests.TEST_NAMESPACE)
public final class RepairPlayerFlowGameTests {
    static final String TEST_NAMESPACE = ModMain.MODID + "_review_flows";
    private static final String RED_SHOES_JUDGEMENT_TIMER =
            "lobotocraft_red_shoes_judgement_timer";

    private RepairPlayerFlowGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "review_player_combat")
    public static void mimicryHealsOnlyHealthActuallyLost(GameTestHelper helper) {
        EmployeeServerPlayer player = spawnEmployee(helper, "mimicry_player", new BlockPos(2, 1, 2));
        Zombie target = spawnZombie(helper, new BlockPos(4, 1, 2));
        Zombie invulnerableTarget = spawnZombie(helper, new BlockPos(5, 1, 2));
        ItemStack weaponStack = new ItemStack(ModItems.NOTHING_THERE_WEAPON.get());
        NothingThereWeapon weapon = (NothingThereWeapon) weaponStack.getItem();

        try {
            equipFullMimicry(player, weaponStack);
            helper.assertTrue(EgoArmorHelper.isFullEGO(player, "nothing_there"),
                    "mimicry fixture did not equip a complete locked set");

            player.setHealth(5.0F);
            target.setHealth(2.0F);
            helper.assertTrue(weapon.onLeftClickEntity(weaponStack, player, target),
                    "mimicry did not own the player's left-click attack");
            helper.assertTrue(Math.abs(player.getHealth() - 7.1F) < 0.001F,
                    "mimicry recovery did not equal two weapon healing plus 5% curio healing: "
                            + player.getHealth());

            player.setHealth(5.0F);
            invulnerableTarget.setInvulnerable(true);
            weapon.onLeftClickEntity(weaponStack, player, invulnerableTarget);
            helper.assertTrue(Math.abs(player.getHealth() - 5.0F) < 0.001F,
                    "mimicry healed even though the target lost no health");
            helper.succeed();
        } finally {
            target.discard();
            invulnerableTarget.discard();
            player.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "review_player_ego_cooldown")
    public static void egoCooldownSurvivesInventoryMoveAndBlocksDrop(GameTestHelper helper) {
        EmployeeServerPlayer player = spawnEmployee(helper, "ego_cooldown_player",
                new BlockPos(2, 1, 2));
        ItemStack weaponStack = new ItemStack(ModItems.NOTHING_THERE_WEAPON.get());
        NothingThereWeapon weapon = (NothingThereWeapon) weaponStack.getItem();

        try {
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, weaponStack);
            player.containerMenu = player.inventoryMenu;
            weapon.startCooldown(player, weaponStack);
            long initialRemaining = weapon.getRemainingCooldown(player, weaponStack);
            helper.assertTrue(initialRemaining > 0,
                    "mimicry fixture did not enter cooldown");
            helper.assertFalse(weapon.onDroppedByPlayer(weaponStack, player),
                    "Q-drop was accepted while the E.G.O skill was cooling down");

            player.inventoryMenu.clicked(36, 0, ClickType.PICKUP, player);
            player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
            ItemStack backpackStack = player.getInventory().getItem(9);
            helper.assertTrue(backpackStack.is(ModItems.NOTHING_THERE_WEAPON.get())
                            && weapon.isOnCooldown(player, backpackStack),
                    "moving the E.G.O weapon into the backpack cleared its cooldown");

            player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
            player.inventoryMenu.clicked(36, 0, ClickType.PICKUP, player);
            ItemStack equippedAgain = player.getInventory().getItem(0);
            long remainingAfterReequip = weapon.getRemainingCooldown(player, equippedAgain);
            helper.assertTrue(equippedAgain.is(ModItems.NOTHING_THERE_WEAPON.get())
                            && remainingAfterReequip > 0
                            && remainingAfterReequip <= initialRemaining,
                    "re-equipping the E.G.O weapon reset or lost its cooldown");
            helper.succeed();
        } finally {
            player.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 60, required = true, batch = "review_exhibition_recovery")
    public static void exhibitionRecoveryClearsAiEmployeeControlStates(GameTestHelper helper) {
        EmployeeRecord record = spawnManagedEmployee(helper, "exhibition_recovery_employee",
                new BlockPos(2, 1, 2), 20);
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
        EntityRedShoes redShoes = spawnRedShoes(helper, new BlockPos(7, 1, 2));

        try {
            helper.assertTrue(employee != null, "managed employee did not become active");
            helper.assertTrue(redShoes.onWorkStart(employee, WorkType.ATTACHMENT),
                    "red shoes rejected the employee's work start");
            redShoes.onWorkComplete(employee, WorkType.ATTACHMENT, WorkResult.GOOD);
            employee.getPersistentData().putInt(RED_SHOES_JUDGEMENT_TIMER, 0);
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(employee));

            helper.assertTrue(EntityRedShoes.isRedShoesControlled(employee),
                    "low-temperance red shoes work did not enter a controlled state");
            helper.assertTrue(PlayerControlLock.isLocked(employee),
                    "red shoes charm did not lock player input");

            employee.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental ->
                    mental.setMentalValue(0.0F));
            MovementLockUtil.acquire(employee, "gametest_freeze");
            MovementLockUtil.acquire(employee, "gametest_execution");
            employee.addEffect(new MobEffectInstance(ModMobEffects.KISS.get(), 200, 2));
            employee.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
            employee.getPersistentData().putBoolean("isSnowQueen", true);
            employee.getPersistentData().putBoolean("isSnowQueenDuel", true);
            employee.getPersistentData().putBoolean("wolf_swallowed", true);
            employee.setCamera(redShoes);
            employee.setTicksFrozen(120);
            employee.setNoGravity(true);
            employee.setShiftKeyDown(true);
            employee.setDeltaMovement(0.4D, 0.2D, -0.3D);
            employee.setHealth(Math.max(1.0F, employee.getMaxHealth() / 2.0F));

            helper.assertTrue(MentalValueUtil.isPanic(employee),
                    "zero mental value did not put the employee into panic");
            helper.assertTrue(MovementLockUtil.isLocked(employee),
                    "movement lock fixture was not applied");
            helper.assertTrue(BuffUtil.hasKiss(employee),
                    "kiss fixture was not applied");

            int recovered = ExhibitionSafetyService.clearAllControlStates(
                    helper.getLevel().getServer());

            helper.assertTrue(recovered >= 1,
                    "exhibition recovery did not include the managed AI employee");
            helper.assertFalse(EntityRedShoes.isRedShoesControlled(employee),
                    "red shoes control survived exhibition recovery");
            helper.assertFalse(PlayerControlLock.isLocked(employee),
                    "player input lock survived exhibition recovery");
            helper.assertFalse(MovementLockUtil.isLocked(employee),
                    "one or more movement locks survived exhibition recovery");
            helper.assertFalse(MentalValueUtil.isPanic(employee),
                    "panic survived exhibition recovery");
            employee.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                helper.assertTrue(mental.getPanicType() == PanicType.NONE,
                        "panic type survived exhibition recovery");
                helper.assertTrue(Math.abs(mental.getMentalValue()
                                - mental.getEffectiveMaxMentalValue()) < 0.001F,
                        "mental value was not fully restored");
            });
            helper.assertFalse(BuffUtil.hasKiss(employee),
                    "kiss survived exhibition recovery");
            helper.assertFalse(employee.hasEffect(MobEffects.BLINDNESS),
                    "a transient control effect survived exhibition recovery");
            helper.assertFalse(employee.getPersistentData().getBoolean("isSnowQueen"),
                    "snow queen frozen marker survived exhibition recovery");
            helper.assertFalse(employee.getPersistentData().getBoolean("isSnowQueenDuel"),
                    "snow queen duel marker survived exhibition recovery");
            helper.assertFalse(employee.getPersistentData().getBoolean("wolf_swallowed"),
                    "swallowed marker survived exhibition recovery");
            helper.assertTrue(employee.getCamera() == employee,
                    "employee camera was not restored after swallowed recovery");
            helper.assertTrue(employee.getTicksFrozen() == 0,
                    "vanilla frozen ticks survived exhibition recovery");
            helper.assertFalse(employee.isNoGravity(),
                    "NoGravity survived exhibition recovery");
            helper.assertFalse(employee.isShiftKeyDown(),
                    "forced crouching survived exhibition recovery");
            helper.assertTrue(employee.getDeltaMovement().equals(Vec3.ZERO),
                    "employee motion survived exhibition recovery");
            helper.assertTrue(Math.abs(employee.getHealth() - employee.getMaxHealth()) < 0.001F,
                    "employee health was not fully restored");
            helper.succeed();
        } finally {
            EntityRedShoes.clearControlForRecovery(employee);
            redShoes.discard();
            EmployeeRuntimeManager.remove(helper.getLevel().getServer(), record);
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "review_login_control_recovery")
    public static void loginClearsTransientControlAndPanicStates(GameTestHelper helper) {
        EmployeeServerPlayer player = spawnEmployee(helper, "login_control_recovery",
                new BlockPos(2, 1, 2));
        player.getPersistentData().putBoolean("lobotocraft_red_shoes_charmed", true);
        player.getPersistentData().putBoolean("isSnowQueen", true);
        player.getPersistentData().putBoolean("isSnowQueenDuel", true);
        player.addEffect(new MobEffectInstance(ModMobEffects.KISS.get(), 200, 2));
        player.setTicksFrozen(120);
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            mental.setPanicType(PanicType.BERSERK);
            mental.setMentalValue(0.0F);
        });

        try {
            helper.assertTrue(EntityRedShoes.isRedShoesControlled(player),
                    "red shoes login fixture was not controlled");
            helper.assertTrue(BuffUtil.hasKiss(player), "kiss login fixture was not applied");
            helper.assertTrue(MentalValueUtil.isPanic(player), "panic login fixture was not applied");

            ForgeModEvent.onPlayerLoggedIn(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));

            helper.assertFalse(EntityRedShoes.isRedShoesControlled(player),
                    "red shoes control survived login recovery");
            helper.assertFalse(BuffUtil.hasKiss(player), "kiss survived login recovery");
            helper.assertFalse(player.getPersistentData().getBoolean("isSnowQueen"),
                    "snow queen marker survived login recovery");
            helper.assertFalse(player.getPersistentData().getBoolean("isSnowQueenDuel"),
                    "snow queen duel marker survived login recovery");
            helper.assertTrue(player.getTicksFrozen() == 0,
                    "vanilla frozen ticks survived login recovery");
            helper.assertFalse(MentalValueUtil.isPanic(player),
                    "panic survived login recovery");
            float partialMental = MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.5F;
            MentalValueUtil.setMentalValue(player, partialMental);
            ForgeModEvent.onPlayerLoggedIn(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(Math.abs(MentalValueUtil.getMentalValue(player) - partialMental) < 0.001F,
                    "non-panicking player restored mental value by reconnecting");
            helper.succeed();
        } finally {
            player.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 70, required = true, batch = "review_green_dawn_death")
    public static void greenDawnFinishesDeathAnimationBeforeDying(GameTestHelper helper) {
        EmployeeServerPlayer player = spawnEmployee(helper, "green_dawn_attacker",
                new BlockPos(2, 1, 2));
        EntityGreenDawn greenDawn = spawnGreenDawn(helper, new BlockPos(3, 1, 2));
        ItemStack weaponStack = new ItemStack(ModItems.NOTHING_THERE_WEAPON.get());
        NothingThereWeapon weapon = (NothingThereWeapon) weaponStack.getItem();

        player.getInventory().selected = 0;
        player.getInventory().setItem(0, weaponStack);
        greenDawn.setHealth(1.0F);
        boolean handled = weapon.onLeftClickEntity(weaponStack, player, greenDawn);

        helper.assertTrue(handled, "mimicry did not handle the player's lethal E.G.O attack");
        helper.assertTrue(greenDawn.isAlive(),
                "green dawn died immediately instead of starting its death animation");
        helper.assertTrue(Math.abs(greenDawn.getHealth() - 1.0F) < 0.001F,
                "green dawn did not hold at one health during its death animation");
        helper.assertTrue(greenDawn.isNoAi(),
                "green dawn AI remained active during its death animation");

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(greenDawn.isAlive() && !greenDawn.isRemoved(),
                    "green dawn disappeared before the 30-tick death animation completed");
            helper.assertTrue(greenDawn.isNoAi(),
                    "green dawn AI resumed during its death animation");
        });

        helper.runAfterDelay(35, () -> {
            try {
                CompoundTag state = new CompoundTag();
                greenDawn.saveWithoutId(state);
                helper.assertFalse(greenDawn.isAlive(),
                        "green dawn remained alive after its death animation completed: tick="
                                + state.getInt("DeathAnimationTick")
                                + ", started=" + state.getBoolean("DeathAnimationStarted")
                                + ", health=" + greenDawn.getHealth());
                helper.succeed();
            } finally {
                greenDawn.discard();
                player.kill();
            }
        });
    }

    private static EmployeeServerPlayer spawnEmployee(GameTestHelper helper, String name,
                                                       BlockPos relative) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        employee.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        employee.getAbilities().invulnerable = false;
        helper.getLevel().addNewPlayer(employee);
        employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(100);
            stats.setPrudence(100);
            stats.setTemperance(100);
            stats.setJustice(100);
        });
        return employee;
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos relative) {
        Zombie zombie = new Zombie(helper.getLevel());
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        zombie.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static EmployeeRecord spawnManagedEmployee(GameTestHelper helper, String name,
                                                       BlockPos relative, int temperance) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        record.setPosition(helper.getLevel().dimension().location(),
                position.x, position.y, position.z);
        record.setStats(100, 100, temperance, 100);
        if (EmployeeRuntimeManager.spawn(helper.getLevel().getServer(), record) == null) {
            throw new IllegalStateException("managed AI employee could not be spawned");
        }
        return record;
    }

    private static EntityRedShoes spawnRedShoes(GameTestHelper helper, BlockPos relative) {
        EntityRedShoes redShoes = ModEntities.red_shoes.get().create(helper.getLevel());
        if (redShoes == null) throw new IllegalStateException("red shoes could not be created");
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        redShoes.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(redShoes);
        return redShoes;
    }

    private static EntityGreenDawn spawnGreenDawn(GameTestHelper helper, BlockPos relative) {
        EntityGreenDawn greenDawn = ModEntities.green_dawn.get().create(helper.getLevel());
        if (greenDawn == null) throw new IllegalStateException("green dawn could not be created");
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        greenDawn.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(greenDawn);
        return greenDawn;
    }

    private static void equipFullMimicry(EmployeeServerPlayer player, ItemStack weapon) {
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, weapon);
        player.setItemSlot(EquipmentSlot.CHEST,
                new ItemStack(ModItems.NOTHING_THERE_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS,
                new ItemStack(ModItems.NOTHING_THERE_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET,
                new ItemStack(ModItems.NOTHING_THERE_BOOTS.get()));
        CuriosApi.getCuriosInventory(player).resolve().ifPresentOrElse(handler ->
                        handler.getCurios().get("headwear").getStacks().setStackInSlot(
                                0, new ItemStack(ModItems.NOTHING_THERE_CURIO.get())),
                () -> {
                    throw new IllegalStateException("mimicry test player has no Curios inventory");
                });
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(data -> data.lockArmor());
    }
}
