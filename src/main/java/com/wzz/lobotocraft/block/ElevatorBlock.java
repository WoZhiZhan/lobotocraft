package com.wzz.lobotocraft.block;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenElevatorScreenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ElevatorBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public ElevatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        if (entity instanceof Player player && !player.isCreative())
            return false;
        return super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof ElevatorBlockEntity be) {
                MessageLoader.getLoader().sendToPlayer(serverPlayer, new OpenElevatorScreenPacket(pos, be.getTeleportDistance(), be.isTeleportUp()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (entity instanceof LivingEntity living) {
            if (level.getBlockEntity(pos) instanceof ElevatorBlockEntity be) {
                be.onStep(living);
            }
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            if (level.getBlockEntity(pos) instanceof ElevatorBlockEntity be) {
                if (isEntityOnElevator(living, pos)) {
                    be.onStep(living);
                }
            }
        }
    }

    private boolean isEntityOnElevator(LivingEntity entity, BlockPos pos) {
        double entityFeetY = entity.getY();
        double blockTopY = pos.getY() + 1.0;

        if (entityFeetY >= blockTopY - 0.1 && entityFeetY <= blockTopY + 0.3) {
            double entityX = entity.getX();
            double entityZ = entity.getZ();
            return entityX >= pos.getX() && entityX <= pos.getX() + 1.0
                    && entityZ >= pos.getZ() && entityZ <= pos.getZ() + 1.0;
        }
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElevatorBlockEntity(pos, state);
    }
    
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(
            blockEntityType,
            ModBlockEntities.ELEVATOR.get(),
            ElevatorBlockEntity::serverTick
        );
    }
}