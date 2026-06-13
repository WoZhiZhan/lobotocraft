package com.wzz.lobotocraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import java.util.function.Function;

public class LobotoTeleporter implements ITeleporter {
    private final BlockPos pos;

    public LobotoTeleporter(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
        return new PortalInfo(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Vec3.ZERO, entity.getYRot(), entity.getXRot());
    }
}
