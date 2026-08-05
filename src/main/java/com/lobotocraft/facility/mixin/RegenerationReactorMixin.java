package com.lobotocraft.facility.mixin;

import com.lobotocraft.facility.network.FacilityNetwork;
import com.lobotocraft.facility.network.ReactorStatusPacket;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.init.ModDimensions;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RegenerationReactorBlockEntity.class, remap = false)
public abstract class RegenerationReactorMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void facility$replaceReactorMessage(Level level, BlockPos pos, CallbackInfo callback) {
        if (level.dimension() != ModDimensions.LOBOTO_KEY) return;
        float progress = ((RegenerationReactorBlockEntity) (Object) this).getHealProgress();
        facility$sendState(level, pos, new ReactorStatusPacket(pos.immutable(), progress, false));
        callback.cancel();
    }

    @Inject(method = "sendFailureMessage", at = @At("HEAD"), cancellable = true)
    private void facility$replaceFailureMessage(Level level, BlockPos pos, CallbackInfo callback) {
        if (level.dimension() != ModDimensions.LOBOTO_KEY) return;
        facility$sendState(level, pos, new ReactorStatusPacket(pos.immutable(), 0.0F, true));
        callback.cancel();
    }

    private static void facility$sendState(Level level, BlockPos pos, ReactorStatusPacket packet) {
        List<Player> players = level.getEntitiesOfClass(Player.class, facility$effectRange(level, pos));
        for (Player player : players) {
            if (!player.isDeadOrDying() && player instanceof ServerPlayer serverPlayer) {
                FacilityNetwork.send(serverPlayer, packet);
            }
        }
    }

    private static AABB facility$effectRange(Level level, BlockPos pos) {
        boolean aboveBlocked = facility$isBlocked(level, pos.above());
        boolean belowBlocked = facility$isBlocked(level, pos.below());
        double minX = pos.getX() + 0.5D - 16.0D;
        double maxX = pos.getX() + 0.5D + 16.0D;
        double minZ = pos.getZ() + 0.5D - 16.0D;
        double maxZ = pos.getZ() + 0.5D + 16.0D;
        if (aboveBlocked && !belowBlocked) {
            return new AABB(minX, pos.getY() - 16.0D, minZ, maxX, pos.getY() + 1.0D, maxZ);
        }
        if (belowBlocked && !aboveBlocked) {
            return new AABB(minX, pos.getY(), minZ, maxX, pos.getY() + 16.0D, maxZ);
        }
        return new AABB(pos).inflate(16.0D);
    }

    private static boolean facility$isBlocked(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape((BlockGetter) level, pos).isEmpty();
    }
}
