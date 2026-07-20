package com.wzz.lobotocraft.item.ego.nothing_there;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NothingThereCurio extends BaseEgoCurio {
    private static final UUID ATTACK_SPEED_MODIFIER = UUID.fromString("c92ae539-1cd8-4476-9ba0-5ffe722481d9");
    private static final String MODIFIER_NAME = "ego_attack_speed_boost";
    private static final double SPEED_BONUS = 0.1;

    private static final String SET_ID = "nothing_there";

    // 效果一(溢出加成): 血量>100, 每额外 1 点血 +1% 移速/攻速/伤害, 上限 50%
    private static final UUID OVERHEAL_MOVE_UUID = UUID.fromString("d3246e30-4215-486f-a531-71d9b1fd565c");
    private static final UUID OVERHEAL_ATTACK_UUID = UUID.fromString("c8ecb3e8-96fb-442a-acc3-4523bc85b77b");
    // 效果二(低血攻速): 每降低 5% 生命值 +1% 攻速
    private static final UUID LOWHP_ATTACK_UUID = UUID.fromString("954cfdc3-f6e8-4afa-8ec7-ac2f5bf86df8");

    public NothingThereCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "nothing_there";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof Player player) {
            // 既有: 手持 EGO 武器时 +10% 攻速
            if (player.tickCount % 10 == 0) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof BaseEgoWeapon) {
                    if (!hasAttackSpeedModifier(player)) {
                        addAttackSpeedModifier(player);
                    }
                } else {
                    if (hasAttackSpeedModifier(player)) {
                        removeAttackSpeedModifier(player);
                    }
                }
            }
            // 新增: 血量相关加成(仅服务端, 数值变化时才重挂)
            if (!player.level().isClientSide) {
                updateHealthBonuses(player);
            }
        }
    }

    /** 每 tick 刷新两条血量加成对应的属性修饰符 */
    private void updateHealthBonuses(Player player) {
        float overheal = overhealBonus(player); // 溢出: 移速+攻速(+伤害走事件)
        float lowHp = lowHpAttackBonus(player);  // 低血: 攻速

        applyBase(player, Attributes.MOVEMENT_SPEED, OVERHEAL_MOVE_UUID, "nothing_there_overheal_move", overheal);
        applyBase(player, Attributes.ATTACK_SPEED, OVERHEAL_ATTACK_UUID, "nothing_there_overheal_attack", overheal);
        applyBase(player, Attributes.ATTACK_SPEED, LOWHP_ATTACK_UUID, "nothing_there_lowhp_attack", lowHp);
    }

    /**
     * 效果一强度: 需要 满套锁定 + 手持拟态 + 当前血量>100。
     * 返回 0..0.5, 供 移速/攻速/伤害 共用。
     */
    public static float overhealBonus(Player player) {
        if (!EgoArmorHelper.isFullEGO(player, SET_ID)) return 0F;
        if (!EgoArmorHelper.isHoldingWeapon(player, SET_ID)) return 0F;
        float health = player.getHealth();
        if (health <= 100F) return 0F;
        int extra = (int) (health - 100F); // 每额外 1 点血
        return Math.min(extra * 0.01F, 0.5F);
    }

    /**
     * 效果二强度: 需要满套锁定。每降低 5% 生命值 +1% 攻速。
     */
    public static float lowHpAttackBonus(Player player) {
        if (!EgoArmorHelper.isFullEGO(player, SET_ID)) return 0F;
        float max = player.getMaxHealth();
        if (max <= 0F) return 0F;
        float lostPercent = (1F - player.getHealth() / max) * 100F;
        if (lostPercent <= 0F) return 0F;
        int steps = (int) (lostPercent / 5F); // 每 5% 一档
        return steps * 0.01F;
    }

    /**
     * 奔跑饱食度 -80% 的条件: 满套锁定 + 当前血量>100(不要求手持)。
     * 是否奔跑由 Mixin 侧判断。
     */
    public static boolean sprintExhaustionReduced(Player player) {
        return EgoArmorHelper.isFullEGO(player, SET_ID) && player.getHealth() > 100F;
    }

    /** 以 MULTIPLY_BASE 方式挂/改/摘一个百分比修饰符(值变了才重挂) */
    private static void applyBase(Player player, Attribute attr, UUID id, String name, float amount) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (amount <= 0F) {
            if (existing != null) inst.removeModifier(id);
            return;
        }
        if (existing != null) {
            if (existing.getAmount() != amount) {
                inst.removeModifier(id);
                inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        } else {
            inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        super.onUnequip(slotContext, newStack, stack);
        if (slotContext.entity() instanceof Player player) {
            removeAttackSpeedModifier(player);
            removeModifier(player, Attributes.MOVEMENT_SPEED, OVERHEAL_MOVE_UUID);
            removeModifier(player, Attributes.ATTACK_SPEED, OVERHEAL_ATTACK_UUID);
            removeModifier(player, Attributes.ATTACK_SPEED, LOWHP_ATTACK_UUID);
        }
    }

    private static void removeModifier(Player player, Attribute attr, UUID id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    /** 添加攻击速度加成(既有 +10%) */
    private void addAttackSpeedModifier(Player player) {
        var attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null) {
            removeAttackSpeedModifier(player);
            AttributeModifier modifier = new AttributeModifier(
                    ATTACK_SPEED_MODIFIER,
                    MODIFIER_NAME,
                    SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            attribute.addTransientModifier(modifier);
        }
    }

    private void removeAttackSpeedModifier(Player player) {
        var attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null) {
            attribute.removeModifier(ATTACK_SPEED_MODIFIER);
        }
    }

    private boolean hasAttackSpeedModifier(Player player) {
        var attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null) {
            return attribute.getModifier(ATTACK_SPEED_MODIFIER) != null;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 10").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 使用任意EGO造成伤害时恢复等同于伤害值 5% 的生命值").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 玩家使用近战EGO攻速 + 10%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用“拟态”特殊攻击冷却从10秒更改为5秒，并且特殊攻击对吸血效果修改为100%。特殊攻击每次造成伤害都有50%概率立刻刷新CD。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家身着拟态护甲时，受到白色和黑色伤害将不再降低精神值。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家锁定后血量高于100时，每额外获得1点生命值，则手持拟态时额外提高1%移速、攻速和伤害，最多50%，奔跑期间饱食度消耗减少80%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家根据当前血量百分比额外获得攻速加成，每降低5%生命值则额外获得1%攻击速度。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        List<AttributeEntry> entries = new ArrayList<>();
        entries.add(new AttributeEntry(UUID.fromString("f098ffc3-63f7-4484-958b-c559b8709bb2"),
                this.getCurrentClassName() + " Max Health Penalty",
                Attributes.MAX_HEALTH, 10.0D));
        return entries;
    }
}