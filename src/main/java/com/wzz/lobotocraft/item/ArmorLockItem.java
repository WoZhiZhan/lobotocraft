package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.network.MessageLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ArmorLockItem extends Item {
    public ArmorLockItem() {
        super(new Properties().stacksTo(64).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 尝试锁定装备
            serverPlayer.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                if (data.isArmorLocked()) {
                    // 解除锁定(生存与创造均可)
                    data.unlockArmor();
                    player.sendSystemMessage(Component.literal(
                            "§e已解除装备锁定"
                    ).withStyle(ChatFormatting.YELLOW));
                    player.playSound(
                            net.minecraft.sounds.SoundEvents.ANVIL_USE,
                            1.0F,
                            0.8F
                    );
                    MessageLoader.getLoader().sendToPlayer(serverPlayer,
                            new com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket(
                                    data.getCurrentDay(),
                                    data.getTodayWorkCount(),
                                    false,
                                    data.isHasSleep()
                            )
                    );
                } else {
                    // 执行锁定
                    data.lockArmor();
                    player.sendSystemMessage(Component.literal(
                            "§a装备已锁定！"
                    ).withStyle(ChatFormatting.GREEN));
                    // 播放音效
                    player.playSound(
                            net.minecraft.sounds.SoundEvents.ANVIL_USE,
                            1.0F,
                            1.5F
                    );
                    // 消耗物品
                    if (!player.isCreative()) {
                        itemStack.shrink(1);
                    }
                    // 同步数据到客户端
                    MessageLoader.getLoader().sendToPlayer(serverPlayer,
                            new com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket(
                                    data.getCurrentDay(),
                                    data.getTodayWorkCount(),
                                    true,  // 锁定状态
                                    data.isHasSleep()
                            )
                    );
                }
            });

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    @Override
    public boolean isFoil(ItemStack p_41453_) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.translatable("item.armor_lock.text.1"));
    }
}
