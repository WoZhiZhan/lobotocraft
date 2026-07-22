package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.EntityRedShoesClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.listener.PlayerControlLock;
import com.wzz.lobotocraft.event.definition.living.LivingSwingEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityRedShoes extends AbstractAbnormality {
    private static final String JUDGEMENT_TAG = "lobotocraft_red_shoes_judgement";
    private static final String JUDGEMENT_TIMER_TAG = "lobotocraft_red_shoes_judgement_timer";
    private static final String CHARMED_TAG = "lobotocraft_red_shoes_charmed";
    private static final String BLOODLUST_TAG = "lobotocraft_red_shoes_bloodlust";
    private static final String SOURCE_UUID_TAG = "lobotocraft_red_shoes_source";
    private static final String CHARM_LEFT_CLICK_TAG = "lobotocraft_red_shoes_left_clicks";
    private static final String CHARM_HURT_COUNT_TAG = "lobotocraft_red_shoes_hurt_count";
    private static final String BLOODLUST_HEALED_TAG = "lobotocraft_red_shoes_healed";
    private static final String BLOODLUST_TELEPORTED_TAG = "lobotocraft_red_shoes_teleported";
    private static final String BLOODLUST_SELECTED_SLOT_TAG = "lobotocraft_red_shoes_selected_slot";
    private static final String BLOODLUST_WEAPON_SLOT_TAG = "lobotocraft_red_shoes_weapon_slot";
    private static final String BLOODLUST_WEAPON_ITEM_TAG = "lobotocraft_red_shoes_weapon";
    private static final String BLOODLUST_ATTACK_COOLDOWN_TAG = "lobotocraft_red_shoes_attack_cooldown";
    private static final String BLOODLUST_TARGET_UUID_TAG = "lobotocraft_red_shoes_target";
    private static final String LOW_TEMPERANCE_WORK_TAG = "lobotocraft_red_shoes_low_temperance_work";

    private static final int JUDGEMENT_DURATION = 20 * 20;
    private static final int CHARM_CLEAR_LEFT_CLICKS = 10;
    private static final int CHARM_CLEAR_HURTS = 5;
    private static final int BLOODLUST_DURATION = 20 * 60 * 60;
    private static final int BLOODLUST_ATTACK_INTERVAL = 20;
    private static final float BLOODLUST_ATTACK_DAMAGE = 6.0F;
    private static final double CHARM_REACH_DISTANCE_SQR = 4.0D;
    private static final double CHARM_TARGET_SEARCH_RANGE = 512.0D;
    private static final double CHARM_PLAYER_SPEED = 0.14D;
    private static final double CHARM_PLAYER_STOP_RANGE = 1.6D;
    private static final double CHARM_MOB_NAV_SPEED = 1.2D;
    private static final double CHARM_DIRECT_SPEED = 0.12D;
    private static final double BLOODLUST_TARGET_RANGE = 24.0D;

    private static final UUID BLOODLUST_HEALTH_UUID = UUID.fromString("ee65501e-910d-49d1-81f7-2536782f8b02");
    private static final UUID BLOODLUST_RED_RESIST_UUID = UUID.fromString("70ae224a-ec64-451b-82ac-2292fefc07d1");
    private static final UUID BLOODLUST_WHITE_RESIST_UUID = UUID.fromString("2f57ace6-4919-432c-a999-7d8513896af5");
    private static final UUID BLOODLUST_BLACK_RESIST_UUID = UUID.fromString("090726c9-71b6-414e-87fb-0a7390a8ebc5");
    private static final UUID BLOODLUST_BLUE_RESIST_UUID = UUID.fromString("bfceca09-4042-450f-9ab1-f83b63f605fd");

    public EntityRedShoes(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-04-08";
        this.abnormalityName = "红舞鞋";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "RED";
        this.maxPEOutput = 16;
        this.workPreferences = new float[]{0.50f, 0.50f, 0.99f, 0.0f};
        this.fullWorkPreferences = new float[][]{
                {0.50f, 0.50f, 0.45f, 0.50f, 0.65f},
                {0.50f, 0.60f, 0.55f, 0.55f, 0.55f},
                {0.99f, 0.99f, 0.50f, 0.40f, 0.30f},
                {0.00f, 0.00f, 0.00f, 0.00f, 0.00f}
        };
        initializeQliphothCounter(1);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            setNoAi(true);
        }
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        boolean canStart = super.onWorkStart(player, workType);
        if (canStart && getTemperanceLevel(player) < 3) {
            player.getPersistentData().putBoolean(LOW_TEMPERANCE_WORK_TAG, true);
        } else {
            player.getPersistentData().remove(LOW_TEMPERANCE_WORK_TAG);
        }
        return canStart;
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        if (shouldWearRedShoesOnWorkComplete(player)) {
            return;
        }
        if (this.random.nextFloat() < 0.50F) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        if (shouldWearRedShoesOnWorkComplete(player)) {
            return;
        }
        if (this.random.nextFloat() < 0.70F) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        boolean shouldWearRedShoes = shouldWearRedShoesOnWorkComplete(player);
        player.getPersistentData().remove(LOW_TEMPERANCE_WORK_TAG);
        if (shouldWearRedShoes) {
            startBloodlust(player, this);
            setQliphothCounter(getMaxQliphothCounter());
        }
    }

    @Override
    public void onWorkInterrupted(ServerPlayer player, WorkType workType, String reason) {
        player.getPersistentData().remove(LOW_TEMPERANCE_WORK_TAG);
    }

    private static boolean shouldWearRedShoesOnWorkComplete(ServerPlayer player) {
        return player.getPersistentData().getBoolean(LOW_TEMPERANCE_WORK_TAG)
                || getTemperanceLevel(player) < 3;
    }

    @Override
    public void decreaseQliphothCounter(int amount) {
        if (shouldBlockCounterDecrease()) {
            return;
        }
        super.decreaseQliphothCounter(amount);
    }

    private boolean shouldBlockCounterDecrease() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (hasActiveJudgement(serverLevel)) {
            return true;
        }

        if (!findConsumableClerks(serverLevel).isEmpty()) {
            return false;
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (isCharmEligiblePlayer(player)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/curio/red_shoes_curio.png"),
                "血之渴望",
                "特殊",
                "red_shoes_curio",
                "最大生命值+4"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/red_shoes_weapon.png"),
                "血之渴望",
                RiskLevel.HE,
                "RED",
                "8",
                "1.0",
                "4格",
                getWeaponDevelopmentMaxCount(),
                "red_shoes_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/red_shoes_armor.png"),
                "血之渴望",
                getRiskLevel(),
                0.6f,
                0.9f,
                0.9f,
                2.0f,
                getArmorDevelopmentMaxCount(),
                "red_shoes_armor"
        );
    }

    @Override
    public float[] getGiftRenderOffset() {
        return new float[] {20f, 1.0f, 1.0f};
    }

    @Override
    public float getGiftProbability() {
        return 0.04f;
    }

    @Override
    public float[] getArmorRenderScale() {
        return new float[] {1.5f, 1.0f, 1.5f};
    }

    @Override
    public float[] getArmorRenderOffset() {
        return new float[] {-20.0f, 1.0f, 1.0f};
    }

    @Override
    public float[] getWeaponRenderOffset() {
        return new float[] {1.0f, 1.0f, 1f};
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 40;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 50;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public void onQliphothMeltdown() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            setQliphothCounter(getMaxQliphothCounter());
            return;
        }

        LivingEntity target = findJudgementTarget(serverLevel);
        if (target == null) {
            setQliphothCounter(getMaxQliphothCounter());
            return;
        }
        startJudgement(target, this);
    }

    private LivingEntity findJudgementTarget(ServerLevel serverLevel) {
        List<LivingEntity> candidates = new ArrayList<>();
        candidates.addAll(serverLevel.players().stream()
                .filter(EntityRedShoes::isCharmEligiblePlayer)
                .toList());
        candidates.addAll(findConsumableClerks(serverLevel));
        if (!candidates.isEmpty()) {
            return candidates.get(this.random.nextInt(candidates.size()));
        }
        return null;
    }

    private List<EntityClerk> findConsumableClerks(ServerLevel serverLevel) {
        return serverLevel.getEntitiesOfClass(
                EntityClerk.class,
                new AABB(this.blockPosition()).inflate(CHARM_TARGET_SEARCH_RANGE),
                clerk -> clerk.isAlive()
                        && !(clerk instanceof EntityRedShoesClerk)
                        && !isRedShoesControlled(clerk)
        );
    }

    private boolean hasActiveJudgement(ServerLevel serverLevel) {
        for (ServerPlayer player : serverLevel.players()) {
            if (isJudgementFrom(player, this)) {
                return true;
            }
        }

        return serverLevel.getEntitiesOfClass(
                        EntityClerk.class,
                        new AABB(this.blockPosition()).inflate(CHARM_TARGET_SEARCH_RANGE),
                        clerk -> isJudgementFrom(clerk, this)
                ).stream()
                .findAny()
                .isPresent();
    }

    private void consumeClerkAndSpawnSpecial(ServerLevel serverLevel, EntityClerk clerk) {
        BlockPos spawnPos = findNearestEscapeBlock(serverLevel, clerk.blockPosition());
        EntityClerk.markNoTombstone(clerk);
        clerk.hurt(clerk.damageSources().genericKill(), Float.MAX_VALUE);
        if (clerk.isAlive()) {
            clerk.die(clerk.damageSources().genericKill());
        }

        EntityRedShoesClerk specialClerk = ModEntities.red_shoes_clerk.get().create(serverLevel);
        if (specialClerk == null) {
            return;
        }
        specialClerk.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        specialClerk.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.EVENT, null, null);
        serverLevel.addFreshEntity(specialClerk);
    }

    private BlockPos findNearestEscapeBlock(ServerLevel serverLevel, BlockPos origin) {
        return EscapeBlockEntity.getEscapeBlocks(serverLevel.dimension()).stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                .orElse(origin);
    }

    private static boolean isCharmEligiblePlayer(ServerPlayer player) {
        return isCharmablePlayer(player)
                && getTemperanceLevel(player) < 3;
    }

    private static boolean isCharmablePlayer(ServerPlayer player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && !isRedShoesControlled(player);
    }

    private static int getTemperanceLevel(Player player) {
        AtomicInteger level = new AtomicInteger(1);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> level.set(stats.getTemperanceLevel()));
        return level.get();
    }

    private static void startJudgement(LivingEntity target, EntityRedShoes source) {
        CompoundTag data = target.getPersistentData();
        data.putBoolean(JUDGEMENT_TAG, true);
        data.putUUID(SOURCE_UUID_TAG, source.getUUID());
        data.putInt(JUDGEMENT_TIMER_TAG, JUDGEMENT_DURATION);
        data.putInt(CHARM_LEFT_CLICK_TAG, 0);
        data.putInt(CHARM_HURT_COUNT_TAG, 0);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                JUDGEMENT_DURATION, 0, false, false, true));

        if (target instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("§5“红舞鞋”的目光锁定了你。"));
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static void clearJudgement(LivingEntity target, EntityRedShoes source) {
        clearJudgementData(target);
        if (target instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("§a你挣脱了“红舞鞋”的诱惑。"));
        }
        if (source != null && source.isAlive()) {
            source.setQliphothCounter(source.getMaxQliphothCounter());
        }
    }

    private static void triggerJudgementFailure(LivingEntity target, EntityRedShoes source) {
        clearJudgementData(target);
        if (source == null || !source.isAlive()) {
            return;
        }

        if (target instanceof EntityClerk clerk
                && !(clerk instanceof EntityRedShoesClerk)
                && target.level() instanceof ServerLevel serverLevel) {
            source.consumeClerkAndSpawnSpecial(serverLevel, clerk);
            source.setQliphothCounter(source.getMaxQliphothCounter());
            return;
        }

        startCharm(target, source);
    }

    private static void clearJudgementData(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        data.remove(JUDGEMENT_TAG);
        data.remove(JUDGEMENT_TIMER_TAG);
        data.remove(SOURCE_UUID_TAG);
        data.remove(CHARM_LEFT_CLICK_TAG);
        data.remove(CHARM_HURT_COUNT_TAG);
        target.removeEffect(MobEffects.GLOWING);
    }

    private static boolean isJudgementFrom(LivingEntity target, EntityRedShoes source) {
        CompoundTag data = target.getPersistentData();
        return data.getBoolean(JUDGEMENT_TAG)
                && data.hasUUID(SOURCE_UUID_TAG)
                && data.getUUID(SOURCE_UUID_TAG).equals(source.getUUID());
    }

    private static void startCharm(LivingEntity target, EntityRedShoes source) {
        clearJudgementData(target);

        CompoundTag data = target.getPersistentData();
        data.putBoolean(CHARMED_TAG, true);
        data.putUUID(SOURCE_UUID_TAG, source.getUUID());
        data.putInt(CHARM_LEFT_CLICK_TAG, 0);
        data.putInt(CHARM_HURT_COUNT_TAG, 0);
        if (target instanceof Player player) {
            player.sendSystemMessage(Component.literal("§5“红舞鞋”的低语缠住了你。"));
            PlayerControlLock.lock(player, source, CHARM_PLAYER_SPEED, CHARM_PLAYER_STOP_RANGE);
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static void clearCharm(LivingEntity target, EntityRedShoes source) {
        CompoundTag data = target.getPersistentData();
        data.remove(CHARMED_TAG);
        data.remove(SOURCE_UUID_TAG);
        data.remove(CHARM_LEFT_CLICK_TAG);
        data.remove(CHARM_HURT_COUNT_TAG);
        if (target instanceof Player player) {
            PlayerControlLock.unlock(player);
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (source != null && source.isAlive()) {
            source.setQliphothCounter(source.getMaxQliphothCounter());
        }
    }

    private static void startBloodlust(LivingEntity target, EntityRedShoes source) {
        clearCharm(target, source);
        clearJudgementData(target);

        CompoundTag data = target.getPersistentData();
        data.putBoolean(BLOODLUST_TAG, true);
        data.putUUID(SOURCE_UUID_TAG, source.getUUID());
        data.putInt(BLOODLUST_ATTACK_COOLDOWN_TAG, 0);

        target.addEffect(new MobEffectInstance(ModMobEffects.RED_SHOES_BLOODLUST.get(),
                BLOODLUST_DURATION, 0, false, true, true));
        applyBloodlustAttributes(target);

        if (!data.getBoolean(BLOODLUST_HEALED_TAG)) {
            target.setHealth(target.getMaxHealth());
            data.putBoolean(BLOODLUST_HEALED_TAG, true);
        }

        if (target instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("§4你穿上了“红舞鞋”，血之渴望吞没了理智！"));
            unlockArmor(player);
            giveBloodlustWeapon(player);
        }

        teleportToNearestEscapeBlock(target);
        source.setQliphothCounter(source.getMaxQliphothCounter());
    }

    private static void unlockArmor(ServerPlayer player) {
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            if (data.isArmorLocked()) {
                data.unlockArmor();
            }
            MessageLoader.getLoader().sendToPlayer(player,
                    new CompanyDailySyncPacket(data.getCurrentDay(), data.getTodayWorkCount(),
                            data.isArmorLocked(), data.isHasSleep()));
        });
    }

    private static void giveBloodlustWeapon(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(BLOODLUST_SELECTED_SLOT_TAG)) {
            data.putInt(BLOODLUST_SELECTED_SLOT_TAG, player.getInventory().selected);
        }

        if (hasMarkedBloodlustWeapon(player)) {
            return;
        }

        int freeSlot = player.getInventory().getFreeSlot();
        if (freeSlot < 0) {
            return;
        }

        int selectedSlot = player.getInventory().selected;
        ItemStack held = player.getInventory().getItem(selectedSlot);
        if (!held.isEmpty()) {
            player.getInventory().setItem(freeSlot, held.copy());
        }

        ItemStack weapon = new ItemStack(Items.IRON_AXE);
        weapon.setHoverName(Component.literal("§4血之渴望"));
        weapon.getOrCreateTag().putBoolean(BLOODLUST_WEAPON_ITEM_TAG, true);
        player.getInventory().setItem(selectedSlot, weapon);
        player.getInventory().selected = selectedSlot;
        data.putInt(BLOODLUST_WEAPON_SLOT_TAG, selectedSlot);
        player.getInventory().setChanged();
    }

    private static boolean hasMarkedBloodlustWeapon(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isMarkedBloodlustWeapon(player.getInventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static void removeMarkedBloodlustWeapons(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isMarkedBloodlustWeapon(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        player.getInventory().setChanged();
    }

    private static boolean isMarkedBloodlustWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && stack.hasTag()
                && stack.getOrCreateTag().getBoolean(BLOODLUST_WEAPON_ITEM_TAG);
    }

    private static void teleportToNearestEscapeBlock(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        if (data.getBoolean(BLOODLUST_TELEPORTED_TAG) || !(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Set<BlockPos> escapeBlocks = EscapeBlockEntity.getEscapeBlocks(serverLevel.dimension());
        BlockPos nearest = escapeBlocks.stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(target.blockPosition())))
                .orElse(null);
        if (nearest != null) {
            target.teleportTo(nearest.getX() + 0.5D, nearest.getY() + 1.0D, nearest.getZ() + 0.5D);
        }
        data.putBoolean(BLOODLUST_TELEPORTED_TAG, true);
    }

    private static void applyBloodlustAttributes(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(BLOODLUST_HEALTH_UUID) == null) {
            maxHealth.addTransientModifier(new AttributeModifier(
                    BLOODLUST_HEALTH_UUID,
                    "Red Shoes bloodlust health",
                    4.0D,
                    AttributeModifier.Operation.MULTIPLY_BASE
            ));
        }

        forceResistance(target, ModAttributes.RED_DAMAGE_RESISTANCE.get(),
                BLOODLUST_RED_RESIST_UUID, "Red Shoes red resistance");
        forceResistance(target, ModAttributes.WHITE_DAMAGE_RESISTANCE.get(),
                BLOODLUST_WHITE_RESIST_UUID, "Red Shoes white resistance");
        forceResistance(target, ModAttributes.BLACK_DAMAGE_RESISTANCE.get(),
                BLOODLUST_BLACK_RESIST_UUID, "Red Shoes black resistance");
        forceResistance(target, ModAttributes.BLUE_DAMAGE_RESISTANCE.get(),
                BLOODLUST_BLUE_RESIST_UUID, "Red Shoes blue resistance");
    }

    private static void forceResistance(LivingEntity target, Attribute attribute, UUID uuid, String name) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        double amount = 1.0D - instance.getValue();
        if (Math.abs(amount) > 0.0001D) {
            instance.addTransientModifier(new AttributeModifier(
                    uuid,
                    name,
                    amount,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    private static void removeBloodlustAttributes(LivingEntity target) {
        removeModifier(target, Attributes.MAX_HEALTH, BLOODLUST_HEALTH_UUID);
        removeModifier(target, ModAttributes.RED_DAMAGE_RESISTANCE.get(), BLOODLUST_RED_RESIST_UUID);
        removeModifier(target, ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), BLOODLUST_WHITE_RESIST_UUID);
        removeModifier(target, ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), BLOODLUST_BLACK_RESIST_UUID);
        removeModifier(target, ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), BLOODLUST_BLUE_RESIST_UUID);
    }

    private static void removeModifier(LivingEntity target, Attribute attribute, UUID uuid) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
    }

    public static boolean isRedShoesControlled(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.getBoolean(JUDGEMENT_TAG)
                || data.getBoolean(CHARMED_TAG)
                || data.getBoolean(BLOODLUST_TAG);
    }

    private static EntityRedShoes findSource(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.hasUUID(SOURCE_UUID_TAG) || !(entity.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity source = serverLevel.getEntity(data.getUUID(SOURCE_UUID_TAG));
        if (source instanceof EntityRedShoes redShoes && redShoes.isAlive()) {
            return redShoes;
        }
        return null;
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity.isDeadOrDying()) {
            return;
        }

        if (entity.getPersistentData().getBoolean(JUDGEMENT_TAG)) {
            tickJudgement(entity);
        }
        if (entity.getPersistentData().getBoolean(CHARMED_TAG)) {
            tickCharmed(entity);
        }
        if (entity.getPersistentData().getBoolean(BLOODLUST_TAG)) {
            tickBloodlust(entity);
        }
    }

    private static void tickJudgement(LivingEntity entity) {
        EntityRedShoes source = findSource(entity);
        if (source == null) {
            clearJudgement(entity, null);
            return;
        }

        CompoundTag data = entity.getPersistentData();
        int timer = data.getInt(JUDGEMENT_TIMER_TAG);
        if (timer <= 0) {
            triggerJudgementFailure(entity, source);
            return;
        }

        if (!entity.hasEffect(MobEffects.GLOWING)) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    timer, 0, false, false, true));
        }

        if (entity.tickCount % 5 == 0) {
            ParticleUtil.spawnParticles(entity,
                    ParticleUtil.getDustParticle(0.58F, 0.0F, 0.85F, 1.4F),
                    12,
                    0.03D);
        }

        timer--;
        data.putInt(JUDGEMENT_TIMER_TAG, timer);
        if (timer <= 0) {
            triggerJudgementFailure(entity, source);
        }
    }

    private static void tickCharmed(LivingEntity entity) {
        EntityRedShoes source = findSource(entity);
        if (source == null) {
            clearCharm(entity, null);
            return;
        }

        if (entity.tickCount % 5 == 0) {
            ParticleUtil.spawnParticles(entity,
                    ParticleUtil.getDustParticle(0.58F, 0.0F, 0.85F, 1.4F),
                    10,
                    0.02D);
        }

        if (entity.distanceToSqr(source) <= CHARM_REACH_DISTANCE_SQR) {
            startBloodlust(entity, source);
            return;
        }

        if (entity instanceof Player player) {
            PlayerControlLock.lock(player, source, CHARM_PLAYER_SPEED, CHARM_PLAYER_STOP_RANGE);
            return;
        }

        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.getLookControl().setLookAt(source);
            mob.getNavigation().moveTo(source, CHARM_MOB_NAV_SPEED);
            mob.getMoveControl().setWantedPosition(source.getX(), source.getY(), source.getZ(), CHARM_MOB_NAV_SPEED);
        }
        moveDirectly(entity, source, CHARM_DIRECT_SPEED);
    }

    private static void tickBloodlust(LivingEntity entity) {
        EntityRedShoes source = findSource(entity);
        CompoundTag data = entity.getPersistentData();

        entity.addEffect(new MobEffectInstance(ModMobEffects.RED_SHOES_BLOODLUST.get(),
                BLOODLUST_DURATION, 0, false, true, true));
        applyBloodlustAttributes(entity);

        if (entity.tickCount % 5 == 0) {
            ParticleUtil.spawnParticles(entity,
                    ParticleUtil.getDustParticle(0.85F, 0.0F, 0.0F, 1.5F),
                    14,
                    0.03D);
        }

        if (entity instanceof ServerPlayer player) {
            forceSelectedWeaponSlot(player);
        }

        int cooldown = data.getInt(BLOODLUST_ATTACK_COOLDOWN_TAG);
        if (cooldown > 0) {
            data.putInt(BLOODLUST_ATTACK_COOLDOWN_TAG, cooldown - 1);
        }

        LivingEntity target = getStoredBloodlustTarget(entity);
        if (!isValidBloodlustTarget(entity, target, source)) {
            target = findBloodlustTarget(entity, source);
            if (target != null) {
                data.putUUID(BLOODLUST_TARGET_UUID_TAG, target.getUUID());
            } else {
                data.remove(BLOODLUST_TARGET_UUID_TAG);
            }
        }

        if (target == null) {
            if (entity instanceof Player player && source != null) {
                PlayerControlLock.lock(player, source, 0.12D, 3.0D);
            }
            return;
        }

        chaseBloodlustTarget(entity, target);
        tryBloodlustAttack(entity, target);
    }

    private static void forceSelectedWeaponSlot(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.contains(BLOODLUST_WEAPON_SLOT_TAG)) {
            int slot = data.getInt(BLOODLUST_WEAPON_SLOT_TAG);
            if (slot >= 0 && slot < 9 && isMarkedBloodlustWeapon(player.getInventory().getItem(slot))) {
                player.getInventory().selected = slot;
                return;
            }
        }
        if (data.contains(BLOODLUST_SELECTED_SLOT_TAG)) {
            player.getInventory().selected = data.getInt(BLOODLUST_SELECTED_SLOT_TAG);
        }
    }

    private static LivingEntity getStoredBloodlustTarget(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.hasUUID(BLOODLUST_TARGET_UUID_TAG) || !(entity.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity target = serverLevel.getEntity(data.getUUID(BLOODLUST_TARGET_UUID_TAG));
        return target instanceof LivingEntity living ? living : null;
    }

    private static LivingEntity findBloodlustTarget(LivingEntity entity, EntityRedShoes source) {
        AABB area = entity.getBoundingBox().inflate(BLOODLUST_TARGET_RANGE);
        return entity.level().getEntitiesOfClass(LivingEntity.class, area,
                        candidate -> isValidBloodlustTarget(entity, candidate, source))
                .stream()
                .min(Comparator.comparingDouble(entity::distanceToSqr))
                .orElse(null);
    }

    private static boolean isValidBloodlustTarget(LivingEntity attacker, LivingEntity target, EntityRedShoes source) {
        if (target == null || target == attacker || !target.isAlive()) {
            return false;
        }
        if (target instanceof EntityRedShoes || target == source || isRedShoesControlled(target)) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    private static void chaseBloodlustTarget(LivingEntity entity, LivingEntity target) {
        if (entity instanceof Player player) {
            PlayerControlLock.lock(player, target, 0.16D, 2.4D);
            return;
        }

        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
            mob.setTarget(target);
            mob.getLookControl().setLookAt(target);
            mob.getNavigation().moveTo(target, 1.15D);
        }
        moveDirectly(entity, target, 0.10D);
    }

    private static void moveDirectly(LivingEntity entity, LivingEntity target, double speed) {
        Vec3 diff = new Vec3(target.getX() - entity.getX(), 0.0D, target.getZ() - entity.getZ());
        if (diff.lengthSqr() < 0.01D) {
            return;
        }
        Vec3 motion = diff.normalize().scale(speed);
        entity.setDeltaMovement(motion.x, entity.getDeltaMovement().y, motion.z);
        entity.hasImpulse = true;
    }

    private static void tryBloodlustAttack(LivingEntity attacker, LivingEntity target) {
        CompoundTag data = attacker.getPersistentData();
        if (data.getInt(BLOODLUST_ATTACK_COOLDOWN_TAG) > 0 || attacker.distanceToSqr(target) > 9.0D) {
            return;
        }
        target.hurt(DamageHelper.getDamage(attacker, "lobotocraft:red"), BLOODLUST_ATTACK_DAMAGE);
        data.putInt(BLOODLUST_ATTACK_COOLDOWN_TAG, BLOODLUST_ATTACK_INTERVAL);
    }

    @SubscribeEvent
    public static void onLivingSwing(LivingSwingEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(JUDGEMENT_TAG)) {
            int clicks = data.getInt(CHARM_LEFT_CLICK_TAG) + 1;
            data.putInt(CHARM_LEFT_CLICK_TAG, clicks);
            if (clicks >= CHARM_CLEAR_LEFT_CLICKS) {
                clearJudgement(player, findSource(player));
            }
            return;
        }

        if (!data.getBoolean(CHARMED_TAG)) {
            return;
        }
        int clicks = data.getInt(CHARM_LEFT_CLICK_TAG) + 1;
        data.putInt(CHARM_LEFT_CLICK_TAG, clicks);
        if (clicks >= CHARM_CLEAR_LEFT_CLICKS) {
            clearCharm(player, findSource(player));
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(JUDGEMENT_TAG)) {
            int hurts = data.getInt(CHARM_HURT_COUNT_TAG) + 1;
            data.putInt(CHARM_HURT_COUNT_TAG, hurts);
            if (hurts >= CHARM_CLEAR_HURTS) {
                clearJudgement(entity, findSource(entity));
            }
            return;
        }

        if (data.getBoolean(CHARMED_TAG)) {
            int hurts = data.getInt(CHARM_HURT_COUNT_TAG) + 1;
            data.putInt(CHARM_HURT_COUNT_TAG, hurts);
            if (hurts >= CHARM_CLEAR_HURTS) {
                clearCharm(entity, findSource(entity));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Entity killer = event.getSource().getEntity();
        if (killer instanceof LivingEntity livingKiller
                && livingKiller.getPersistentData().getBoolean(BLOODLUST_TAG)) {
            livingKiller.heal(livingKiller.getMaxHealth() * 0.04F);
        }

        if (dead.getPersistentData().getBoolean(BLOODLUST_TAG)) {
            cleanupBloodlust(dead);
        }
        if (dead.getPersistentData().getBoolean(JUDGEMENT_TAG)) {
            clearJudgement(dead, findSource(dead));
        }
        if (dead.getPersistentData().getBoolean(CHARMED_TAG)) {
            clearCharm(dead, findSource(dead));
        }
    }

    private static void cleanupBloodlust(LivingEntity entity) {
        removeBloodlustAttributes(entity);
        entity.removeEffect(ModMobEffects.RED_SHOES_BLOODLUST.get());
        CompoundTag data = entity.getPersistentData();
        data.remove(BLOODLUST_TAG);
        data.remove(SOURCE_UUID_TAG);
        data.remove(BLOODLUST_HEALED_TAG);
        data.remove(BLOODLUST_TELEPORTED_TAG);
        data.remove(BLOODLUST_SELECTED_SLOT_TAG);
        data.remove(BLOODLUST_WEAPON_SLOT_TAG);
        data.remove(BLOODLUST_ATTACK_COOLDOWN_TAG);
        data.remove(BLOODLUST_TARGET_UUID_TAG);
        if (entity instanceof Player player) {
            PlayerControlLock.unlock(player);
            removeMarkedBloodlustWeapons(player);
        }
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public int getGoodWorkResultMin() {
        return 12;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 8;
    }

    @Override
    public int getBasicInfoCost() {
        return 16;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 16;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 5;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 6;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), 4.0F + this.random.nextInt(3));
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.04F, 0),
                new ObservationLevelBonus(0.0F, 4, true, false, false),
                new ObservationLevelBonus(0.04F, 0, false, true, true),
                new ObservationLevelBonus(0.0F, 4)
        };
    }

    @Override
    public List<String> getWorkLogs() {
        return List.of(
                "“红舞鞋”被安放在一块精美的天鹅绒垫上，就像是在展出一样。",
                "如果你在“红舞鞋”中看到类似牙齿的东西，那一定是某种障眼法。",
                "无论是谁看到“红舞鞋”，都会产生一种“想要穿上”的冲动。",
                "女孩泪流满面地哀求着：“先生，求您把我的脚砍下来吧！”",
                "员工将“红舞鞋”放到垫子上后开始了工作。",
                "“红舞鞋”掉到了地上，就好像最近有人碰过它似的。",
                "员工将“红舞鞋”清理干净后继续进行工作。",
                "“红舞鞋”那红色的珐琅皮革在灯光下闪闪发光。",
                "尽管有些磨损，可“红舞鞋”的踝带依然很有光彩。",
                "“红舞鞋”有着悠远的历史，那条磨损的踝带正无声地诉说这着一切。",
                "“红舞鞋”的皮革保养得非常好，这真是不可思议。",
                "员工在工作时偷偷瞥了“红舞鞋”一眼。",
                "员工正盯着“红舞鞋”看。",
                "员工无法集中注意力，停下了手头的工作，直勾勾地看着“红舞鞋”。",
                "为了避免再看到“红舞鞋”，员工把头转向另一边，并试图重新开始手头的工作。",
                "“红舞鞋”显然不是一个活着的有机体，可它能够营造出阴郁的氛围。",
                "“红舞鞋”不是一个活着的有机体，但它独特的氛围让周围的人想起了它血腥的过往。",
                "“红舞鞋”那深红的颜色看起来很眼熟。",
                "“红舞鞋”被静静地放置在那儿。"
        );
    }

    @Override
    public String name() {
        return "red_shoes";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityRedShoes> event) {
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }
}
