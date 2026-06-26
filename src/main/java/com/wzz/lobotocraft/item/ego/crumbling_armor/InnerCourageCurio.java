package com.wzz.lobotocraft.item.ego.crumbling_armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class InnerCourageCurio extends BaseEgoCurio {
    public InnerCourageCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "inner_courage";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public boolean rendersOnWearer() {
        return false;
    }

    @Override
    public BodyPartType getBodyPartType() {
        return BodyPartType.BODY;
    }

    @Override
    public void applyTransform(PoseStack poseStack, HumanoidModel<?> model, BodyPartType part) {
        model.body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.15F, 0.42F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.7F, 0.7F, 0.7F);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return List.of(
                new AttributeEntry(UUID.fromString("c0610001-0561-4000-8000-000000000001"),
                        this.getCurrentClassName() + " Move Speed Bonus", Attributes.MOVEMENT_SPEED, 0.10D,
                        AttributeModifier.Operation.MULTIPLY_BASE),
                new AttributeEntry(UUID.fromString("c0610002-0561-4000-8000-000000000002"),
                        this.getCurrentClassName() + " Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.10D,
                        AttributeModifier.Operation.MULTIPLY_BASE)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("内在的勇气：+10移动速度+10攻击速度").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("破裂盔甲赐予的勇气。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("充满勇气的战士时刻准备着冲锋陷阵！勇气愈发强大，一切都成为可能！过度的勇气可能会铸成大错。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("继续进行三次压迫工作后会转化为「匹夫之勇」。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("携带者进行沟通工作时会被处决。").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
