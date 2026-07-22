package com.wzz.lobotocraft.item.ego.leticia;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LeticiaBaseArmor extends BaseEgoArmor {

    public LeticiaBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 3);
        return map;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HE;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "leticia";
    }

    @Override
    public String getSetId() {
        return "leticia";
    }

    @Override
    public float getRedResistance() {
        return 0.7f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.7f;
    }

    @Override
    public float getBlackResistance() {
        return 0.7f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※玩家受到的伤害增加30%，造成的伤害减少30%，拥有“蕾蒂希娅的礼物”“破碎的礼物”效果或者EGO饰品“蕾蒂希娅”时，此效果反转"));
        p_41423_.add(Component.literal(""));
        p_41423_.add(Component.literal("§7外套上精美的丝带和蝴蝶结寄托着少女对幸福的向往，"));
        p_41423_.add(Component.literal("§7那个小孩不愿撇下她的朋友，所以，她想出了这个绝妙的主意！"));
        p_41423_.add(Component.literal("§c红色伤害：0.7"));
        p_41423_.add(Component.literal("§f白色伤害：0.7"));
        p_41423_.add(Component.literal("§5黑色伤害：0.7"));
        p_41423_.add(Component.literal("§b蓝色伤害：1.5"));
    }
}