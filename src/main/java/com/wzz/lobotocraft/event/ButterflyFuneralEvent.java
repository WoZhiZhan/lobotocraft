package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModEffects;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 亡蝶葬仪的处决("蝴蝶缠身")事件:
 *  - buff 期间身上持续绽放蝴蝶贴图特效
 *  - buff 结束(自然到期)时玩家死亡,死亡提示改为"xxx被亡蝶葬仪"救赎"了"
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class ButterflyFuneralEvent {

    /** buff 自然到期 → 玩家死亡 */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() == null) return;
        if (event.getEffectInstance().getEffect() != ModEffects.BUTTERFLY_SHROUD.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        // 处决:玩家死亡
        player.getPersistentData().putBoolean("butterfly_salvation", true);
        player.invulnerableTime = 0;
        player.hurt(DamageHelper.getDamage(player, "white"), Float.MAX_VALUE);
        // 死亡提示:xxx被亡蝶葬仪"救赎"了
        if (player.level() instanceof ServerLevel level) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§f" + player.getName().getString() + " 被亡蝶葬仪“救赎”了"), false);
        }
    }

    /** buff 期间持续绽放蝴蝶特效 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!player.hasEffect(ModEffects.BUTTERFLY_SHROUD.get())) return;
        if (player.tickCount % 5 != 0) return;
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles((SimpleParticleType) ModParticleTypes.BUTTERFLY.get(),
                    player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                    3, 0.4, 0.5, 0.4, 0.0);
        }
    }
}
