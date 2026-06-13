package com.wzz.lobotocraft.capability;

import net.minecraft.core.BlockPos;
import java.util.Set;

/**
 * 受保护方块能力接口
 */
public interface IProtectedBlocksCapability {
    void markProtected(BlockPos pos);
    void removeProtection(BlockPos pos);
    boolean isProtected(BlockPos pos);
    void markProtectedBatch(Set<BlockPos> positions);
    Set<BlockPos> getAllProtectedBlocks();
    void clear();
}