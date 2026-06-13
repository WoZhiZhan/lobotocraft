package com.wzz.lobotocraft.item;

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
        list.add(Component.literal("§7添加了抑制器能量的捕捉单元，可以收容一只未出逃的异想体携带在身上。"));
        if (stack.getOrCreateTag().contains(ENTITY_UUID)) {
            list.add(Component.literal("§7当前已捕获的异想体编号：" + stack.getOrCreateTag().getString(ENTITY_CODE)));
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
        if (stack.getOrCreateTag().contains(ENTITY_UUID)) {
            player.displayClientMessage(Component.literal("§c捕获失败，当前已有捕获的异想体"), true);
            return InteractionResult.FAIL;
        }
        if (living instanceof AbstractAbnormality abnormality) {
            if (abnormality.hasEscape()) {
                player.displayClientMessage(Component.literal("§c捕获失败！"), true);
                return InteractionResult.FAIL;
            }
        } else {
            player.displayClientMessage(Component.literal("§c目标实体不是异想体，无法捕获！"), true);
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
                        Vec3 hitPos = context.getClickLocation();
                        abnormality.setPos(hitPos.x(), hitPos.y(), hitPos.z());
                        abnormality.setEscape(false);
                        abnormality.qliphothCounter = abnormality.maxQliphothCounter;
                        serverLevel.addFreshEntity(abnormality);
                        tag.remove(ENTITY_UUID);
                        tag.remove(ENTITY_CODE);
                        tag.remove(ENTITY_TYPE);
                        tag.remove(ENTITY_NBT);
                        player.playSound(SoundEvents.BOTTLE_EMPTY, 1.0f, 1.0f);
                        player.displayClientMessage(Component.literal("§a已释放异想体"), true);
                        return InteractionResult.SUCCESS;
                    }
                }
            } catch (Exception e) {
                player.displayClientMessage(Component.literal("§c释放失败：实体数据损坏"), true);
                e.printStackTrace();
            }
        }
        player.displayClientMessage(Component.literal("§c错误：异想体数据已失效，捕捉单元已重置"), true);
        tag.remove(ENTITY_UUID);
        tag.remove(ENTITY_CODE);
        tag.remove(ENTITY_TYPE);
        tag.remove(ENTITY_NBT);
        return InteractionResult.FAIL;
    }
}