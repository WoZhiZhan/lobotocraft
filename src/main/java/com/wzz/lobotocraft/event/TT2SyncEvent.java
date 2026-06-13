package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.TT2Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 修复:玩家死亡或重进游戏后,TT2 协议的实际激活状态(useTT2,存于 persistentData,
 * 死亡不保留)被重置为 false,但 TT2 物品的附魔光效标签(isStartTT2,随物品保留)仍为 true,
 * 导致物品显示附魔光效却处于未激活状态。
 * 此处每秒将背包内 TT2 物品的光效标签与实际激活状态对齐。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class TT2SyncEvent {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;

        boolean active = player.getPersistentData().getBoolean("useTT2");
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof TT2Item) {
                boolean foil = stack.getOrCreateTag().getBoolean("isStartTT2");
                if (foil != active) {
                    stack.getOrCreateTag().putBoolean("isStartTT2", active);
                }
            }
        }
    }
}
