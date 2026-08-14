package com.wzz.lobotocraft.item.ego.end_bird;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class EndBirdBaseArmor extends BaseEgoArmor {

    public EndBirdBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("JusticeLevel", 5);
        map.put("EmployeeLevel", 5);
        map.put("TemperanceLevel", 5);
        map.put("PrudenceLevel", 5);
        map.put("FortitudeLevel", 5);
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
        return "end_bird";
    }

    @Override
    public String getSetId() {
        return "end_bird";
    }

    @Override
    public float getRedResistance() {
        return 0.3f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.3f;
    }

    @Override
    public float getBlackResistance() {
        return 0.3f;
    }

    @Override
    public float getPaleResistance() {
        return 0.5f;
    }

    @Override
    public void onInventoryTick(ItemStack stack, Level level, Player player, int slotIndex, int selectedIndex) {
        super.onInventoryTick(stack, level, player, slotIndex, selectedIndex);
        if (player.tickCount % 100 == 0) {
            if (EgoArmorHelper.isFullEGO(player, getSetId())) {
                boolean thinDuskPunishing = EndBirdWeapon.hasThinDuskSetWithCurio(
                        player, ModItems.PUNISHING_BIRD_CURIO.get());
                for (LivingEntity entity : EntityUtil.findAllEntities(player, 10)) {
                    if (isPassiveTarget(entity)) {
                        if (thinDuskPunishing) {
                            EndBirdWeapon.markPassiveVulnerable(entity, level);
                        }
                        EntityUtil.clearHurtTime(entity, () -> {
                            entity.hurt(DamageHelper.getDamage(player, "black"), 5f);
                            EntityUtil.clearHurtTime(entity, () -> {
                                entity.hurt(DamageHelper.getDamage(player, "blue"), 5f);
                                EntityUtil.clearHurtTime(entity, () -> {
                                    entity.hurt(DamageHelper.getDamage(player, "red"), 5f);
                                    EntityUtil.clearHurtTime(entity, () -> {
                                        entity.hurt(DamageHelper.getDamage(player, "white"), 5f);
                                    });
                                });
                            });
                        });
                    }
                }
            } else {
                if (EgoArmorHelper.isWearingFullSet(player, getSetId())) {
                    for (LivingEntity entity : EntityUtil.findAllEntities(player, 2.5D)) {
                        if (isPassiveTarget(entity)) {
                            entity.hurt(DamageHelper.getDamage(player, "black"), 5f);
                        }
                    }
                }
            }
        }
    }

    private boolean isPassiveTarget(LivingEntity entity) {
        return entity.getClassification(true) == MobCategory.MONSTER
                || (entity instanceof AbstractAbnormality abnormality && abnormality.hasEscape());
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> components, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, components, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            for (int index = 1; index <= 5; index++) {
                EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.detail." + index,
                        ChatFormatting.GOLD);
            }
            return;
        }
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.summary." + index,
                    ChatFormatting.GOLD);
        }
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.story.1",
                ChatFormatting.GRAY);
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.story.2",
                ChatFormatting.GRAY);
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.common.shift_details",
                ChatFormatting.GRAY);
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.resistance.red",
                ChatFormatting.RED);
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.resistance.white",
                ChatFormatting.WHITE);
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.resistance.black",
                ChatFormatting.DARK_PURPLE);
        EgoTooltipUtil.add(components, "tooltip.lobotocraft.end_bird_armor.resistance.pale",
                ChatFormatting.AQUA);
    }
}
