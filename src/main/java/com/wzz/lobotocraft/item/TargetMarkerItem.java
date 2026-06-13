package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.entity.abnormality.EntityRedHoodMercenary;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "目标"道具(小红帽雇佣兵的委托凭证)。
 * 右键掷向面前的出逃异想体:命中后该异想体被标记(头顶高亮,这里以发光效果呈现),
 * "小红帽雇佣兵"立刻进入委托状态,前往并只攻击被标记目标;
 * 镇压完成后小红帽自行回到出逃位置并重置计数器。
 */
public class TargetMarkerItem extends Item {

    public TargetMarkerItem() {
        super(new Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        // 拾取玩家视线前方 24 格内最近的"出逃状态异想体"
        AbstractAbnormality target = pickEscapedAbnormality(serverLevel, player, 24);
        if (target == null) {
            player.displayClientMessage(Component.literal("§7前方没有可以委托镇压的出逃异想体……"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (target instanceof EntityRedHoodMercenary) {
            player.displayClientMessage(Component.literal("§c她可不接以自己为目标的委托。"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 标记目标:头顶悬浮"目标"标识(以持续发光效果呈现)
        target.getPersistentData().putBoolean("redhat_marked", true);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));

        // 通知所有小红帽进入委托状态
        boolean found = false;
        for (EntityRedHoodMercenary redhat : serverLevel.getEntitiesOfClass(EntityRedHoodMercenary.class,
                player.getBoundingBox().inflate(2048), LivingEntity::isAlive)) {
            redhat.startCommission(target);
            found = true;
        }
        if (found) {
            player.sendSystemMessage(Component.literal("§c[小红帽雇佣兵] §7成交。让我看看这次的猎物……"));
        } else {
            player.sendSystemMessage(Component.literal("§7目标已标记,但附近没有可受雇的小红帽雇佣兵。"));
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    private AbstractAbnormality pickEscapedAbnormality(ServerLevel level, Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AbstractAbnormality best = null;
        double bestDot = 0.85; // 视线角度阈值
        for (AbstractAbnormality ab : level.getEntitiesOfClass(AbstractAbnormality.class,
                player.getBoundingBox().inflate(range),
                a -> a.isAlive() && a.hasEscape())) {
            Vec3 to = ab.position().add(0, ab.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > range) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = ab;
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7对准出逃的异想体使用，向小红帽雇佣兵发出委托。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7“想雇佣我的话，希望你付得起代价。”").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
