package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.*;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.util.ItemUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class CapabilityEventHandler {

    // 死亡备份数据，防止数据丢失
    private static final Map<UUID, CompoundTag> deathBackupData = new HashMap<>();
    private static final Map<UUID, CompoundTag> deathBackupStats = new HashMap<>();
    private static final Map<UUID, CompoundTag> deathBackupDaily = new HashMap<>();

    /**
     * 注册Capability
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerAbnormalityData.class);
        event.register(EmployeeStats.class);
        event.register(CompanyDailyData.class);
        event.register(ProtectedBlocksCapability.class);
    }

    /**
     * 附加Capability到玩家
     */
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            // 附加异想体数据
            if (!event.getObject().getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).isPresent()) {
                event.addCapability(
                        ResourceUtil.createInstance("player_abnormality_data"),
                        new PlayerAbnormalityDataProvider()
                );
            }

            // 附加员工属性
            if (!event.getObject().getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).isPresent()) {
                event.addCapability(
                        ResourceUtil.createInstance("employee_stats"),
                        new EmployeeStatsProvider()
                );
            }

            // 附加公司日常数据
            if (!event.getObject().getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).isPresent()) {
                event.addCapability(
                        ResourceUtil.createInstance("company_daily_data"),
                        new CompanyDailyDataProvider()
                );
            }
        }
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesLevelChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        if (!event.getObject().getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS).isPresent()) {
            event.addCapability(ResourceUtil.createInstance("protected_blocks"), new ProtectedBlocksProvider());
        }
    }

    /**
     * 玩家死亡时备份数据
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 备份异想体数据
            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
                CompoundTag backupData = data.serializeNBT();
                deathBackupData.put(player.getUUID(), backupData);
            });

            // 备份员工属性
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                CompoundTag backupStats = new CompoundTag();
                if (stats instanceof EmployeeStats employeeStats) {
                    employeeStats.saveToNBT(backupStats);
                }
                deathBackupStats.put(player.getUUID(), backupStats);
            });

            // 备份公司日常数据
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                CompoundTag backupDaily = data.serializeNBT();
                deathBackupDaily.put(player.getUUID(), backupDaily);
            });
        }
    }

    /**
     * 玩家克隆时恢复数据（死亡重生和从末地返回）
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        UUID playerUUID = event.getEntity().getUUID();

        // 检查是否因为死亡而克隆（而不是从末地返回）
        boolean isDeath = event.isWasDeath();

        // 检查游戏规则是否保留物品栏
        boolean keepInventory = false;
        if (event.getEntity() instanceof ServerPlayer player) {
            keepInventory = player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        }

        // 恢复异想体数据（观察等级等，始终保留）
        CompoundTag backupData = deathBackupData.get(playerUUID);
        if (backupData != null) {
            event.getEntity().getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(newData -> {
                newData.deserializeNBT(backupData);
            });
            deathBackupData.remove(playerUUID);
        } else {
            event.getOriginal().getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(oldData -> {
                event.getEntity().getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(newData -> {
                    CompoundTag data = oldData.serializeNBT();
                    newData.deserializeNBT(data);
                });
            });
        }

        // 异想体机制处决(如银河之子计数器归零)致死时,不应触发死亡属性清空惩罚
        boolean abnormalityExecuted = event.getOriginal().getPersistentData()
                .getBoolean("lobotocraft_abnormality_executed");

        // 员工属性的处理：非死亡克隆、开启keepInventory、或异想体处决致死时恢复
        if (!isDeath || keepInventory || abnormalityExecuted) {
            // 从末地返回、开启了keepInventory、或被异想体机制处决，恢复员工属性
            CompoundTag backupStats = deathBackupStats.get(playerUUID);
            if (backupStats != null) {
                event.getEntity().getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(newStats -> {
                    if (newStats instanceof EmployeeStats employeeStats) {
                        employeeStats.loadFromNBT(backupStats);
                    }
                });
                deathBackupStats.remove(playerUUID);
            } else {
                event.getOriginal().getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(oldStats -> {
                    event.getEntity().getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(newStats -> {
                        if (oldStats instanceof EmployeeStats oldEmployeeStats &&
                                newStats instanceof EmployeeStats newEmployeeStats) {
                            newEmployeeStats.copyFrom(oldEmployeeStats);
                        }
                    });
                });
            }
        } else {
            deathBackupStats.remove(playerUUID);
        }
        // 清除处决标记,避免影响后续的正常死亡惩罚
        event.getEntity().getPersistentData().remove("lobotocraft_abnormality_executed");
        event.getOriginal().getPersistentData().remove("lobotocraft_abnormality_executed");

        CompoundTag backupDaily = deathBackupDaily.get(playerUUID);
        if (backupDaily != null) {
            event.getEntity().getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(newData -> {
                newData.deserializeNBT(backupDaily);
            });
            deathBackupDaily.remove(playerUUID);
        } else {
            event.getOriginal().getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(oldData -> {
                event.getEntity().getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    if (newData.isArmorLocked()) {
                        if (event.getEntity() instanceof ServerPlayer serverPlayer)
                            newData.setOwner(serverPlayer);
                        newData.unlockArmor();
                        ItemUtil.addItem(event.getEntity(), new ItemStack(ModItems.ARMOR_LOCK.get()));
                    }
                });
            });
        }
    }

    /**
     * 玩家死亡重生后,向客户端重新同步天数数据。
     * 修复:死亡后 onPlayerClone 虽已复制服务端天数,但未发送同步包,
     * 导致客户端 HUD 仍显示重生新实体的默认值(第1天)或旧值,造成天数显示错误。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncDailyToClient(player);
        }
    }

    /**
     * 玩家切换维度后(例如进入公司维度)同步天数,确保 HUD 显示正确。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncDailyToClient(player);
            // 玩家切换维度时,精灵盛宴的祝福随之失效,清除标记避免回来后误触机制杀
            player.getPersistentData().putBoolean("isInWingBeat", false);
        }
    }

    /**
     * 玩家登录时同步天数。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncDailyToClient(player);
        }
    }

    private static void syncDailyToClient(ServerPlayer player) {
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            MessageLoader.getLoader().sendToPlayer(player,
                    new CompanyDailySyncPacket(
                            data.getCurrentDay(),
                            data.getTodayWorkCount(),
                            data.isArmorLocked(),
                            data.isHasSleep()
                    )
            );
        });
    }
}