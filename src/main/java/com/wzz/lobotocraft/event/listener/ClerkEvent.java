package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.block.entity.TombstoneBlockEntity;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntityBlueStar;
import com.wzz.lobotocraft.entity.abnormality.EntityCleaner;
import com.wzz.lobotocraft.entity.abnormality.EntityIronMaiden;
import com.wzz.lobotocraft.entity.abnormality.EntityWorkerBee;
import com.wzz.lobotocraft.event.definition.company.CompanyDayAdvanceEvent;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.ClerkTombstoneData;
import com.wzz.lobotocraft.world.data.GenerationBiggerData;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class ClerkEvent {
    private static final int CLERKS_PER_REACTOR = 8;
    private static final int PLAYER_DEATH_CLERK_RESPAWN_COUNT = 3;

    @SubscribeEvent
    public static void onDayAdvance(CompanyDayAdvanceEvent event) {
        if (event.getPlayer().getServer() == null) return;
        ServerLevel level = event.getPlayer().getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return;

        resetDayClerks(level);
    }

    public static ClerkResetResult resetDayClerks(ServerLevel level) {
        int clearedTombstones = clearTombstones(level);
        return resetClerks(level, clearedTombstones);
    }

    public static void respawnClerksAfterPlayerDeath(ServerLevel level) {
        ArrayList<BlockPos> reactorPositions = new ArrayList<>(collectReactorPositions(level));
        if (reactorPositions.isEmpty()) return;

        BlockPos reactorPos = reactorPositions.get(level.random.nextInt(reactorPositions.size()));
        for (int i = 0; i < PLAYER_DEATH_CLERK_RESPAWN_COUNT; i++) {
            spawnClerk(level, reactorPos);
        }
    }

    @SubscribeEvent
    public static void onClerkDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityClerk clerk)) return;
        if (clerk.level().isClientSide || !clerk.shouldCreateTombstone()) return;
        if (isSpecialDeath(event.getSource())) return;
        if (!(clerk.level() instanceof ServerLevel level)) return;

        placeTombstone(level, clerk.blockPosition());
    }

    private static int clearTombstones(ServerLevel level) {
        Set<BlockPos> tombstonePositions = new HashSet<>();
        ClerkTombstoneData tombstoneData = ClerkTombstoneData.get(level);
        tombstonePositions.addAll(tombstoneData.getPositions());

        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level)) {
            if (blockEntity instanceof TombstoneBlockEntity) {
                tombstonePositions.add(blockEntity.getBlockPos().immutable());
            }
        }

        GenerationBiggerData.Entry company = Structures.LOBOTO.data(level);
        if (company.generated && company.currentRadius > 0) {
            collectTombstonesInArea(level,
                    company.centerX - company.currentRadius,
                    company.centerZ - company.currentRadius,
                    company.centerX + company.currentRadius,
                    company.centerZ + company.currentRadius,
                    tombstonePositions);
        }

        int cleared = 0;
        for (BlockPos pos : tombstonePositions) {
            if (level.getBlockState(pos).is(ModBlocks.TOMBSTONE.get())) {
                if (level.removeBlock(pos, false)) {
                    cleared++;
                }
            }
        }
        tombstoneData.clearAll();
        return cleared;
    }

    private static void collectTombstonesInArea(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                               Set<BlockPos> tombstonePositions) {
        int minChunkX = Math.floorDiv(minX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
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

    private static ClerkResetResult resetClerks(ServerLevel level, int clearedTombstones) {
        Set<BlockPos> reactorPositions = collectReactorPositions(level);
        Map<BlockPos, List<EntityClerk>> clerksByReactor = new HashMap<>();
        List<EntityClerk> clerksToRemove = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityClerk clerk) || !clerk.isAlive()) {
                continue;
            }

            BlockPos reactorPos = clerk.getReactorPos();
            if (reactorPos == null || !reactorPositions.contains(reactorPos)) {
                clerksToRemove.add(clerk);
                continue;
            }

            clerksByReactor.computeIfAbsent(reactorPos, ignored -> new ArrayList<>()).add(clerk);
        }

        int resetClerks = 0;
        int spawnedClerks = 0;

        for (BlockPos reactorPos : reactorPositions) {
            List<EntityClerk> clerks = clerksByReactor.getOrDefault(reactorPos, Collections.emptyList());
            int retained = Math.min(clerks.size(), CLERKS_PER_REACTOR);

            for (int i = 0; i < retained; i++) {
                resetClerkState(clerks.get(i));
                resetClerks++;
            }

            for (int i = CLERKS_PER_REACTOR; i < clerks.size(); i++) {
                clerksToRemove.add(clerks.get(i));
            }

            for (int i = retained; i < CLERKS_PER_REACTOR; i++) {
                if (spawnClerk(level, reactorPos)) {
                    spawnedClerks++;
                }
            }
        }

        for (EntityClerk clerk : clerksToRemove) {
            clerk.discard();
        }

        return new ClerkResetResult(clearedTombstones, resetClerks, spawnedClerks, clerksToRemove.size());
    }

    private static Set<BlockPos> collectReactorPositions(ServerLevel level) {
        Set<BlockPos> reactorPositions = new HashSet<>();
        GenerationBiggerData.Entry company = Structures.LOBOTO.data(level);
        if (company.generated && company.currentRadius > 0) {
            collectReactorsInArea(level,
                    company.centerX - company.currentRadius,
                    company.centerZ - company.currentRadius,
                    company.centerX + company.currentRadius,
                    company.centerZ + company.currentRadius,
                    reactorPositions);
            return reactorPositions;
        }

        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level)) {
            if (blockEntity instanceof RegenerationReactorBlockEntity) {
                reactorPositions.add(blockEntity.getBlockPos().immutable());
            }
        }
        return reactorPositions;
    }

    private static void collectReactorsInArea(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                             Set<BlockPos> reactorPositions) {
        int minChunkX = Math.floorDiv(minX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                collectReactorsInChunk(chunk, reactorPositions);
            }
        }
    }

    private static void collectReactorsInChunk(LevelChunk chunk, Set<BlockPos> reactorPositions) {
        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (blockEntity instanceof RegenerationReactorBlockEntity) {
                reactorPositions.add(blockEntity.getBlockPos().immutable());
            }
        }
    }

    private static void resetClerkState(EntityClerk clerk) {
        clerk.setHealth(clerk.getMaxHealth());
        clerk.removeAllEffects();
        clerk.getPersistentData().remove(EntityClerk.NO_TOMBSTONE_TAG);
        clerk.getPersistentData().remove(EntityClerk.COMMAND_KILL_TAG);
    }

    private static boolean spawnClerk(ServerLevel level, BlockPos reactorPos) {
        BlockPos spawnPos = EntityUtil.findReactorSpawnPositionInCompany(level, reactorPos, 8);
        EntityClerk clerk = ModEntities.clerk.get().create(level);
        if (clerk == null) return false;

        clerk.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        clerk.setReactorPos(reactorPos);
        clerk.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(clerk);
        return true;
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
                if (level.setBlock(cursor, ModBlocks.TOMBSTONE.get().defaultBlockState(), 3)) {
                    ClerkTombstoneData.get(level).add(cursor);
                }
                return;
            }
            cursor.move(0, 1, 0);
        }
    }

    public record ClerkResetResult(int clearedTombstones, int resetClerks, int spawnedClerks, int removedClerks) {
    }
}
