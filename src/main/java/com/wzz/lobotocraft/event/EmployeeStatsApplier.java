package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.MentalValue;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.MentalValueSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class EmployeeStatsApplier {

    private static final UUID FORTITUDE_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID JUSTICE_SPEED_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID JUSTICE_ATTACK_MODIFIER_UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyAllAttributes(player);
            syncAttributesToClient(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 1,
                () -> {
                    applyAllAttributes(player);
                    syncAttributesToClient(player);
                }
            ));
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 1,
                () -> {
                    applyAllAttributes(player);
                    syncAttributesToClient(player);
                }
            ));
        }
    }

    public static void applyAllAttributes(ServerPlayer player) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            applyFortitude(player, stats.getFortitude());
            applyPrudence(player, stats.getPrudence());
            applyJustice(player, stats.getJustice());

            // Bug fix 3: 玩家加入/重生时，把 capability 里保存的 extraMentalValue
            // 重新同步给 player attribute，防止 NBT 加载后 attribute 没有对应 modifier
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                if (mental instanceof MentalValue mv) {
                    mv.syncExtraToAttribute();
                }
            });
        });
    }

    /**
     * Bug fix 3: sync 时改用 getEffectiveMaxMentalValue()，
     * 确保客户端收到的最大精神值包含 extra 部分，
     * 避免最大值显示和实际上限不一致。
     */
    private static void syncAttributesToClient(ServerPlayer player) {
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            MessageLoader.getLoader().sendToPlayer(player,
                    new com.wzz.lobotocraft.network.packet.EmployeeStatsSyncPacket(
                            stats.getFortitude(),
                            stats.getPrudence(),
                            stats.getTemperance(),
                            stats.getJustice()
                    )
            );
        });

        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            MessageLoader.getLoader().sendToPlayer(player,
                    new MentalValueSyncPacket(
                            mental.getMentalValue(),
                            mental.getEffectiveMaxMentalValue()
                    )
            );
        });
    }

    /**
     * 勇气 → 最大生命值
     */
    private static void applyFortitude(ServerPlayer player, int fortitude) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        float currentHealth = player.getHealth();
        float oldMaxHealth = player.getMaxHealth();
        float healthRatio = oldMaxHealth > 0 ? currentHealth / oldMaxHealth : 1.0f;

        maxHealth.removeModifier(FORTITUDE_MODIFIER_UUID);

        double bonusHealth = (fortitude - 20);

        if (bonusHealth > 0) {
            AttributeModifier modifier = new AttributeModifier(
                FORTITUDE_MODIFIER_UUID,
                "Fortitude Health Bonus",
                bonusHealth,
                AttributeModifier.Operation.ADDITION
            );
            maxHealth.addPermanentModifier(modifier);
        }

        float newMaxHealth = player.getMaxHealth();
        float newHealth = Math.min(newMaxHealth, newMaxHealth * healthRatio);
        player.setHealth(newHealth);
    }

    /**
     * 谨慎 → 最大精神值
     */
    private static void applyPrudence(ServerPlayer player, int prudence) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            float maxMental = prudence;
            mental.setMaxMentalValue(maxMental);

            if (mental.getMentalValue() > maxMental) {
                mental.setMentalValue(maxMental);
            }

            // Bug fix 3: sync 统一在 syncAttributesToClient 中发，
            // 这里不再单独发，避免发出不含 extra 的旧值覆盖正确值
        });
    }

    /**
     * 正义 → 移动速度和攻击速度
     */
    private static void applyJustice(ServerPlayer player, int justice) {
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(JUSTICE_SPEED_MODIFIER_UUID);

            double speedBonus = (justice - 20) * 0.005;
            if (speedBonus > 0) {
                AttributeModifier modifier = new AttributeModifier(
                    JUSTICE_SPEED_MODIFIER_UUID,
                    "Justice Speed Bonus",
                    speedBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
                );
                moveSpeed.addPermanentModifier(modifier);
            }
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(JUSTICE_ATTACK_MODIFIER_UUID);

            double attackBonus = (justice - 20) * 0.0025;
            if (attackBonus > 0) {
                AttributeModifier modifier = new AttributeModifier(
                    JUSTICE_ATTACK_MODIFIER_UUID,
                    "Justice Attack Speed Bonus",
                    attackBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
                );
                attackSpeed.addPermanentModifier(modifier);
            }
        }
    }
}