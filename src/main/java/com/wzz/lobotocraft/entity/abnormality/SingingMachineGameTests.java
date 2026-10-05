package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.control.HostileControlState;
import com.wzz.lobotocraft.entity.control.SingingMachineControl;
import com.wzz.lobotocraft.init.ModEntities;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.UUID;

@GameTestHolder(SingingMachineGameTests.TEST_NAMESPACE)
public final class SingingMachineGameTests {
    public static final String TEST_NAMESPACE = ModMain.MODID + "_singing_machine";

    private SingingMachineGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 100, required = true, batch = "singing_machine_glow")
    public static void controlledClerkGlowsWhileControlledAndExpiresAfterRecovery(GameTestHelper helper) {
        for (int x=1;x<=14;x++) for (int z=1;z<=10;z++)
            helper.setBlock(new BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.STONE);
        var machine=ModEntities.singing_machine.get().create(helper.getLevel());
        var clerk=ModEntities.clerk.get().create(helper.getLevel());
        if(machine==null||clerk==null)throw new IllegalStateException("missing glow fixture");
        var pos=helper.absolutePos(new BlockPos(4,1,4));
        machine.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        clerk.moveTo(pos.getX()+3.5,pos.getY(),pos.getZ()+0.5,0,0);
        helper.getLevel().addFreshEntity(machine);helper.getLevel().addFreshEntity(clerk);
        clerk.setInvulnerable(true);
        try {
            helper.assertTrue(SingingMachineControl.start(clerk,machine),"clerk control rejected");
            helper.assertTrue(clerk.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                    "controlled clerk has no glow at activation");
        } catch(RuntimeException failure) {
            SingingMachineControl.clearForRecovery(clerk);clerk.discard();machine.discard();throw failure;
        }
        helper.runAfterDelay(45,()->{
            helper.assertTrue(clerk.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                    "glow was not refreshed during control");
            SingingMachineControl.clearForRecovery(clerk);
            clerk.setNoAi(true);
        });
        helper.runAfterDelay(90,()->{
            try {
                helper.assertFalse(clerk.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                        "control glow leaked permanently after recovery");
                helper.succeed();
            }finally{SingingMachineControl.clearForRecovery(clerk);clerk.discard();machine.discard();}
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "singing_machine_glow_preservation")
    public static void glowFixPreservesOtherGlowAndDoesNotMarkPlayers(GameTestHelper helper) {
        var machine=ModEntities.singing_machine.get().create(helper.getLevel());
        var clerk=ModEntities.clerk.get().create(helper.getLevel());
        if(machine==null||clerk==null)throw new IllegalStateException("missing fixture");
        var pos=helper.absolutePos(new BlockPos(4,1,4));
        machine.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        clerk.moveTo(pos.getX()+3.5,pos.getY(),pos.getZ()+0.5,0,0);
        helper.getLevel().addFreshEntity(machine);helper.getLevel().addFreshEntity(clerk);
        var player=employee(helper,"singing_glow_player",new BlockPos(8,1,4),50,50);
        clerk.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING,200,0,false,false));
        try {
            SingingMachineControl.start(clerk,machine);
            SingingMachineControl.start(player,machine);
            helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                    "clerk-only fix also marks a player/AI employee");
            SingingMachineControl.clearForRecovery(clerk);
            helper.assertTrue(clerk.getEffect(net.minecraft.world.effect.MobEffects.GLOWING).getDuration()==200,
                    "control shortens or clears an unrelated glow effect");
            helper.succeed();
        }finally{
            SingingMachineControl.clearForRecovery(clerk);SingingMachineControl.clearForRecovery(player);
            clerk.discard();machine.discard();player.kill();
        }
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 150, required = true, batch = "singing_machine_cycle")
    public static void consumedWorkerStartsSongAndCreatesControlledCombatant(
            GameTestHelper helper) {
        EntitySingingMachine machine = ModEntities.singing_machine.get().create(helper.getLevel());
        if (machine == null) {
            helper.fail("singing machine could not be created");
            return;
        }
        Vec3 machinePos = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(3, 1, 3)));
        machine.absMoveTo(machinePos.x, machinePos.y, machinePos.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(machine);

        EmployeeServerPlayer sacrifice = employee(helper, "singing_sacrifice",
                new BlockPos(4, 1, 3), 100, 100);
        EmployeeServerPlayer listener = employee(helper, "singing_listener",
                new BlockPos(7, 1, 3), 50, 50);
        float listenerBaseHealth = listener.getMaxHealth();

        machine.onWorkComplete(sacrifice, WorkType.INSTINCT, WorkResult.GOOD);
        helper.assertTrue(!sacrifice.isAlive(), "high-fortitude worker survived consumption");
        helper.assertTrue("song_open".equals(machine.getAction()),
                "consumption did not start the opening animation");

        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(HostileControlState.isActive(listener),
                        "song did not create a facility-visible controlled combatant");
                helper.assertTrue(listener.getMaxHealth() >= listenerBaseHealth * 1.9F,
                        "controlled combatant did not receive the original health increase");
                helper.succeed();
            } finally {
                HostileControlState.clearForRecovery(listener);
                listener.kill();
                sacrifice.kill();
                machine.discard();
            }
        });
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "singing_machine_day_reset")
    public static void dayChangeClearsMachineGore(GameTestHelper helper) {
        EntitySingingMachine machine = ModEntities.singing_machine.get().create(helper.getLevel());
        if (machine == null) {
            helper.fail("singing machine could not be created");
            return;
        }
        machine.absMoveTo(helper.absolutePos(new BlockPos(3, 1, 3)).getCenter().x,
                helper.absolutePos(new BlockPos(3, 1, 3)).getY(),
                helper.absolutePos(new BlockPos(3, 1, 3)).getCenter().z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(machine);
        machine.acceptCorpseDelivery();
        helper.assertTrue("singing_machine_2".equals(machine.getTexture()),
                "song did not advance the gore texture");

        FacilityDayData day = FacilityDayData.get(helper.getLevel());
        machine.tick();
        day.beginDay(day.getDay() + 1);
        machine.tick();
        helper.assertTrue("singing_machine".equals(machine.getTexture()),
                "day change did not clear the singing machine gore texture");
        machine.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 220, required = true, batch = "singing_machine_runtime_control")
    public static void runtimeEmployeeAttacksAndReturnsToMachine(GameTestHelper helper) {
        EntitySingingMachine machine = ModEntities.singing_machine.get().create(helper.getLevel());
        if (machine == null) {
            helper.fail("singing machine could not be created");
            return;
        }
        Vec3 machinePos = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(3, 1, 3)));
        machine.absMoveTo(machinePos.x, machinePos.y, machinePos.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(machine);

        EmployeeRecord attackerRecord = runtimeEmployee(helper, "singing_runtime_attacker",
                new BlockPos(4, 1, 3), 100);
        EmployeeRecord victimRecord = runtimeEmployee(helper, "singing_runtime_victim",
                new BlockPos(5, 1, 3), 10);
        EmployeeServerPlayer attacker = EmployeeRuntimeManager.activePlayer(attackerRecord.id());
        EmployeeServerPlayer victim = EmployeeRuntimeManager.activePlayer(victimRecord.id());
        if (attacker == null || victim == null) {
            cleanupRuntime(machine, attackerRecord, victimRecord);
            helper.fail("runtime employees could not be spawned");
            return;
        }
        victim.setHealth(1.0F);
        if (!SingingMachineControl.start(attacker, machine)) {
            cleanupRuntime(machine, attackerRecord, victimRecord);
            helper.fail("singing machine control refused the runtime employee");
            return;
        }

        helper.runAfterDelay(180, () -> {
            try {
                helper.assertTrue(!victim.isAlive() || victim.isRemoved(),
                        "controlled runtime employee did not kill its target");
                helper.assertTrue("corpse_open".equals(machine.getAction())
                                || "corpse_loop".equals(machine.getAction()),
                        "controlled runtime employee did not return to the machine");
                helper.succeed();
            } finally {
                cleanupRuntime(machine, attackerRecord, victimRecord);
            }
        });
    }

    private static EmployeeRecord runtimeEmployee(GameTestHelper helper, String name,
                                                  BlockPos relative, int fortitude) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        record.setStats(fortitude, 50, 50, 50);
        record.setPosition(helper.getLevel().dimension().location(),
                position.x, position.y, position.z);
        record.setDeployed(true);
        EmployeeRuntimeManager.spawn(helper.getLevel().getServer(), record);
        EmployeeServerPlayer player = EmployeeRuntimeManager.activePlayer(record.id());
        if (player != null) player.getAbilities().invulnerable = false;
        return record;
    }

    private static void cleanupRuntime(EntitySingingMachine machine,
                                       EmployeeRecord... records) {
        for (EmployeeRecord record : records) {
            EmployeeServerPlayer player = EmployeeRuntimeManager.activePlayer(record.id());
            if (player != null) HostileControlState.clearForRecovery(player);
            EmployeeRuntimeManager.remove(machine.level().getServer(), record);
        }
        machine.discard();
    }

    private static EmployeeServerPlayer employee(GameTestHelper helper, String name,
                                                  BlockPos relative, int fortitude,
                                                  int temperance) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(record.id(), record.name()), record);
        Vec3 position = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        player.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.getAbilities().invulnerable = false;
        helper.getLevel().addNewPlayer(player);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(fortitude);
            stats.setPrudence(50);
            stats.setTemperance(temperance);
            stats.setJustice(50);
        });
        return player;
    }
}
