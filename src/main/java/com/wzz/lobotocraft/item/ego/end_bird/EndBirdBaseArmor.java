package com.wzz.lobotocraft.item.ego.end_bird;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
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
        super(ModArmorMaterial.REPENTANCE, type, new Properties().stacksTo(1).fireResistant());
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
                for (LivingEntity entity : EntityUtil.findAllEntities(player, 10)) {
                    if (entity.getClassification(true) == MobCategory.MONSTER) {
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
                        if (entity.getClassification(true) == MobCategory.MONSTER) {
                            entity.hurt(DamageHelper.getDamage(player, "black"), 5f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> components, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, components, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            components.add(Component.literal("§6※只有全属性满级的员工才可以穿戴这件装备。"));
            components.add(Component.literal("§6※穿戴者在同时持有 E.G.O 武器“薄暝”时，穿戴者的攻击伤害会额外提高（血量降低1%则提高2%），最高可使攻击伤害提升100%。当玩家佩戴E.G.O饰品“破晓”时无论穿戴者持有的是什么 E.G.O 武器，这条特效均会起效。"));
            components.add(Component.literal("§6※穿戴者每 5 秒会对房间内所有的敌对目标造成 5 点无来源侵蚀伤害，不会伤害到非敌对目标。"));
            components.add(Component.literal("§6※当穿戴者持有全套“终末鸟”的 E.G.O 装备(护甲、饰品)时，穿戴者将每5秒对20x20范围内所有敌对目标（不包括中立生物）造成来源为玩家的红色，白色，黑色，蓝色伤害各 5 点。"));
            components.add(Component.literal("§6※如果有员工装备了这件 E.G.O 护甲，则终末鸟就不会出现。"));
            return;
        }
        components.add(Component.literal("§6※持有“薄暝”时，穿戴者的生命值越低，伤害就越高。"));
        components.add(Component.literal("§6※每 5 秒对同一区域内的目标造成侵蚀伤害。穿戴整套“终末鸟”的 E.G.O 装备后(武器、护甲、饰品)，每 5 秒对同一区域内的目标同时造成物理，精神，侵蚀和灵魂伤害。"));
        components.add(Component.literal("§6※如果有员工穿戴这件 E.G.O 装备，“终末鸟”就不会出现。"));
        components.add(Component.literal("§7为了击退黑森林里的可怕“怪物”，三只鸟齐心协力，合为一体。"));
        components.add(Component.literal("§7它能避免很多无辜的人遇害，但在那之前，你必须做好万无一失的准备，去踏入那片黑暗而又绝望的森林。"));
        components.add(Component.literal("§7按住<Shift>查看详情"));
    }
}