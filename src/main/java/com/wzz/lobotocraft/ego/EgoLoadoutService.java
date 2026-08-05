package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.capability.PlayerAbnormalityData;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.item.ego.base.IEgoLevelItem;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import com.wzz.lobotocraft.util.EgoArmorHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class EgoLoadoutService {
    private static final String SOLEMN_LAMENT_CODE = "T-01-68";
    public static final String PROJECTION_TAG = "LobotocraftEgoProjection";
    private static final String OWNER_TAG = "LobotocraftEgoOwner";
    private static final String CODE_TAG = "LobotocraftEgoCode";
    private static final String TYPE_TAG = "LobotocraftEgoType";
    private static final String VARIANT_TAG = "LobotocraftEgoVariant";
    private static final String SPECIAL_ID_TAG = "LobotocraftSpecialEgo";

    private EgoLoadoutService() {
    }

    public static boolean equipPersonal(ServerPlayer player, String equipmentType, String abnormalityCode) {
        if (!"weapon".equals(equipmentType) && !"armor".equals(equipmentType)) return false;
        if (SpecialEgoService.hasEquippedSpecial(player, equipmentType)) return false;
        PlayerAbnormalityData data = player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .resolve().orElse(null);
        if (data == null) return false;
        List<EgoItemSpec> itemSpecs = data.getPersonalEgoItemSpecs(abnormalityCode, equipmentType);
        if (itemSpecs.isEmpty()) return false;
        if (!missingRequirementsForSpecs(player, itemSpecs).isEmpty()) return false;

        removeProjections(player, equipmentType);
        data.setEquippedPersonalEgo(equipmentType, abnormalityCode);
        if ("armor".equals(equipmentType)) {
            projectArmor(player, abnormalityCode, itemSpecs);
        } else if ("weapon".equals(equipmentType)) {
            projectWeapons(player, abnormalityCode, itemSpecs);
        }
        syncEquipmentState(player);
        return true;
    }

    public static boolean unequipPersonal(ServerPlayer player, String equipmentType) {
        if (!"weapon".equals(equipmentType) && !"armor".equals(equipmentType)) return false;
        PlayerAbnormalityData data = player.getCapability(
                PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).resolve().orElse(null);
        if (data == null) return false;
        data.setEquippedPersonalEgo(equipmentType, "");
        removeProjections(player, equipmentType);
        syncEquipmentState(player);
        return true;
    }

    public static void ensurePersonalLoadout(ServerPlayer player) {
        player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
            ensureType(player, data, "armor");
            ensureType(player, data, "weapon");
        });
    }

    public static boolean equipAi(ServerPlayer player, AiEgoRepositoryData repository,
                                  String abnormalityCode, String equipmentType) {
        AiEgoRepositoryData.Stock stock = repository.stock(abnormalityCode, equipmentType);
        if (stock == null || stock.itemSpecs().isEmpty()) return false;
        if (!missingRequirementsForSpecs(player, stock.itemSpecs()).isEmpty()) return false;
        removeProjections(player, equipmentType);
        if ("armor".equals(equipmentType)) {
            projectArmor(player, abnormalityCode, stock.itemSpecs());
        } else if ("weapon".equals(equipmentType)) {
            projectWeapons(player, abnormalityCode, stock.itemSpecs());
            prepareSolemnLamentAiHands(player, abnormalityCode);
        }
        syncEquipmentState(player);
        return true;
    }

    /** Projects one independently assigned part of a special E.G.O. */
    public static boolean equipSpecial(ServerPlayer player, SpecialEgoCatalog.Definition definition,
                                       String equipmentType) {
        if (player == null || definition == null || player instanceof EmployeeServerPlayer) return false;
        if (!SpecialEgoRegistryData.validType(equipmentType)) return false;
        List<EgoItemSpec> itemSpecs = EgoItemSpec.fromItemIds(definition.itemIds(equipmentType));
        if (itemSpecs.isEmpty() || !missingRequirementsForSpecs(player, itemSpecs).isEmpty()) return false;

        removeProjections(player, equipmentType);
        if ("armor".equals(equipmentType)) {
            projectArmor(player, definition.projectionCode(), itemSpecs, definition.id());
        } else {
            projectWeapons(player, definition.projectionCode(), itemSpecs, definition.id());
        }
        syncEquipmentState(player);
        return true;
    }

    /** Removes one special part and restores the same type from the personal loadout. */
    public static void unequipSpecial(ServerPlayer player, String equipmentType) {
        if (player == null) return;
        removeSpecialProjections(player, equipmentType);
        ensurePersonalType(player, equipmentType);
        syncEquipmentState(player);
    }

    public static boolean hasCompleteSpecialProjectionSet(
            ServerPlayer player, SpecialEgoCatalog.Definition definition, String equipmentType) {
        if (player == null || definition == null) return false;
        List<EgoItemSpec> expected = EgoItemSpec.fromItemIds(definition.itemIds(equipmentType));
        return !expected.isEmpty() && hasCompleteProjectionSet(
                player, definition.projectionCode(), equipmentType, expected);
    }

    public static void ensurePersonalType(ServerPlayer player, String equipmentType) {
        if (player == null || !SpecialEgoRegistryData.validType(equipmentType)
                || SpecialEgoService.hasEquippedSpecial(player, equipmentType)) return;
        player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .ifPresent(data -> ensureType(player, data, equipmentType));
    }

    public static void releaseAi(ServerPlayer player, AiEgoRepositoryData repository,
                                 String equipmentType) {
        repository.release(player.getUUID(), equipmentType);
        clearAiProjection(player, equipmentType);
    }

    public static void clearAiProjection(ServerPlayer player, String equipmentType) {
        removeProjections(player, equipmentType);
        syncEquipmentState(player);
    }

    public static void ensureAiLoadout(ServerPlayer player, AiEgoRepositoryData repository) {
        for (String type : List.of("armor", "weapon")) {
            String code = repository.assignedCode(player.getUUID(), type);
            if (code.isEmpty()) continue;
            AiEgoRepositoryData.Stock stock = repository.stock(code, type);
            if (stock == null) continue;
            boolean complete = hasCompleteProjectionSet(player, code, type, stock.itemSpecs());
            if (!complete) equipAi(player, repository, code, type);
        }
    }

    /**
     * Returns whether the employee has a real, researched weapon assignment and
     * currently satisfies every E.G.O level requirement.
     */
    public static boolean hasValidAssignedAiWeapon(ServerPlayer player,
                                                    AiEgoRepositoryData repository) {
        String code = repository.assignedCode(player.getUUID(), "weapon");
        AiEgoRepositoryData.Stock stock = code.isEmpty() ? null : repository.stock(code, "weapon");
        return isValidAiWeaponAssignment(code, stock,
                stock == null ? List.of() : missingRequirements(player, stock.itemIds()),
                EgoLoadoutService::isEgoWeaponItem);
    }

    /** Restores and selects the assigned weapon projection before combat starts. */
    public static boolean selectUsableAssignedAiWeapon(ServerPlayer player,
                                                        AiEgoRepositoryData repository) {
        if (!hasValidAssignedAiWeapon(player, repository)) return false;
        ensureAiLoadout(player, repository);
        String code = repository.assignedCode(player.getUUID(), "weapon");
        if (prepareSolemnLamentAiHands(player, code)) return true;
        for (int slot = 0; slot < Math.min(9, player.getInventory().items.size()); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (isAssignedWeaponProjection(stack, player.getUUID(), code)) {
                player.getInventory().selected = slot;
                return true;
            }
        }
        return false;
    }

    static boolean prepareSolemnLamentAiHands(ServerPlayer player, String code) {
        if (player == null || !SOLEMN_LAMENT_CODE.equalsIgnoreCase(code)) return false;
        ItemStack white = findSolemnLamentProjection(player, code, false);
        ItemStack black = findSolemnLamentProjection(player, code, true);
        if (white.isEmpty() || black.isEmpty()) return false;
        if (player.getMainHandItem() == white && player.getOffhandItem() == black) return true;

        removeStackReference(player.getInventory().items, white);
        removeStackReference(player.getInventory().items, black);
        removeStackReference(player.getInventory().offhand, white);
        removeStackReference(player.getInventory().offhand, black);

        int mainSlot = player.getInventory().selected;
        stashNonProjection(player, player.getOffhandItem());
        // Preserve the selected slot until the offhand item has found another slot;
        // otherwise Inventory.add may place it here just before the white gun replaces it.
        stashInventorySlot(player, mainSlot);
        player.getInventory().setItem(mainSlot, white);
        player.setItemSlot(EquipmentSlot.OFFHAND, black);
        syncEquipmentState(player);
        return true;
    }

    private static ItemStack findSolemnLamentProjection(ServerPlayer player, String code,
                                                        boolean black) {
        for (ItemStack stack : allInventoryStacks(player)) {
            if (isAssignedWeaponProjection(stack, player.getUUID(), code)
                    && stack.getItem() instanceof ButterflyFuneralWeapon
                    && ButterflyFuneralWeapon.isBlack(stack) == black) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void removeStackReference(List<ItemStack> stacks, ItemStack target) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (stacks.get(slot) == target) stacks.set(slot, ItemStack.EMPTY);
        }
    }

    static boolean isValidAiWeaponAssignment(String assignedCode,
                                              AiEgoRepositoryData.Stock stock,
                                              List<String> missingRequirements,
                                              Predicate<String> isWeaponItem) {
        return assignedCode != null && !assignedCode.isBlank()
                && stock != null
                && assignedCode.equals(stock.abnormalityCode())
                && "weapon".equals(stock.equipmentType())
                && stock.total() > 0
                && !stock.itemIds().isEmpty()
                && missingRequirements != null && missingRequirements.isEmpty()
                && isWeaponItem != null
                && stock.itemIds().stream().allMatch(isWeaponItem);
    }

    public static void validateAiAssignmentsOnSpawn(ServerPlayer player,
                                                    AiEgoRepositoryData repository) {
        boolean changed = false;
        for (String type : List.of("armor", "weapon")) {
            String code = repository.assignedCode(player.getUUID(), type);
            if (code.isEmpty()) {
                if (hasProjectionType(player, type)) {
                    removeProjections(player, type);
                    changed = true;
                }
                continue;
            }
            AiEgoRepositoryData.Stock stock = repository.stock(code, type);
            if (stock == null || !missingRequirements(player, stock.itemIds()).isEmpty()) {
                repository.release(player.getUUID(), type);
                removeProjections(player, type);
                changed = true;
            }
        }
        if (changed) syncEquipmentState(player);
    }

    private static boolean hasProjectionType(ServerPlayer player, String equipmentType) {
        for (ItemStack stack : player.getInventory().items) {
            if (isProjectionType(stack, equipmentType)) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (isProjectionType(stack, equipmentType)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isProjectionType(stack, equipmentType)) return true;
        }
        return false;
    }

    private static boolean isProjectionType(ItemStack stack, String equipmentType) {
        return isProjection(stack) && equipmentType.equals(stack.getOrCreateTag().getString(TYPE_TAG));
    }

    public static List<String> personalMissingRequirements(ServerPlayer player,
                                                            String equipmentType,
                                                            String abnormalityCode) {
        PlayerAbnormalityData data = player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .resolve().orElse(null);
        if (data == null) return List.of();
        return missingRequirements(player, data.getPersonalEgoItems(abnormalityCode, equipmentType));
    }

    public static List<String> missingRequirements(ServerPlayer player, List<String> itemIds) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (String itemId : itemIds) {
            ItemStack stack = stack(itemId);
            if (stack.getItem() instanceof IEgoLevelItem levelItem) {
                missing.addAll(levelItem.getMissingLevels(player));
            }
        }
        return List.copyOf(missing);
    }

    public static List<String> missingRequirementsForSpecs(ServerPlayer player, List<EgoItemSpec> itemSpecs) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (EgoItemSpec itemSpec : itemSpecs) {
            ItemStack stack = itemSpec.createStack();
            if (stack.getItem() instanceof IEgoLevelItem levelItem) {
                missing.addAll(levelItem.getMissingLevels(player));
            }
        }
        return List.copyOf(missing);
    }

    public static Map<String, Integer> requiredLevels(List<String> itemIds) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (String itemId : itemIds) {
            ItemStack stack = stack(itemId);
            if (stack.getItem() instanceof IEgoLevelItem levelItem) {
                levelItem.getRequiredLevels().forEach((key, value) ->
                        required.merge(key, value, Math::max));
            }
        }
        return Map.copyOf(required);
    }

    private static void ensureType(ServerPlayer player, PlayerAbnormalityData data, String equipmentType) {
        if (SpecialEgoService.hasEquippedSpecial(player, equipmentType)) return;
        String code = data.getEquippedPersonalEgo(equipmentType);
        if (code.isEmpty()) {
            removeProjections(player, equipmentType);
            return;
        }
        List<EgoItemSpec> expected = data.getPersonalEgoItemSpecs(code, equipmentType);
        if (expected.isEmpty()) {
            data.setEquippedPersonalEgo(equipmentType, "");
            removeProjections(player, equipmentType);
            return;
        }
        if ("armor".equals(equipmentType)) {
            for (EgoItemSpec itemSpec : expected) {
                ItemStack stack = itemSpec.createStack();
                if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) continue;
                EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
                ItemStack equipped = player.getItemBySlot(slot);
                if (!matches(equipped, player.getUUID(), code, equipmentType, itemSpec, expected)) {
                    stashNonProjection(player, equipped);
                    player.setItemSlot(slot, projection(stack, player.getUUID(), code, equipmentType));
                }
            }
        } else {
            reconcileWeaponProjections(player, code, expected);
        }
    }

    private static void projectArmor(ServerPlayer player, String code, List<EgoItemSpec> itemSpecs) {
        projectArmor(player, code, itemSpecs, null);
    }

    private static void projectArmor(ServerPlayer player, String code, List<EgoItemSpec> itemSpecs,
                                     String specialId) {
        for (EgoItemSpec itemSpec : itemSpecs) {
            ItemStack stack = itemSpec.createStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) continue;
            EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
            stashNonProjection(player, player.getItemBySlot(slot));
            player.setItemSlot(slot,
                    projection(stack, player.getUUID(), code, "armor", specialId));
        }
    }

    private static void projectWeapons(ServerPlayer player, String code, List<EgoItemSpec> itemSpecs) {
        projectWeapons(player, code, itemSpecs, null);
    }

    private static void projectWeapons(ServerPlayer player, String code, List<EgoItemSpec> itemSpecs,
                                       String specialId) {
        boolean first = true;
        for (EgoItemSpec itemSpec : itemSpecs) {
            ItemStack stack = itemSpec.createStack();
            if (stack.isEmpty()) continue;
            ItemStack projected = projection(
                    stack, player.getUUID(), code, "weapon", specialId);
            if (first) {
                int slot = player.getInventory().selected;
                stashInventorySlot(player, slot);
                player.getInventory().setItem(slot, projected);
                first = false;
            } else {
                putWeaponProjection(player, projected);
            }
        }
    }

    private static void putWeaponProjection(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
    }

    private static void stashInventorySlot(ServerPlayer player, int reservedSlot) {
        Inventory inventory = player.getInventory();
        ItemStack existing = inventory.getItem(reservedSlot);
        if (existing.isEmpty() || isProjection(existing)) return;
        ItemStack copy = existing.copy();
        inventory.setItem(reservedSlot, ItemStack.EMPTY);
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (slot != reservedSlot && inventory.items.get(slot).isEmpty()) {
                inventory.setItem(slot, copy);
                return;
            }
        }
        player.drop(copy, false);
    }

    private static void stashNonProjection(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || isProjection(stack)) return;
        ItemStack copy = stack.copy();
        stack.setCount(0);
        if (!player.getInventory().add(copy)) player.drop(copy, false);
    }

    public static void removeProjections(ServerPlayer player, String equipmentType) {
        Inventory inventory = player.getInventory();
        removeFromList(inventory.items, equipmentType);
        removeFromList(inventory.armor, equipmentType);
        removeFromList(inventory.offhand, equipmentType);
        if (isProjectionType(player.containerMenu.getCarried(), equipmentType)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public static void removeSpecialProjections(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        removeSpecialFromList(inventory.items);
        removeSpecialFromList(inventory.armor);
        removeSpecialFromList(inventory.offhand);
        if (isSpecialProjection(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public static void removeSpecialProjections(ServerPlayer player, String equipmentType) {
        Inventory inventory = player.getInventory();
        removeSpecialFromList(inventory.items, equipmentType);
        removeSpecialFromList(inventory.armor, equipmentType);
        removeSpecialFromList(inventory.offhand, equipmentType);
        if (isSpecialProjection(player.containerMenu.getCarried())
                && equipmentType.equals(projectionType(player.containerMenu.getCarried()))) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    private static void removeSpecialFromList(List<ItemStack> stacks) {
        for (int index = 0; index < stacks.size(); index++) {
            if (isSpecialProjection(stacks.get(index))) stacks.set(index, ItemStack.EMPTY);
        }
    }

    private static void removeSpecialFromList(List<ItemStack> stacks, String equipmentType) {
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (isSpecialProjection(stack) && equipmentType.equals(projectionType(stack))) {
                stacks.set(index, ItemStack.EMPTY);
            }
        }
    }

    private static void removeFromList(List<ItemStack> stacks, String equipmentType) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (isProjection(stack) && equipmentType.equals(stack.getOrCreateTag().getString(TYPE_TAG))) {
                stacks.set(i, ItemStack.EMPTY);
            }
        }
    }

    public static boolean isProjection(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(PROJECTION_TAG);
    }

    public static boolean isArmorProjection(ItemStack stack) {
        return isProjection(stack) && "armor".equals(stack.getOrCreateTag().getString(TYPE_TAG));
    }

    public static boolean isWeaponProjection(ItemStack stack) {
        return isProjection(stack) && "weapon".equals(stack.getOrCreateTag().getString(TYPE_TAG));
    }

    public static boolean isSpecialProjection(ItemStack stack) {
        return isProjection(stack) && !stack.getOrCreateTag().getString(SPECIAL_ID_TAG).isBlank();
    }

    public static String specialProjectionId(ItemStack stack) {
        return isSpecialProjection(stack)
                ? stack.getOrCreateTag().getString(SPECIAL_ID_TAG) : "";
    }

    public static String projectionType(ItemStack stack) {
        return isProjection(stack) ? stack.getOrCreateTag().getString(TYPE_TAG) : "";
    }

    public static boolean isProjectionOwnedBy(ItemStack stack, UUID owner) {
        if (!isProjection(stack) || owner == null) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER_TAG) && owner.equals(tag.getUUID(OWNER_TAG));
    }

    private static boolean isEgoWeaponItem(String itemId) {
        return stack(itemId).getItem() instanceof BaseEgoWeapon;
    }

    private static boolean isAssignedWeaponProjection(ItemStack stack, UUID owner, String code) {
        if (!isProjection(stack) || !(stack.getItem() instanceof BaseEgoWeapon)) return false;
        var tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER_TAG) && owner.equals(tag.getUUID(OWNER_TAG))
                && code.equals(tag.getString(CODE_TAG)) && "weapon".equals(tag.getString(TYPE_TAG));
    }

    static void syncEquipmentState(ServerPlayer player) {
        player.detectEquipmentUpdates();
        EgoArmorHelper.applyArmorResistances(player);
        player.inventoryMenu.broadcastChanges();
        player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(),
                player.getAttributes().getSyncableAttributes()));
    }

    private static ItemStack projection(ItemStack source, UUID owner, String code, String equipmentType) {
        return projection(source, owner, code, equipmentType, null);
    }

    private static ItemStack projection(ItemStack source, UUID owner, String code,
                                        String equipmentType, String specialId) {
        ItemStack result = source.copy();
        result.setCount(1);
        result.getOrCreateTag().putBoolean(PROJECTION_TAG, true);
        result.getOrCreateTag().putUUID(OWNER_TAG, owner);
        result.getOrCreateTag().putString(CODE_TAG, code);
        result.getOrCreateTag().putString(TYPE_TAG, equipmentType);
        if (specialId != null && !specialId.isBlank()) {
            result.getOrCreateTag().putString(SPECIAL_ID_TAG, specialId);
        } else {
            result.getOrCreateTag().remove(SPECIAL_ID_TAG);
        }
        EgoItemSpec spec = EgoItemSpec.fromStack(source);
        if (spec != null) result.getOrCreateTag().putString(VARIANT_TAG, spec.variantId());
        return result;
    }

    private static boolean hasCompleteProjectionSet(ServerPlayer player, String code, String type,
                                                    List<EgoItemSpec> expected) {
        boolean[] matched = new boolean[expected.size()];
        for (ItemStack stack : allInventoryStacks(player)) {
            if (!isProjectionType(stack, type)) continue;
            boolean found = false;
            for (int i = 0; i < expected.size(); i++) {
                if (!matched[i] && matches(stack, player.getUUID(), code, type, expected.get(i), expected)) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        for (boolean value : matched) if (!value) return false;
        return true;
    }

    private static boolean matches(ItemStack stack, UUID owner, String code, String type,
                                   EgoItemSpec spec, List<EgoItemSpec> siblingSpecs) {
        if (!isProjection(stack)) return false;
        var tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER_TAG) && owner.equals(tag.getUUID(OWNER_TAG))
                && code.equals(tag.getString(CODE_TAG)) && type.equals(tag.getString(TYPE_TAG))
                && spec.variantId().equals(tag.getString(VARIANT_TAG))
                && spec.hasControlledState(stack, controlledKeys(siblingSpecs));
    }

    private static java.util.Set<String> controlledKeys(List<EgoItemSpec> specs) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        specs.forEach(spec -> keys.addAll(spec.initialKeys()));
        return keys;
    }

    private static List<ItemStack> allInventoryStacks(ServerPlayer player) {
        List<ItemStack> all = new ArrayList<>();
        all.addAll(player.getInventory().items);
        all.addAll(player.getInventory().armor);
        all.addAll(player.getInventory().offhand);
        return all;
    }

    private static void reconcileWeaponProjections(ServerPlayer player, String code,
                                                   List<EgoItemSpec> expected) {
        boolean[] matched = new boolean[expected.size()];
        ItemStack carried = player.containerMenu.getCarried();
        if (isWeaponProjection(carried)) {
            int match = findUnmatched(carried, player.getUUID(), code, expected, matched);
            if (match < 0) player.containerMenu.setCarried(ItemStack.EMPTY);
            else matched[match] = true;
        }
        reconcileWeaponList(player.getInventory().items, player.getUUID(), code, expected, matched);
        reconcileWeaponList(player.getInventory().offhand, player.getUUID(), code, expected, matched);
        for (int i = 0; i < expected.size(); i++) {
            if (!matched[i]) {
                ItemStack stack = expected.get(i).createStack();
                if (!stack.isEmpty()) putWeaponProjection(player,
                        projection(stack, player.getUUID(), code, "weapon"));
            }
        }
    }

    private static void reconcileWeaponList(List<ItemStack> stacks, UUID owner, String code,
                                            List<EgoItemSpec> expected, boolean[] matched) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!isProjectionType(stack, "weapon")) continue;
            int match = findUnmatched(stack, owner, code, expected, matched);
            if (match < 0) stacks.set(slot, ItemStack.EMPTY);
            else matched[match] = true;
        }
    }

    private static int findUnmatched(ItemStack stack, UUID owner, String code,
                                     List<EgoItemSpec> expected, boolean[] matched) {
        for (int i = 0; i < expected.size(); i++) {
            if (!matched[i] && matches(stack, owner, code, "weapon", expected.get(i), expected)) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack stack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
