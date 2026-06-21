package com.wzz.lobotocraft.world.structure;

import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 流水线式高速结构放置器（边放边收尾，恒定低内存）。
 * 设计目标：放置任意大小结构都不 OOM、不长时间卡顿。
 * 内存控制：
 *  - 不持有 Template：每个 part 放进 section 后立刻被调用方清空。
 *  - 不用 force-load ticket（避免上千 ticket 堵塞主线程 + 内存）。
 *  - 区块放完即收尾即标脏，靠调用方周期 save 落盘（区块此刻在内存，必落盘）。
 * 完成检测：
 *  - 预先用 metadata 算出每个 MC 区块列被多少 part 覆盖（含旋转保守包围盒）。
 *  - 放置时递减，归零即该区块所有 part 已就位 → 立刻收尾。
 *  - pipeline 结束后对残余未归零区块兜底收尾。
 * 所有 public 方法必须在【主线程】调用。
 */
public class FastSectionPlacer {

    private static final int FINISH_CHUNKS_PER_TICK = 16;

    private final ServerLevel level;
    private final StructurePlaceSettings settings;
    private final RandomSource random;
    private final BlockPos targetPos;
    private final int chunkSize;

    private final int minBuildHeight;
    private final int maxBuildHeight;

    // 每个 MC 区块列剩余未放置的 part 数；归零即可收尾
    private final Map<Long, Integer> remainingPartsPerChunk = new HashMap<>();
    // 已收尾的区块，避免重复
    private final Set<Long> finishedChunks = new HashSet<>();
    // 放置过程中临时持有的 chunk 引用（收尾后可移除以省内存）
    private final Map<Long, LevelChunk> chunkCache = new HashMap<>();

    // 延迟放置的方块实体 / 实体（依赖方块就位）
    private final List<DeferredBE> deferredBlockEntities = new ArrayList<>();
    private final List<CompoundTag> deferredEntities = new ArrayList<>();

    private record DeferredBE(BlockPos pos, CompoundTag nbt) {}

    public FastSectionPlacer(ServerLevel level, StructurePlaceSettings settings,
                             RandomSource random, BlockPos targetPos, int chunkSize) {
        this.level = level;
        this.settings = settings;
        this.random = random;
        this.targetPos = targetPos;
        this.chunkSize = chunkSize;
        this.minBuildHeight = level.getMinBuildHeight();
        this.maxBuildHeight = level.getMaxBuildHeight();
    }

    // ========================= 预计算区块覆盖 =========================

    /**
     * 放置前调用一次：根据结构维度预计算每个 MC 区块被多少 part 覆盖。
     * 用于"区块所有 part 放完 → 立即收尾"的判定。
     */
    public void precomputeCoverage(int xChunks, int yChunks, int zChunks) {
        for (int px = 0; px < xChunks; px++) {
            for (int pz = 0; pz < zChunks; pz++) {
                // 一个 (px,pz) 列覆盖的 MC 区块（对所有 py 相同），
                // 这里乘以 yChunks 因为纵向每层 part 都会触及同一批 MC 区块列
                for (long chunkKey : mcChunksForPart(px, pz)) {
                    remainingPartsPerChunk.merge(chunkKey, yChunks, Integer::sum);
                }
            }
        }
    }

