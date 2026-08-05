package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.compat.ysm.YsmEmployeeCompat;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.PEBoxItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RecruitmentService {
    public static final int MAX_PERSONA_CODE_POINTS = 256;
    public static final int MAX_PERSONA_UTF16_LENGTH = MAX_PERSONA_CODE_POINTS * 2;

    private RecruitmentService() {
    }

    public static Result recruit(ServerPlayer recruiter, String requestedName, int fortitudeLevel,
                                 int prudenceLevel, int temperanceLevel, int justiceLevel) {
        return recruit(recruiter, requestedName, fortitudeLevel, prudenceLevel,
                temperanceLevel, justiceLevel, "");
    }

    public static Result recruit(ServerPlayer recruiter, String requestedName, int fortitudeLevel,
                                 int prudenceLevel, int temperanceLevel, int justiceLevel,
                                 String requestedPersona) {
        return recruit(recruiter, requestedName, fortitudeLevel, prudenceLevel,
                temperanceLevel, justiceLevel, requestedPersona, EmployeeAppearance.DEFAULT);
    }

    public static Result recruit(ServerPlayer recruiter, String requestedName, int fortitudeLevel,
                                 int prudenceLevel, int temperanceLevel, int justiceLevel,
                                 String requestedPersona, EmployeeAppearance requestedAppearance) {
        if (!com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(recruiter.level())) {
            return Result.failure("请回到公司后再招募员工。");
        }
        if (!recruiter.hasPermissions(2)) return Result.failure("没有员工招募权限。");
        String name = requestedName == null ? "" : requestedName.strip();
        if (!validName(name)) return Result.failure("员工名称必须为1-24个有效字符。");
        String rawPersona = requestedPersona == null ? "" : requestedPersona;
        if (!validPersona(rawPersona)) {
            return Result.failure("员工人设最多256个字符，且不能包含控制字符或§。");
        }
        String persona = rawPersona.strip();
        if (!validLevel(fortitudeLevel) || !validLevel(prudenceLevel)
                || !validLevel(temperanceLevel) || !validLevel(justiceLevel)) {
            return Result.failure("四维等级必须在I-V之间。");
        }
        EmployeeAppearance appearance = requestedAppearance == null
                ? EmployeeAppearance.DEFAULT : requestedAppearance.sanitized();
        var validatedAppearance = YsmEmployeeCompat.validateRequested(appearance);
        if (validatedAppearance.isEmpty()) {
            return Result.failure("所选YSM模型在服务端不可用，请刷新模型目录后重试。");
        }
        appearance = validatedAppearance.get();
        if (FacilityPersonnelData.get(recruiter.server).findByName(name) != null) {
            return Result.failure("已经存在同名员工。");
        }

        Cost cost = cost(fortitudeLevel, prudenceLevel, temperanceLevel, justiceLevel);
        String missing = missingResources(recruiter, cost);
        if (!missing.isEmpty()) return Result.failure("招募资源不足：" + missing);

        // 原子扣费：预检已确认 5 项资源全部足够，且预检与扣除之间没有任何副作用，
        // 因此在创建员工之前一次性全额扣除，保证要么全扣要么（失败时）全退，玩家资源守恒。
        Inventory inventory = recruiter.getInventory();
        consumeItem(inventory, Items.EMERALD, cost.emeralds());
        consumePeBoxes(inventory, cost.peBoxes());
        consumeItem(inventory, Items.IRON_INGOT, cost.iron());
        consumeItem(inventory, Items.GOLD_INGOT, cost.gold());
        consumeItem(inventory, Items.DIAMOND, cost.diamonds());
        inventory.setChanged();

        EmployeeRecord record = EmployeeRuntimeManager.createAndSpawn(recruiter, name,
                EmployeeDepartment.UNASSIGNED, statValue(fortitudeLevel), statValue(prudenceLevel),
                statValue(temperanceLevel), statValue(justiceLevel), appearance);
        if (record == null) {
            // createAndSpawn 可能已在名册创建 record 但 spawn 失败，留下未部署的"幽灵"记录；
            // 清理它，避免占用名称或在下次重启时被重新 spawn（那会导致已退款的资源不守恒）。
            FacilityPersonnelData data = FacilityPersonnelData.get(recruiter.server);
            EmployeeRecord phantom = data.findByName(name);
            if (phantom != null) data.remove(phantom.id());
            // 创建失败：全额退还刚刚扣除的资源。
            refundItem(recruiter, Items.EMERALD, cost.emeralds());
            refundPeBoxes(recruiter, cost.peBoxes());
            refundItem(recruiter, Items.IRON_INGOT, cost.iron());
            refundItem(recruiter, Items.GOLD_INGOT, cost.gold());
            refundItem(recruiter, Items.DIAMOND, cost.diamonds());
            inventory.setChanged();
            return Result.failure("员工创建失败，已退还全部资源。");
        }
        record.setPersona(persona);
        FacilityPersonnelData.get(recruiter.server).changed();
        return Result.success(record);
    }

    public static Cost cost(int fortitudeLevel, int prudenceLevel,
                            int temperanceLevel, int justiceLevel) {
        int peBoxes = 0;
        int iron = 0;
        int gold = 0;
        int diamonds = 0;
        for (int level : new int[]{fortitudeLevel, prudenceLevel, temperanceLevel, justiceLevel}) {
            if (level >= 2) peBoxes += 10;
            if (level >= 3) iron += 2;
            if (level >= 4) gold += 4;
            if (level >= 5) diamonds += 5;
        }
        return new Cost(1, peBoxes, iron, gold, diamonds);
    }

    public static int countPeBoxes(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PEBoxItem) count += stack.getCount();
        }
        return count;
    }

    private static String missingResources(ServerPlayer player, Cost cost) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        Inventory inventory = player.getInventory();
        addMissing(missing, "绿宝石", inventory.countItem(Items.EMERALD), cost.emeralds());
        addMissing(missing, "任意PE-BOX", countPeBoxes(inventory), cost.peBoxes());
        addMissing(missing, "铁锭", inventory.countItem(Items.IRON_INGOT), cost.iron());
        addMissing(missing, "金锭", inventory.countItem(Items.GOLD_INGOT), cost.gold());
        addMissing(missing, "钻石", inventory.countItem(Items.DIAMOND), cost.diamonds());
        return String.join("，", missing);
    }

    private static void addMissing(java.util.List<String> missing, String name, int held, int required) {
        if (held < required) missing.add(name + " " + held + "/" + required);
    }

    private static void refundItem(ServerPlayer player, Item item, int amount) {
        if (amount <= 0) return;
        // placeItemBackInInventory 会尽量堆叠进背包，放不下的部分自动掉落到玩家脚下，确保不丢失。
        player.getInventory().placeItemBackInInventory(new ItemStack(item, amount));
    }

    private static void refundPeBoxes(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        // 扣除时不区分 PE-BOX 的来源异想体，无法完美还原被消耗的具体种类，
        // 因此退还等量的基础款（无异想体 NBT）PE-BOX，保证数量守恒。
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.PE_BOX.get(), amount));
    }

    private static boolean consumePeBoxes(Inventory inventory, int amount) {
        if (amount <= 0) return true;
        if (countPeBoxes(inventory) < amount) return false;
        return consumeMatching(inventory, amount, stack -> stack.getItem() instanceof PEBoxItem);
    }

    private static boolean consumeItem(Inventory inventory, Item item, int amount) {
        if (amount <= 0) return true;
        if (inventory.countItem(item) < amount) return false;
        return consumeMatching(inventory, amount, stack -> stack.is(item));
    }

    private static boolean consumeMatching(Inventory inventory, int amount,
                                           java.util.function.Predicate<ItemStack> predicate) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!predicate.test(stack)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return remaining == 0;
    }

    static int statValue(int level) {
        return switch (level) {
            case 2 -> 30;
            case 3 -> 45;
            case 4 -> 65;
            case 5 -> 85;
            default -> EmployeeStats.MIN_STAT;
        };
    }

    private static boolean validLevel(int level) {
        return level >= 1 && level <= 5;
    }

    private static boolean validName(String name) {
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 24) return false;
        return name.codePoints().noneMatch(character -> Character.isISOControl(character)
                || character == '\u00a7');
    }

    private static boolean validPersona(String persona) {
        if (persona.codePointCount(0, persona.length()) > MAX_PERSONA_CODE_POINTS) return false;
        return persona.codePoints().noneMatch(character -> Character.isISOControl(character)
                || character == '\u00a7');
    }

    public record Cost(int emeralds, int peBoxes, int iron, int gold, int diamonds) {
    }

    public record Result(boolean success, String message, EmployeeRecord employee) {
        private static Result success(EmployeeRecord employee) {
            return new Result(true, "", employee);
        }

        private static Result failure(String message) {
            return new Result(false, message, null);
        }
    }
}
