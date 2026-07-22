package com.wzz.lobotocraft.item.ego.approval_birds;

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

public abstract class ApprovalBirdBaseArmor extends BaseEgoArmor {

    public ApprovalBirdBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("JusticeLevel", 5);
        map.put("EmployeeLevel", 5);
        return map;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.ALEPH;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "approval_bird";
    }

    @Override
    public String getSetId() {
        return "approval_bird";
    }

    @Override
    public float getRedResistance() {
        return 0.5f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.5f;
    }

    @Override
    public float getBlackResistance() {
        return 0.5f;
    }

    @Override
    public float getPaleResistance() {
        return 0.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7就像其他生物一样，它最初也满怀着希望。"));
        p_41423_.add(Component.literal("§7但如今，对和平的渴望只能潜藏在幼稚的童话里。"));
        p_41423_.add(Component.literal("§7只有公司中最公正无私的人才能穿上这件E.G.O护甲。"));
        p_41423_.add(Component.literal("§7不要试着移去缠在这件护甲上的绷带，它掩盖着那些属于过去的，不应被人所了解的悲哀记忆。"));
        p_41423_.add(Component.literal("§c红色伤害：0.5"));
        p_41423_.add(Component.literal("§f白色伤害：0.5"));
        p_41423_.add(Component.literal("§5黑色伤害：0.5"));
        p_41423_.add(Component.literal("§b蓝色伤害：0.5"));
    }
}