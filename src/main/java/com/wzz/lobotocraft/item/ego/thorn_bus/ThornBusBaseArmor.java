package com.wzz.lobotocraft.item.ego.thorn_bus;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ThornBusBaseArmor extends BaseEgoArmor {

    public ThornBusBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
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
        return "thorn_bus";
    }

    @Override
    public String getSetId() {
        return "thorn_bus";
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
        return 0.8f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public boolean useSeparateTextures() {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7寻求常人无法承受的快感通常会使人丧失自我。"));
        p_41423_.add(Component.literal("§7如果那些荆棘上落下的粉末散播到了尘世，那么人们恐怕此生都将像陷入泥沼一般难以求生。"));
        p_41423_.add(Component.literal("§c红色伤害：1.2"));
        p_41423_.add(Component.literal("§f白色伤害：0.8"));
        p_41423_.add(Component.literal("§5黑色伤害：0.8"));
        p_41423_.add(Component.literal("§b蓝色伤害：1.5"));
    }
}
