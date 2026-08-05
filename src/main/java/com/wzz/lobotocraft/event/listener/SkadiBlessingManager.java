package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.init.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Binds Wish Without Light to the exact Skadi instance that granted it. */
public final class SkadiBlessingManager {
    static final String SOURCE_UUID = "lobotocraft_skadi_blessing_source";
    static final String SOURCE_DIMENSION = "lobotocraft_skadi_blessing_dimension";
    static final String SOURCE_POSITION = "lobotocraft_skadi_blessing_position";
    static final String SOURCE_EPOCH = "lobotocraft_skadi_blessing_epoch";

    private SkadiBlessingManager() {
    }

    public static void bind(ServerPlayer player, EntityDarkSkadi source) {
        CompoundTag data = player.getPersistentData();
        data.putUUID(SOURCE_UUID, source.getUUID());
        data.putString(SOURCE_DIMENSION, source.level().dimension().location().toString());
        data.putLong(SOURCE_POSITION, source.blockPosition().asLong());
        data.putUUID(SOURCE_EPOCH, source.getBlessingEpoch());
    }

    public static boolean isValid(ServerPlayer player) {
        return resolveSource(player) != null;
    }

    /** Must run on the server thread. The counter transition completes before this returns true. */
    public static boolean tryConsumeCharge(ServerPlayer player) {
        EntityDarkSkadi source = resolveSource(player);
        if (source == null || !source.tryConsumeBlessingCharge(player)) {
            clear(player);
            return false;
        }
        return true;
    }

    public static void revokeSource(MinecraftServer server, UUID sourceUuid) {
        if (server == null || sourceUuid == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CompoundTag data = player.getPersistentData();
            if (data.hasUUID(SOURCE_UUID) && sourceUuid.equals(data.getUUID(SOURCE_UUID))) {
                clear(player);
            }
        }
    }

    public static void clear(ServerPlayer player) {
        player.removeEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get());
        clearBinding(player.getPersistentData());
    }

    static void clearBinding(CompoundTag data) {
        data.remove(SOURCE_UUID);
        data.remove(SOURCE_DIMENSION);
        data.remove(SOURCE_POSITION);
        data.remove(SOURCE_EPOCH);
    }

    static boolean hasCompleteBinding(CompoundTag data) {
        return data.hasUUID(SOURCE_UUID)
                && ResourceLocation.tryParse(data.getString(SOURCE_DIMENSION)) != null
                && data.contains(SOURCE_POSITION) && data.hasUUID(SOURCE_EPOCH);
    }

    private static EntityDarkSkadi resolveSource(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!player.hasEffect(ModMobEffects.WISH_WITHOUT_LIGHT.get()) || !hasCompleteBinding(data)) {
            clear(player);
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(data.getString(SOURCE_DIMENSION));
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel level = player.server.getLevel(key);
        if (level == null) {
            clear(player);
            return null;
        }

        // Skadi is stationary. Loading its recorded containment chunk distinguishes an unloaded
        // source from a captured or removed source without searching every loaded entity.
        BlockPos sourcePos = BlockPos.of(data.getLong(SOURCE_POSITION));
        level.getChunk(sourcePos);
        if (level.getEntity(data.getUUID(SOURCE_UUID)) instanceof EntityDarkSkadi skadi
                && skadi.isAlive() && !skadi.isRemoved() && skadi.getQliphothCounter() > 0
                && skadi.getBlessingEpoch().equals(data.getUUID(SOURCE_EPOCH))) {
            return skadi;
        }
        clear(player);
        return null;
    }
}
