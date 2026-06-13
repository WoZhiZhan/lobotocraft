package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class EntitySnowQueen extends AbstractAbnormality {
    public EntitySnowQueen(EntityType<? extends TamableAnimal> p_21803_, Level p_21804_) {
        super(p_21803_, p_21804_);
    }

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "F-01-37";
        this.abnormalityName = "冰雪女皇";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "WHITE";
        this.maxPEOutput = 18;

        // 工作偏好（基础成功率）
        float[] basePreferences = {0.3f, 0.5f, 0.4f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 4.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:white"), damage);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.1f, 0.1f, 0.2f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.1f, 0.1f, 0.2f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.1f, 0.1f, 0.2f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return levelModifiers;
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/snowqueen_curio.png"),
                "翅振",
                "手套",
                "snowqueen_curio",
                "成功率+2",
                "工作速度+2"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/snowqueen_weapon.png"),
                "翅振",
                getRiskLevel(),
                "RED",           // 伤害类型
                "5-7",             // 攻击力
                "2s",            // 攻击速度
                "近",              // 攻击距离
                getWeaponDevelopmentMaxCount(),                  // 研发总数
                "snowqueen_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/snowqueen_armor.png"),
                "翅振",
                getRiskLevel(),
                0.8f,    // RED抗性
                0.8f,    // WHITE抗性
                1.0f,    // BLACK抗性
                2.0f,    // PALE抗性
                getArmorDevelopmentMaxCount(),        // 研发总数
                "snowqueen"
        );
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        for (Player p : EntityUtil.findPlayersAround(player, 4, 8)) {
            if (com.wzz.lobotocraft.util.BuffUtil.hasKiss(p) && p.getPersistentData().getBoolean("isSnowQueen")) {
                player.displayClientMessage(Component.literal("你将要和冰雪女皇决斗！"), false);
                player.getPersistentData().putBoolean("isSnowQueenDuel", true);
                SoundUtil.playSound(player.level, player, ModSounds.SNOW_QUEEN_DUEL.get());
                return true;
            }
        }
        if (com.wzz.lobotocraft.util.BuffUtil.hasKiss(player) && player.getPersistentData().getBoolean("isSnowQueen")) {
            player.displayClientMessage(Component.literal("你将要和冰雪女皇决斗！"), false);
            player.getPersistentData().putBoolean("isSnowQueenDuel", true);
            SoundUtil.playSound(player.level, player, ModSounds.SNOW_QUEEN_DUEL.get());
            return true;
        }
        return super.onWorkStart(player, workType);
    }

    @Override
    public Float modifyWorkSuccessRate(ServerPlayer player, WorkType workType, float baseRate) {
        AtomicReference<Float> f = new AtomicReference<>(baseRate);
        if (player.getPersistentData().getBoolean("isSnowQueenDuel")) {
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> {
                f.set(data.getJusticeLevel() * 0.2f);
            });
        }
        return f.get();
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 15;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 10;
    }

    @Override
    public int getBasicInfoCost() {
        return 16;  // 基础信息
    }

    @Override
    public int getWorkPreferencesCost() {
        return 5;  // 工作偏好
    }

    @Override
    public int getSensitiveInfoCost() {
        return 16;  // 敏感信息
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 4;  // 每个管理须知
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        if (this.random.nextInt(2) == 0) {
            SnowQueenTimerEntry.add(player, this);
        }
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AbstractAbnormality.DATA_ANIMATION, "animation.snowqueen.idle");
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (player.getPersistentData().getBoolean("isSnowQueenDuel")) {
            if (result != WorkResult.BAD) {
                player.getPersistentData().remove("isSnowQueenDuel");
                player.displayClientMessage(Component.literal("§a你在与冰雪女皇的决斗中获得了胜利！"), false);
                SoundUtil.playSound(player.level, player, ModSounds.SNOW_QUEEN_DUEL_SUCCESS.get());
                for (Player p : EntityUtil.findPlayersAround(player, 4, 8)) {
                    if (p.getPersistentData().getBoolean("isSnowQueen")) {
                        p.getPersistentData().remove("isSnowQueen");
                        p.displayClientMessage(Component.literal("§a你的队友在与冰雪女皇的决斗中获得了胜利！"), false);
                        p.setTicksFrozen(0);
                        SoundUtil.playSound(p.level, p, ModSounds.SNOW_QUEEN_DUEL_SUCCESS.get());
                        ItemUtil.addItem(p, new ItemStack(Items.DIAMOND));
                        break;
                    }
                }
                ItemUtil.addItem(player, new ItemStack(Items.DIAMOND));
            } else {
                player.displayClientMessage(Component.literal("§c很遗憾，你在与冰雪女皇的决斗中失败了"), false);
                for (Player p : EntityUtil.findPlayersAround(player, 4, 8)) {
                    if (p.getPersistentData().getBoolean("isSnowQueen")) {
                        killPlayer(p);
                        break;
                    }
                }
                killPlayer(player);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;
        if (this.tickCount % 40 == 0) {
            ParticleUtil.spawnAreaParticles(this, ParticleTypes.SNOWFLAKE, 100, 10, 6);
            int radius = 7;
            int baseY = this.blockPosition().getY() - 1;
            BlockPos center = this.blockPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (random.nextFloat() > 0.2f) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    BlockPos groundPos = new BlockPos(pos.getX(), baseY, pos.getZ());
                    BlockState groundState = level.getBlockState(groundPos);
                    BlockPos abovePos = groundPos.above();
                    if (groundState.isSolidRender(level, groundPos) &&
                            level.getBlockState(abovePos).isAir()) {
                        BlockState existingSnow = level.getBlockState(abovePos);
                        if (existingSnow.is(Blocks.SNOW)) {
                            int layers = existingSnow.getValue(SnowLayerBlock.LAYERS);
                            if (layers < 8) {
                                level.setBlock(abovePos, existingSnow.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                            } else {
                                level.setBlock(abovePos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                            }
                        } else {
                            level.setBlock(abovePos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), 3);
                        }
                    }
                }
            }
        }
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.SNOW_QUEEN_IDLE.get();
    }

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 260;
    }

    public static void killPlayer(Player player) {
        ParticleUtil.spawnParticlesAroundEntity(player, ParticleTypes.SNOWFLAKE, 100, 0.5d);
        BlockState state = Blocks.ICE.defaultBlockState();
        if (state.is(Blocks.ICE)) {
            BlockParticleOption particleOption = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
            ParticleUtil.spawnParticles(player, particleOption, 100, 0.1d);
        }
        com.wzz.lobotocraft.util.BuffUtil.removeKiss(player);
        SoundUtil.playSound(player.level, player, ModSounds.SNOW_QUEEN_DUEL_FAIL.get());
        SoundUtil.playSound(player.level, player, SoundEvents.GLASS_BREAK);
        // 移除冰封标记,否则 kill() 产生的 fellOutOfWorld 伤害会被 ForgeModEvent 中的 isSnowQueen 判定拦截,导致玩家不死
        player.getPersistentData().remove("isSnowQueen");
        player.kill();
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        SnowQueenTimerEntry.add(player, this);
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“冰雪女皇”的收容单元里刮着寒冷的暴风雪。");
        logs.add("收容单元内的冰与雪霜闪闪发亮。");
        logs.add("当冰雪消融的那天，她冰封的内心也将一并融化。");
        return logs;
    }

    @Override
    public String name() {
        return "snowqueen";
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.04f, 0),
                new ObservationLevelBonus(0.0f, 4),
                new ObservationLevelBonus(0.04f, 0, false, true, false),
                new ObservationLevelBonus(0.0f, 4, false, false, true)
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "dj", 0, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntitySnowQueen> event) {
        if (getAnimation().equals("animation.snowqueen.special")) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.snowqueen.special"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.snowqueen.idle"));
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.02D)
                .add(Attributes.FLYING_SPEED, 0.02D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    static class SnowQueenTimerEntry extends TimerEntry {
        @Override
        public void onRunning(@NotNull LivingEntity living) {
            if (getExecutions() == 1) {
                if (!com.wzz.lobotocraft.util.BuffUtil.hasKiss((Player) living)) {
                    if (!living.getPersistentData().getBoolean("isSnowQueenDuel")) {
                        SoundUtil.playSound(living.level, living, ModSounds.SNOW_QUEEN_KISS.get());
                    }
                } else {
                    SoundUtil.playSound(living.level, living, ModSounds.SNOW_QUEEN_SIGN_KISS.get());
                }
            }
        }

        @Override
        public void onEnd(@NotNull LivingEntity living) {
            if (!com.wzz.lobotocraft.util.BuffUtil.hasKiss((Player) living)) {
                if (!living.getPersistentData().getBoolean("isSnowQueenDuel")) {
                    com.wzz.lobotocraft.util.BuffUtil.giveKiss((Player) living);
                }
            } else {
                for (Entity entity : EntityUtil.findAllEntities(living, 4)) {
                    if (entity instanceof Villager villager) {
                        villager.getPersistentData().putBoolean("isSnowQueen", true);
                        ((Player) living).displayClientMessage(Component.literal("§c有人被冰雪女皇困住了！"), false);
                        return;
                    }
                }
                ((Player) living).displayClientMessage(Component.literal("§c你被冰雪女皇困住了！"), false);
                living.getPersistentData().putBoolean("isSnowQueen", true);
                boolean hasPlayer = false;
                for (ServerPlayer player : EntityUtil.findAllPlayer(living)) {
                    if (player != living) {
                        hasPlayer = true;
                        break;
                    }
                }
                if (!hasPlayer) {
                    ((Player) living).displayClientMessage(Component.literal("§c你所在的存档没有其他玩家存在，你被彻底冰封了"), false);
                    // 移除冰封标记,否则 kill() 的伤害会被 isSnowQueen 判定拦截,导致单人玩家不会死亡
                    living.getPersistentData().remove("isSnowQueen");
                    living.kill();
                }
            }
        }

        public static void add(LivingEntity living, AbstractAbnormality abnormality) {
            new AnimationTimerEntry(abnormality, "animation.snowqueen.special", "animation.snowqueen.idle", 1250);
            new SnowQueenTimerEntry().addSkillTimer(living, 0, 2000, 1, true);
        }
    }
}