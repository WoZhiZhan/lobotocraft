package com.wzz.lobotocraft.block;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.RoomEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 收容间信标方块。
 * <p>
 * 手动摆到每个异想体房间的地面上即可，1 像素厚、没有碰撞箱，不影响房间里的走动和战斗。
 * <ul>
 *   <li>空手右键：显示当前监控数据。登记是全自动的，不用手点</li>
 *   <li>潜行右键：切换扫描半径（6 / 8 / 10 / 12 / 16 / 21，默认 10 对应 21 长的房间）</li>
 *   <li>手持剪刀右键：清空登记，强制下次扫描重新识别</li>
 * </ul>
 * 房间名不需要手动起 —— 直接用登记到的异想体名字。楼层由楼层标记方块决定。
 * 想手动指定住户的话，在监控界面里 Ctrl+右键那个房间。
 */
public class RoomMarkerBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1, 14);

    public RoomMarkerBlock(Properties properties) {
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
        if (!(level.getBlockEntity(pos) instanceof RoomMarkerBlockEntity be)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);

        // 剪刀 -> 解除登记
        if (held.is(Items.SHEARS)) {
            be.clearBinding();
            player.displayClientMessage(Component.literal(
                    "§b[收容间信标] §7已清空登记，下次扫描会自动重新认房间里的异想体"), true);
            return InteractionResult.CONSUME;
        }

        // 潜行 -> 切扫描半径
        if (player.isShiftKeyDown()) {
            int radius = be.cycleRadius();
            player.displayClientMessage(
                    Component.literal("§b[收容间信标] §7扫描半径 -> §f" + radius + " §7格"), true);
            return InteractionResult.CONSUME;
        }

        // 空手 -> 报告状态（登记是全自动的，不需要手动点）
        RoomEntry entry = CompanyRoomData.get(serverLevel).findRoom(serverLevel.dimension().location(), pos);
        if (entry == null) {
            player.displayClientMessage(Component.literal("§b[收容间信标] §7尚未完成首次扫描……"), false);
        } else {
            String statusText = switch (entry.status) {
                case RoomEntry.STATUS_OCCUPIED -> "§a收容中";
                case RoomEntry.STATUS_ESCAPED -> "§c出逃中"
                        + (entry.escapeDistance > 0 ? "§7(§c" + entry.escapeDistance + "格§7)" : "");
                case RoomEntry.STATUS_LOST -> "§6失联";
                default -> "§8空置";
            };
            player.displayClientMessage(Component.literal(
                    "§b[收容间信标] §f" + entry.resolveTitle()
                            + " §7| " + statusText
                            + " §7| 半径 §f" + be.getRadiusXZ()
                            + " §7| 房内玩家 §f" + entry.playersInside), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 只有方块真的换掉时才注销登记，区块卸载不会走到这里
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CompanyRoomData.get(serverLevel).removeRoom(serverLevel.dimension().location(), pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoomMarkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type,
                ModBlockEntities.ROOM_MARKER.get(),
                RoomMarkerBlockEntity::serverTick
        );
    }
}
