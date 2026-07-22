package com.wzz.lobotocraft.item.ego.redhat_mercenary;

import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class RedhatMercenaryBaseArmor extends BaseEgoArmor {

    public RedhatMercenaryBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 4);
        map.put("FortitudeLevel", 3);
        map.put("JusticeLevel", 3);
        return map;
    }

    @Override
    public boolean onDamaged(Player player,@Nullable String damageType, float damage) {
        if (!player.getPersistentData().getBoolean("isAddRedHat") && !player.level.isClientSide) {
            SpeedBoostTimer timer = new SpeedBoostTimer();
            timer.addSkillTimer(player, 0, 5000, 1, true);
        }
        return super.onDamaged(player, damageType, damage);
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.WAW;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "redhat_mercenary";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 0.6f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.6f;
    }

    @Override
    public float getBlackResistance() {
        return 0.6f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("※穿戴者受到伤害后，移动速度增加30%，持续5秒不可叠加，受到伤害后刷新持续时间。").withStyle(ExtendedColor.PINK.toStyle()));
        p_41423_.add(Component.literal("§7一位穿戴着血红披风的佣兵唯一热衷的事就是撕烂那匹恶狼！"));
        p_41423_.add(Component.literal("§7似乎只有黑暗才会等待那些从破灭中幸存的人们。"));
        p_41423_.add(Component.literal("§7有时候，抛弃旧日的仇恨并不是一件坏事，就如同放下你肩膀上的重担一样，令人更加轻松。"));
        p_41423_.add(Component.literal("§c红色伤害：0.6"));
        p_41423_.add(Component.literal("§f白色伤害：0.6"));
        p_41423_.add(Component.literal("§5黑色伤害：0.6"));
        p_41423_.add(Component.literal("§b蓝色伤害：1.5"));
    }

    public static class SpeedBoostTimer extends TimerEntry<Player> {
        private static final UUID SPEED_BOOST_UUID = UUID.fromString("a42f8dfe-71ea-4097-9cbf-6978d55eb6ac");

        @Override
        public void onStart(@NotNull Player player) {
            player.getPersistentData().putBoolean("isAddRedHat", true);
            applySpeedBoost(player, true);
        }

        @Override
        public void onEnd(@NotNull Player player) {
            removeSpeedBoost(player);
            player.getPersistentData().remove("isAddRedHat");
        }

        private void applySpeedBoost(Player player, boolean add) {
            AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(SPEED_BOOST_UUID);
                if (add) {
                    AttributeModifier modifier = new AttributeModifier(
                            SPEED_BOOST_UUID,
                            "damage_speed_boost",
                            0.3,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    attribute.addTransientModifier(modifier);
                }
            }
        }

        private void removeSpeedBoost(Player player) {
            AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(SPEED_BOOST_UUID);
            }
        }
    }
}