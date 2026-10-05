package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.item.ego.bigbadwolf.BigBadwolfWeapon;
import com.wzz.lobotocraft.item.ego.smiling_corpse_mountain.SmilingCorpseMountainWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;

import java.util.*;
import java.util.function.Consumer;
import static com.wzz.lobotocraft.entity.abnormality.OvertimeEquipmentGameTests.*;

@GameTestHolder("lobotocraft_overtime_combat")
@PrefixGameTestTemplate(false)
public final class OvertimeCombatRootGameTests {
    private static final String T = "workcloseregressiongametests.navigation_empty";
    private static final String NS = "lobotocraft_overtime_combat";

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 70, batch = "corpse_front_skill")
    public static void corpseSkillHitsFrontAfterMovingWithCrosshairAboveTarget(GameTestHelper h) {
        var p = player(h, h.getLevel(), 2);
        var target = spawn(h, EntityType.ZOMBIE, 7);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        var weapon = (SmilingCorpseMountainWeapon) ModItems.SMILING_CORPSE_MOUNTAIN_WEAPON.get();
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        p.moveTo(p.position().add(1, 0, 0)); p.setYRot(-90); p.setXRot(-85);
        float before = target.getHealth();
        weapon.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.runAfterDelay(32, () -> {
            try {
                h.assertTrue(target.getHealth() < before, "front target missed because crosshair aimed over it");
                h.succeed();
            } finally { TimerEntry.shutdown(target); TimerEntry.shutdown(p); p.discard(); target.discard(); }
        });
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "skadi_beam_audio")
    public static void skadiBeamImpactSoundReachesItsActualDistantImpact(GameTestHelper h) throws Exception {
        var boss = spawn(h, ModEntities.isharmla.get(), 3);
        var target = player(h, h.getLevel(), 43);
        var sounds = new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
            if (e.getLevel() == h.getLevel()
                    && e.getSound() != null
                    && e.getSound().value() == net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM) sounds.add(e);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            set(boss, "anchorReactorPos", boss.blockPosition().offset(20, 0, 0));
            invoke(boss, "startPeriodicBeamSkill", new Class[]{ServerLevel.class}, h.getLevel());
            boss.tickCount += 13;
            invoke(boss, "tickBeamAttack", new Class[]{ServerLevel.class}, h.getLevel());
            h.assertTrue(!sounds.isEmpty(), "beam impact emitted no sonic sound");
            h.assertTrue(sounds.stream().anyMatch(e ->
                    e.getPosition().distanceTo(target.position()) < e.getSound().value().getRange(e.getNewVolume())),
                    "sound emitted only at boss outside the actual impact listener radius");
            h.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); boss.discard(); target.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "thorn_five_attacks")
    public static void thornSetBlocksAllFiveProductionRangedEntrances(GameTestHelper h) throws Exception {
        var wearer = player(h, h.getLevel(), 9);
        equip(wearer, ModItems.THORN_BUS_CHESTPLATE.get(), ModItems.THORN_BUS_LEGGINGS.get(),
                ModItems.THORN_BUS_BOOTS.get(), ModItems.THORN_BUS_WEAPON.get(), ModItems.THORN_BUS_CURIO.get());
        var robot = spawn(h, ModEntities.green_dawn.get(), 3);
        var funeral = spawn(h, ModEntities.butterfly_funeral.get(), 3);
        var hood = spawn(h, ModEntities.redhat_mercenary.get(), 3);
        var boss = spawn(h, ModEntities.isharmla.get(), 3);
        var end = spawn(h, ModEntities.end_bird.get(), 3);
        try {
            float health = wearer.getHealth(), mental = MentalValueUtil.getMentalValue(wearer);
            invoke(robot, "hurtSpecialTargets", new Class[0]);
            assertUnchanged(h, wearer, health, mental, "green dawn special");
            funeral.setEscape(true); set(funeral, "skillPhase", 2); set(funeral, "skillTimer", 260);
            set(funeral, "skillDirection", new Vec3(1, 0, 0));
            invoke(funeral, "damageButterflyCone", new Class[]{ServerLevel.class}, h.getLevel());
            assertUnchanged(h, wearer, health, mental, "funeral cone");
            hood.setEscape(true); hood.setYRot(-90);
            invoke(hood, "gunShot", new Class[]{ServerLevel.class, double.class}, h.getLevel(), 15D);
            assertUnchanged(h, wearer, health, mental, "redhood gun");
            invoke(boss, "startPeriodicBeamSkill", new Class[]{ServerLevel.class}, h.getLevel());
            boss.tickCount += 13;
            invoke(boss, "tickBeamAttack", new Class[]{ServerLevel.class}, h.getLevel());
            assertUnchanged(h, wearer, health, mental, "Skadi wave including direct mental deduction");
            var orb = ModEntities.light_orb.get().create(h.getLevel());
            orb.setOwner(end); orb.setTarget(wearer); orb.setDamage(10);
            orb.setPos(wearer.position().add(0, wearer.getBbHeight() / 2D, 0));
            h.getLevel().addFreshEntity(orb); orb.tick();
            assertUnchanged(h, wearer, health, mental, "end bird orb");
            h.assertTrue(!orb.isRemoved() && orb.getOwner() == wearer,
                    "end bird orb was cancelled/discarded instead of reflected");
            orb.discard();
            h.succeed();
        } finally {
            wearer.discard(); robot.discard(); funeral.discard(); hood.discard(); boss.discard(); end.discard();
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 40, batch = "thorn_arrow")
    public static void thornSetReflectsVanillaArrowWithoutChangingNormalMelee(GameTestHelper h) {
        var wearer = player(h, h.getLevel(), 9);
        equip(wearer, ModItems.THORN_BUS_CHESTPLATE.get(), ModItems.THORN_BUS_LEGGINGS.get(),
                ModItems.THORN_BUS_BOOTS.get(), ModItems.THORN_BUS_WEAPON.get(), ModItems.THORN_BUS_CURIO.get());
        var shooter = spawn(h, EntityType.ZOMBIE, 3);
        var arrow = new Arrow(h.getLevel(), shooter);
        arrow.setPos(wearer.position().add(-.2, 1, 0)); arrow.setDeltaMovement(1, 0, 0);
        h.getLevel().addFreshEntity(arrow);
        try {
            var impact = new ProjectileImpactEvent(arrow, new EntityHitResult(wearer));
            MinecraftForge.EVENT_BUS.post(impact);
            h.assertTrue(arrow.getOwner() == wearer && arrow.getDeltaMovement().x < 0 && !arrow.isRemoved(),
                    "arrow was not reflected with new owner and reverse velocity");
            float before = wearer.getHealth();
            wearer.hurt(DamageHelper.getDamage(shooter, "red"), 3);
            h.assertTrue(wearer.getHealth() < before, "ordinary melee incorrectly gained ranged protection");
            h.succeed();
        } finally { arrow.discard(); wearer.discard(); shooter.discard(); }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 70, batch = "wolf_delayed_intent")
    public static void lowHealthWolfDelayedSkillKeepsIntentAfterHealAndWeaponSwitchWithPvpOff(GameTestHelper h) {
        var attacker = player(h, h.getLevel(), 3);
        var target = player(h, h.getLevel(), 5);
        boolean pvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(false);
        var weapon = (BigBadwolfWeapon) ModItems.BIG_BADWOLF_WEAPON.get();
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        attacker.setHealth(attacker.getMaxHealth() * .4F); attacker.setYRot(-90);
        float before = target.getHealth();
        int[] hits = {0};
        Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> recorder = event -> {
            if (event.getEntity() == target && event.getSource().getEntity() == attacker
                    && !event.isCanceled() && event.getAmount() > 0) hits[0]++;
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                net.minecraftforge.event.entity.living.LivingDamageEvent.class, recorder);
        weapon.use(h.getLevel(), attacker, InteractionHand.MAIN_HAND);
        attacker.setHealth(attacker.getMaxHealth());
        attacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        h.runAfterDelay(55, () -> {
            try {
                h.assertTrue(target.getHealth() < before, "delayed low-health attack lost intent or PvP rejected it");
                h.assertTrue(hits[0] == 2, "delayed skill skipped or repeated a hit: " + hits[0]);
                EntityUtil.clearHurtTime(target);
                float health = target.getHealth();
                target.hurt(DamageHelper.getDamage(attacker, "red"), 10);
                h.assertTrue(target.getHealth() == health, "later ordinary source inherited indiscriminate permission");
                h.succeed();
            } finally {
                h.getLevel().getServer().setPvpAllowed(pvp);
                MinecraftForge.EVENT_BUS.unregister(recorder);
                TimerEntry.shutdown(target); attacker.discard(); target.discard();
            }
        });
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30, batch = "intent_direct_negative")
    public static void directWolfKnifeAndGunOnlyAllowBelowHalfHealthEvenWithPvpOff(GameTestHelper h) throws Exception {
        var p = player(h, h.getLevel(), 3);
        var target = player(h, h.getLevel(), 5);
        boolean pvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(false);
        try {
            var weaponItems = List.of(ModItems.BIG_BADWOLF_WEAPON.get(),
                    ModItems.REDHAT_MERCENARY_WEAPON_KNIFE.get(), ModItems.REDHAT_MERCENARY_WEAPON_GUN.get());
            for (var item : weaponItems) {
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
                p.setYRot(-90); p.setXRot(0);
                for (float ratio : new float[]{.5F, .4F}) {
                    p.setHealth(p.getMaxHealth() * ratio);
                    target.setHealth(target.getMaxHealth()); EntityUtil.clearHurtTime(target);
                    float hp = target.getHealth();
                    if (item == ModItems.REDHAT_MERCENARY_WEAPON_GUN.get())
                        invoke(item, "fire", new Class[]{net.minecraft.world.level.Level.class,
                                net.minecraft.world.entity.player.Player.class}, h.getLevel(), p);
                    else ((com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon)item)
                            .onLeftClickEntity(p.getMainHandItem(), p, target);
                    h.assertTrue((target.getHealth() < hp) == (ratio < .5F),
                            item + " did not respect strict low-health intent ratio=" + ratio);
                    if (item == ModItems.REDHAT_MERCENARY_WEAPON_GUN.get())
                        h.assertTrue(com.wzz.lobotocraft.item.ego.redhat_mercenary.RedhatMercenaryWeaponGun
                                .Prey.isPreyOf(p, target) == (ratio < .5F),
                                "rejected gun hit applied prey or successful gun hit lost it");
                    DotHelper.clearDots(target);
                    com.wzz.lobotocraft.item.ego.redhat_mercenary.RedhatMercenaryWeaponGun.Prey.release(target);
                    TimerEntry.shutdown(target);
                }
            }
            p.setHealth(p.getMaxHealth()); p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            EntityUtil.clearHurtTime(target);
            float hp = target.getHealth();
            target.hurt(DamageHelper.getDamage(p, "red"), 10);
            h.assertTrue(target.getHealth() == hp, "ordinary attack gained permanent permission");
            h.succeed();
        } finally {
            h.getLevel().getServer().setPvpAllowed(pvp); DotHelper.clearDots(target);
            TimerEntry.shutdown(target); p.discard(); target.discard();
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 30, batch = "intent_dot_save")
    public static void wolfDotKeepsOriginIntentAcrossSaveButOrdinaryRefreshRemovesIt(GameTestHelper h) {
        var p = player(h, h.getLevel(), 3);
        var target = player(h, h.getLevel(), 5);
        var weapon = (BigBadwolfWeapon) ModItems.BIG_BADWOLF_WEAPON.get();
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        p.setHealth(p.getMaxHealth() * .4F); p.setYRot(-90);
        boolean pvp = h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(false);
        try {
            weapon.onLeftClickEntity(p.getMainHandItem(), p, target);
            h.assertTrue(DotHelper.getStacks(target, "big_badwolf") == 1, "successful hit did not attach DOT");
            net.minecraft.nbt.CompoundTag save = new net.minecraft.nbt.CompoundTag();
            target.saveWithoutId(save);
            target.load(save);
            p.setHealth(p.getMaxHealth()); p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            float hp = target.getHealth();
            for (int i = 0; i < 20; i++)
                DotHelper.onLivingTick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(target));
            h.assertTrue(target.getHealth() < hp, "saved DOT lost original attack intent after heal/switch");
            DotHelper.applyDot("big_badwolf", p, target, 3, "red", 20, 100, 1);
            hp = target.getHealth();
            for (int i = 0; i < 20; i++)
                DotHelper.onLivingTick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(target));
            h.assertTrue(target.getHealth() == hp, "normal refresh inherited previous indiscriminate DOT flag");
            h.succeed();
        } finally {
            h.getLevel().getServer().setPvpAllowed(pvp);
            DotHelper.clearDots(target); p.discard(); target.discard();
        }
    }

    @GameTest(template = T, templateNamespace = NS, timeoutTicks = 70, batch = "corpse_reject_geometry")
    public static void corpseSkillDoesNotSelectBehindWallOrAnotherFloor(GameTestHelper h) {
        var p = player(h, h.getLevel(), 3);
        var behind = spawn(h, EntityType.ZOMBIE, 1);
        var front = spawn(h, EntityType.ZOMBIE, 6);
        var above = spawn(h, EntityType.ZOMBIE, 6); above.moveTo(above.position().add(0, 5, 0));
        var weapon = (SmilingCorpseMountainWeapon) ModItems.SMILING_CORPSE_MOUNTAIN_WEAPON.get();
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        p.setYRot(-90); p.setXRot(-85);
        for (int z = 0; z < 12; z++) for (int y = 1; y <= 5; y++)
            h.setBlock(new BlockPos(5, y, z), Blocks.STONE);
        float f = front.getHealth(), b = behind.getHealth(), a = above.getHealth();
        weapon.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.runAfterDelay(32, () -> {
            try {
                h.assertTrue(front.getHealth() == f && behind.getHealth() == b && above.getHealth() == a,
                        "ground slam selected a blocked, rear, or other-floor target");
                h.succeed();
            } finally {
                TimerEntry.shutdown(p); p.discard(); front.discard(); behind.discard(); above.discard();
            }
        });
    }

    private static void assertUnchanged(GameTestHelper h, LivingEntity e, float hp, float sp, String attack) {
        h.assertTrue(e.getHealth() == hp && MentalValueUtil.getMentalValue((net.minecraft.world.entity.player.Player)e) == sp,
                attack + " penetrated set: HP=" + e.getHealth() + ", SP="
                        + MentalValueUtil.getMentalValue((net.minecraft.world.entity.player.Player)e));
    }

    private static void set(Object obj, String name, Object value) throws Exception {
        var f = obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj, value);
    }
    private static Object invoke(Object obj, String name, Class<?>[] args, Object... values) throws Exception {
        var m = obj.getClass().getDeclaredMethod(name, args); m.setAccessible(true); return m.invoke(obj, values);
    }
}
