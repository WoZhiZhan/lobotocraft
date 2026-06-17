package com.wzz.lobotocraft.item.base;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class TextSpawnEggItem extends ForgeSpawnEggItem {
    private final String[] texts;

    public TextSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                            int backgroundColor, int highlightColor, Properties props,
                            String text) {
        this(type, backgroundColor, highlightColor, props, new String[]{text});
    }

    public TextSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                            int backgroundColor, int highlightColor, Properties props,
                            String... texts) {
        super(type, backgroundColor, highlightColor, props);
        this.texts = texts;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        EntityType<?> type = getType(stack.getTag());
        boolean abnormality = type.create(level) instanceof AbstractAbnormality;
        if (!abnormality) {
            return super.useOn(context);
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.is(Blocks.SPAWNER)) {
            return super.useOn(context);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
                ? clickedPos
                : clickedPos.relative(context.getClickedFace());
        AbstractAbnormality spawned = AbnormalitySpawnHelper.spawnPersistent(
                (ServerLevel) level, (EntityType) type, spawnPos, MobSpawnType.SPAWN_EGG);
        if (spawned == null) {
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        for (String text : texts) {
            tooltip.add(Component.literal(text));
        }
    }
}
