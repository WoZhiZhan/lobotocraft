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
 * 员工属性Provider
 */
public class EmployeeStatsProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    
    public static Capability<IEmployeeStats> EMPLOYEE_STATS = 
            CapabilityManager.get(new CapabilityToken<>() {});
    
    private IEmployeeStats employeeStats = null;
    private final LazyOptional<IEmployeeStats> optional = LazyOptional.of(this::createEmployeeStats);
    
    private IEmployeeStats createEmployeeStats() {
        if (this.employeeStats == null) {
            this.employeeStats = new EmployeeStats();
        }
        return this.employeeStats;
    }
    
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == EMPLOYEE_STATS) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }
    
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createEmployeeStats();
        if (employeeStats instanceof EmployeeStats stats) {
            stats.saveToNBT(nbt);
        }
        return nbt;
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createEmployeeStats();
        if (employeeStats instanceof EmployeeStats stats) {
            stats.loadFromNBT(nbt);
        }
    }
}