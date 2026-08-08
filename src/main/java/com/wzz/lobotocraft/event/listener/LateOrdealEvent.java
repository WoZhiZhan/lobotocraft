package com.wzz.lobotocraft.event.listener;

import com.lobotocraft.facility.data.OrdealColorPool;
import com.lobotocraft.facility.data.OrdealTier;
import com.lobotocraft.facility.OrdealRewardService;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.ordeal.EntityAmberDawn;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenDawn;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenNoon;
import com.wzz.lobotocraft.entity.ordeal.EntityLateOrdeal;
import com.wzz.lobotocraft.entity.ordeal.LateOrdealVariant;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.sendOrdealTitle;

/** Save compatibility for placeholder late-ordeal entities. Natural triggers remain unfinished. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class LateOrdealEvent {
    private static final int CRIMSON_COLOR = 0xC62E2E;
    private static final int GREEN_COLOR = 0x35A65A;
    private static final int VIOLET_COLOR = 0xA14FD0;
    private static final int AMBER_COLOR = 0xD39A2C;

    private LateOrdealEvent() {
    }

    public static boolean triggerDusk(ServerLevel level, int color) {
        return showUnfinished(level, OrdealTier.DUSK, color);
    }

    public static boolean triggerMidnight(ServerLevel level, int color) {
        return showUnfinished(level, OrdealTier.MIDNIGHT, color);
    }

    private static boolean showUnfinished(ServerLevel level, OrdealTier tier, int color) {
        if (level == null) return false;
        String tierName = tier == OrdealTier.DUSK ? "黄昏" : "午夜";
        sendOrdealTitle(level.getServer(), familyName(color) + "的" + tierName,
                "未完工", "该考验尚未实现，不会生成实体。", themeColor(color));
        return true;
    }

    private static EntityLateOrdeal spawnLate(ServerLevel level, LateOrdealVariant variant,
                                               BlockPos anchor, boolean primary, int sequence) {
        RegistryObject<EntityType<EntityLateOrdeal>> registry = switch (variant) {
            case CRIMSON_DUSK -> ModEntities.crimson_dusk;
            case GREEN_DUSK -> ModEntities.green_dusk;
            case AMBER_DUSK -> ModEntities.amber_dusk;
            case GREEN_MIDNIGHT -> ModEntities.green_midnight;
            case VIOLET_MIDNIGHT -> ModEntities.violet_midnight;
            case AMBER_MIDNIGHT -> ModEntities.amber_midnight;
        };
        EntityLateOrdeal entity = registry.get().create(level);
        if (entity == null) return null;
        BlockPos spawnPos = findPlace(level, entity, anchor, 7);
        if (spawnPos == null) return null;

        entity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        entity.setPrimarySpawn(primary);
        if (variant == LateOrdealVariant.VIOLET_MIDNIGHT) entity.setPillarDamage(sequence % 4);
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        entity.setPersistenceRequired();
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static BlockPos findPlace(ServerLevel level, Entity entity, BlockPos center, int range) {
        if (center == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                level.dimension().location(), center)) return null;
        for (int radius = 0; radius <= range; radius++) {
            int attempts = radius == 0 ? 1 : 12;
            for (int attempt = 0; attempt < attempts; attempt++) {
                int dx = radius == 0 ? 0 : level.getRandom().nextInt(radius * 2 + 1) - radius;
                int dz = radius == 0 ? 0 : level.getRandom().nextInt(radius * 2 + 1) - radius;
                BlockPos candidate = EntityUtil.findSafeGroundPositionInCompany(level, center.offset(dx, 0, dz), 2);
                if (candidate == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                        level.dimension().location(), candidate)) continue;
                AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(
                        candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                if (level.noCollision(entity, box)) return candidate.immutable();
            }
        }
        return null;
    }

    public static boolean spawnReinforcement(ServerLevel level, EntityLateOrdeal source, boolean amber) {
        OrdealData data = OrdealData.get(level);
        if (!data.hasActiveLateOrdeal() || source == null || !source.isAlive()) return false;
        BlockPos anchor = EntityUtil.findSafeGroundPositionInCompany(level, source.blockPosition(), 6);
        if (anchor == null) return false;

        Entity child;
        if (amber && source.getVariant() == LateOrdealVariant.AMBER_MIDNIGHT) {
            child = spawnLate(level, LateOrdealVariant.AMBER_DUSK, anchor, false, 0);
        } else if (amber) {
            EntityAmberDawn dawn = ModEntities.amber_dawn.get().create(level);
            if (dawn == null) return false;
            BlockPos pos = findPlace(level, dawn, anchor, 4);
            if (pos == null) return false;
            dawn.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            dawn.setHomePos(anchor);
            dawn.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            dawn.setOrdealSpawn(false);
            dawn.setPersistenceRequired();
            child = level.addFreshEntity(dawn) ? dawn : null;
        } else if (level.getRandom().nextBoolean()) {
            EntityGreenDawn dawn = ModEntities.green_dawn.get().create(level);
            if (dawn == null) return false;
            BlockPos pos = findPlace(level, dawn, anchor, 4);
            if (pos == null) return false;
            dawn.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            dawn.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            dawn.setOrdealSpawn(false);
            dawn.setPersistenceRequired();
            child = level.addFreshEntity(dawn) ? dawn : null;
        } else {
            EntityGreenNoon noon = ModEntities.green_noon.get().create(level);
            if (noon == null) return false;
            BlockPos pos = findPlace(level, noon, anchor, 4);
            if (pos == null) return false;
            noon.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            noon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            noon.setOrdealSpawn(false);
            noon.setPersistenceRequired();
            child = level.addFreshEntity(noon) ? noon : null;
        }
        if (child == null) return false;
        data.trackLateOrdeal(record(child));
        return true;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            onTrackedEntityKilled(level, event.getEntity());
        }
    }

    public static void onTrackedEntityKilled(ServerLevel level, Entity entity) {
        OrdealData data = OrdealData.get(level);
        if (!data.hasActiveLateOrdeal()) return;
        boolean tracked = data.getTrackedLateOrdeals().stream()
                .anyMatch(record -> record.uuid().equals(entity.getUUID()));
        if (!tracked || data.removeTrackedLateOrdeal(entity.getUUID()) > 0) return;

        int tier = data.getLateOrdealTier();
        int color = data.getLateOrdealColor();
        data.finishLateOrdeal();
        OrdealRewardService.complete(level.getServer(),
                tier == 1 ? OrdealTier.DUSK : tier == 2 ? OrdealTier.MIDNIGHT : OrdealTier.NONE);
        showEnd(level.getServer(), tier, color);
    }

    private static OrdealData.LateOrdealRecord record(Entity entity) {
        return new OrdealData.LateOrdealRecord(entity.getUUID(),
                entity.level().dimension().location().toString(),
                entity.chunkPosition().x, entity.chunkPosition().z);
    }

    private static void showEnd(MinecraftServer server, int tierId, int color) {
        String tier = tierId == 1 ? "黄昏" : "午夜";
        sendOrdealTitle(server, familyName(color) + "的" + tier,
                "考验终结", "公司暂时恢复了平静。", themeColor(color));
        playGlobalSound(server, endSound(color));
    }

    private static String familyName(int color) {
        return switch (color) {
            case OrdealColorPool.CRIMSON -> "血红";
            case OrdealColorPool.GREEN -> "绿色";
            case OrdealColorPool.VIOLET -> "紫罗兰";
            default -> "琥珀";
        };
    }

    private static int themeColor(int color) {
        return switch (color) {
            case OrdealColorPool.CRIMSON -> CRIMSON_COLOR;
            case OrdealColorPool.GREEN -> GREEN_COLOR;
            case OrdealColorPool.VIOLET -> VIOLET_COLOR;
            default -> AMBER_COLOR;
        };
    }

    private static SoundEvent endSound(int color) {
        return switch (color) {
            case OrdealColorPool.CRIMSON -> ModSounds.CRIMSON_NOON_END.get();
            case OrdealColorPool.GREEN -> ModSounds.GREEN_NOON_END.get();
            case OrdealColorPool.VIOLET -> ModSounds.VIOLET_NOON_END.get();
            default -> ModSounds.AMBER_DAWN_END.get();
        };
    }

    private static void playGlobalSound(MinecraftServer server, SoundEvent sound) {
        if (server == null || sound == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }
}
