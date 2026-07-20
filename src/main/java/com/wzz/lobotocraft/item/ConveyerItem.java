package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ConveyerItem extends Item {
    public ConveyerItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.translatable("item.conveyer.text.1"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return super.use(level, player, hand);
        }
        if (CoreSuppressionManager.isDeviceRestricted(player)) {
            player.sendSystemMessage(Component.literal("§c核心抑制期间无法使用传送装置。"));
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        CompoundTag nbt = player.getPersistentData();
        final String keyX = "lobotocraft:LotoOldX";
        final String keyY = "lobotocraft:LotoOldY";
        final String keyZ = "lobotocraft:LotoOldZ";
        final String keyLevel = "lobotocraft:LotoOldLevelName";
        ResourceKey<Level> currentDim = serverPlayer.level.dimension;
        
        if (!currentDim.equals(ModDimensions.LOBOTO_KEY)) {
            // 在主世界等维度，传送到脑叶公司
            nbt.putDouble(keyX, player.getX());
            nbt.putDouble(keyY, player.getY());
            nbt.putDouble(keyZ, player.getZ());
            nbt.putString(keyLevel, player.level.dimension.location().toString());
            
            // 获取脑叶公司的坐标，如果没有则使用默认值
            double x = nbt.contains(keyX + "_loboto") ? nbt.getDouble(keyX + "_loboto") : 0.5;
            double y = nbt.contains(keyY + "_loboto") ? nbt.getDouble(keyY + "_loboto") : 4.0;
            double z = nbt.contains(keyZ + "_loboto") ? nbt.getDouble(keyZ + "_loboto") : 0.5;
            if (Structures.LOBOTO.isGenerated(serverPlayer.serverLevel()) && x == 0.5 && y == 4.0) {
                x = 195;
                y = 273;
                z = 29;
            }
            EntityUtil.teleportPlayer(serverPlayer, ModDimensions.LOBOTO_KEY, BlockPos.containing(x, y, z));
            return InteractionResultHolder.success(serverPlayer.getItemInHand(hand));
        }
        
        // 在脑叶公司维度，传送回主世界
        String levelName = nbt.getString(keyLevel);

        if (levelName.isEmpty()) {
            levelName = "minecraft:overworld";
            nbt.putString(keyLevel, levelName);
            BlockPos spawnPos = serverPlayer.getServer().overworld().getSharedSpawnPos();
            nbt.putDouble(keyX, spawnPos.getX() + 0.5);
            nbt.putDouble(keyY, spawnPos.getY());
            nbt.putDouble(keyZ, spawnPos.getZ() + 0.5);
        }
        
        ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, ResourceUtil.createInstanceWithColon(levelName));
        double x = nbt.getDouble(keyX);
        double y = nbt.getDouble(keyY);
        double z = nbt.getDouble(keyZ);
        
        // 保存当前在脑叶公司的位置
        nbt.putDouble(keyX + "_loboto", player.getX());
        nbt.putDouble(keyY + "_loboto", player.getY());
        nbt.putDouble(keyZ + "_loboto", player.getZ());
        
        if (!currentDim.equals(targetDim)) {
            EntityUtil.teleportPlayer(serverPlayer, targetDim, BlockPos.containing(x, y, z));
        }
        return InteractionResultHolder.success(serverPlayer.getItemInHand(hand));
    }
}
