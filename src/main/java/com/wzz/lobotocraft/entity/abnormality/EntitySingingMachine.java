package com.wzz.lobotocraft.entity.abnormality;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.control.SingingMachineControl;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.data.RoomEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

public class EntitySingingMachine extends AbstractAbnormality {
    private static final EntityDataAccessor<String> ACTION =
            SynchedEntityData.defineId(EntitySingingMachine.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> GORE =
            SynchedEntityData.defineId(EntitySingingMachine.class, EntityDataSerializers.INT);

    private static final int SONG_TICKS = SingingMachinePolicy.SONG_TICKS;
    private static final int OPENING_TICKS = 40;
    private static final int CLOSING_TICKS = 62;

    private int songTicks;
    private int closingTicks;
    private boolean corpseSong;
    private int lastFacilityDaySeen = -1;

    public EntitySingingMachine(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            lastFacilityDaySeen = FacilityDayData.get(serverLevel).getDay();
        }
    }

    @Override
    protected void initializeAbnormality() {
        abnormalityCode = "O-05-30";
        abnormalityName = "歌唱机";
        riskLevel = RiskLevel.HE;
        damageType = "RED";
        maxPEOutput = 18;
        initializeWorkPreferences(new float[]{0.45F, 0.45F, 0.50F, 0.35F});
        initializeQliphothCounter(1);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, "idle");
        entityData.define(GORE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (level() instanceof ServerLevel serverLevel) {
            int facilityDay = FacilityDayData.get(serverLevel).getDay();
            if (lastFacilityDaySeen >= 0 && lastFacilityDaySeen != facilityDay) {
                entityData.set(GORE, 0);
            }
            lastFacilityDaySeen = facilityDay;
        }
        setNoAi(true);
        setDeltaMovement(0.0D, 0.0D, 0.0D);

        if (songTicks > 0) {
            songTicks--;
            if (songTicks == SONG_TICKS - OPENING_TICKS) {
                setAction(corpseSong ? "corpse_loop" : "song_loop");
            }
            if (tickCount % 16 == 0) {
                level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_HARP.get(),
                        SoundSource.HOSTILE, 1.2F, 0.45F + random.nextFloat() * 0.35F);
            }
            if (songTicks == 0) finishSong();
            return;
        }
        if (closingTicks > 0 && --closingTicks == 0) setAction("idle");
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        if (getQliphothCounter() <= 0) {
            setQliphothCounter(getMaxQliphothCounter());
            consumeWorker(player);
            return false;
        }
        return super.onWorkStart(player, workType);
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        decreaseQliphothCounter(1);
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (!player.isAlive() || songTicks > 0) return;
        int[] levels = {1, 1};
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            levels[0] = stats.getFortitudeLevel();
            levels[1] = stats.getTemperanceLevel();
        });
        if (SingingMachinePolicy.consumesWorkerAfterWork(levels[0], levels[1])) {
            consumeWorker(player);
        }
    }

    private void consumeWorker(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;
        WorkManager.cancelWork(player, "被歌唱机吞入");
        startSong(false);
        player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.abnormality.singing_machine.consumed"));
        player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    public void acceptCorpseDelivery() {
        if (songTicks > 0) return;
        startSong(true);
    }

    private void startSong(boolean corpse) {
        corpseSong = corpse;
        songTicks = SONG_TICKS;
        closingTicks = 0;
        entityData.set(GORE, Math.min(3, entityData.get(GORE) + 1));
        setAction(corpse ? "corpse_open" : "song_open");
        level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND,
                SoundSource.HOSTILE, 1.3F, 0.55F);
        level().playSound(null, blockPosition(), SoundEvents.GRINDSTONE_USE,
                SoundSource.HOSTILE, 1.4F, 0.65F);
    }

    private void finishSong() {
        LivingEntity target = chooseAttractedWorker();
        if (target != null) SingingMachineControl.start(target, this);
        if (corpseSong) {
            setAction("corpse_close");
            closingTicks = CLOSING_TICKS;
        } else {
            setAction("idle");
        }
        corpseSong = false;
    }

    private LivingEntity chooseAttractedWorker() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        String department = homeDepartment(serverLevel);
        List<LivingEntity> localClerks = new ArrayList<>();
        List<LivingEntity> localEmployees = new ArrayList<>();
        List<LivingEntity> allClerks = new ArrayList<>();
        List<LivingEntity> allEmployees = new ArrayList<>();

        for (Entity entity : serverLevel.getAllEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || !SingingMachineControl.isEligibleWorker(living)) continue;
            boolean clerk = living instanceof EntityClerk;
            boolean local = !department.isBlank()
                    && department.equals(departmentAt(serverLevel, living));
            if (clerk) {
                allClerks.add(living);
                if (local) localClerks.add(living);
            } else {
                allEmployees.add(living);
                if (local) localEmployees.add(living);
            }
        }
        return randomFrom(localClerks, localEmployees, allClerks, allEmployees);
    }

    @SafeVarargs
    private final LivingEntity randomFrom(List<LivingEntity>... groups) {
        for (List<LivingEntity> group : groups) {
            if (!group.isEmpty()) return group.get(random.nextInt(group.size()));
        }
        return null;
    }

    private String homeDepartment(ServerLevel level) {
        if (getHomeRoomDimension() == null || getHomeRoomMarker() == null) return "";
        RoomEntry room = CompanyRoomData.get(level).findRoom(
                getHomeRoomDimension(), getHomeRoomMarker());
        return room == null || room.departmentId == null ? "" : room.departmentId;
    }

    private static String departmentAt(ServerLevel level, LivingEntity entity) {
        return FacilityWorldLayout.facilityAreaAt(level, entity.blockPosition())
                .map(area -> area.departmentId).orElse("");
    }

    private void setAction(String action) {
        entityData.set(ACTION, action);
    }

    public String getAction() {
        return entityData.get(ACTION);
    }

    @Override
    public String name() {
        return "singing_machine";
    }

    @Override
    public String getTexture() {
        return switch (entityData.get(GORE)) {
            case 1 -> "singing_machine_2";
            case 2 -> "singing_machine_3";
            case 3 -> "singing_machine_4";
            default -> "singing_machine";
        };
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public List<String> getWorkLogs() {
        return List.of(
                "在你面前的可能只是一台普通的绞肉机，但异想体一定有其存在的目的。",
                "它会使人产生一种强烈的想要跳入其中的冲动。",
                "它奏出的旋律恐怖却又美妙。",
                "献上血和肉，谱写协奏曲。"
        );
    }

    @Override
    public int getGoodWorkResultMin() {
        return 13;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 7;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(com.wzz.lobotocraft.util.DamageHelper.getDamage(this, "lobotocraft:red"),
                4.0F + random.nextInt(3));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 0, this::animation));
    }

    private PlayState animation(AnimationState<EntitySingingMachine> state) {
        return switch (getAction()) {
            case "song_open" -> state.setAndContinue(RawAnimation.begin().thenPlay("attack1-1"));
            case "song_loop" -> state.setAndContinue(RawAnimation.begin().thenLoop("attack1-2"));
            case "corpse_open" -> state.setAndContinue(RawAnimation.begin().thenPlay("attack2-1"));
            case "corpse_loop" -> state.setAndContinue(RawAnimation.begin().thenLoop("attack2-2"));
            case "corpse_close" -> state.setAndContinue(RawAnimation.begin().thenPlay("attack2-3"));
            default -> state.setAndContinue(RawAnimation.begin().thenLoop("idle2"));
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SingingMachineAction", getAction());
        tag.putInt("SingingMachineGore", entityData.get(GORE));
        tag.putInt("SingingMachineSongTicks", songTicks);
        tag.putInt("SingingMachineClosingTicks", closingTicks);
        tag.putBoolean("SingingMachineCorpseSong", corpseSong);
        tag.putInt("SingingMachineLastFacilityDay", lastFacilityDaySeen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAction(tag.getString("SingingMachineAction").isBlank()
                ? "idle" : tag.getString("SingingMachineAction"));
        entityData.set(GORE, Math.max(0, Math.min(3, tag.getInt("SingingMachineGore"))));
        songTicks = Math.max(0, tag.getInt("SingingMachineSongTicks"));
        closingTicks = Math.max(0, tag.getInt("SingingMachineClosingTicks"));
        corpseSong = tag.getBoolean("SingingMachineCorpseSong");
        if (tag.contains("SingingMachineLastFacilityDay")) {
            lastFacilityDaySeen = tag.getInt("SingingMachineLastFacilityDay");
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
