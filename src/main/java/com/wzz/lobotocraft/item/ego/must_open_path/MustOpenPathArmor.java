package com.wzz.lobotocraft.item.ego.must_open_path;

import com.wzz.lobotocraft.client.renderer.item.MustOpenPathArmorItemRenderer;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MustOpenPathArmor extends BaseEgoArmor {
    public MustOpenPathArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type,
                new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> requirements = new LinkedHashMap<>();
        requirements.put("FortitudeLevel", 5);
        requirements.put("PrudenceLevel", 6);
        requirements.put("JusticeLevel", 5);
        requirements.put("EmployeeLevel", 5);
        return requirements;
    }

    @Override public String armorName() { return MustOpenPathWeapon.SET_ID; }
    @Override public String getSetId() { return MustOpenPathWeapon.SET_ID; }
    @Override public RiskLevel riskLevel() { return RiskLevel.ALEPH; }
    @Override public float getRedResistance() { return 1.0F; }
    @Override public float getWhiteResistance() { return 1.0F; }
    @Override public float getBlackResistance() { return 1.0F; }
    @Override public float getPaleResistance() { return 1.0F; }
    @Override public boolean hasAnimatable() { return false; }
    @Override public boolean useSeparateTextures() { return true; }
    @Override public boolean useSeparateModel() { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        EgoTooltipUtil.appendStandardDescription(
                tooltip,
                ClientInputUtil.isShiftPressed(),
                "tooltip.lobotocraft.must_open_path_armor.info",
                new String[]{
                        "tooltip.lobotocraft.must_open_path_armor.story_1",
                        "tooltip.lobotocraft.must_open_path_armor.story_2",
                        "tooltip.lobotocraft.must_open_path_armor.story_3"
                },
                new String[]{
                        "tooltip.lobotocraft.must_open_path_armor.red",
                        "tooltip.lobotocraft.must_open_path_armor.white",
                        "tooltip.lobotocraft.must_open_path_armor.black",
                        "tooltip.lobotocraft.must_open_path_armor.pale"
                },
                new String[0],
                new ChatFormatting[]{
                        ChatFormatting.RED,
                        ChatFormatting.WHITE,
                        ChatFormatting.DARK_PURPLE,
                        ChatFormatting.AQUA
                }
        );
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> armorRenderer;
            private MustOpenPathArmorItemRenderer itemRenderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(
                    LivingEntity livingEntity, ItemStack itemStack,
                    EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (armorRenderer == null) {
                    armorRenderer = new com.wzz.lobotocraft.client.renderer.item.BaseEgoArmorRenderer(
                            MustOpenPathWeapon.SET_ID);
                }
                armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return armorRenderer;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) itemRenderer = new MustOpenPathArmorItemRenderer();
                return itemRenderer;
            }
        });
    }
}
