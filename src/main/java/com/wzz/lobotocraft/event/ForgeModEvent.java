package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.*;
import com.wzz.lobotocraft.entity.ai.AttackAI;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.event.living.LivingSwingEvent;
import com.wzz.lobotocraft.event.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.event.work.WorkCompleteEvent;
import com.wzz.lobotocraft.event.work.WorkDamageEvent;
import com.wzz.lobotocraft.event.work.WorkScreenOpenEvent;
import com.wzz.lobotocraft.event.work.WorkStartEvent;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.item.CaptureUnitItem;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.children_galaxy.ChildrenGalaxyCurio;
import com.wzz.lobotocraft.item.ego.red_shoes.RedShoesWeapon;
import com.wzz.lobotocraft.item.ego.thorn_bus.ThornBusWeapon;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.*;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.OrdealData;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wzz.lobotocraft.util.DamageHelper.*;

@Mod.EventBusSubscriber
public class ForgeModEvent {
	private static final net.minecraft.resources.ResourceLocation ORDEAL_ADVANCEMENT =
			ResourceUtil.createInstance("ordeal");
	private static final String LOW_HEALTH_SOUND_ACTIVE_TAG = "lobotocraft_low_health_sound_active";
	private static final String LOW_HEALTH_SOUND_COOLDOWN_TAG = "lobotocraft_low_health_sound_cooldown";
	private static final int LOW_HEALTH_SOUND_COOLDOWN_TICKS = 30 * 20;

