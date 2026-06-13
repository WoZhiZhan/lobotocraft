package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EscapeBlockEntity extends BlockEntity {

    // 各维度已加载的出逃方块位置注册表(供"深蓝色正午"考验随机选取出逃点)
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ESCAPE_BLOCKS = new ConcurrentHashMap<>();

    public EscapeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ESCAPE.get(), pos, blockState);
    }

    public static Set<BlockPos> getEscapeBlocks(ResourceKey<Level> dimension) {
        return ESCAPE_BLOCKS.getOrDefault(dimension, java.util.Collections.emptySet());
    }

    private void register() {
        if (this.level != null && !this.level.isClientSide) {
            ESCAPE_BLOCKS.computeIfAbsent(this.level.dimension(),
                    k -> ConcurrentHashMap.newKeySet()).add(this.getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            Set<BlockPos> set = ESCAPE_BLOCKS.get(this.level.dimension());
            if (set != null) set.remove(this.getBlockPos());
        }
    }

    /**
     * 服务端Tick逻辑
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EscapeBlockEntity blockEntity) {
        // 首次tick时登记自身位置
        blockEntity.register();
    }
}