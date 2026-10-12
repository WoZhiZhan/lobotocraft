package com.wzz.lobotocraft.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only adapter: share vanilla's configured fluid picker, do not duplicate fluid rules. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseGeneratorFluidPickerAccessor {
    @Accessor("globalFluidPicker")
    Supplier<Aquifer.FluidPicker> lobotocraft$getFluidPicker();
}
