package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityData;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.event.definition.work.WorkScreenOpenEvent;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenManualScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenToolUseScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenWorkScreenPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 异想体工作交互处理器
 * 处理玩家右键异想体打开工作界面的逻辑
 */
@Mod.EventBusSubscriber
public class AbnormalityWorkHandler {

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getTarget() instanceof IAbnormality abnormality && !event.getLevel().isClientSide) {
			Player player = event.getEntity();
			boolean isShiftClick = player.isShiftKeyDown();
			WorkScreenOpenEvent screenOpenEvent = new WorkScreenOpenEvent(player, abnormality, isShiftClick);
			if (MinecraftForge.EVENT_BUS.post(screenOpenEvent)) {
				event.setCancellationResult(InteractionResult.FAIL);
				event.setCanceled(true);
				return;
			}
            ServerPlayer serverPlayer = (ServerPlayer) player;
            if (isShiftClick) {
				// Shift + 右键：打开图鉴界面
                if (!abnormality.onOpenManualScreen(serverPlayer)) {
					return;
				}
				String abnormalityCode = abnormality.getAbnormalityCode();
				serverPlayer.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
						.ifPresent(data -> {
							openAbnormalityEncyclopediaScreen(serverPlayer, abnormality, data, abnormalityCode, 0);
						});

			} else {
				// 普通右键：根据异想体类型打开不同界面
                // 检查玩家是否正在工作
				if (WorkManager.isPlayerWorking(serverPlayer)) {
					serverPlayer.displayClientMessage(Component.literal("§c你正在进行工作，无法开始新的工作！"), false);
					event.setCancellationResult(InteractionResult.FAIL);
					event.setCanceled(true);
					return;
				}
				if (!abnormality.onOpenWorkScreen(serverPlayer)) {
					return;
				}
				// 检查精神值状态（恐慌/疯狂）
				final boolean[] isMentalBroken = {false};
				final boolean[] isBerserk = {false};
				serverPlayer.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
					if (mental.isMentalValueEmpty()) {
						isMentalBroken[0] = true;
						// 判断是恐慌还是疯狂
						float maxHealth = serverPlayer.getMaxHealth();
						float maxMental = mental.getEffectiveMaxMentalValue();
						isBerserk[0] = maxHealth > maxMental;
					}
				});
				// 恐慌或疯狂状态下无法打开工作面板
				if (isMentalBroken[0]) {
					if (isBerserk[0]) {
						serverPlayer.displayClientMessage(Component.literal("§c你陷入了疯狂状态，无法进行工作！"), false);
					} else {
						serverPlayer.displayClientMessage(Component.literal("§c你陷入了恐慌状态，无法进行工作！"), false);
					}
					event.setCancellationResult(InteractionResult.FAIL);
					event.setCanceled(true);
					return;
				}

				String abnormalityCode = abnormality.getAbnormalityCode();
				// 检查是否为工具类型异想体
				if (abnormality.isToolType()) {
					// 打开工具使用界面
					if (!serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
						MessageLoader.getLoader().sendToPlayer(serverPlayer,
								new OpenToolUseScreenPacket(
										abnormality.getEntityId(),
										abnormality.getAbnormalityName(),
										abnormality.isContinuousUseTool() ? "continuous" : "normal",
										abnormality.getToolWarningTitle(),
										abnormality.getToolWarningMessages()
								)
						);
					}
				} else {
					serverPlayer.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
							.ifPresent(data -> {
								serverPlayer.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
										.ifPresent(employeeStats -> {
											int obsLevel = data.getObservationLevel(abnormalityCode);
											boolean[] workPrefsUnlocked = new boolean[4];
											for (int i = 0; i < 4; i++) {
												workPrefsUnlocked[i] = data.isManualUnlocked(abnormalityCode, 100 + i);
											}
											// 普通异想体：打开工作选择界面
											MessageLoader.getLoader().sendToPlayer(serverPlayer,
													new OpenWorkScreenPacket(
															abnormality.getEntityId(),
															abnormality.getFullWorkPreferences(),
															abnormality.getRiskLevel(),
															obsLevel,
															employeeStats.getEmployeeLevel(),
															workPrefsUnlocked
													)
											);
										});
							});
				}
			}
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

	public static void openAbnormalityEncyclopediaScreen(ServerPlayer player, IAbnormality abnormality, @NotNull PlayerAbnormalityData data, String abnormalityCode, double scrollOffset) {
		int obsLevel = data.getObservationLevel(abnormalityCode);
		boolean basicInfo = data.isManualUnlocked(abnormalityCode, 0);
		boolean[] workPrefsUnlocked = new boolean[4];
		for (int i = 0; i < 4; i++) {
			workPrefsUnlocked[i] = data.isManualUnlocked(abnormalityCode, 100 + i);
		}
		boolean sensitiveInfo = data.isManualUnlocked(abnormalityCode, 2);
		AbnormalityEncyclopediaData.EntryData entryData =
				AbnormalityEncyclopediaData.getData(abnormalityCode);
		int manualCount = entryData.getManualCount();
		boolean[] manualsUnlocked = new boolean[manualCount];
		for (int i = 0; i < manualCount; i++) {
			manualsUnlocked[i] = data.isManualUnlocked(abnormalityCode, i + 3);
		}
		MessageLoader.getLoader().sendToPlayer(player,
				new OpenManualScreenPacket(
						abnormality.getEntityId(),
						abnormality.getAbnormalityCode(),
						abnormality.getAbnormalityName(),
						abnormality.getRiskLevel(),
						abnormality.getDamageType(),
						abnormality.getMaxPEOutput(),
						abnormality.getFullWorkPreferences(),
						obsLevel,
						basicInfo, workPrefsUnlocked, sensitiveInfo, manualsUnlocked,
						abnormality.getBasicInfoCost(),
						abnormality.getWorkPreferencesCost(),
						abnormality.getSensitiveInfoCost(),
						abnormality.getManualCost(0),
						data.getEquipmentDevelopmentCount(abnormalityCode, "weapon"),
						data.getEquipmentDevelopmentCount(abnormalityCode, "armor"),
						scrollOffset,
						abnormality.isToolType()
				)
		);
	}
}