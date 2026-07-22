package com.wzz.lobotocraft.item.ego.happy_teddy;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wzz.lobotocraft.util.MentalValueUtil.getMentalValue;

public abstract class HappyTeddyBaseArmor extends BaseEgoArmor {

    public HappyTeddyBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 2);
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
        return "happy_teddy";
    }

    @Override
    public String getSetId() {
        return "happy_teddy";
    }

    @Override
    public float getRedResistance() {
        return 0.8f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
    }

    @Override
    public float getBlackResistance() {
        return 1.0f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※这件护甲会根据玩家的精神值额外提供自律加成。"));
        p_41423_.add(Component.literal("§7或许它的身体曾经是毛茸茸的，但如今却因为破烂而显得寒酸。"));
        p_41423_.add(Component.literal("§7修复它的请求被果断回绝。"));
        p_41423_.add(Component.literal("§7那些在它印象中对拥抱的温暖记忆，伴随着对它的抛弃一并消逝了。"));
        p_41423_.add(Component.literal("§7如果这只熊喜欢你身上被遗忘的童真，那些棉花就会从裂缝中伸出。"));
        p_41423_.add(Component.literal("§c红色伤害：0.8"));
        p_41423_.add(Component.literal("§f白色伤害：1.0"));
        p_41423_.add(Component.literal("§5黑色伤害：1.0"));
        p_41423_.add(Component.literal("§b蓝色伤害：1.5"));
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        if (EgoArmorHelper.isWearingFullSet(player, "happy_teddy")) {
            float mentalValue = getMentalValue(player);
            float bonus = (mentalValue / 20) * 0.02f;
            return Math.min(bonus, 0.06f);
        }
        return super.getWorkSuccessBonus(player, workType);
    }
}