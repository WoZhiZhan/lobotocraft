package com.wzz.lobotocraft.item.base;

import com.wzz.lobotocraft.entity.abnormality.EntityLadyFacingTheWall;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.block.EscapeBlock;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

        AbstractAbnormality spawned;
        if (clickedState.getBlock() instanceof EscapeBlock) {
            spawned = AbnormalitySpawnHelper.spawnPersistentFromAnchor(
                    (ServerLevel) level, (EntityType) type, clickedPos, MobSpawnType.SPAWN_EGG);
        } else {
            BlockPos spawnSearchPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
                    ? clickedPos
                    : clickedPos.relative(context.getClickedFace()).below();
            spawned = AbnormalitySpawnHelper.spawnPersistent(
                    (ServerLevel) level, (EntityType) type, spawnSearchPos, MobSpawnType.SPAWN_EGG,
                    AbnormalitySpawnHelper.SpawnPlacementPolicy.DEBUG_BYPASS);
        }
        if (spawned == null) {
            return InteractionResult.FAIL;
        }
        if (spawned instanceof EntityLadyFacingTheWall) {
            alignLadyFacingWall(spawned, context.getClickedFace());
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        EntityType<?> type = getType(stack.getTag());
        if (!isAbnormalityType(type, level)) return super.use(level, player, hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK
                || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof LiquidBlock)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);

        AbstractAbnormality spawned = AbnormalitySpawnHelper.spawnPersistent(
                (ServerLevel) level, (EntityType) type, hit.getBlockPos(), MobSpawnType.SPAWN_EGG,
                AbnormalitySpawnHelper.SpawnPlacementPolicy.DEBUG_BYPASS);
        if (spawned == null) return InteractionResultHolder.fail(stack);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE,
                spawned.position());
        return InteractionResultHolder.consume(stack);
    }

    @Nullable
    @Override
    protected DispenseItemBehavior createDispenseBehavior() {
        DispenseItemBehavior fallback = super.createDispenseBehavior();
        return (source, stack) -> dispense(source, stack, fallback);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ItemStack dispense(BlockSource source, ItemStack stack, DispenseItemBehavior fallback) {
        EntityType<?> type = getType(stack.getTag());
        if (!isAbnormalityType(type, source.getLevel())) {
            return fallback == null ? stack : fallback.dispense(source, stack);
        }
        Direction direction = source.getBlockState().getValue(DispenserBlock.FACING);
        BlockPos searchPos = source.getPos().relative(direction);
        AbstractAbnormality spawned = AbnormalitySpawnHelper.spawnPersistent(
                source.getLevel(), (EntityType) type, searchPos, MobSpawnType.DISPENSER,
                AbnormalitySpawnHelper.SpawnPlacementPolicy.DEBUG_BYPASS);
        if (spawned != null) {
            stack.shrink(1);
            source.getLevel().gameEvent(spawned,
                    net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, spawned.position());
        }
        return stack;
    }

    private static boolean isAbnormalityType(EntityType<?> type, Level level) {
        return type != null && type.create(level) instanceof AbstractAbnormality;
    }

    private static void alignLadyFacingWall(AbstractAbnormality spawned, Direction clickedFace) {
        if (!clickedFace.getAxis().isHorizontal()) {
            return;
        }
        float yaw = clickedFace.getOpposite().toYRot();
        spawned.setYRot(yaw);
        spawned.setYBodyRot(yaw);
        spawned.setYHeadRot(yaw);
        spawned.yRotO = yaw;
        spawned.yBodyRotO = yaw;
        spawned.yHeadRotO = yaw;
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
