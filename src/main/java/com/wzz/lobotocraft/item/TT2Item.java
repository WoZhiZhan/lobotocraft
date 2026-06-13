package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.item.api.ProhibitDiscardingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TT2Item extends ProhibitDiscardingItem {
    public TT2Item() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7加快工作效率，通过TT2协议能够让工作时间流逝速度加快为原来的2倍。"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            if (!player.getPersistentData().getBoolean("useTT2")) {
                player.getPersistentData().putBoolean("useTT2", true);
                player.sendSystemMessage(Component.literal("§aTT2协议已启动！"));
                player.getItemInHand(hand).getOrCreateTag().putBoolean("isStartTT2", true);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                player.getPersistentData().putBoolean("useTT2", false);
                player.getItemInHand(hand).getOrCreateTag().putBoolean("isStartTT2", false);
                player.sendSystemMessage(Component.literal("§aTT2协议已关闭！"));
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean("isStartTT2");
    }
}
