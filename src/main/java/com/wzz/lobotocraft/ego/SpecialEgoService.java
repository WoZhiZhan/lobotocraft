package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-authoritative unlock, active-slot and projection orchestration for special E.G.O. */
public final class SpecialEgoService {
    private static final List<String> EQUIPMENT_TYPES = List.of("weapon", "armor");
    private static final EquipmentSlot[] ACTIVE_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private SpecialEgoService() {
    }

    public static boolean unlock(net.minecraft.server.MinecraftServer server, String specialEgoId) {
        return server != null && SpecialEgoCatalog.byId(specialEgoId) != null
                && SpecialEgoRegistryData.get(server).unlock(specialEgoId);
    }

    public static EquipResult setEquipped(ServerPlayer player, String specialEgoId,
                                          String equipmentType, boolean equipped) {
        SpecialEgoCatalog.Definition definition = SpecialEgoCatalog.byId(specialEgoId);
        if (!isHuman(player) || definition == null
                || !SpecialEgoRegistryData.validType(equipmentType)) return EquipResult.INVALID;
        SpecialEgoRegistryData registry = SpecialEgoRegistryData.get(player.server);
        if (!registry.isUnlocked(specialEgoId)) return EquipResult.LOCKED;

        if (!equipped) {
            if (!registry.release(specialEgoId, equipmentType, player.getUUID())) {
                return EquipResult.NOT_ASSIGNED;
            }
            EgoLoadoutService.unequipSpecial(player, equipmentType);
            return EquipResult.SUCCESS;
        }

        List<String> missing = missingRequirements(player, definition, equipmentType);
        if (!missing.isEmpty()) return EquipResult.REQUIREMENTS_NOT_MET;

        SpecialEgoRegistryData.AcquireResult acquired = registry.acquire(
                specialEgoId, equipmentType, player.getUUID(), player.getGameProfile().getName());
        if (acquired == SpecialEgoRegistryData.AcquireResult.IN_USE_BY_OTHER) {
            return EquipResult.IN_USE_BY_OTHER;
        }
        if (acquired == SpecialEgoRegistryData.AcquireResult.LOCKED) return EquipResult.LOCKED;
        if (acquired == SpecialEgoRegistryData.AcquireResult.INVALID) return EquipResult.INVALID;

        // A player may hold one special weapon and one special armor assignment, independently.
        for (SpecialEgoCatalog.Definition other : SpecialEgoCatalog.definitions()) {
            if (!other.id().equals(specialEgoId)) {
                registry.release(other.id(), equipmentType, player.getUUID());
            }
        }
        if (!EgoLoadoutService.equipSpecial(player, definition, equipmentType)) {
            registry.release(specialEgoId, equipmentType, player.getUUID());
            EgoLoadoutService.ensurePersonalType(player, equipmentType);
            return EquipResult.INVALID;
        }
        return EquipResult.SUCCESS;
    }

    public static boolean hasEquippedSpecial(ServerPlayer player, String equipmentType) {
        if (!isHuman(player) || !SpecialEgoRegistryData.validType(equipmentType)) return false;
        SpecialEgoRegistryData registry = SpecialEgoRegistryData.get(player.server);
        return SpecialEgoCatalog.definitions().stream().anyMatch(definition ->
                registry.isUsedBy(definition.id(), equipmentType, player.getUUID()));
    }

    public static List<String> missingRequirements(ServerPlayer player,
                                                   SpecialEgoCatalog.Definition definition,
                                                   String equipmentType) {
        return definition == null ? List.of() : EgoLoadoutService.missingRequirements(
                player, definition.itemIds(equipmentType));
    }

    /** Repairs projections after login/death and imports old physical items once. */
    public static void reconcile(ServerPlayer player) {
        if (player == null) return;
        boolean inventoryChanged = migrateAndPurgePhysicalItems(player);
        if (!isHuman(player)) {
            if (hasAnySpecialProjection(player)) {
                EgoLoadoutService.removeSpecialProjections(player);
                inventoryChanged = true;
            }
            if (inventoryChanged) EgoLoadoutService.syncEquipmentState(player);
            return;
        }

        for (String equipmentType : EQUIPMENT_TYPES) {
            reconcileType(player, equipmentType);
        }
        if (inventoryChanged) EgoLoadoutService.syncEquipmentState(player);
    }

    private static void reconcileType(ServerPlayer player, String equipmentType) {
        SpecialEgoRegistryData registry = SpecialEgoRegistryData.get(player.server);
        SpecialEgoCatalog.Definition assigned = null;
        for (SpecialEgoCatalog.Definition definition : SpecialEgoCatalog.definitions()) {
            if (!registry.isUsedBy(definition.id(), equipmentType, player.getUUID())) continue;
            if (assigned == null) assigned = definition;
            else registry.release(definition.id(), equipmentType, player.getUUID());
        }

        if (assigned == null) {
            if (hasSpecialProjection(player, equipmentType)) {
                EgoLoadoutService.unequipSpecial(player, equipmentType);
            } else {
                EgoLoadoutService.ensurePersonalType(player, equipmentType);
            }
            return;
        }
        if (!missingRequirements(player, assigned, equipmentType).isEmpty()) {
            registry.release(assigned.id(), equipmentType, player.getUUID());
            EgoLoadoutService.unequipSpecial(player, equipmentType);
            player.sendSystemMessage(Component.literal("§c属性等级已不满足要求，特殊 E.G.O "
                    + typeName(equipmentType) + "已自动卸下。"));
            return;
        }
        if (!EgoLoadoutService.hasCompleteSpecialProjectionSet(player, assigned, equipmentType)) {
            EgoLoadoutService.equipSpecial(player, assigned, equipmentType);
        }
    }

