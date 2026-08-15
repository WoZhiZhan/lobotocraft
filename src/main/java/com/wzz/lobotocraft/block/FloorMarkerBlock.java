package com.wzz.lobotocraft.block;

import com.wzz.lobotocraft.block.entity.FloorMarkerBlockEntity;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 楼层标记方块。每层楼随便找个地方放一个就行。
 * <p>
 * 不需要等间距 —— 收容间归属到"位于自己高度或下方、且最接近的那个楼层标记"，
 * 所以每层间隔 3 格还是 30 格都无所谓。
 * <p>
 * 楼层名在监控界面里右键楼层条改，不在方块上改（那样每层要跑一趟太麻烦，
 * 而且楼层名存在存档数据里，区块没加载也能改）。
 */
public class FloorMarkerBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 1, 13);

    public FloorMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /**
     * 完全不渲染。
     * <p>
     * 注意：靠"全透明贴图"是做不到隐形的 —— 方块默认走 solid 渲染层，那一层<b>直接无视 alpha 通道</b>，
     * 一张 alpha=0 的白色 PNG 会被画成纯白色块。要靠贴图透明得在模型 json 里加
     * {@code "render_type": "translucent"}（Forge 1.20.1 的模型扩展）。
     * 既然要求是彻底看不见，这里直接 INVISIBLE，连模型都不画，也就不需要方块贴图了。
     * <p>
     * 碰撞箱本来就是空的，但 {@link #getShape} 保留了薄片形状，所以还能瞄准和敲掉它。
     * 想看它们在哪，手里拿着监控终端或者任意一种标记方块，
     * {@code MarkerBlockRenderer} 会把轮廓和扫描范围画出来。
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FloorMarkerBlockEntity be) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.floor_marker.status", be.getFloorName(), pos.getY()), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CompanyRoomData.get(serverLevel).removeFloor(serverLevel.dimension().location(), pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloorMarkerBlockEntity(pos, state);
    }
}
