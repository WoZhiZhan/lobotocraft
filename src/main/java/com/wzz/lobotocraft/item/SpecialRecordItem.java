package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.entity.abnormality.EntityIsharmla;
import com.wzz.lobotocraft.entity.abnormality.EntityIsharmlaTear;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 特殊唱片 —— 镇压伊莎玛拉(机制5)。
 * 在伊莎玛拉附近右键使用:浊心斯卡蒂牺牲自己使伊莎玛拉死亡,
 * 此后伊莎玛拉与斯卡蒂都从存档中永久消失,斯卡蒂不再自然生成。
 */
public class SpecialRecordItem extends Item {

    public SpecialRecordItem() {
        super(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        // 检测附近16格内的伊莎玛拉
        List<EntityIsharmla> isharmlas = serverLevel.getEntitiesOfClass(EntityIsharmla.class,
                player.getBoundingBox().inflate(16), e -> e.isAlive());
        if (isharmlas.isEmpty()) {
            player.displayClientMessage(Component.literal("§7附近没有可以镇压的伊莎玛拉……"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 播放唱片镇压音效
        serverLevel.playSound(null, player.blockPosition(),
                ModSounds.ISHARMLA_TO_HUMAN.get(), SoundSource.RECORDS, 1.2f, 1.0f);

        AABB whole = new AABB(-30000000, serverLevel.getMinBuildHeight(), -30000000,
                30000000, serverLevel.getMaxBuildHeight(), 30000000);
        // 移除残余的斯卡蒂实体(正常流程下斯卡蒂已变为伊莎玛拉,这里以防万一)
        for (EntityDarkSkadi skadi : serverLevel.getEntitiesOfClass(EntityDarkSkadi.class, whole)) {
            skadi.discard();
        }
        // 伊莎玛拉与其之泪彻底消失
        for (EntityIsharmla isharmla : isharmlas) {
            isharmla.discard();
        }
        for (EntityIsharmlaTear tear : serverLevel.getEntitiesOfClass(EntityIsharmlaTear.class, whole)) {
            tear.discard();
        }

        // 项3修复:在斯卡蒂原位置重新生成一只满血的浊心斯卡蒂(不再永久消失)
        SkadiBanishData banishData = SkadiBanishData.get(serverLevel);
        double sx, sy, sz;
        if (banishData.hasSkadiOrigin()) {
            sx = banishData.getOriginX();
            sy = banishData.getOriginY();
            sz = banishData.getOriginZ();
        } else {
            // 无记录时回退到玩家附近
            sx = player.getX(); sy = player.getY(); sz = player.getZ();
        }
        EntityDarkSkadi skadi = ModEntities.skadi_corrupted.get().create(serverLevel);
        if (skadi != null) {
            skadi.moveTo(sx, sy, sz, 0f, 0f);
            skadi.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(
                            net.minecraft.core.BlockPos.containing(sx, sy, sz)),
                    net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
            skadi.setHealth(skadi.getMaxHealth());
            serverLevel.addFreshEntity(skadi);
        }
        banishData.clearSkadiOrigin();
        // 斯卡蒂回归后不再标记为 banished,使其可继续正常运作
        banishData.setBanished(false);

        player.displayClientMessage(Component.literal(
                "§9伊莎玛拉归于沉寂,浊心斯卡蒂回到了她原本的位置。"), false);

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7在伊莎玛拉附近使用,").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7浊心斯卡蒂将牺牲自己使其永远沉寂。").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
