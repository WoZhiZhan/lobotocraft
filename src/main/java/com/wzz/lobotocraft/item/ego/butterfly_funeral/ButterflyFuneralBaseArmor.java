package com.wzz.lobotocraft.item.ego.butterfly_funeral;

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

public abstract class ButterflyFuneralBaseArmor extends BaseEgoArmor {

    public ButterflyFuneralBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("JusticeLevel", 3);
        return map;
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
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
        return "butterfly_funeral";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 1.2f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
    }

    @Override
    public float getBlackResistance() {
        return 0.5f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※玩家正义等级越高受到的伤害越低。"));
        p_41423_.add(Component.literal("§7黑色的丧服是为哀悼死者的人准备的。"));
        p_41423_.add(Component.literal("§7葬礼上需要的是严肃，不需要那些色彩斑斓的配饰。"));
        p_41423_.add(Component.literal("§7用蝴蝶为那些长眠于不毛之地的人们献上哀悼吧。"));
    }
}