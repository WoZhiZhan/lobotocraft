package com.wzz.lobotocraft.capability;

import com.wzz.lobotocraft.init.ModAttributes;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MentalValueProvider implements ICapabilitySerializable<CompoundTag> {
    public static Capability<IMentalValue> MENTAL_VALUE = CapabilityManager.get(new CapabilityToken<>(){});
    private final MentalValue mentalValue;
    private final LazyOptional<IMentalValue> optional;

    public MentalValueProvider(Player player) {
        this.mentalValue = new MentalValue(player);
        this.optional = LazyOptional.of(() -> mentalValue);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MENTAL_VALUE) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        mentalValue.saveNBTData(nbt);
        return nbt;
    }

    public void syncExtraMentalValue() {
        if (mentalValue.getPlayer() != null) {
            AttributeInstance attr = mentalValue.getPlayer().getAttribute(ModAttributes.EXTRA_MENTAL_VALUE.get());
            if (attr != null) {
                mentalValue.setExtraMentalValue((float) attr.getValue());
            }
        }
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        mentalValue.loadNBTData(nbt);
    }
}