package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ModMain.MODID);

    // Missing attributes are injected with RED/WHITE=1.0, BLACK=1.5, PALE(legacy BLUE)=2.0.
    // An abnormality with different tuning must declare it explicitly; absence is not neutral.
    // 0 is immunity; negative values represent absorption where supported by the damage pipeline.
    public static final RegistryObject<Attribute> RED_DAMAGE_RESISTANCE = ATTRIBUTES.register("red_damage_resistance",
            () -> new RangedAttribute("attribute.lobotocraft.red_damage_resistance", 1.0D, -1.0D, 10.0D).setSyncable(true));

    public static final RegistryObject<Attribute> WHITE_DAMAGE_RESISTANCE = ATTRIBUTES.register("white_damage_resistance",
            () -> new RangedAttribute("attribute.lobotocraft.white_damage_resistance", 1.0D, -1.0D, 10.0D).setSyncable(true));

    public static final RegistryObject<Attribute> BLACK_DAMAGE_RESISTANCE = ATTRIBUTES.register("black_damage_resistance",
            () -> new RangedAttribute("attribute.lobotocraft.black_damage_resistance", 1.5D, -1.0D, 10.0D).setSyncable(true));

    public static final RegistryObject<Attribute> BLUE_DAMAGE_RESISTANCE = ATTRIBUTES.register("blue_damage_resistance",
            () -> new RangedAttribute("attribute.lobotocraft.blue_damage_resistance", 2.0D, -1.0D, 10.0D).setSyncable(true));

    public static final RegistryObject<Attribute> EXTRA_MENTAL_VALUE = ATTRIBUTES.register("extra_mental_value",
            () -> new RangedAttribute("attribute.lobotocraft.extra_mental_value", 0.0D, 0.0D, 100.0D).setSyncable(true));

    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> livingType : event.getTypes()) {
            addIfMissing(event, livingType, RED_DAMAGE_RESISTANCE.get());
            addIfMissing(event, livingType, WHITE_DAMAGE_RESISTANCE.get());
            addIfMissing(event, livingType, BLACK_DAMAGE_RESISTANCE.get());
            addIfMissing(event, livingType, BLUE_DAMAGE_RESISTANCE.get());
            addIfMissing(event, livingType, EXTRA_MENTAL_VALUE.get());
        }
    }

    private static void addIfMissing(EntityAttributeModificationEvent event,
                                     EntityType<? extends LivingEntity> livingType,
                                     Attribute attribute) {
        if (!event.has(livingType, attribute)) event.add(livingType, attribute);
    }
}
