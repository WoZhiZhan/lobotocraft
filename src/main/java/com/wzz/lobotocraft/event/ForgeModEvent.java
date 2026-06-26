package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
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
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.LargeBirdBorderPacket;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import com.wzz.lobotocraft.network.packet.OpenChatScreenPacket;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

import static com.wzz.lobotocraft.item.CaptureUnitItem.*;
import static com.wzz.lobotocraft.util.DamageHelper.*;

@Mod.EventBusSubscriber
public class ForgeModEvent {
	private static final net.minecraft.resources.ResourceLocation ORDEAL_ADVANCEMENT =
			ResourceUtil.createInstance("ordeal");

	@SubscribeEvent
	public static void onLivingAttack(LivingAttackEvent event) {
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
			if (EgoArmorHelper.isFullEGO(player, "fourth_match_flame")) {
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
		LivingEntity entity = event.getEntity();
		Holder<DamageType> holder = src.typeHolder();
		Optional<ResourceKey<DamageType>> optionalKey = holder.unwrapKey();
		if (optionalKey.isEmpty() || entity == null) {
			return;
		}
		if (src.getEntity() instanceof Player player) {
			// 补充4第2条:正义裁决者武器命中刷新攻速移速buff
			com.wzz.lobotocraft.event.WeaponBuffEvent.triggerJusticeBuff(player);
			// 悔恨武器命中刷新减速buff,且buff期间造成的红色伤害+10%
			com.wzz.lobotocraft.event.WeaponBuffEvent.triggerAbandonedMurdererBuff(player);
			if (com.wzz.lobotocraft.event.WeaponBuffEvent.isAbandonedMurdererBuffActive(player)
					&& EgoArmorHelper.isHoldingWeapon(player, "abandoned_murderer") && isRedDamage(src)) {
				event.setAmount(event.getAmount() * 1.1f);
			}
			if (EgoArmorHelper.isFullEGO(player, "punishing_bird")
					&& EgoArmorHelper.isHoldingWeapon(player, "punishing_bird")
					&& player.random.nextFloat() <= 0.1f) {
				event.setAmount(event.getAmount() * 2f);
			}
			if (EgoArmorHelper.isFullEGO(player, "fourth_match_flame") && entity.isOnFire()) {
				event.setAmount(event.getAmount() * 1.2f);
			}
			if (EgoArmorHelper.isFullEGO(player, "wingbeat") && EgoArmorHelper.isHoldingWeapon(player, "wingbeat")) {
				player.heal(event.getAmount());
			}
			if (EgoArmorHelper.isFullEGO(player, "end_bird")
					&& EgoArmorHelper.isHoldingWeapon(player, "end_bird")
					&& CuriosUtil.hasCurios(player, ModItems.PUNISHING_BIRD_CURIO.get())
					&& !com.wzz.lobotocraft.item.ego.end_bird.EndBirdWeapon.isThinDuskSpecialDamage(player)) {
				player.heal(event.getAmount() * 0.1f);
			}
			com.wzz.lobotocraft.event.WeaponBuffEvent.triggerThinDuskApprovalBuff(player);
			if ((EgoArmorHelper.isFullEGO(player, "end_bird") && EgoArmorHelper.isHoldingWeapon(player, "end_bird")) ||
					EgoArmorHelper.hasEquipmentCombination(player, "end_bird", true, false, true)) {
				float multiplier = EntityUtil.getDamageMultiplierByLostHealth(player, 2.0f, 1.0f);
				event.setAmount(event.getAmount() * multiplier);
			}
			if (CuriosUtil.hasCurios(player, ModItems.ABANDONED_MURDERER_CURIO.get())) {
				event.setAmount(event.getAmount() + 1f);
			}
			if (CuriosUtil.hasCurios(player, ModItems.END_BIRD_CURIO.get())) {
				event.setAmount(event.getAmount() * 1.1f);
			}
		}
		if (entity instanceof ServerPlayer player) {
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
		}

		// 判断伤害类型
		boolean isRedDamage = DamageHelper.isRedDamage(src);
		boolean isWhiteDamage = DamageHelper.isWhiteDamage(src);
		boolean isBlackDamage = DamageHelper.isBlackDamage(src);
		boolean isBlueDamage = DamageHelper.isBlueDamage(src);

		// 处理非玩家的颜色伤害抗性
		if (!(entity instanceof Player) && (isRedDamage || isWhiteDamage || isBlackDamage || isBlueDamage)) {
			AttributeInstance resistanceAttr;
			if (isRedDamage) {
				resistanceAttr = entity.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
			} else if (isWhiteDamage) {
				resistanceAttr = entity.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
			} else if (isBlackDamage) {
				resistanceAttr = entity.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
			} else {
				resistanceAttr = entity.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
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
			ParticleUtil.spawnParticlesAroundEntity(entity, ModParticleTypes.RED.get(), 1, 0);
		} else if (isWhiteDamage) {
			ParticleUtil.spawnParticlesAroundEntity(entity, ModParticleTypes.WHITE.get(), 1, 0);
		} else if (isBlackDamage) {
			ParticleUtil.spawnParticlesAroundEntity(entity, ModParticleTypes.BLACK.get(), 1, 0);
			if (entity.getPersistentData().getBoolean("isLargeBirdWeaponBlackDamage")) {
				event.setAmount(event.getAmount() * 1.5f);
			}
		} else if (isBlueDamage) {
			ParticleUtil.spawnParticlesAroundEntity(entity, ModParticleTypes.BLUE.get(), 1, 0);
		} else if (isVanillaDamage(key)) {
			ParticleUtil.spawnParticlesAroundEntity(entity, ModParticleTypes.RED.get(), 1, 0);
		}
        if (entity instanceof Player && DamageHelper.isExecution(event.getSource())) {
			return;
		}
		if (!isRedDamage && !isWhiteDamage && !isBlackDamage && !isBlueDamage) {
			return;
		}
		AttributeInstance resistanceAttr = null;
		if (isRedDamage) {
			resistanceAttr = entity.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
		} else if (isWhiteDamage) {
			resistanceAttr = entity.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
		} else if (isBlackDamage) {
			resistanceAttr = entity.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
		} else {
			resistanceAttr = entity.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
		}
		if (resistanceAttr == null) return;
		double resistance = resistanceAttr.getValue();

		if (!(entity instanceof Player player)) {
			return;
		}
		if (resistance < 0) {
			event.setCanceled(true);
			float healAmount = event.getAmount() * (float) Math.abs(resistance);
			if (healAmount <= 0) return;
			if (isRedDamage || isBlueDamage) {
				entity.heal(healAmount);
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
				entity.heal(healAmount);
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
	public static void onPlayerClone(PlayerEvent.Clone event) {
		if (!event.isWasDeath()) return;

		Player oldPlayer = event.getOriginal();
		Player newPlayer = event.getEntity();
		if (newPlayer.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
		Inventory oldInv = oldPlayer.getInventory();
		Inventory newInv = newPlayer.getInventory();

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
		if (event.getEntity() instanceof Player player) {
			if (PlayerControlLock.isLocked(player)) {
				PlayerControlLock.unlock(player);
			}
			player.getPersistentData().putBoolean("isInWingBeat", false);
			for (ItemStack itemStack : player.inventory.items) {
				if (itemStack.getItem() instanceof PEBoxItem) {
					itemStack.setCount((int) (itemStack.getCount() * 0.3));
				}
			}
			player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> data.setTodayWorkCount(0));
			if (player.level() instanceof ServerLevel level) {
				OrdealData.get(level).resetDawnTriggersToday();
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
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDeathLow(LivingDeathEvent event) {
		if (event.getEntity() instanceof Player player) {
			player.playNotifySound(ModSounds.PLAYER_DEATH.get(), SoundSource.PLAYERS, 1, 1);
		}
	}

	@SubscribeEvent
	public static void onLivingSwing(LivingSwingEvent.Pre event) {
		if (event.getEntity() instanceof Player player && player.getPersistentData().getBoolean("isLargeBirdCharm") && !player.level.isClientSide) {
			if (EgoArmorHelper.isFullEGO(player, "end_bird")
					&& EgoArmorHelper.isHoldingWeapon(player, "end_bird")
					&& CuriosUtil.hasCurios(player, ModItems.LARGEBIRD_CURIO.get())) {
				if (player instanceof ServerPlayer serverPlayer) {
					MessageLoader.getLoader().sendToPlayer(serverPlayer, new LargeBirdBorderPacket(0));
				}
				PlayerControlLock.unlock(player);
				player.getPersistentData().remove("LeftCountLobocraft");
				player.getPersistentData().remove("isLargeBirdCharm");
				TimerEntry timerEntry = new TimerEntry() {
					@Override
					public void onStart(@NotNull LivingEntity living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", true);
					}

					@Override
					public void onEnd(@NotNull LivingEntity living) {
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
				if (player instanceof ServerPlayer serverPlayer)
					MessageLoader.getLoader().sendToPlayer(serverPlayer, new LargeBirdBorderPacket(0));
				PlayerControlLock.unlock(player);
				TimerEntry timerEntry = new TimerEntry() {
					@Override
					public void onStart(@NotNull LivingEntity living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", true);
					}

					@Override
					public void onEnd(@NotNull LivingEntity living) {
						living.getPersistentData().putBoolean("notLargeBirdCharm", false);
					}
				};
				timerEntry.addSkillTimer(player, 0, 20000, 1);
				player.getPersistentData().remove("LeftCountLobocraft");
				player.getPersistentData().remove("isLargeBirdCharm");
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
				for (EntityChildrenGalaxy entityChildrenGalaxy : EntityUtil.findEntitiesAround(event.getEntity(), 16, 64, EntityChildrenGalaxy.class)) {
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
		}
		if (event.player.getMaxHealth() > MentalValueUtil.getEffectiveMaxMentalValue(event.player) &&
		MentalValueUtil.getMentalValue(event.player) <= 0f) {
			AttackAI.doAttack(event.player);
		}
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
			if (EgoArmorHelper.isFullEGO(player, "wingbeat")) {
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
	public static void onWorkDamage(WorkDamageEvent event) {
		if (event.getWorkType() == WorkType.REPRESSION && EgoArmorHelper.isFullSetWithCurioLocked(event.getPlayer(), "approval_bird")) {
			event.setCanceled(true);
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
