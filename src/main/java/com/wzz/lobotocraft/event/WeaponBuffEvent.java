package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
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
 * 悔恨(补充7):使用悔恨武器造成伤害后,10秒内减少 20% 移动速度、造成的红色伤害 +10%,
 *   不可叠加,每次造成伤害刷新持续时间。
 *
 * 实现:造成伤害时(在 ForgeModEvent.onLivingHurt 调用 trigger* 方法)记录到期时间到 persistentData,
 * 由此处 PlayerTick 维护属性 modifier 的增删,过期自动移除。红伤+10% 由 ForgeModEvent 读取标记处理。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class WeaponBuffEvent {

    public static final String JUSTICE_BUFF_UNTIL = "lobotocraft_justice_buff_until";
    public static final String REPENTANCE_BUFF_UNTIL = "lobotocraft_repentance_buff_until";

    private static final UUID JUSTICE_AS_UUID = UUID.fromString("c0ffee00-0001-4000-8000-000000000001");
    private static final UUID JUSTICE_MS_UUID = UUID.fromString("c0ffee00-0002-4000-8000-000000000002");
    private static final UUID REPENTANCE_MS_UUID = UUID.fromString("c0ffee00-0003-4000-8000-000000000003");

    private static final int BUFF_DURATION = 200; // 10秒

    /** 正义裁决者武器命中:刷新攻速移速 buff(装备锁定+全套+饰品才生效) */
    public static void triggerJusticeBuff(Player player) {
        if (!EgoArmorHelper.isFullSetWithCurioLocked(player, "approval_bird")) return;
        if (!EgoArmorHelper.isHoldingWeapon(player, "approval_bird")) return;
        player.getPersistentData().putLong(JUSTICE_BUFF_UNTIL, player.level().getGameTime() + BUFF_DURATION);
    }

    /** 悔恨武器命中:刷新减速 buff(装备锁定+全套+饰品才生效);红伤+10% 由调用方处理 */
    public static void triggerRepentanceBuff(Player player) {
        if (!EgoArmorHelper.isFullSetWithCurioLocked(player, "repentance")) return;
        if (!EgoArmorHelper.isHoldingWeapon(player, "repentance")) return;
        player.getPersistentData().putLong(REPENTANCE_BUFF_UNTIL, player.level().getGameTime() + BUFF_DURATION);
    }

    /** 悔恨 buff 是否激活(供 ForgeModEvent 判断红伤+10%) */
    public static boolean isRepentanceBuffActive(Player player) {
        return player.getPersistentData().getLong(REPENTANCE_BUFF_UNTIL) > player.level().getGameTime();
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
        boolean repentanceActive = player.getPersistentData().getLong(REPENTANCE_BUFF_UNTIL) > now;
        if (ms != null) {
            boolean has = ms.getModifier(REPENTANCE_MS_UUID) != null;
            if (repentanceActive && !has) {
                ms.addTransientModifier(new AttributeModifier(REPENTANCE_MS_UUID, "repentance_ms", -0.20, AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!repentanceActive && has) {
                ms.removeModifier(REPENTANCE_MS_UUID);
            }
        }
    }
}
