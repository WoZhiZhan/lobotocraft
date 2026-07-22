package com.wzz.lobotocraft.item.ego.repentance;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
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
                        player.sendSystemMessage(Component.literal("§a忏悔：恢复了10点精神值"));
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
            p_41423_.add(Component.literal("§7枪炮和刀刃无法对异想体造成伤害。"));
            p_41423_.add(Component.literal("§7因此我们试着将从异想体中提取的核心塑造成武器和护甲。"));
            p_41423_.add(Component.literal("§7但效果会根据使用者的不同而有或大或小的差异。"));
            p_41423_.add(Component.literal("§7荆棘之冠会保护人们的灵魂。"));
            p_41423_.add(Component.literal("§7荆棘带来的刺痛是短暂的，但它时刻警示着自己。"));
            p_41423_.add(Component.literal("§7然而，对于那些毫无负罪感的人而言，他们不会从这件护甲中获得丝毫的益处。"));
        } else {
            p_41423_.add(Component.literal("§6当穿戴者受到物理或侵蚀伤害时，有5%的概率恢复10点精神值。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
            p_41423_.add(Component.literal("§c红色伤害：0.9"));
            p_41423_.add(Component.literal("§f白色伤害：0.8"));
            p_41423_.add(Component.literal("§5黑色伤害：0.9"));
            p_41423_.add(Component.literal("§b蓝色伤害：2.0"));
        }
    }
}
