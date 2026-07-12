package com.wzz.lobotocraft.init;

import java.util.EnumMap;
import java.util.function.Supplier;

import net.minecraft.Util;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LazyLoadedValue;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

public class ModArmorMaterial {
    public static final ArmorMaterial ARMOR_MATERIAL;
    public static final ArmorMaterial BLUE_STAR;

    public ModArmorMaterial() {
    }

    static {
        ARMOR_MATERIAL = new SimpleArmorMaterial("repentance", 3000, SoundEvents.ARMOR_EQUIP_DIAMOND, 0f, Ingredient::of);
        BLUE_STAR = new SimpleArmorMaterial("blue_star", 4000, SoundEvents.ARMOR_EQUIP_NETHERITE, 0f, Ingredient::of);
    }

    public static class SimpleArmorMaterial implements ArmorMaterial {
        private final String name;
        private final int durabilityMultiplier;
        private final EnumMap<ArmorItem.Type, Integer> protectionFunctionForType;
        private final int enchantmentValue;
        private final SoundEvent sound;
        private final float toughness;
        private final float knockbackResistance;
        private final LazyLoadedValue<Ingredient> repairIngredient;
        private static final EnumMap<ArmorItem.Type, Integer> HEALTH_FUNCTION_FOR_TYPE = Util.make(new EnumMap(ArmorItem.Type.class), (p_266653_) -> {
            p_266653_.put(Type.BOOTS, 13);
            p_266653_.put(Type.LEGGINGS, 15);
            p_266653_.put(Type.CHESTPLATE, 16);
            p_266653_.put(Type.HELMET, 11);
        });

        public SimpleArmorMaterial(String name, int enchantmentValue, SoundEvent sound, float toughness, Supplier<Ingredient> repairIngredient) {
            this.name = name;
            this.durabilityMultiplier = 0;
            this.protectionFunctionForType = Util.make(new EnumMap(ArmorItem.Type.class), (p_266655_) -> {
                p_266655_.put(Type.BOOTS, 0);
                p_266655_.put(Type.LEGGINGS, 0);
                p_266655_.put(Type.CHESTPLATE, 0);
                p_266655_.put(Type.HELMET, 0);
            });
            this.enchantmentValue = enchantmentValue;
            this.sound = sound;
            this.toughness = toughness;
            this.knockbackResistance = Float.POSITIVE_INFINITY;
            this.repairIngredient = new LazyLoadedValue(repairIngredient);
        }

        public int getDurabilityForType(ArmorItem.@NotNull Type slot) {
            return HEALTH_FUNCTION_FOR_TYPE.get(slot) * this.durabilityMultiplier;
        }

        public int getDefenseForType(ArmorItem.@NotNull Type slot) {
            return this.protectionFunctionForType.get(slot);
        }

        public int getEnchantmentValue() {
            return this.enchantmentValue;
        }

        public @NotNull SoundEvent getEquipSound() {
            return this.sound;
        }

        public @NotNull Ingredient getRepairIngredient() {
            return this.repairIngredient.get();
        }

        public @NotNull String getName() {
            return this.name;
        }

        public float getToughness() {
            return this.toughness;
        }

        public float getKnockbackResistance() {
            return this.knockbackResistance;
        }
    }
}
