package com.wzz.lobotocraft.ego;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;

/** Exercises the real container/mixin path used to arrange owned E.G.O projections. */
@GameTestHolder(ModMain.MODID)
public final class EgoInventoryGameTests {
    private EgoInventoryGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "ego_inventory")
    public static void ownedSolemnLamentMovesFromHotbarToBackpackAndOffhand(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        EmployeeRecord record = new EmployeeRecord(id, "ego_inventory_test",
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(id, record.name()), record);
        helper.getLevel().addNewPlayer(player);

        ItemStack blackGun = ButterflyFuneralWeapon.createBlackWeapon(
                ModItems.BUTTERFLY_FUNERAL_WEAPON.get());
        blackGun.getOrCreateTag().putBoolean(EgoLoadoutService.PROJECTION_TAG, true);
        blackGun.getOrCreateTag().putUUID("LobotocraftEgoOwner", id);
        blackGun.getOrCreateTag().putString("LobotocraftEgoCode", "T-01-68");
        blackGun.getOrCreateTag().putString("LobotocraftEgoType", "weapon");
        player.getInventory().setItem(0, blackGun);
        player.containerMenu = player.inventoryMenu;

        // InventoryMenu: slot 36 is hotbar index 0; slot 9 is main inventory index 9.
        player.inventoryMenu.clicked(36, 0, ClickType.PICKUP, player);
        player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
        ItemStack backpack = player.getInventory().getItem(9);
        if (!EgoLoadoutService.isProjectionOwnedBy(backpack, id)
                || !ButterflyFuneralWeapon.isBlack(backpack)) {
            player.kill();
            helper.fail("owned black gun could not be moved into the main inventory");
            return;
        }

        // InventoryMenu slot 45 is the vanilla offhand slot.
        player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
        player.inventoryMenu.clicked(45, 0, ClickType.PICKUP, player);
        ItemStack offhand = player.getOffhandItem();
        if (!EgoLoadoutService.isProjectionOwnedBy(offhand, id)
                || !ButterflyFuneralWeapon.isBlack(offhand)) {
            player.kill();
            helper.fail("owned black gun could not be moved into the offhand");
            return;
        }

        player.kill();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "ego_inventory")
    public static void personalUnequipRemovesOnlyRequestedProjectionType(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        EmployeeRecord record = new EmployeeRecord(id, "ego_unequip_test",
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(id, record.name()), record);
        helper.getLevel().addNewPlayer(player);

        ItemStack weapon = projection(new ItemStack(Items.IRON_SWORD), id, "weapon");
        ItemStack armor = projection(new ItemStack(Items.IRON_CHESTPLATE), id, "armor");
        ItemStack ordinary = new ItemStack(Items.DIAMOND, 3);
        player.getInventory().setItem(0, weapon);
        player.getInventory().setItem(1, ordinary);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        player.getCapability(com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider
                .PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
            data.setEquippedPersonalEgo("weapon", "test-code");
            data.setEquippedPersonalEgo("armor", "test-code");
        });

        if (!EgoLoadoutService.unequipPersonal(player, "weapon")
                || !player.getInventory().getItem(0).isEmpty()
                || !EgoLoadoutService.isArmorProjection(player.getItemBySlot(EquipmentSlot.CHEST))
                || player.getInventory().getItem(1).getCount() != 3) {
            player.kill();
            helper.fail("weapon unequip removed unrelated equipment or inventory");
            return;
        }
        if (!EgoLoadoutService.unequipPersonal(player, "armor")
                || !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || player.getInventory().getItem(1).getCount() != 3) {
            player.kill();
            helper.fail("armor unequip did not preserve ordinary inventory");
            return;
        }
        boolean assignmentsCleared = player.getCapability(
                com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider
                        .PLAYER_ABNORMALITY_DATA)
                .map(data -> data.getEquippedPersonalEgo("weapon").isEmpty()
                        && data.getEquippedPersonalEgo("armor").isEmpty())
                .orElse(false);
        player.kill();
        if (!assignmentsCleared) {
            helper.fail("personal E.G.O assignment survived unequip");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "ego_inventory")
    public static void aiSolemnLamentUsesWhiteMainHandAndBlackOffhand(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        EmployeeRecord record = new EmployeeRecord(id, "solemn_lament_ai_test",
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer player = new EmployeeServerPlayer(helper.getLevel(),
                new GameProfile(id, record.name()), record);
        helper.getLevel().addNewPlayer(player);

        ItemStack white = solemnLamentProjection(
                ButterflyFuneralWeapon.createWhiteWeapon(ModItems.BUTTERFLY_FUNERAL_WEAPON.get()), id);
        ItemStack black = solemnLamentProjection(
                ButterflyFuneralWeapon.createBlackWeapon(ModItems.BUTTERFLY_FUNERAL_WEAPON.get()), id);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        player.getInventory().setItem(5, black);
        player.getInventory().setItem(6, white);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND, 2));

        boolean prepared = EgoLoadoutService.prepareSolemnLamentAiHands(player, "T-01-68");
        boolean ordinaryItemsPreserved = count(player, Items.IRON_INGOT) == 3
                && count(player, Items.DIAMOND) == 2;
        if (!prepared
                || ButterflyFuneralWeapon.isBlack(player.getMainHandItem())
                || !ButterflyFuneralWeapon.isBlack(player.getOffhandItem())
                || !ordinaryItemsPreserved) {
            player.kill();
            helper.fail("AI Solemn Lament hand layout was not prepared safely");
            return;
        }
        player.kill();
        helper.succeed();
    }

    private static ItemStack projection(ItemStack stack, UUID owner, String type) {
        stack.getOrCreateTag().putBoolean(EgoLoadoutService.PROJECTION_TAG, true);
        stack.getOrCreateTag().putUUID("LobotocraftEgoOwner", owner);
        stack.getOrCreateTag().putString("LobotocraftEgoCode", "test-code");
        stack.getOrCreateTag().putString("LobotocraftEgoType", type);
        return stack;
    }

    private static ItemStack solemnLamentProjection(ItemStack stack, UUID owner) {
        stack.getOrCreateTag().putBoolean(EgoLoadoutService.PROJECTION_TAG, true);
        stack.getOrCreateTag().putUUID("LobotocraftEgoOwner", owner);
        stack.getOrCreateTag().putString("LobotocraftEgoCode", "T-01-68");
        stack.getOrCreateTag().putString("LobotocraftEgoType", "weapon");
        return stack;
    }

    private static int count(EmployeeServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
