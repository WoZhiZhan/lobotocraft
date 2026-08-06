package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.abnormality.EntityHappyTeddy;
import com.wzz.lobotocraft.event.definition.company.CompanyDayAdvanceEvent;
import com.lobotocraft.facility.day.FacilityDayCommittedEvent;
import com.wzz.lobotocraft.init.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Server-authoritative lock for Happy Teddy's execution sequence. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class TeddyExecutionManager {
    static final String ACTIVE = "lobotocraft_teddy_execution_active";
    static final String SOURCE = "lobotocraft_teddy_execution_source";
    static final String DIMENSION = "lobotocraft_teddy_execution_dimension";
    static final String ANCHOR_X = "lobotocraft_teddy_execution_anchor_x";
    static final String ANCHOR_Y = "lobotocraft_teddy_execution_anchor_y";
    static final String ANCHOR_Z = "lobotocraft_teddy_execution_anchor_z";

    private TeddyExecutionManager() {
    }

    public static boolean begin(EntityHappyTeddy teddy, ServerPlayer player) {
        if (!(teddy.level() instanceof ServerLevel level) || player.serverLevel() != level) return false;
        Vec3 anchor = findSafeAnchor(level, teddy, player);
        if (anchor == null) return false;
        finish(player);
        CompoundTag data = player.getPersistentData();
        data.putBoolean(ACTIVE, true);
        data.putUUID(SOURCE, teddy.getUUID());
        data.putString(DIMENSION, level.dimension().location().toString());
        data.putDouble(ANCHOR_X, anchor.x);
        data.putDouble(ANCHOR_Y, anchor.y);
        data.putDouble(ANCHOR_Z, anchor.z);
        enforce(player);
        return true;
    }

    public static boolean tick(EntityHappyTeddy teddy, ServerPlayer player) {
        if (!isLocked(player) || !player.getPersistentData().hasUUID(SOURCE)
                || !teddy.getUUID().equals(player.getPersistentData().getUUID(SOURCE))
                || player.serverLevel() != teddy.level()) {
            finish(player);
            return false;
        }
        enforce(player);
        return true;
    }

    public static void enforce(ServerPlayer player) {
        if (!isLocked(player)) return;
        CompoundTag data = player.getPersistentData();
        player.stopRiding();
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        Vec3 anchor = new Vec3(data.getDouble(ANCHOR_X), data.getDouble(ANCHOR_Y), data.getDouble(ANCHOR_Z));
        if (player.position().distanceToSqr(anchor) > 1.0E-5D) {
            player.teleportTo(anchor.x, anchor.y, anchor.z);
        }
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setShiftKeyDown(true);
        player.fallDistance = 0.0F;
    }

    public static void finish(ServerPlayer player) {
        clearState(player.getPersistentData());
        player.setNoGravity(false);
        player.setShiftKeyDown(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    public static void releaseSource(ServerLevel level, UUID sourceUuid) {
        if (level == null || sourceUuid == null) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            CompoundTag data = player.getPersistentData();
            if (data.hasUUID(SOURCE) && sourceUuid.equals(data.getUUID(SOURCE))) finish(player);
        }
    }

    public static boolean isLocked(ServerPlayer player) {
        return player != null && isLocked(player.getPersistentData());
    }

    static boolean isLocked(CompoundTag data) {
        return data.getBoolean(ACTIVE) && data.hasUUID(SOURCE)
                && data.contains(DIMENSION) && data.contains(ANCHOR_X)
                && data.contains(ANCHOR_Y) && data.contains(ANCHOR_Z);
    }

    static void clearState(CompoundTag data) {
        data.remove(ACTIVE);
        data.remove(SOURCE);
        data.remove(DIMENSION);
        data.remove(ANCHOR_X);
        data.remove(ANCHOR_Y);
        data.remove(ANCHOR_Z);
    }

    private static Vec3 findSafeAnchor(ServerLevel level, EntityHappyTeddy teddy, ServerPlayer player) {
        BlockPos center = teddy.blockPosition();
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };
        Vec3 nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int dy : new int[]{0, 1, -1}) {
            for (int[] offset : offsets) {
                Vec3 result = validateAnchor(level, teddy, player,
                        new Vec3(center.getX() + offset[0] + 0.5D, center.getY() + dy, center.getZ() + offset[1] + 0.5D));
                if (result == null) continue;
                double distance = result.distanceToSqr(player.position());
                if (distance < nearestDistance) {
                    nearest = result;
                    nearestDistance = distance;
                }
            }
        }
        return nearest != null ? nearest : validateAnchor(level, teddy, player, player.position());
    }

    private static Vec3 validateAnchor(ServerLevel level, EntityHappyTeddy teddy,
                                       ServerPlayer player, Vec3 anchor) {
        BlockPos pos = BlockPos.containing(anchor);
        if (!level.getWorldBorder().isWithinBounds(pos)) return null;
        if (level.dimension().equals(ModDimensions.LOBOTO_KEY)
                && !FacilityAccessService.isPositionOpen(level.getServer(), level.dimension().location(), pos)) {
            return null;
        }
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(anchor);
        if (box.intersects(teddy.getBoundingBox().inflate(0.1D)) || !level.noCollision(player, box)) return null;
        if (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return null;
        return anchor;
    }

    private static boolean locked(Entity entity) {
        return entity instanceof ServerPlayer player && isLocked(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemInteract(PlayerInteractEvent.RightClickItem event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(PlayerInteractEvent.EntityInteractSpecific event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isLocked(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isLocked(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (locked(source.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) finish(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) finish(player);
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) finish(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) finish(player);
    }

    @SubscribeEvent
    public static void onDayAdvance(FacilityDayCommittedEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (isLocked(player)) finish(player);
        }
    }
}
