package com.wzz.lobotocraft.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability Provider
 */
public class ProtectedBlocksProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<IProtectedBlocksCapability> PROTECTED_BLOCKS =
            CapabilityManager.get(new CapabilityToken<>(){});
    private final ProtectedBlocksCapability capability = new ProtectedBlocksCapability();
    private final LazyOptional<IProtectedBlocksCapability> optional = LazyOptional.of(() -> capability);
    
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PROTECTED_BLOCKS) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }
    
    @Override
    public CompoundTag serializeNBT() {
        return capability.serializeNBT();
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        capability.deserializeNBT(nbt);
    }
    
    public void invalidate() {
        optional.invalidate();
    }
}