    /** 计算一个 part(px,*,pz) 覆盖哪些 MC 区块列（含旋转的保守包围盒） */
    private Set<Long> mcChunksForPart(int px, int pz) {
        // part 在结构局部坐标的方块范围
        int lx0 = px * chunkSize;
        int lz0 = pz * chunkSize;
        int lx1 = lx0 + chunkSize - 1;
        int lz1 = lz0 + chunkSize - 1;

        // 对 4 个角做坐标变换（旋转/镜像），取世界范围包围盒
        int minWx = Integer.MAX_VALUE, minWz = Integer.MAX_VALUE;
        int maxWx = Integer.MIN_VALUE, maxWz = Integer.MIN_VALUE;
        int[][] corners = {{lx0, lz0}, {lx1, lz0}, {lx0, lz1}, {lx1, lz1}};
        for (int[] c : corners) {
            BlockPos local = new BlockPos(c[0], 0, c[1]);
            BlockPos world = StructureTemplate
                    .calculateRelativePosition(settings, local).offset(targetPos);
            minWx = Math.min(minWx, world.getX());
            maxWx = Math.max(maxWx, world.getX());
            minWz = Math.min(minWz, world.getZ());
            maxWz = Math.max(maxWz, world.getZ());
        }

        Set<Long> result = new HashSet<>();
        int cx0 = SectionPos.blockToSectionCoord(minWx);
        int cx1 = SectionPos.blockToSectionCoord(maxWx);
        int cz0 = SectionPos.blockToSectionCoord(minWz);
        int cz1 = SectionPos.blockToSectionCoord(maxWz);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                result.add(ChunkPos.asLong(cx, cz));
            }
        }
        return result;
    }

    // ========================= 放置 =========================

    /**
     * 放置一个 part。主线程。放完会检测涉及的区块是否已完整，
     * 完整的立即收尾。
     * @return 放置的方块数
     */
    public int placeTemplate(StructureTemplate template, BlockPos placePos) {
        if (template.palettes.isEmpty()) return 0;

        List<StructureTemplate.StructureBlockInfo> blocks =
                settings.getRandomPalette(template.palettes, placePos).blocks();

        // 本次 part 触及的 MC 区块（用于放完后递减计数）
        Set<Long> touched = new HashSet<>();
        int placed = 0;

        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockPos worldPos = StructureTemplate
                    .calculateRelativePosition(settings, info.pos())
                    .offset(placePos);

            int y = worldPos.getY();
            if (y < minBuildHeight || y >= maxBuildHeight) continue;

            BlockState state = info.state()
                    .mirror(settings.getMirror())
                    .rotate(settings.getRotation());

            int cx = SectionPos.blockToSectionCoord(worldPos.getX());
            int cz = SectionPos.blockToSectionCoord(worldPos.getZ());
            long chunkKey = ChunkPos.asLong(cx, cz);
            touched.add(chunkKey);

            LevelChunk chunk = chunkCache.get(chunkKey);
            if (chunk == null) {
                chunk = level.getChunk(cx, cz);
                chunkCache.put(chunkKey, chunk);
            }

            int sectionIndex = chunk.getSectionIndex(y);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
            LevelChunkSection section = chunk.getSection(sectionIndex);

            section.setBlockState(worldPos.getX() & 15, y & 15, worldPos.getZ() & 15, state, false);
            placed++;

            if (info.nbt() != null) {
                deferredBlockEntities.add(new DeferredBE(worldPos.immutable(), info.nbt().copy()));
            }
        }

        collectEntities(template, placePos);

        // 递减每个触及区块的剩余 part 计数；归零 → 立刻收尾
        for (long chunkKey : touched) {
            Integer rem = remainingPartsPerChunk.get(chunkKey);
            if (rem == null) continue; // 预计算外的区块（理论不该有），忽略
            rem -= 1;
            if (rem <= 0) {
                remainingPartsPerChunk.remove(chunkKey);
                finishChunkNow(chunkKey);
            } else {
                remainingPartsPerChunk.put(chunkKey, rem);
            }
        }

        return placed;
    }

    private void collectEntities(StructureTemplate template, BlockPos placePos) {
        try {
            List<StructureTemplate.StructureEntityInfo> list = template.entityInfoList;
            if (list == null || list.isEmpty()) return;

            for (StructureTemplate.StructureEntityInfo entityInfo : list) {
                Vec3 pos = StructureTemplate.transformedVec3d(settings, entityInfo.pos)
                        .add(Vec3.atLowerCornerOf(placePos));
                CompoundTag tag = entityInfo.nbt.copy();
                ListTag posTag = new ListTag();
                posTag.add(DoubleTag.valueOf(pos.x));
                posTag.add(DoubleTag.valueOf(pos.y));
                posTag.add(DoubleTag.valueOf(pos.z));
                tag.put("Pos", posTag);
                tag.remove("UUID");
                tag.putDouble("_fsp_x", pos.x);
                tag.putDouble("_fsp_y", pos.y);
                tag.putDouble("_fsp_z", pos.z);
                deferredEntities.add(tag);
            }
        } catch (Exception e) {
            ModLogger.getLogger().error("收集实体失败", e);
        }
    }

    // ========================= 收尾 =========================

    /** 立即收尾单个区块（区块所有 part 已放完时调用） */
    private void finishChunkNow(long chunkKey) {
        if (!finishedChunks.add(chunkKey)) return; // 已收尾

        LevelChunk chunk = chunkCache.get(chunkKey);
        if (chunk == null) {
            ChunkPos tmp = new ChunkPos(chunkKey);
            chunk = level.getChunk(tmp.x, tmp.z);
        }

        ChunkPos cp = chunk.getPos();

        Heightmap.primeHeightmaps(chunk, EnumSet.of(
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.WORLD_SURFACE
        ));
        chunk.setUnsaved(true);

        ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            boolean hasOnlyAir = sections[i].hasOnlyAir();
            int sectionY = level.getSectionYFromSectionIndex(i);
            lightEngine.updateSectionStatus(SectionPos.of(cp, sectionY), hasOnlyAir);
        }
        lightEngine.propagateLightSources(cp);

        // 发包给跟踪玩家
        net.minecraft.server.level.ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ClientboundLevelChunkWithLightPacket packet =
                new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null);
        for (ServerPlayer player : chunkMap.getPlayers(cp, false)) {
            player.connection.send(packet);
        }

        // 收尾后释放该 chunk 的强引用（GC 可回收 part 残留）
        chunkCache.remove(chunkKey);
    }

    /**
     * pipeline 全部结束后调用：处理延迟的方块实体/实体，
     * 并对任何残余未收尾区块兜底收尾。主线程。
     * 调用方应在此之后 save。
     */
    public void finalizeAll() {
        loadBlockEntities();
        addEntities();

        // 兜底：仍有剩余计数未归零的区块（理论上不该有，保险）
        List<Long> remaining = new ArrayList<>(remainingPartsPerChunk.keySet());
        remainingPartsPerChunk.clear();
        for (long key : remaining) {
            finishChunkNow(key);
        }

        level.getChunkSource().getLightEngine().tryScheduleUpdate();
    }

    private void loadBlockEntities() {
        for (DeferredBE be : deferredBlockEntities) {
            try {
                int cx = SectionPos.blockToSectionCoord(be.pos().getX());
                int cz = SectionPos.blockToSectionCoord(be.pos().getZ());
                LevelChunk chunk = level.getChunk(cx, cz);

                BlockState state = chunk.getBlockState(be.pos());
                if (!state.hasBlockEntity()) continue;

                BlockEntity blockEntity = chunk.getBlockEntity(be.pos(), LevelChunk.EntityCreationType.CHECK);
                if (blockEntity == null) {
                    blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(be.pos(), state);
                    if (blockEntity != null) chunk.addAndRegisterBlockEntity(blockEntity);
                }
                if (blockEntity != null) {
                    if (blockEntity instanceof RandomizableContainerBlockEntity) {
                        be.nbt().putLong("LootTableSeed", random.nextLong());
                    }
                    blockEntity.load(be.nbt());
                    blockEntity.setChanged();
                }
            } catch (Exception e) {
                ModLogger.getLogger().error("加载方块实体失败 @ {}", be.pos(), e);
            }
        }
        deferredBlockEntities.clear();
    }

    private void addEntities() {
        for (CompoundTag tag : deferredEntities) {
            try {
                double x = tag.getDouble("_fsp_x");
                double y = tag.getDouble("_fsp_y");
                double z = tag.getDouble("_fsp_z");
                tag.remove("_fsp_x"); tag.remove("_fsp_y"); tag.remove("_fsp_z");

                EntityType.create(tag, level).ifPresent(entity -> {
                    float yaw = entity.rotate(settings.getRotation());
                    yaw += entity.mirror(settings.getMirror()) - entity.getYRot();
                    entity.moveTo(x, y, z, yaw, entity.getXRot());
                    if (settings.shouldFinalizeEntities() && entity instanceof Mob mob) {
                        mob.finalizeSpawn(level,
                                level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                                MobSpawnType.STRUCTURE, null, tag);
                    }
                    level.addFreshEntityWithPassengers(entity);
                });
            } catch (Exception e) {
                ModLogger.getLogger().error("放置实体失败", e);
            }
        }
        deferredEntities.clear();
    }
}