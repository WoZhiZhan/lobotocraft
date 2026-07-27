package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FloorEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 楼层标记方块的 BlockEntity。
 * <p>
 * 本体几乎不存东西 —— 楼层名是存在存档级的 {@link CompanyRoomData} 里的，
 * 这样在监控界面里改名时，即使那层区块没加载也能改。
 * 这里只负责在区块加载时把自己登记进去。
 */
public class FloorMarkerBlockEntity extends BlockEntity {

    public FloorMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOOR_MARKER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            CompanyRoomData.get(serverLevel)
                    .getOrCreateFloor(serverLevel.dimension().location(), worldPosition);
        }
    }

    public String getFloorName() {
        if (level instanceof ServerLevel serverLevel) {
            FloorEntry entry = CompanyRoomData.get(serverLevel)
                    .findFloor(serverLevel.dimension().location(), worldPosition);
            if (entry != null) return entry.displayName();
        }
        return "Y " + worldPosition.getY();
    }
}
