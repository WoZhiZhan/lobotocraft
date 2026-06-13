package com.wzz.lobotocraft.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.*;

public class CuriosUtil {

    public static void addAttributeAndRemoveOld(LivingEntity entity, AttributeModifier modifier, Attribute attribute) {
        if (entity != null) {
            AttributeInstance attributeInstance = entity.getAttribute(attribute);
            if (attributeInstance != null) {
                attributeInstance.removeModifier(modifier);
                attributeInstance.addTransientModifier(modifier);
            }
        }
    }

    public static void applyDynamicModifier(
            LivingEntity entity,
            Attribute attribute,
            UUID modifierId,
            String modifierName,
            double newAmount,
            AttributeModifier.Operation operation
    ) {
        if (entity == null || attribute == null) return;
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) return;
        AttributeModifier oldModifier = attr.getModifier(modifierId);
        if (oldModifier != null) {
            if (oldModifier.getAmount() != newAmount) {
                attr.removeModifier(modifierId);
                attr.addTransientModifier(
                        new AttributeModifier(modifierId, modifierName, newAmount, operation)
                );
            }
        } else {
            attr.addTransientModifier(
                    new AttributeModifier(modifierId, modifierName, newAmount, operation)
            );
        }
    }

    public static void addAttribute(LivingEntity entity, AttributeModifier modifier, Attribute attribute) {
        if (entity != null) {
            AttributeInstance entityAttribute = entity.getAttribute(attribute);
            if (entityAttribute != null && !entityAttribute.hasModifier(modifier)) {
                entityAttribute.addTransientModifier(modifier);
            }
        }
    }

    public static void removeAttribute(LivingEntity entity, AttributeModifier modifier, Attribute attributes) {
        if (entity != null) {
            AttributeInstance attribute = entity.getAttribute(attributes);
            if (attribute != null) {
                attribute.removeModifier(modifier);
            }
        }
    }

    @SuppressWarnings({"deprecation", "removal", "all"})
    public static boolean hasCurios(LivingEntity entity, Item item) {
        if (!(entity instanceof Player))
            return false;
        Optional<ICuriosItemHandler> curiosHandler = CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve();
        if (curiosHandler.isPresent()) {
            ICuriosItemHandler handler = curiosHandler.get();
            return handler.getCurios().entrySet().stream()
                    .anyMatch(entry -> {
                        int slots = entry.getValue().getSlots();
                        for (int i = 0; i < slots; i++) {
                            if (entry.getValue().getStacks().getStackInSlot(i).getItem() == item) {
                                return true;
                            }
                        }
                        return false;
                    });
        }
        return false;
    }

    @SuppressWarnings({"deprecation", "removal", "all"})
    public static void removeCurios(LivingEntity entity, Item item) {
        Optional<ICuriosItemHandler> curiosHandler = CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve();
        if (curiosHandler.isPresent()) {
            ICuriosItemHandler handler = curiosHandler.get();
            handler.getCurios().entrySet().stream()
                    .forEach(entry -> {
                        int slots = entry.getValue().getSlots();
                        for (int i = 0; i < slots; i++) {
                            if (entry.getValue().getStacks().getStackInSlot(i).getItem() == item) {
                                entry.getValue().getStacks().getStackInSlot(i).setCount(0);
                            }
                        }
                    });
        }
    }

    @SuppressWarnings({"deprecation", "removal", "all"})
    public static ItemStack getCuriosItem(LivingEntity entity, Item item) {
        Optional<ICuriosItemHandler> curiosHandler = CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve();
        if (curiosHandler.isPresent()) {
            ICuriosItemHandler handler = curiosHandler.get();
            for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
                ICurioStacksHandler stacksHandler = entry.getValue();
                int slots = stacksHandler.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
                    if (stack.getItem() == item) {
                        return stack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings({"deprecation", "removal", "all"})
    public static List<ItemStack> getCuriosItems(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>();
        Optional<ICuriosItemHandler> curiosHandler = CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve();
        if (curiosHandler.isPresent()) {
            ICuriosItemHandler handler = curiosHandler.get();
            for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
                ICurioStacksHandler stacksHandler = entry.getValue();
                int slots = stacksHandler.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
                    stacks.add(stack);
                }
            }
        }
        return stacks;
    }
}
