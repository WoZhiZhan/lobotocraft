package com.wzz.lobotocraft.world.structure;

import com.wzz.lobotocraft.capability.ProtectedBlocksProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * 方块保护辅助工具
 */
public class ProtectionHelper {
    
    /**
     * 标记方块为受保护
     */
    public static void markProtected(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS).ifPresent(cap -> {
            cap.markProtected(pos);
            chunk.setUnsaved(true);
        });
    }
    
    /**
     * 移除方块保护
     */
    public static void removeProtection(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS).ifPresent(cap -> {
            cap.removeProtection(pos);
            chunk.setUnsaved(true);
        });
    }
    
    /**
     * 检查方块是否受保护
     */
    public static boolean isProtected(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS)
                .map(cap -> cap.isProtected(pos))
                .orElse(false);
    }
    
    /**
     * 批量标记受保护方块（结构加载时使用）
     */
    public static void markProtectedBatch(Level level, Set<BlockPos> positions) {
        // 按区块分组
        Map<LevelChunk, Set<BlockPos>> chunkMap = new HashMap<>();
        for (BlockPos pos : positions) {
            LevelChunk chunk = level.getChunkAt(pos);
            chunkMap.computeIfAbsent(chunk, k -> new HashSet<>()).add(pos);
        }
        
        // 批量设置
        for (Map.Entry<LevelChunk, Set<BlockPos>> entry : chunkMap.entrySet()) {
            LevelChunk chunk = entry.getKey();
            chunk.getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS).ifPresent(cap -> {
                cap.markProtectedBatch(entry.getValue());
                chunk.setUnsaved(true);
            });
        }
    }
    
    /**
     * 清除区块的所有保护（调试用）
     */
    public static void clearProtection(LevelChunk chunk) {
        chunk.getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS).ifPresent(cap -> {
            cap.clear();
            chunk.setUnsaved(true);
        });
    }
}