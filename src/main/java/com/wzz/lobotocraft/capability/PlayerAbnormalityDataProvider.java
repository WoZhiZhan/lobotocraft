package com.wzz.lobotocraft.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 玩家异想体数据Provider
 */
public class PlayerAbnormalityDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static Capability<PlayerAbnormalityData> PLAYER_ABNORMALITY_DATA = 
            CapabilityManager.get(new CapabilityToken<>(){});

    private PlayerAbnormalityData data = null;
    private final LazyOptional<PlayerAbnormalityData> optional = LazyOptional.of(this::createData);

    private PlayerAbnormalityData createData() {
        if (this.data == null) {
            this.data = new PlayerAbnormalityData();
        }
        return this.data;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == PLAYER_ABNORMALITY_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createData().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createData().loadNBTData(nbt);
    }
}