	@SubscribeEvent
	public static void onLivingAttack(LivingAttackEvent event) {
		if (event.getEntity() instanceof AbstractAbnormality abnormality
				&& event.getSource().is(DamageTypeTags.IS_FIRE)) {
			abnormality.clearFire();
			event.setCanceled(true);
			return;
		}
		if (event.getEntity() instanceof ServerPlayer player && event.getSource() != null && event.getSource().getEntity() instanceof Player attacker) {
			if (MentalValueUtil.getMentalValue(player) <= 0) {
				if (attacker.getMainHandItem().getItem() instanceof BaseEgoWeapon) {
					DamageSource src = event.getSource();
					if (DamageHelper.isWhiteDamage(src)) {
						MentalValueUtil.addMentalValue(player, event.getAmount());
						event.setCanceled(true);
					} else if (DamageHelper.isBlackDamage(src)) {
						MentalValueUtil.addMentalValue(player, event.getAmount());
						EntityUtil.clearHurtTime(player, () -> player.hurt(DamageHelper.getDamage().getDamageSources().fellOutOfWorld(), event.getAmount()));
					}
				} else {
					event.setCanceled(true);
				}
			}
		}
		if (event.getEntity() instanceof Player player) {
			if (EgoArmorHelper.isFullEGO(player, "fourth_match_flame") || EgoArmorHelper.isFullEGO(player, "snowqueen")) {
				if (DamageHelper.getDamage().isAllFire(event.getSource())) {
					event.setCanceled(true);
				}
			}
			if (player.getPersistentData().getBoolean("isSnowQueen")) {
				if (!DamageHelper.getDamage().isKill(event.getSource())) {
					event.setCanceled(true);
				} else com.wzz.lobotocraft.util.BuffUtil.removeKiss(player);
			}
			if (EgoArmorHelper.isFullEGO(player, "thorn_bus")) {
				if (isRangedAttack(event.getSource())) {
					event.setCanceled(true);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onLivingHurt(LivingHurtEvent event) {
		DamageSource src = event.getSource();
		LivingEntity target = event.getEntity();
		Holder<DamageType> holder = src.typeHolder();
		Optional<ResourceKey<DamageType>> optionalKey = holder.unwrapKey();
		if (optionalKey.isEmpty() || target == null) {
			return;
		}
		boolean isRedDamage = DamageHelper.isRedDamage(src);
		boolean isWhiteDamage = DamageHelper.isWhiteDamage(src);
		boolean isBlackDamage = DamageHelper.isBlackDamage(src);
		boolean isBlueDamage = DamageHelper.isBlueDamage(src);
		boolean isDot = DotHelper.isDotDamage(target);
		if (!isDot && target.hasEffect(ModMobEffects.KISS.get())) {
			MobEffectInstance effect = target.getEffect(ModMobEffects.KISS.get());
			if (effect != null && effect.getAmplifier() >= 2 && isRedDamage) {
				SoundUtil.playSound(target.level(), target, ModSounds.SNOWQUEEN_WEAPON_ICE_BREAK.get());
				event.setAmount(event.getAmount() * 2f + target.getMaxHealth() * 0.02f);
				target.removeEffect(effect.getEffect());
			}
		}
		if (!isDot && src.getEntity() instanceof Player attacker) {
			// 补充4第2条:正义裁决者武器命中刷新攻速移速buff
			com.wzz.lobotocraft.event.WeaponBuffEvent.triggerJusticeBuff(attacker);
			// 悔恨武器命中刷新减速buff,且buff期间造成的红色伤害+10%
			com.wzz.lobotocraft.event.WeaponBuffEvent.triggerAbandonedMurdererBuff(attacker);
			if (com.wzz.lobotocraft.event.WeaponBuffEvent.isAbandonedMurdererBuffActive(attacker)
					&& EgoArmorHelper.isHoldingWeapon(attacker, "abandoned_murderer") && isRedDamage(src)) {
				event.setAmount(event.getAmount() * 1.1f);
			}
			if (EgoArmorHelper.isFullEGO(attacker, "punishing_bird")
					&& EgoArmorHelper.isHoldingWeapon(attacker, "punishing_bird")
					&& attacker.random.nextFloat() <= 0.1f) {
				event.setAmount(event.getAmount() * 2f);
			}
			if (EgoArmorHelper.isFullEGO(attacker, "fourth_match_flame") && target.isOnFire()) {
				event.setAmount(event.getAmount() * 1.2f);
			}
			if (EgoArmorHelper.isFullEGO(attacker, "wingbeat") && EgoArmorHelper.isHoldingWeapon(attacker, "wingbeat")) {
				attacker.heal(event.getAmount());
			}
			if (EgoArmorHelper.isFullEGO(attacker, "end_bird")
					&& EgoArmorHelper.isHoldingWeapon(attacker, "end_bird")
					&& CuriosUtil.hasCurios(attacker, ModItems.PUNISHING_BIRD_CURIO.get())
					&& !com.wzz.lobotocraft.item.ego.end_bird.EndBirdWeapon.isThinDuskSpecialDamage(attacker)) {
				attacker.heal(event.getAmount() * 0.1f);
			}
			com.wzz.lobotocraft.event.WeaponBuffEvent.triggerThinDuskApprovalBuff(attacker);
			if ((EgoArmorHelper.isFullEGO(attacker, "end_bird") && EgoArmorHelper.isHoldingWeapon(attacker, "end_bird")) ||
					EgoArmorHelper.hasEquipmentCombination(attacker, "end_bird", true, false, true)) {
				float multiplier = EntityUtil.getDamageMultiplierByLostHealth(attacker, 2.0f, 1.0f);
				event.setAmount(event.getAmount() * multiplier);
			}
			if (CuriosUtil.hasCurios(attacker, ModItems.ABANDONED_MURDERER_CURIO.get())) {
				event.setAmount(event.getAmount() + 1f);
			}
			if (CuriosUtil.hasCurios(attacker, ModItems.END_BIRD_CURIO.get())) {
				event.setAmount(event.getAmount() * 1.1f);
			}
			if (EgoArmorHelper.isFullEGO(attacker, "fragment_of_the_universe") && !attacker.level.isClientSide) {
				for (ServerPlayer serverPlayer : EntityUtil.findAllPlayer(attacker)) {
					if (!MentalValueUtil.isPanic(serverPlayer)) {
						MentalValueUtil.addMentalValue(serverPlayer, 3f);
					}
				}
			}
			if (EgoArmorHelper.isFullEGO(attacker, "crumbling_armor")) {
				boolean hasPlayer = false;
				for (ServerPlayer serverPlayer : EntityUtil.findAllPlayer(attacker, 8D)) {
					if (serverPlayer.isAlive()) {
						hasPlayer = true;
						break;
					}
				}
				if (!hasPlayer) {
					event.setAmount(event.getAmount() * 1.35f);
				}
			}
			// 血之渴望(红鞋)套装：每次造成伤害为目标叠加一层流血
			// 流血：每秒造成 3点红色伤害/层，最多5层，持续10秒（命中会刷新持续时间）
			if (EgoArmorHelper.isFullEGO(attacker, "red_shoes")
					&& !DotHelper.isDotDamage(target) && attacker.getMainHandItem().getItem() instanceof RedShoesWeapon) {
				RedShoesBleedHandler.applyBleed(attacker, target);
			}
			if (target.hasEffect(ModMobEffects.MENACE.get())) {
				event.setAmount(event.getAmount() * 1.1f);
			}
			if (EgoArmorHelper.isFullEGO(attacker, "the_lady_facing_the_wall")) {
				int currentAmplifier = 0;
				if (target.hasEffect(ModMobEffects.LONELINESS.get())) {
					currentAmplifier = target.getEffect(ModMobEffects.LONELINESS.get()).getAmplifier() + 1;
				}
				int newAmplifier = Math.min(currentAmplifier, 9);
				target.addEffect(new MobEffectInstance(ModMobEffects.LONELINESS.get(), 200, newAmplifier));
			}
			boolean hasBuff = attacker.hasEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get()) || attacker.hasEffect(ModMobEffects.LETICIA_GIFT.get());
			if (EgoArmorHelper.isWearingFullSet(attacker, "leticia")) {
				if (!hasBuff) {
					event.setAmount(event.getAmount() * 0.7f);
				} else {
					event.setAmount(event.getAmount() * 1.3f);
				}
			}
			if (EgoArmorHelper.isFullEGO(attacker, "redhat_mercenary")) {
				float multiplier = EntityUtil.getDamageMultiplierByLostHealth(attacker, 0.004f, 0.4f);
				event.setAmount(event.getAmount() * multiplier);
			}
			if (CuriosUtil.hasCurios(attacker, ModItems.REDHAT_MERCENARY_CURIO.get())) {
				event.setAmount(event.getAmount() + 2f);
			}
			if (CuriosUtil.hasCurios(attacker, ModItems.BIG_BADWOLF_CURIO.get()) && isRedDamage) {
				event.setAmount(event.getAmount() * 1.1f);
			}
			if (EgoArmorHelper.isFullEGO(attacker, "army_in_black", false) && attacker.getMainHandItem().getItem() == ModItems.ARMY_IN_BLACK_WEAPON.get()) {
				event.setAmount(event.getAmount() + 15f);
			}
		}
		if (!isDot && target instanceof ServerPlayer player) {
			if (EntityArmyInBlack.hasActiveProtection(player) && !DamageHelper.isExecution(src)) {
				event.setAmount(event.getAmount() * 0.8f);
			}
			if (EgoArmorHelper.isFullEGO(player, "punishing_bird") && player.random.nextFloat() <= 0.1f) {
				event.setAmount(event.getAmount() * 0.5f);
			}
			if (EgoArmorHelper.isFullEGO(player, "end_bird") &&
					EgoArmorHelper.isHoldingWeapon(player, "end_bird") &&
					CuriosUtil.hasCurios(player, ModItems.APPROVAL_BIRD_CURIO.get())) {
				if (event.getAmount() >= player.getMaxHealth() * 0.2f) {
					event.setAmount(player.getMaxHealth() * 0.2f);
				}
			}
			if (CuriosUtil.hasCurios(player, ModItems.THORN_BUS_CURIO.get())) {
				MentalValueUtil.addMentalValue(player, 2 + player.random.nextInt(3));
				if (EgoArmorHelper.isFullEGO(player, "thorn_bus")) {
					MentalValueUtil.addMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.05f);
				}
			}
			if (CuriosUtil.hasCurios(player, ModItems.END_BIRD_CURIO.get())) {
				event.setAmount(event.getAmount() * 1.1f);
			}
			if (ChildrenGalaxyCurio.hasFullSet(player) && BuffUtil.hasFriendshipProof(player)) {
				ChildrenGalaxyCurio.triggerRecovery(player);
			}
			if (src.getEntity() instanceof Player attacker && !player.level.isClientSide) {
				if (MentalValueUtil.getMentalValue(player) <= 0 && isWhiteDamage(src) && MentalValueUtil.getMentalValue(attacker) > 0) {
					MentalValueUtil.addMentalValue(player, event.getAmount());
					event.setCanceled(true);
				}
				if (MentalValueUtil.getMentalValue(player) <= 0 && isBlackDamage(src) && MentalValueUtil.getMentalValue(attacker) > 0) {
					MentalValueUtil.addMentalValue(player, event.getAmount());
				}
			}
			if (EgoArmorHelper.getFullSetId(player) != null) {
				RiskLevel riskLevel = EgoArmorHelper.getArmorRiskLevel(player);
				if (src.getEntity() instanceof IAbnormality abnormality && riskLevel != null) {
					event.setAmount(EgoArmorHelper.applyRiskLevelSuppression(event.getAmount(), riskLevel, abnormality.getRiskLevel()));
				}
			}
			if (EgoArmorHelper.isFullEGO(player, "fragment_of_the_universe")) {
				if (isBlackDamage) {
					MentalValueUtil.addMentalValue(player, 5f);
				}
			}
			if (EgoArmorHelper.isFullEGO(player, "crumbling_armor")) {
				boolean hasPlayer = false;
				for (ServerPlayer serverPlayer : EntityUtil.findAllPlayer(player, 8D)) {
					if (serverPlayer.isAlive()) {
						hasPlayer = true;
						break;
					}
				}
				if (!hasPlayer) {
					event.setAmount(event.getAmount() * 0.9f);
				}
			}
			if (EgoArmorHelper.isWearingFullSet(player, "red_shoes")) {
				float reduction = EntityUtil.getDamageReductionByLostHealth(player, 0.02f, 0.6f);
				event.setAmount(event.getAmount() * (1 - reduction));
			}
			boolean hasBuff = player.hasEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get()) || player.hasEffect(ModMobEffects.LETICIA_GIFT.get());
			if (EgoArmorHelper.isWearingFullSet(player, "leticia")) {
				if (hasBuff) {
					event.setAmount(event.getAmount() * 0.7f);
				} else {
					event.setAmount(event.getAmount() * 1.3f);
				}
			}
			if (EgoArmorHelper.isFullEGO(player, "butterfly_funeral")) {
				AtomicInteger level = new AtomicInteger();
				player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> {
					level.set(data.getJusticeLevel());
				});
				if (level.get() > 0) {
					event.setAmount(event.getAmount() - level.get());
				}
			}
			if (EgoArmorHelper.isFullSetWithCurioLocked(player, "big_badwolf") && event.getAmount() >= player.getMaxHealth() * 0.25f) {
				if (!SheepskinSetHandler.isBigBadwolfSetActive(player)) {
					TimerEntry<Player> timerEntry = new TimerEntry<>() {
						@Override
						public void onStart(@NotNull Player entity) {
							entity.getPersistentData().putBoolean("isSuperWolf", true);
							SoundUtil.playSound(player, ModSounds.BIG_BADWOLF_CURIO.get());
						}

						@Override
						public void onRunning(@NotNull Player entity) {
							ParticleUtil.spawnParticlesAroundEntity(entity, ParticleTypes.END_ROD, 20, 0.01f);
						}

						@Override
						public void onEnd(@NotNull Player entity) {
							entity.getPersistentData().putBoolean("isSuperWolf", false);
						}
					};
					timerEntry.addSkillTimer(player, 0, 5000, 1, true);
				}
			}
			if (player.getPersistentData().getBoolean("isSuperWolf")) {
				long currentTick = player.level().getGameTime();
				long lastHurtTick = player.getPersistentData().getLong("lastSuperWolfHurtTick");
				if (currentTick - lastHurtTick < 20) {
					event.setCanceled(true);
				} else {
					player.getPersistentData().putLong("lastSuperWolfHurtTick", currentTick);
				}
			}
			if (EgoArmorHelper.isWearingFullSet(player, "army_in_black", false)) {
				float mentalValue = MentalValueUtil.getMentalValue(player);
				if (mentalValue >= MentalValueUtil.getEffectiveMaxMentalValue(player)) {
					float reduction = Math.min((mentalValue / 10) * 0.05f, 0.30f);
					event.setAmount(event.getAmount() * (1 - reduction));
				}
			}
		}

		// 处理非玩家的颜色伤害抗性
		if (!(target instanceof Player) && (isRedDamage || isWhiteDamage || isBlackDamage || isBlueDamage)) {
			AttributeInstance resistanceAttr;
			if (isRedDamage) {
				resistanceAttr = target.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
			} else if (isWhiteDamage) {
				resistanceAttr = target.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
			} else if (isBlackDamage) {
				resistanceAttr = target.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
			} else {
				resistanceAttr = target.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
			}

			if (resistanceAttr != null) {
				double resistance = resistanceAttr.getValue();
				if (resistance != 1.0) {
					float finalAmount = event.getAmount() * (float) resistance;
					event.setAmount(finalAmount);
				}
			}
		}

		// 粒子效果处理（所有实体）
		ResourceKey<DamageType> key = optionalKey.get();
		if (isRedDamage) {
			ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.RED.get(), 1, 0);
		} else if (isWhiteDamage) {
			ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.WHITE.get(), 1, 0);
		} else if (isBlackDamage) {
			ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.BLACK.get(), 1, 0);
			if (target.getPersistentData().getBoolean("isLargeBirdWeaponBlackDamage")) {
				event.setAmount(event.getAmount() * 1.5f);
			}
		} else if (isBlueDamage) {
			ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.BLUE.get(), 1, 0);
		} else if (isVanillaDamage(key)) {
			ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.RED.get(), 1, 0);
		}
        if (target instanceof Player && DamageHelper.isExecution(event.getSource())) {
			return;
		}
		if (!isRedDamage && !isWhiteDamage && !isBlackDamage && !isBlueDamage) {
			return;
		}
		AttributeInstance resistanceAttr = null;
		if (isRedDamage) {
			resistanceAttr = target.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
		} else if (isWhiteDamage) {
			resistanceAttr = target.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
		} else if (isBlackDamage) {
			resistanceAttr = target.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
		} else {
			resistanceAttr = target.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
		}
		if (resistanceAttr == null) return;
		double resistance = resistanceAttr.getValue();

		if (!(target instanceof Player player)) {
			return;
		}
		if (resistance < 0) {
			event.setCanceled(true);
			float healAmount = event.getAmount() * (float) Math.abs(resistance);
			if (healAmount <= 0) return;
			if (isRedDamage || isBlueDamage) {
				target.heal(healAmount);
			} else if (isWhiteDamage) {
				player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mentalValue -> {
					int currentMental = (int) mentalValue.getMentalValue();
					int maxMental = (int) mentalValue.getEffectiveMaxMentalValue();
					int newMental = Math.min(maxMental, currentMental + (int) healAmount);
					mentalValue.setMentalValue(newMental);

					if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
						MessageLoader.getLoader().sendToPlayer(serverPlayer,
								new MentalValueSyncPacket(newMental, maxMental));
					}
				});
			} else {
				target.heal(healAmount);
				player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mentalValue -> {
					int currentMental = (int) mentalValue.getMentalValue();
					int maxMental = (int) mentalValue.getEffectiveMaxMentalValue();
					int newMental = Math.min(maxMental, currentMental + (int) healAmount);
					mentalValue.setMentalValue(newMental);

					if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
						MessageLoader.getLoader().sendToPlayer(serverPlayer,
								new MentalValueSyncPacket(newMental, maxMental));
					}
				});
			}
		}
	}

	@SubscribeEvent
	public static void onEntityJoinWorld(EntityJoinLevelEvent e) {
		if (DamageHelper.getDamage() == null)
			new DamageHelper(e.getLevel());
		if (e.getEntity() instanceof Player player && e.getLevel().dimension == ModDimensions.LOBOTO_KEY &&
				e.getLevel() instanceof ServerLevel serverLevel && !Structures.LOBOTO.isGenerated(serverLevel)) {
			Structures.LOBOTO.setGenerated(serverLevel, 100, 100, 100);
			StructureLoader.beginLoading(serverLevel, player, Structures.LOBOTO.name(), BlockPos.containing(5,2,0),
					() -> serverLevel.getServer().execute(() ->  {
						if (player instanceof ServerPlayer serverPlayer) {
							serverPlayer.teleportTo(195, 273, 29);
							serverPlayer.resetFallDistance();
							BlockPos bedPos = new BlockPos(195, 273, 29);
							serverPlayer.setRespawnPosition(serverLevel.dimension(), bedPos, 0.0F, true, false);
						}
						if (serverLevel.dimension() == ModDimensions.LOBOTO_KEY) {
							serverLevel.setDefaultSpawnPos(new BlockPos(195, 273, 29), 0.0F);
						}
					}));
		}
	}

	@SubscribeEvent
	public static void onLivingHeal(LivingHealEvent event) {
		if (event.getEntity() instanceof ServerPlayer serverPlayer) {
			if (WorkManager.isPlayerWorking(serverPlayer) && WorkManager.getWorkingAbnormality(serverPlayer) instanceof EntityIronMaiden) {
				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent
	public static void onEntityJoinLevel(EntityJoinLevelEvent e) {
		if (e.getEntity() instanceof EntityEndBird) {
			for (Entity entity : EntityUtil.findAllEntities(e.getEntity(), 400)) {
				if (entity instanceof EntityLargeBird) {
					e.setCanceled(true);
					break;
				}
			}
		}
		if (e.getEntity() instanceof EntityBlackForestDoor) {
			for (Entity entity : EntityUtil.findAllEntities(e.getEntity(), 400)) {
				if (entity instanceof EntityBlackForestDoor) {
					e.setCanceled(true);
					break;
				}
			}
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		RedShoesBleedHandler.clearBleed(event.getEntity());
		ThornBusWeapon.tryAwardKillTulip(event.getEntity(), event.getSource());
		if (event.getEntity() instanceof Player player) {
			EntityLargeBird.clearLargeBirdCharm(player);
			player.getPersistentData().putBoolean("isInWingBeat", false);
			for (ItemStack itemStack : player.inventory.items) {
				if (itemStack.getItem() instanceof PEBoxItem) {
					itemStack.setCount((int) (itemStack.getCount() * 0.3));
				}
			}
			player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
				data.setTodayWorkCount(0);
				if (player instanceof ServerPlayer serverPlayer) {
					MessageLoader.getLoader().sendToPlayer(serverPlayer,
							new CompanyDailySyncPacket(
									data.getCurrentDay(),
									data.getTodayWorkCount(),
									data.isArmorLocked(),
									data.isHasSleep()
							));
				}
			});
			if (player.level() instanceof ServerLevel level) {
				OrdealData ordealData = OrdealData.get(level);
				if (ordealData.getMiddayTriggersToday() > 0) {
					ordealData.decrementMiddayTriggersToday();
				} else {
					ordealData.decrementDawnTriggersToday();
				}
				ServerLevel lobotoLevel = level.getServer().getLevel(ModDimensions.LOBOTO_KEY);
				if (lobotoLevel != null) {
					ClerkEvent.respawnClerksAfterPlayerDeath(lobotoLevel);
				}
			}
			if (com.wzz.lobotocraft.util.BuffUtil.hasFriendshipProof(player)) {
				boolean has = false;
				for (Entity entity : EntityUtil.findAllEntities(event.getEntity(), 8)) {
					if (entity instanceof EntityChildrenGalaxy) {
						has = true;
						break;
					}
				}
				if (!has) {
					for (Entity entity : EntityUtil.findAllEntities(event.getEntity(), 400)) {
						if (entity instanceof EntityChildrenGalaxy childrenGalaxy) {
							childrenGalaxy.decreaseQliphothCounter(999);
							player.displayClientMessage(Component.literal("§c银河之子的计时器归零了"), false);
							break;
						}
					}
				}
				com.wzz.lobotocraft.util.BuffUtil.removeFriendshipProof(player);
			}
		}
		if (event.getEntity().level.dimension == ModDimensions.LOBOTO_KEY
				&& (event.getEntity() instanceof Player || event.getEntity() instanceof Villager
				|| event.getEntity() instanceof EntityClerk)) {
			if (event.getEntity() instanceof EntityClerk clerk && EntityClerk.isCommandKill(clerk)) {
				return;
			}
			boolean isClerkOrVillagerDeath = event.getEntity() instanceof Villager || event.getEntity() instanceof EntityClerk;
			for (Entity entity : EntityUtil.findAllEntities(event.getEntity(), 300)) {
				if (entity instanceof EntityLargeBird largeBird) {
					largeBird.addDataPlayerOrVillagerDeathCount(1);
				}
				if (isClerkOrVillagerDeath && entity instanceof EntityArmyInBlack armyInBlack) {
					armyInBlack.addClerkOrVillagerDeathCount(1);
				}
			}
		}
		if (event.getSource() != null && event.getSource().getEntity() instanceof ServerPlayer player) {
			if (EgoArmorHelper.isFullEGO(player, "red_shoes")) {
				player.heal(player.getMaxHealth() * 0.05f);
				MentalValueUtil.addMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.05f);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDeathLow(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			stopLowHealthSound(player);
			player.playNotifySound(ModSounds.PLAYER_DEATH.get(), SoundSource.PLAYERS, 1, 1);
			player.playNotifySound(ModSounds.PLAYER_DEATH_AFTER.get(), SoundSource.PLAYERS, 1, 1);
		}
	}

	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			stopLowHealthSound(player);
			stopDeathSounds(player);
			EntityLargeBird.clearLargeBirdCharm(player);
		}
	}

	@SubscribeEvent
	public static void onLivingSwing(LivingSwingEvent.Pre event) {
		if (event.getEntity() instanceof Player player && player.getPersistentData().getBoolean("isLargeBirdCharm") && !player.level.isClientSide) {
			if (EgoArmorHelper.isFullEGO(player, "end_bird")
					&& EgoArmorHelper.isHoldingWeapon(player, "end_bird")
					&& CuriosUtil.hasCurios(player, ModItems.LARGEBIRD_CURIO.get())) {
				EntityLargeBird.clearLargeBirdCharm(player);
				TimerEntry<Player> timerEntry = new TimerEntry<>() {
					@Override
					public void onStart(@NotNull Player living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", true);
					}

					@Override
					public void onEnd(@NotNull Player living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", false);
					}
				};
				timerEntry.addSkillTimer(player, 0, 20000, 1);
				return;
			}
			for (Entity entity : EntityUtil.findEntitiesAround(player, 6, 100, EntityLargeBird.class)) {
				if (entity instanceof EntityLargeBird entityLargeBird) {
					if (EntityUtil.getDistanceBetweenEntities(player, entityLargeBird) >= 16D) {
						return;
					}
				}
			}
			if (player.getPersistentData().getInt("LeftCountLobocraft") >= 2) {
				player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP);
				EntityLargeBird.clearLargeBirdCharm(player);
				TimerEntry<Player> timerEntry = new TimerEntry<>() {
					@Override
					public void onStart(@NotNull Player living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", true);
					}

					@Override
					public void onEnd(@NotNull Player living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", false);
					}
				};
				timerEntry.addSkillTimer(player, 0, 20000, 1);
				return;
			}
			int i = player.random.nextInt(101);
			if (i <= 10) {
				player.getPersistentData().putInt("LeftCountLobocraft", player.getPersistentData().getInt("LeftCountLobocraft") + 1);
				player.playSound(SoundEvents.PLAYER_HURT);
				return;
			}
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onWorkStart(WorkStartEvent event) {
		if (event.getWorkType() == WorkType.ATTACHMENT && EntityCrumblingArmor.hasCourageCurio(event.getEntity())) {
			EntityCrumblingArmor.clearCourage(event.getEntity());
			EntityCrumblingArmor.executeWorker(event.getEntity(), "§4你背离了破裂盔甲赐予的勇气。");
			event.setCancelReason("");
			event.setCanceled(true);
			return;
		}
		if (!(event.getAbnormality() instanceof EntityPunishingBird)) {
			if (event.getEntity().random.nextFloat() <= 0.2f) {
				int birdsAffected = 0;
				for (EntityPunishingBird punishingBird : EntityUtil.findEntitiesAround(event.getEntity(), 16, 64, EntityPunishingBird.class)) {
					if (!punishingBird.hasEscape()) {
						boolean hasPlayer = false;
						for (Player player : EntityUtil.findPlayersAround(punishingBird, 8, 8)) {
							if (player != null) {
								hasPlayer = true;
								break;
							}
						}
						if (!hasPlayer) {
							punishingBird.decreaseQliphothCounter(1);
							birdsAffected++;
						}
					}
				}
				if (birdsAffected > 0 && event.getEntity().level.isClientSide) {
					event.getEntity().displayClientMessage(Component.literal(
							String.format("§c惩戒鸟感到被忽视，%d只惩戒鸟的计数器减少了！", birdsAffected)
					), false);
					event.getEntity().playSound(
							SoundEvents.ANVIL_DESTROY,
							0.5f,
							1.5f
					);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onWorkComplete(WorkCompleteEvent event) {
		if (!(event.getAbnormality() instanceof EntityChildrenGalaxy)
				&& com.wzz.lobotocraft.util.BuffUtil.hasFriendshipProof(event.getEntity())) {
			int count = com.wzz.lobotocraft.util.BuffUtil.getFriendshipProofCounter(event.getEntity());
			if (count != -1) {
				event.getEntity().setHealth(event.getEntity().getHealth() - (count * 4));
				MentalValueUtil.setMentalValue(event.getEntity(), MentalValueUtil.getMentalValue(event.getEntity()) - (count * 4));
				int childrenAffected = 0;
				for (EntityChildrenGalaxy entityChildrenGalaxy : EntityUtil.findAllEntities(event.getEntity(), 400, EntityChildrenGalaxy.class)) {
					entityChildrenGalaxy.decreaseQliphothCounter(1);
					childrenAffected++;
				}
				if (childrenAffected > 0) {
					event.getEntity().sendSystemMessage(Component.literal(
							String.format("§c%d只银河之子的计数器减少了！", childrenAffected)
					));
					event.getEntity().playNotifySound(
							SoundEvents.ANVIL_DESTROY,
							SoundSource.PLAYERS,
							0.5f,
							1.5f
					);
				}
			}
		}
		if (!event.isForcedEnd() && event.getWorkType() == WorkType.REPRESSION) {
			EntityCrumblingArmor.recordRepressionWork(event.getEntity());
		}
		if (!event.isForcedEnd() && event.getWorkType() == WorkType.INSIGHT) {
			if (EgoArmorHelper.isFullEGO(event.getEntity(), "ppodae")) {
				event.getEntity().heal(event.getEntity().getMaxHealth() * 0.1f);
				event.getEntity().playNotifySound(
						SoundEvents.PLAYER_LEVELUP,
						SoundSource.PLAYERS,
						1,
						1
				);
				ParticleUtil.spawnParticlesAroundEntity(event.getEntity(), ParticleTypes.HEART, 10, 0.5f);
			}
		}
	}

	@SubscribeEvent
	public static void onMentalValueDepleted(MentalValueEvent.Depleted event) {
		int birdsAffected = 0;
		for (EntityPunishingBird punishingBird : EntityUtil.findEntitiesAround(event.getEntity(), 16, 64, EntityPunishingBird.class)) {
			if (!punishingBird.hasEscape()) {
				punishingBird.decreaseQliphothCounter(1);
				birdsAffected++;
			}
		}
		if (birdsAffected > 0 && event.getEntity().level.isClientSide) {
			event.getEntity().displayClientMessage(Component.literal(
					String.format("§c你感受到了恐惧，%d只惩戒鸟的计数器减少了！", birdsAffected)
			), false);
			event.getEntity().playSound(
					SoundEvents.ANVIL_DESTROY,
					0.5f,
					1.5f
			);
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (!event.player.level().isClientSide && event.player instanceof ServerPlayer serverPlayer) {
			clearThinDuskLargeBirdPanic(serverPlayer);
			tickLowHealthSound(serverPlayer);
		}
		if (event.player.getMaxHealth() > MentalValueUtil.getEffectiveMaxMentalValue(event.player) &&
		MentalValueUtil.getMentalValue(event.player) <= 0f) {
			AttackAI.doAttack(event.player);
		}
	}

	private static void tickLowHealthSound(ServerPlayer player) {
		CompoundTag tag = player.getPersistentData();
		int cooldown = tag.getInt(LOW_HEALTH_SOUND_COOLDOWN_TAG);
		if (cooldown > 0) {
			tag.putInt(LOW_HEALTH_SOUND_COOLDOWN_TAG, cooldown - 1);
		}

		if (!player.isAlive() || player.getMaxHealth() <= 0.0F) {
			stopLowHealthSound(player);
			return;
		}

		float healthRatio = player.getHealth() / player.getMaxHealth();
		if (healthRatio <= 0.2F) {
			if (!tag.getBoolean(LOW_HEALTH_SOUND_ACTIVE_TAG) || cooldown <= 0) {
				player.playNotifySound(ModSounds.PLAYER_LOW_HEALTH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
				tag.putInt(LOW_HEALTH_SOUND_COOLDOWN_TAG, LOW_HEALTH_SOUND_COOLDOWN_TICKS);
			}
			tag.putBoolean(LOW_HEALTH_SOUND_ACTIVE_TAG, true);
		} else if (healthRatio >= 0.35F) {
			stopLowHealthSound(player);
		}
	}

	private static void stopLowHealthSound(ServerPlayer player) {
		CompoundTag tag = player.getPersistentData();
		if (tag.getBoolean(LOW_HEALTH_SOUND_ACTIVE_TAG)) {
			stopPlayerSound(player, ModSounds.PLAYER_LOW_HEALTH.get(), SoundSource.PLAYERS);
		}
		tag.putBoolean(LOW_HEALTH_SOUND_ACTIVE_TAG, false);
	}

	private static void stopDeathSounds(ServerPlayer player) {
		stopPlayerSound(player, ModSounds.PLAYER_DEATH.get(), SoundSource.PLAYERS);
		stopPlayerSound(player, ModSounds.PLAYER_DEATH_AFTER.get(), SoundSource.PLAYERS);
	}

	private static void stopPlayerSound(ServerPlayer player, SoundEvent sound, SoundSource soundSource) {
		MessageLoader.getLoader().sendToPlayer(player,
				new StopAmbientSoundPacket(sound.getLocation(), soundSource));
	}

	private static void clearThinDuskLargeBirdPanic(ServerPlayer player) {
		if (!com.wzz.lobotocraft.item.ego.end_bird.EndBirdWeapon.hasThinDuskSetWithCurio(player, ModItems.LARGEBIRD_CURIO.get())) {
			return;
		}
		if (MentalValueUtil.getMentalValue(player) > 0f) {
			return;
		}
		float recoveredMental = Math.max(1.0f, MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.1f);
		MentalValueUtil.setMentalValue(player, recoveredMental);
		player.getPersistentData().putBoolean("isharmla_panic", false);
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		if (event.getEntity() instanceof Player player) {
			if (EgoArmorHelper.isFullEGO(player, "wingbeat")
					|| CuriosUtil.hasCurios(player, ModItems.END_BIRD_CURIO.get())) {
				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerRightBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getEntity().getMainHandItem().getItem() instanceof BaseEgoWeapon) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPlayerRightEntity(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		ItemStack stack = event.getItemStack();
		if (player.getMainHandItem().getItem() instanceof BaseEgoWeapon) {
			event.setCanceled(true);
		}
		if (player.level().isClientSide) {
			return;
		}
		if (!(stack.getItem() instanceof CaptureUnitItem)) {
			return;
		}
		if (!(event.getTarget() instanceof AbstractAbnormality abnormality)) {
			player.displayClientMessage(Component.literal("§c目标实体不是异想体，无法捕获！"), true);
			event.setCanceled(true);
			return;
		}
		if (stack.getOrCreateTag().contains(CaptureUnitItem.ENTITY_UUID)) {
			player.displayClientMessage(Component.literal("§c捕获失败，当前已有捕获的异想体"), true);
			event.setCanceled(true);
			return;
		}
		if (abnormality.hasEscape()) {
			player.displayClientMessage(Component.literal("§c捕获失败！异想体正在出逃无法收容"), true);
			event.setCanceled(true);
			return;
		}
		if (player.getCooldowns().isOnCooldown(stack.getItem())) {
			event.setCanceled(true);
			return;
		}
		CompoundTag entityNbt = new CompoundTag();
		abnormality.saveWithoutId(entityNbt);
		String entityTypeKey = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
				.getKey(abnormality.getType()).toString();
		CompoundTag tag = stack.getOrCreateTag();
		tag.putUUID(CaptureUnitItem.ENTITY_UUID, abnormality.getUUID());
		tag.putString(CaptureUnitItem.ENTITY_CODE, abnormality.getAbnormalityCode());
		tag.putString(CaptureUnitItem.ENTITY_TYPE, entityTypeKey);
		tag.put(CaptureUnitItem.ENTITY_NBT, entityNbt);
		player.displayClientMessage(Component.literal("§a捕获成功！"), true);
		player.playSound(SoundEvents.BOTTLE_FILL, 1.0f, 1.0f);
		abnormality.discard();
		player.getCooldowns().addCooldown(stack.getItem(), 20);
		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			boolean hasBuff = player.hasEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get()) || player.hasEffect(ModMobEffects.LETICIA_GIFT.get());
			if (hasBuff) {
				if (EgoArmorHelper.isFullEGO(player, "leticia") && event.getAmount() >= player.getHealth()) {
					event.setAmount(0f);
					event.setCanceled(true);
					player.playSound(SoundEvents.TOTEM_USE);
					MessageLoader.getLoader().sendToPlayer(player, new DisplayItemActivationPacket(new ItemStack(ModItems.LETICIA_CURIO.get())));
					player.removeEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get());
					player.removeEffect(ModMobEffects.LETICIA_GIFT.get());
				}
			}
		}
	}

	@SubscribeEvent
	public static void onWorkDamage(WorkDamageEvent event) {
		if (event.getWorkType() == WorkType.REPRESSION && EgoArmorHelper.isFullSetWithCurioLocked(event.getPlayer(), "approval_bird")) {
			event.setCanceled(true);
		}
		if (event.getWorkType() == WorkType.ATTACHMENT && CuriosUtil.hasCurios(event.getPlayer(), ModItems.LETICIA_CURIO.get())) {
			int i = new Random().nextInt(101);
			if (i <= 25) {
				event.setCanceled(true);
			}
		}
		if (EgoArmorHelper.isFullEGO(event.getPlayer(), "army_in_black")) {
			event.setCanceled(true);
			event.getPlayer().hurt(DamageHelper.getDamage(event.getAbnormality(), event.getWorkType().getDamageType()), 1f);
		}
	}

	@SubscribeEvent
	public static void onWorkScreenOpen(WorkScreenOpenEvent event) {
		if (event.getEntity().getMainHandItem().getItem() instanceof CaptureUnitItem) {
			event.setCanceled(true);
		}
		if (!(event.getAbnormality() instanceof EntityWingBeat) && !event.getEntity().level.isClientSide
				&& event.getEntity().getPersistentData().getBoolean("isInWingBeat") && event.isOpeningWorkScreen()) {
			event.getEntity().sendSystemMessage(Component.literal("§c你因尝试在精灵盛宴的祝福下对其他异想体工作被精灵盛宴啃噬了..."));
			event.getEntity().hurt(DamageHelper.getDamage().getDamageSources().fellOutOfWorld(), event.getEntity().getMaxHealth());
			event.getEntity().playSound(ModSounds.WINGBEAT_KILL_PLAYER.get());
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onDayAdvance(CompanyDayAdvanceEvent event) {
		SuppressorCounterUtil.restoreToFull(event.getPlayer());
		MessageLoader.getLoader().sendToPlayer(event.getPlayer(),
				new com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket(SuppressorCounterUtil.MAX_COUNT));
		com.wzz.lobotocraft.util.BuffUtil.removeFriendshipProof(event.getPlayer());
		com.wzz.lobotocraft.util.BuffUtil.removeKiss(event.getPlayer());
		if (event.getPlayer().getPersistentData().getBoolean("isSnowQueen")) {
			EntitySnowQueen.killPlayer(event.getPlayer());
		}
		if (event.getNewDay() == 2 && !ItemUtil.hasItem(event.getPlayer(), ModItems.TT2.get())) {
			ItemUtil.addItem(event.getPlayer(), new ItemStack(ModItems.TT2.get()));
		}
		if (event.getNewDay() == 2 && !ItemUtil.hasItem(event.getPlayer(), ModItems.WORK_DEVICE.get())) {
			ItemUtil.addItem(event.getPlayer(), new ItemStack(ModItems.WORK_DEVICE.get()));
		}
		if (event.getNewDay() >= 3) {
			grantOrdealAdvancement(event.getPlayer());
		}
	}

	private static void grantOrdealAdvancement(ServerPlayer player) {
		var advancement = player.server.getAdvancements().getAdvancement(ORDEAL_ADVANCEMENT);
		if (advancement != null) {
			player.getAdvancements().award(advancement, "reached_day_three");
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		Player player = event.getEntity();
		if (player.getPersistentData().getBoolean("isSnowQueen")
				|| player.getPersistentData().getBoolean("isSnowQueenDuel")) {
			player.getPersistentData().remove("isSnowQueen");
			player.getPersistentData().remove("isSnowQueenDuel");
			player.setTicksFrozen(0);
			BuffUtil.removeKiss(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		TimerEntry.shutdown(event.getEntity());
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity().getPersistentData().getBoolean("isSnowQueen")) {
			event.getEntity().setTicksFrozen(60);
			if (event.getEntity().tickCount % 30 == 0) {
				ParticleUtil.spawnParticlesAroundEntity(event.getEntity(), ParticleTypes.SNOWFLAKE, 10, 0.1d);
			}
			if (event.getEntity() instanceof ServerPlayer serverPlayer) {
				MessageLoader.getLoader().sendToPlayer(serverPlayer, new OpenChatScreenPacket());
			}
		}
	}

	@SubscribeEvent
	public static void onWorldUnload(LevelEvent.Unload event) {
		TimerEntry.shutdownAll();
	}

	@SubscribeEvent
	public static void onWorldLoad(LevelEvent.Load event) {
		if (!TimerEntry.isSchedulerRunning()) {
			TimerEntry.reinitializeScheduler();
		}
		if (event.getLevel() instanceof ServerLevel level && level.dimension == ModDimensions.LOBOTO_KEY) {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		}
	}

	@SubscribeEvent
	public static void onServerStarting(ServerStartingEvent event) {
		TimerEntry.reinitializeScheduler();
	}
}