package com.wzz.lobotocraft.item.ego.bigbadwolf;

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

public abstract class BigBadwolfBaseArmor extends BaseEgoArmor {

    public BigBadwolfBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 4);
        map.put("FortitudeLevel", 4);
        return map;
    }

    @Override
    public boolean onDamaged(Player player, String damageType, float damage) {
        if (!player.getPersistentData().getBoolean("isAddBigBadwolf")) {
            BigBadwolfBaseArmor.SpeedBoostTimer timer = new BigBadwolfBaseArmor.SpeedBoostTimer();
            timer.addSkillTimer(player, 0, 7000, 1, true);
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
        return "big_badwolf";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 0.4f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
    }

    @Override
    public float getBlackResistance() {
        return 0.7f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("※这件护甲在玩家受伤时会短暂提高玩家移速。").withStyle(ExtendedColor.ORANGE.toStyle()));
        p_41423_.add(Component.literal("§7这件护甲上有许多因为激烈的战斗而留下的痕迹。"));
        p_41423_.add(Component.literal("§7尽管如此，这些伤口最终也不会起到任何有利的作用，它们只会让你的痛觉变得迟钝，但是无法保护你的身体。"));
    }

    public static class SpeedBoostTimer extends TimerEntry<Player> {
        private static final UUID SPEED_BOOST_UUID = UUID.fromString("c9045b32-4131-494b-81d5-666e0613ad05");

        @Override
        public void onStart(@NotNull Player player) {
            player.getPersistentData().putBoolean("isAddBigBadwolf", true);
            applySpeedBoost(player, true);
        }

        @Override
        public void onEnd(@NotNull Player player) {
            removeSpeedBoost(player);
            player.getPersistentData().remove("isAddBigBadwolf");
        }

        private void applySpeedBoost(Player player, boolean add) {
            AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.removeModifier(SPEED_BOOST_UUID);
                if (add) {
                    AttributeModifier modifier = new AttributeModifier(
                            SPEED_BOOST_UUID,
                            "damage_speed_boost",
                            0.1,
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