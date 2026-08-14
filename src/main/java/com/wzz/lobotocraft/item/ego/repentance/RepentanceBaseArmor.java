package com.wzz.lobotocraft.item.ego.repentance;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 忏悔套装基类
 * 定义忏悔套装的共同属性和效果
 * 4件装备（头盔、胸甲、护腿、靴子）都继承这个类，避免重复代码
 */
public abstract class RepentanceBaseArmor extends BaseEgoArmor {

    public RepentanceBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.ZAYIN;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "repentance";
    }

    @Override
    public String getSetId() {
        return "repentance";
    }

    @Override
    public float getRedResistance() {
        return 0.9f;  // 减伤10%
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;  // 减伤20%
    }

    @Override
    public float getBlackResistance() {
        return 0.9f;  // 减伤10%
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;  // 增伤100%
    }

    @Override
    public boolean onDamaged(Player player, String damageType, float damage) {
        if (damageType != null) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (damageType.equals("red") || damageType.equals("black")) {
                    if (player.getRandom().nextFloat() < 0.05f) {
                        MentalValueUtil.addMentalValue(serverPlayer, 10);
                        player.sendSystemMessage(Component.translatable(
                                "message.lobotocraft.penitence.restored_mental", 10));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            for (int index = 1; index <= 6; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_armor.story." + index,
                        ChatFormatting.GRAY);
            }
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_armor.effect",
                    ChatFormatting.GOLD);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details",
                    ChatFormatting.GRAY);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_armor.resistance.red",
                    ChatFormatting.RED);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_armor.resistance.white",
                    ChatFormatting.WHITE);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_armor.resistance.black",
                    ChatFormatting.DARK_PURPLE);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_armor.resistance.pale",
                    ChatFormatting.AQUA);
        }
    }
}
