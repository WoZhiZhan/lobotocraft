package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CaptureUnitItem extends Item {
    public static final String ENTITY_UUID = "entity_uuid";
    public static final String ENTITY_CODE = "entity_code";
    public static final String ENTITY_TYPE = "entity_type";
    public static final String ENTITY_NBT = "entity_nbt";
    public CaptureUnitItem() {
        super(new Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level p_41422_, List<Component> list, TooltipFlag p_41424_) {
        super.appendHoverText(stack, p_41422_, list, p_41424_);
        list.add(Component.translatable("tooltip.lobotocraft.capture_unit.description"));
        if (stack.getOrCreateTag().contains(ENTITY_UUID)) {
            list.add(Component.translatable("tooltip.lobotocraft.capture_unit.captured_code",
                    stack.getOrCreateTag().getString(ENTITY_CODE)));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        if (stack.getOrCreateTag().contains(ENTITY_UUID))
            return true;
        return super.isFoil(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity living, InteractionHand hand) {
        if (CoreSuppressionManager.isDeviceRestricted(player)) {
            player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.restricted"), true);
            return InteractionResult.FAIL;
        }
        if (stack.getOrCreateTag().contains(ENTITY_UUID)) {
            player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.occupied"), true);
            return InteractionResult.FAIL;
        }
        if (living instanceof AbstractAbnormality abnormality) {
            if (abnormality.hasEscape()) {
                player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.escaped"), true);
                return InteractionResult.FAIL;
            }
        } else {
            player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.not_abnormality"), true);
            return InteractionResult.PASS;
        }
        return super.interactLivingEntity(stack, player, living, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }
        if (CoreSuppressionManager.isDeviceRestricted(player)) {
            player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.restricted"), true);
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return super.useOn(context);
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(ENTITY_UUID)) {
            return InteractionResult.FAIL;
        }
        if (tag.contains(ENTITY_NBT) && tag.contains(ENTITY_TYPE)) {
            try {
                ResourceLocation entityTypeId = ResourceUtil.createInstanceWithColon(tag.getString(ENTITY_TYPE));
                EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);

                if (entityType != null) {
                    Entity newEntity = entityType.create(serverLevel);
                    if (newEntity instanceof AbstractAbnormality abnormality) {
                        CompoundTag entityNbt = tag.getCompound(ENTITY_NBT);
                        abnormality.load(entityNbt);
                        // Qliphoth counter and source epochs are part of the captured entity state.
                        Vec3 hitPos = context.getClickLocation();
                        abnormality.setPos(hitPos.x(), hitPos.y(), hitPos.z());
                        abnormality.setEscape(false);
                        serverLevel.addFreshEntity(abnormality);
                        tag.remove(ENTITY_UUID);
                        tag.remove(ENTITY_CODE);
                        tag.remove(ENTITY_TYPE);
                        tag.remove(ENTITY_NBT);
                        player.playSound(SoundEvents.BOTTLE_EMPTY, 1.0f, 1.0f);
                        player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.released"), true);
                        return InteractionResult.SUCCESS;
                    }
                }
            } catch (Exception e) {
                player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.corrupt"), true);
                e.printStackTrace();
            }
        }
        player.displayClientMessage(Component.translatable("message.lobotocraft.capture_unit.reset"), true);
        tag.remove(ENTITY_UUID);
        tag.remove(ENTITY_CODE);
        tag.remove(ENTITY_TYPE);
        tag.remove(ENTITY_NBT);
        return InteractionResult.FAIL;
    }
}
