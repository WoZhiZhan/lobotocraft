package com.wzz.lobotocraft.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 公司日常数据Capability Provider
 */
public class CompanyDailyDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static Capability<CompanyDailyData> COMPANY_DAILY_DATA = 
            CapabilityManager.get(new CapabilityToken<>() {});

    private CompanyDailyData data = null;
    private final LazyOptional<CompanyDailyData> optional = LazyOptional.of(this::createData);

    private CompanyDailyData createData() {
        if (this.data == null) {
            this.data = new CompanyDailyData();
        }
        return this.data;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == COMPANY_DAILY_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        createData().serializeNBT().getAllKeys().forEach(key -> {
            tag.put(key, createData().serializeNBT().get(key));
        });
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createData().deserializeNBT(nbt);
    }
}