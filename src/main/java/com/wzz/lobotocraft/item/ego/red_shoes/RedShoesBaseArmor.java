package com.wzz.lobotocraft.item.ego.red_shoes;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class RedShoesBaseArmor extends BaseEgoArmor {

    public RedShoesBaseArmor(Type type) {
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
        return "red_shoes";
    }

    @Override
    public String getSetId() {
        return "red_shoes";
    }

    @Override
    public float getRedResistance() {
        return 0.5f;
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
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※穿戴护甲的玩家血量越高受到的伤害越低。"));
        p_41423_.add(Component.literal("§7甚至在女孩的双脚被砍断后，舞鞋依旧凭借着难以置信的执念，带着残肢进入了森林。"));
        p_41423_.add(Component.literal("§7这件护甲上可爱的蕾丝花边能让人联想起一位面带微笑的美丽女孩。"));
        p_41423_.add(Component.literal("§7一定要对这双舞鞋保持警惕，不要让那些悲剧再度重演。"));
        p_41423_.add(Component.literal("§7也许某一天，这双沾满鲜血的舞鞋将走向只属于它的豪华舞厅。"));
    }
}