package com.wzz.lobotocraft.client.ui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Renders a fixed-facing player doll using Minecraft's inventory-screen model. */
public final class EmployeeDollRenderer {
    private static final Map<UUID, RemotePlayer> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Integer> EQUIPMENT_SIGNATURES = new HashMap<>();
    private static ClientLevel previewLevel;

    private EmployeeDollRenderer() {
    }

    public static boolean render(GuiGraphics graphics, CompoundTag employee,
                                 int centerX, int bottomY, int scale) {
        LivingEntity entity = entity(employee);
        if (entity == null) return false;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, centerX, bottomY, scale, 70.0F, 0.0F, entity);
        return true;
    }

    private static LivingEntity entity(CompoundTag employee) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !employee.hasUUID("Id")) return null;
        UUID id = employee.getUUID("Id");
        var tracked = minecraft.level.getPlayerByUUID(id);
        if (tracked != null) return tracked;

        if (previewLevel != minecraft.level) {
            previewLevel = minecraft.level;
            PREVIEWS.clear();
            EQUIPMENT_SIGNATURES.clear();
        }
        RemotePlayer preview = PREVIEWS.computeIfAbsent(id, ignored -> {
            GameProfile profile = minecraft.getConnection() == null ? null
                    : minecraft.getConnection().getPlayerInfo(id) == null ? null
                    : minecraft.getConnection().getPlayerInfo(id).getProfile();
            return new RemotePlayer(minecraft.level, profile == null
                    ? new GameProfile(id, profileName(employee, id)) : profile);
        });
        updateEquipment(preview, employee, id);
        return preview;
    }

    private static String profileName(CompoundTag employee, UUID id) {
        String profileName = employee.getString("ProfileName");
        if (!profileName.isBlank() && profileName.length() <= 16) return profileName;
        return "AI_" + id.toString().replace("-", "").substring(0, 12);
    }

    private static void updateEquipment(RemotePlayer preview, CompoundTag employee, UUID id) {
        CompoundTag equipment = employee.getCompound("PreviewEquipment");
        int signature = equipment.hashCode();
        if (EQUIPMENT_SIGNATURES.getOrDefault(id, Integer.MIN_VALUE) == signature) return;
        EQUIPMENT_SIGNATURES.put(id, signature);
        setSlot(preview, equipment, EquipmentSlot.MAINHAND, "MainHand");
        setSlot(preview, equipment, EquipmentSlot.OFFHAND, "OffHand");
        setSlot(preview, equipment, EquipmentSlot.HEAD, "Head");
        setSlot(preview, equipment, EquipmentSlot.CHEST, "Chest");
        setSlot(preview, equipment, EquipmentSlot.LEGS, "Legs");
        setSlot(preview, equipment, EquipmentSlot.FEET, "Feet");
    }

    private static void setSlot(RemotePlayer preview, CompoundTag equipment,
                                EquipmentSlot slot, String key) {
        ItemStack stack = equipment.contains(key, Tag.TAG_COMPOUND)
                ? ItemStack.of(equipment.getCompound(key)) : ItemStack.EMPTY;
        preview.setItemSlot(slot, stack);
    }
}