    /** Releases world-exclusive active slots as soon as a player leaves the server. */
    public static void releaseOnLogout(ServerPlayer player) {
        if (player != null) SpecialEgoRegistryData.get(player.server).releaseAll(player.getUUID());
    }

    /** Removes an actively usable physical copy immediately, before full reconciliation. */
    public static void purgeActivePhysicalCopies(ServerPlayer player) {
        if (player == null) return;
        boolean changed = false;
        SpecialEgoRegistryData registry = SpecialEgoRegistryData.get(player.server);
        for (EquipmentSlot slot : ACTIVE_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (SpecialEgoCatalog.isSpecialItem(stack) && !EgoLoadoutService.isProjection(stack)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                changed = true;
                continue;
            }
            if (EgoLoadoutService.isSpecialProjection(stack)) {
                String type = EgoLoadoutService.projectionType(stack);
                if (player instanceof EmployeeServerPlayer
                        || !registry.isUsedBy(EgoLoadoutService.specialProjectionId(stack),
                                type, player.getUUID())
                        || !EgoLoadoutService.isProjectionOwnedBy(stack, player.getUUID())) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                    changed = true;
                }
            }
        }
        if (changed) EgoLoadoutService.syncEquipmentState(player);
    }

    private static boolean migrateAndPurgePhysicalItems(ServerPlayer player) {
        List<ItemStack> stacks = allStacks(player);
        Set<String> physicalIds = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (!EgoLoadoutService.isProjection(stack) && SpecialEgoCatalog.isSpecialItem(stack)) {
                physicalIds.add(itemId(stack));
            }
        }
        if (physicalIds.isEmpty()) return false;

        purgePhysical(player.getInventory().items);
        purgePhysical(player.getInventory().armor);
        purgePhysical(player.getInventory().offhand);
        if (SpecialEgoCatalog.isSpecialItem(player.containerMenu.getCarried())
                && !EgoLoadoutService.isProjection(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        if (isHuman(player)) {
            SpecialEgoRegistryData registry = SpecialEgoRegistryData.get(player.server);
            for (SpecialEgoCatalog.Definition definition : SpecialEgoCatalog.definitions()) {
                boolean matchedAny = definition.itemIds().stream().anyMatch(physicalIds::contains);
                if (!matchedAny) continue;
                boolean newlyUnlocked = registry.unlock(definition.id());
                if (newlyUnlocked) {
                    player.sendSystemMessage(Component.translatable(
                            "message.lobotocraft.special_ego.legacy_unlocked",
                            Component.translatable(definition.displayNameKey())));
                }
                for (String type : EQUIPMENT_TYPES) {
                    if (!physicalIds.containsAll(definition.itemIds(type))
                            || !missingRequirements(player, definition, type).isEmpty()) continue;
                    EquipResult result = setEquipped(player, definition.id(), type, true);
                    if (result == EquipResult.IN_USE_BY_OTHER) {
                        player.sendSystemMessage(Component.literal("§7该特殊 E.G.O 的"
                                + typeName(type) + "席位正由其他玩家使用，旧版复制品已回收。"));
                    }
                }
            }
        }
        return true;
    }

    private static void purgePhysical(List<ItemStack> stacks) {
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (SpecialEgoCatalog.isSpecialItem(stack) && !EgoLoadoutService.isProjection(stack)) {
                stacks.set(index, ItemStack.EMPTY);
            }
        }
    }

    private static boolean hasAnySpecialProjection(ServerPlayer player) {
        return allStacks(player).stream().anyMatch(EgoLoadoutService::isSpecialProjection);
    }

    private static boolean hasSpecialProjection(ServerPlayer player, String equipmentType) {
        return allStacks(player).stream().anyMatch(stack -> EgoLoadoutService.isSpecialProjection(stack)
                && equipmentType.equals(EgoLoadoutService.projectionType(stack)));
    }

    private static List<ItemStack> allStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(player.getInventory().items);
        stacks.addAll(player.getInventory().armor);
        stacks.addAll(player.getInventory().offhand);
        stacks.add(player.containerMenu.getCarried());
        return stacks;
    }

    private static String itemId(ItemStack stack) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static String typeName(String equipmentType) {
        return "weapon".equals(equipmentType) ? "武器" : "护甲";
    }

    private static boolean isHuman(ServerPlayer player) {
        return player != null && !(player instanceof EmployeeServerPlayer);
    }

    public enum EquipResult {
        SUCCESS,
        IN_USE_BY_OTHER,
        NOT_ASSIGNED,
        LOCKED,
        REQUIREMENTS_NOT_MET,
        INVALID
    }
}
