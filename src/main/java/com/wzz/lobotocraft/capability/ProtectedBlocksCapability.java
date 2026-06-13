package com.wzz.lobotocraft.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashSet;
import java.util.Set;

/**
 * 受保护方块能力实现
 */
public class ProtectedBlocksCapability implements IProtectedBlocksCapability, INBTSerializable<CompoundTag> {
    
    private final Set<BlockPos> protectedBlocks = new HashSet<>();
    
    @Override
    public void markProtected(BlockPos pos) {
        protectedBlocks.add(pos.immutable());
    }
    
    @Override
    public void removeProtection(BlockPos pos) {
        protectedBlocks.remove(pos);
    }
    
    @Override
    public boolean isProtected(BlockPos pos) {
        return protectedBlocks.contains(pos);
    }
    
    @Override
    public void markProtectedBatch(Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            protectedBlocks.add(pos.immutable());
        }
    }
    
    @Override
    public Set<BlockPos> getAllProtectedBlocks() {
        return new HashSet<>(protectedBlocks);
    }
    
    @Override
    public void clear() {
        protectedBlocks.clear();
    }
    
    // ========== NBT 序列化 ==========
    
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag listTag = new ListTag();
        
        for (BlockPos pos : protectedBlocks) {
            listTag.add(NbtUtils.writeBlockPos(pos));
        }
        
        tag.put("ProtectedBlocks", listTag);
        return tag;
    }
    
    @Override
    public void deserializeNBT(CompoundTag tag) {
        protectedBlocks.clear();
        
        if (tag.contains("ProtectedBlocks")) {
            ListTag listTag = tag.getList("ProtectedBlocks", 10); // 10 = CompoundTag
            for (int i = 0; i < listTag.size(); i++) {
                BlockPos pos = NbtUtils.readBlockPos(listTag.getCompound(i));
                protectedBlocks.add(pos);
            }
        }
    }
}