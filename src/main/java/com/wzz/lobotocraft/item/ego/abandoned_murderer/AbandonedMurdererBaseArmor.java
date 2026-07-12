package com.wzz.lobotocraft.item.ego.abandoned_murderer;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbandonedMurdererBaseArmor extends BaseEgoArmor {

    public AbandonedMurdererBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.TETH;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "abandoned_murderer";
    }

    @Override
    public String getSetId() {
        return "abandoned_murderer";
    }

    @Override
    public float getRedResistance() {
        return 0.7f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.2f;
    }

    @Override
    public float getBlackResistance() {
        return 0.8f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public boolean useSeparateModel() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7秘密研究是从一个地下室开始的，而那项研究有着改变人类未来的无限可能。"));
        p_41423_.add(Component.literal("§7没人会为那些沦为小白鼠的死囚哀悼，他们只能慢慢烂在束缚自己的紧身衣中。"));
        p_41423_.add(Component.literal("§7现在那件紧身衣只是被用作配饰，但过去的悔恨和愤怒依然存在。"));
        p_41423_.add(Component.literal("§7如果穿戴者再也无法忍受这件护甲所带来的压力，那他们必须被送去接受心理咨询。"));
    }
}