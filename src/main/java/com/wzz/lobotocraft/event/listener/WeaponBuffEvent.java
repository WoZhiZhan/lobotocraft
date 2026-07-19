package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.end_bird.EndBirdWeapon;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 正义裁决者 / 悔恨 套装的"造成伤害后获得 buff"效果(装备锁定后生效)。
 *
 * 正义裁决者(补充4 第2条):使用正义裁决者武器攻击,获得 10% 攻速和移速加成,
 *   持续10秒,不可叠加,每次攻击刷新持续时间。
 * 悔恨:使用悔恨武器造成伤害后,10秒内减少 20% 移动速度、造成的红色伤害 +10%,
 *   不可叠加,每次造成伤害刷新持续时间。
 *
 * 实现:造成伤害时(在 ForgeModEvent.onLivingHurt 调用 trigger* 方法)记录到期时间到 persistentData,
 * 由此处 PlayerTick 维护属性 modifier 的增删,过期自动移除。红伤+10% 由 ForgeModEvent 读取标记处理。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class WeaponBuffEvent {

    public static final String JUSTICE_BUFF_UNTIL = "lobotocraft_justice_buff_until";
    public static final String ABANDONED_MURDERER_BUFF_UNTIL = "lobotocraft_abandoned_murderer_buff_until";
    public static final String THIN_DUSK_APPROVAL_BUFF_UNTIL = "lobotocraft_thin_dusk_approval_buff_until";

    private static final UUID JUSTICE_AS_UUID = UUID.fromString("c0ffee00-0001-4000-8000-000000000001");
    private static final UUID JUSTICE_MS_UUID = UUID.fromString("c0ffee00-0002-4000-8000-000000000002");
    private static final UUID ABANDONED_MURDERER_MS_UUID = UUID.fromString("c0ffee00-0003-4000-8000-000000000003");
    private static final UUID THIN_DUSK_APPROVAL_AS_UUID = UUID.fromString("c0ffee00-0004-4000-8000-000000000004");
    private static final UUID THIN_DUSK_APPROVAL_MS_UUID = UUID.fromString("c0ffee00-0005-4000-8000-000000000005");

    private static final int BUFF_DURATION = 200; // 10秒

    /** 正义裁决者武器命中:刷新攻速移速 buff(装备锁定+全套+饰品才生效) */
    public static void triggerJusticeBuff(Player player) {
        if (!EgoArmorHelper.isFullSetWithCurioLocked(player, "approval_bird")) return;
        if (!EgoArmorHelper.isHoldingWeapon(player, "approval_bird")) return;
        player.getPersistentData().putLong(JUSTICE_BUFF_UNTIL, player.level().getGameTime() + BUFF_DURATION);
    }

    /** 悔恨武器命中:刷新减速 buff(装备锁定+全套+饰品才生效);红伤+10% 由调用方处理 */
    public static void triggerAbandonedMurdererBuff(Player player) {
        if (!EgoArmorHelper.isFullSetWithCurioLocked(player, "abandoned_murderer")) return;
        if (!EgoArmorHelper.isHoldingWeapon(player, "abandoned_murderer")) return;
        player.getPersistentData().putLong(ABANDONED_MURDERER_BUFF_UNTIL, player.level().getGameTime() + BUFF_DURATION);
    }

    /** 薄暝 + 破晓(审判鸟):命中后刷新 30% 攻速移速 buff */
    public static void triggerThinDuskApprovalBuff(Player player) {
        if (!EndBirdWeapon.hasThinDuskSetWithCurio(player, ModItems.APPROVAL_BIRD_CURIO.get())) return;
        player.getPersistentData().putLong(THIN_DUSK_APPROVAL_BUFF_UNTIL, player.level().getGameTime() + BUFF_DURATION);
    }

    /** 悔恨 buff 是否激活(供 ForgeModEvent 判断红伤+10%) */
    public static boolean isAbandonedMurdererBuffActive(Player player) {
        return player.getPersistentData().getLong(ABANDONED_MURDERER_BUFF_UNTIL) > player.level().getGameTime();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        long now = player.level().getGameTime();

        // 正义裁决者:攻速+10% 移速+10%
        AttributeInstance as = player.getAttribute(Attributes.ATTACK_SPEED);
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean justiceActive = player.getPersistentData().getLong(JUSTICE_BUFF_UNTIL) > now;
        if (as != null) {
            boolean has = as.getModifier(JUSTICE_AS_UUID) != null;
            if (justiceActive && !has) {
                as.addTransientModifier(new AttributeModifier(JUSTICE_AS_UUID, "justice_as", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!justiceActive && has) {
                as.removeModifier(JUSTICE_AS_UUID);
            }
        }
        if (ms != null) {
            boolean has = ms.getModifier(JUSTICE_MS_UUID) != null;
            if (justiceActive && !has) {
                ms.addTransientModifier(new AttributeModifier(JUSTICE_MS_UUID, "justice_ms", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!justiceActive && has) {
                ms.removeModifier(JUSTICE_MS_UUID);
            }
        }

        // 悔恨:移速-20%
        boolean abandonedMurdererActive = player.getPersistentData().getLong(ABANDONED_MURDERER_BUFF_UNTIL) > now;
        if (ms != null) {
            boolean has = ms.getModifier(ABANDONED_MURDERER_MS_UUID) != null;
            if (abandonedMurdererActive && !has) {
                ms.addTransientModifier(new AttributeModifier(ABANDONED_MURDERER_MS_UUID, "abandoned_murderer_ms", -0.20, AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!abandonedMurdererActive && has) {
                ms.removeModifier(ABANDONED_MURDERER_MS_UUID);
            }
        }

        // 薄暝 + 破晓(审判鸟):攻速+30% 移速+30%
        boolean thinDuskApprovalActive = player.getPersistentData().getLong(THIN_DUSK_APPROVAL_BUFF_UNTIL) > now;
        if (as != null) {
            boolean has = as.getModifier(THIN_DUSK_APPROVAL_AS_UUID) != null;
            if (thinDuskApprovalActive && !has) {
                as.addTransientModifier(new AttributeModifier(THIN_DUSK_APPROVAL_AS_UUID, "thin_dusk_approval_as", 0.30, AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!thinDuskApprovalActive && has) {
                as.removeModifier(THIN_DUSK_APPROVAL_AS_UUID);
            }
        }
        if (ms != null) {
            boolean has = ms.getModifier(THIN_DUSK_APPROVAL_MS_UUID) != null;
            if (thinDuskApprovalActive && !has) {
                ms.addTransientModifier(new AttributeModifier(THIN_DUSK_APPROVAL_MS_UUID, "thin_dusk_approval_ms", 0.30, AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!thinDuskApprovalActive && has) {
                ms.removeModifier(THIN_DUSK_APPROVAL_MS_UUID);
            }
        }
    }
}
