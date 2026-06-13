package com.wzz.lobotocraft.block;

import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 再生反应堆方块
 * 在封闭视线区域内为玩家提供恢复效果
 */
public class RegenerationReactorBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    
    public RegenerationReactorBlock(Properties properties) {
        super(properties);
    }
    
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
    
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
    
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RegenerationReactorBlockEntity(pos, state);
    }
    
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(
            blockEntityType,
            ModBlockEntities.REGENERATION_REACTOR.get(),
            RegenerationReactorBlockEntity::serverTick
        );
    }
}