package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ConveyerItem extends Item {
    static final int TELEPORT_COOLDOWN_TICKS = 40;
    private static final String RETURN_X = "lobotocraft:LotoOldX";
    private static final String RETURN_Y = "lobotocraft:LotoOldY";
    private static final String RETURN_Z = "lobotocraft:LotoOldZ";
    private static final String RETURN_LEVEL = "lobotocraft:LotoOldLevelName";
    private static final String COMPANY_SUFFIX = "_loboto";
    private static final Vec3 INITIAL_COMPANY_POSITION = new Vec3(0.5D, 4.0D, 0.5D);
    private static final Vec3 GENERATED_COMPANY_POSITION = new Vec3(195.5D, 273.0D, 29.5D);

    public ConveyerItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.translatable("item.conveyer.text.1"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (CoreSuppressionManager.isDeviceRestricted(player)) {
            player.sendSystemMessage(Component.literal("§c核心抑制期间无法使用传送装置。"));
            return InteractionResultHolder.fail(stack);
        }
        if (serverPlayer.isChangingDimension() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        CompoundTag nbt = player.getPersistentData();
        ResourceKey<Level> currentDim = serverPlayer.serverLevel().dimension();
        if (!currentDim.equals(ModDimensions.LOBOTO_KEY)) {
            return enterCompany(serverPlayer, stack, nbt, currentDim);
        }
        return leaveCompany(serverPlayer, stack, nbt);
    }

    private InteractionResultHolder<ItemStack> enterCompany(ServerPlayer player, ItemStack stack,
                                                             CompoundTag data,
                                                             ResourceKey<Level> sourceDimension) {
        MinecraftServer server = player.getServer();
        ServerLevel company = server == null ? null : server.getLevel(ModDimensions.LOBOTO_KEY);
        if (company == null) return InteractionResultHolder.fail(stack);

        Vec3 sourcePosition = player.position();
        Vec3 destination = companyPosition(data, company);
        player.getCooldowns().addCooldown(this, TELEPORT_COOLDOWN_TICKS);
        if (!EntityUtil.teleportPlayer(player, ModDimensions.LOBOTO_KEY, destination)) {
            player.getCooldowns().removeCooldown(this);
            return InteractionResultHolder.fail(stack);
        }

        putPosition(data, RETURN_X, RETURN_Y, RETURN_Z, sourcePosition);
        data.putString(RETURN_LEVEL, sourceDimension.location().toString());
        return InteractionResultHolder.success(stack);
    }

    private InteractionResultHolder<ItemStack> leaveCompany(ServerPlayer player, ItemStack stack,
                                                             CompoundTag data) {
        ReturnTarget target = returnTarget(player, data);
        Vec3 companyPosition = player.position();
        boolean rememberCompanyPosition = EntityUtil.isInCompany(
                player.serverLevel(), BlockPos.containing(companyPosition));

        player.getCooldowns().addCooldown(this, TELEPORT_COOLDOWN_TICKS);
        if (!EntityUtil.teleportPlayer(player, target.dimension, target.position)) {
            player.getCooldowns().removeCooldown(this);
            return InteractionResultHolder.fail(stack);
        }

        if (rememberCompanyPosition) {
            putPosition(data, RETURN_X + COMPANY_SUFFIX, RETURN_Y + COMPANY_SUFFIX,
                    RETURN_Z + COMPANY_SUFFIX, companyPosition);
        }
        return InteractionResultHolder.success(stack);
    }

    private static Vec3 companyPosition(CompoundTag data, ServerLevel company) {
        boolean hasSavedPosition = hasPosition(data, RETURN_X + COMPANY_SUFFIX,
                RETURN_Y + COMPANY_SUFFIX, RETURN_Z + COMPANY_SUFFIX);
        Vec3 fallback = Structures.LOBOTO.isGenerated(company)
                ? GENERATED_COMPANY_POSITION : INITIAL_COMPANY_POSITION;
        if (!hasSavedPosition) return fallback;

        Vec3 saved = getPosition(data, RETURN_X + COMPANY_SUFFIX,
                RETURN_Y + COMPANY_SUFFIX, RETURN_Z + COMPANY_SUFFIX);
        if (!isFinite(saved) || company.isOutsideBuildHeight(BlockPos.containing(saved))) return fallback;
        if (Structures.LOBOTO.isGenerated(company)
                && !EntityUtil.isInCompany(company, BlockPos.containing(saved))) return fallback;
        return saved;
    }

    private static ReturnTarget returnTarget(ServerPlayer player, CompoundTag data) {
        MinecraftServer server = player.getServer();
        ResourceLocation location = ResourceLocation.tryParse(data.getString(RETURN_LEVEL));
        ResourceKey<Level> dimension = location == null
                ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, location);
        ServerLevel target = server == null ? null : server.getLevel(dimension);
        if (target == null || dimension.equals(ModDimensions.LOBOTO_KEY)) {
            dimension = Level.OVERWORLD;
            target = server == null ? null : server.overworld();
        }

        if (target != null && hasPosition(data, RETURN_X, RETURN_Y, RETURN_Z)) {
            Vec3 saved = getPosition(data, RETURN_X, RETURN_Y, RETURN_Z);
            if (isFinite(saved) && !target.isOutsideBuildHeight(BlockPos.containing(saved))) {
                return new ReturnTarget(dimension, saved);
            }
        }

        BlockPos spawn = target == null ? BlockPos.ZERO : target.getSharedSpawnPos();
        return new ReturnTarget(dimension, Vec3.atBottomCenterOf(spawn));
    }

    private static boolean hasPosition(CompoundTag data, String x, String y, String z) {
        return data.contains(x) && data.contains(y) && data.contains(z);
    }

    private static Vec3 getPosition(CompoundTag data, String x, String y, String z) {
        return new Vec3(data.getDouble(x), data.getDouble(y), data.getDouble(z));
    }

    private static void putPosition(CompoundTag data, String x, String y, String z, Vec3 position) {
        data.putDouble(x, position.x);
        data.putDouble(y, position.y);
        data.putDouble(z, position.z);
    }

    private static boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private record ReturnTarget(ResourceKey<Level> dimension, Vec3 position) {
    }
}
