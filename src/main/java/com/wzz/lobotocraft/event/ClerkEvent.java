package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.block.entity.TombstoneBlockEntity;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntityBlueStar;
import com.wzz.lobotocraft.entity.abnormality.EntityCleaner;
import com.wzz.lobotocraft.entity.abnormality.EntityIronMaiden;
import com.wzz.lobotocraft.entity.abnormality.EntityWorkerBee;
import com.wzz.lobotocraft.event.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.GenerationBiggerData;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class ClerkEvent {
    private static final int CLERKS_PER_REACTOR = 5;
    private static final int REACTOR_SEARCH_RADIUS = 64;

    @SubscribeEvent
    public static void onDayAdvance(CompanyDayAdvanceEvent event) {
        if (event.getPlayer().getServer() == null) return;
        ServerLevel level = event.getPlayer().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return;

        clearTombstones(level);
        replenishClerks(level);
    }

    @SubscribeEvent
    public static void onClerkDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityClerk clerk)) return;
        if (clerk.level().isClientSide || !clerk.shouldCreateTombstone()) return;
        if (isSpecialDeath(event.getSource())) return;
        if (!(clerk.level() instanceof ServerLevel level)) return;

        placeTombstone(level, clerk.blockPosition());
    }

    private static void clearTombstones(ServerLevel level) {
        Set<BlockPos> tombstonePositions = new HashSet<>();
        GenerationBiggerData.Entry company = Structures.LOBOTO.data(level);
        if (company.generated && company.currentRadius > 0) {
            collectLoadedTombstonesInArea(level,
                    company.centerX - company.currentRadius,
                    company.centerZ - company.currentRadius,
                    company.centerX + company.currentRadius,
                    company.centerZ + company.currentRadius,
                    tombstonePositions);
        } else {
            for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level)) {
                if (blockEntity instanceof RegenerationReactorBlockEntity) {
                    collectLoadedTombstonesAround(level, blockEntity.getBlockPos(), tombstonePositions);
                }
            }
        }

        for (BlockPos pos : tombstonePositions) {
            if (level.getBlockState(pos).is(ModBlocks.TOMBSTONE.get())) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static void collectLoadedTombstonesAround(ServerLevel level, BlockPos center, Set<BlockPos> tombstonePositions) {
        collectLoadedTombstonesInArea(level,
                center.getX() - REACTOR_SEARCH_RADIUS,
                center.getZ() - REACTOR_SEARCH_RADIUS,
                center.getX() + REACTOR_SEARCH_RADIUS,
                center.getZ() + REACTOR_SEARCH_RADIUS,
                tombstonePositions);
    }

    private static void collectLoadedTombstonesInArea(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                                     Set<BlockPos> tombstonePositions) {
        int minChunkX = Math.floorDiv(minX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                collectTombstonesInChunk(chunk, tombstonePositions);
            }
        }
    }

    private static void collectTombstonesInChunk(LevelChunk chunk, Set<BlockPos> tombstonePositions) {
        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (blockEntity instanceof TombstoneBlockEntity) {
                tombstonePositions.add(blockEntity.getBlockPos().immutable());
            }
        }
    }

    private static void replenishClerks(ServerLevel level) {
        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level)) {
            if (!(blockEntity instanceof RegenerationReactorBlockEntity)) continue;

            BlockPos reactorPos = blockEntity.getBlockPos();
            int existing = countAssignedClerks(level, reactorPos);
            for (int i = existing; i < CLERKS_PER_REACTOR; i++) {
                spawnClerk(level, reactorPos);
            }
        }
    }

    private static int countAssignedClerks(ServerLevel level, BlockPos reactorPos) {
        AABB searchBox = new AABB(reactorPos).inflate(REACTOR_SEARCH_RADIUS);
        List<EntityClerk> clerks = level.getEntitiesOfClass(EntityClerk.class, searchBox, clerk ->
                clerk.isAlive() && reactorPos.equals(clerk.getReactorPos()));
        return clerks.size();
    }

    private static void spawnClerk(ServerLevel level, BlockPos reactorPos) {
        BlockPos spawnPos = EntityUtil.findReactorSpawnPositionInCompany(level, reactorPos, 8);
        EntityClerk clerk = ModEntities.clerk.get().create(level);
        if (clerk == null) return;

        clerk.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        clerk.setReactorPos(reactorPos);
        clerk.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(clerk);
    }

    private static boolean isSpecialDeath(DamageSource source) {
        return source.getEntity() instanceof EntityIronMaiden
                || source.getEntity() instanceof EntityBlueStar
                || source.getEntity() instanceof EntityWorkerBee
                || source.getEntity() instanceof EntityCleaner;
    }

    private static void placeTombstone(ServerLevel level, BlockPos deathPos) {
        BlockPos.MutableBlockPos cursor = deathPos.mutable();
        for (int i = 0; i < 4; i++) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || state.canBeReplaced()) {
                level.setBlock(cursor, ModBlocks.TOMBSTONE.get().defaultBlockState(), 3);
                return;
            }
            cursor.move(0, 1, 0);
        }
    }
}
