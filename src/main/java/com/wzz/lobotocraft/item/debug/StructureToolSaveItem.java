package com.wzz.lobotocraft.item.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.lwjgl.opengl.GL11.GL_LEQUAL;

public class StructureToolSaveItem extends Item {
    private static final String FIRST_POSITION_KEY = "first_position";
    private static final String SECOND_POSITION_KEY = "second_position";
    private static final String STRUCTURE_NAME_KEY = "structure_name";
    private static final String READY_TO_SAVE_KEY = "ready_to_save";

    private static final double MEMORY_WARNING_THRESHOLD = 0.85;
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.95;

    private static final Set<String> AIR_BLOCK_IDS = Set.of(
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:void_air"
    );

    public StructureToolSaveItem() {
        super(new Properties().fireResistant().rarity(Rarity.EPIC).stacksTo(1));
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.getMainHandItem().getItem().equals(this)) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getTag() == null) return;
        if (!stack.getTag().contains(FIRST_POSITION_KEY) || !stack.getTag().contains(SECOND_POSITION_KEY)) return;
        BlockPos firstPos  = BlockPos.of(stack.getTag().getLong(FIRST_POSITION_KEY));
        BlockPos secondPos = BlockPos.of(stack.getTag().getLong(SECOND_POSITION_KEY));
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        renderSelectionBox(firstPos, secondPos, poseStack, bufferSource,
                stack.getTag().contains(READY_TO_SAVE_KEY));
        bufferSource.endBatch(RenderType.LINES);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL_LEQUAL);
        poseStack.popPose();
    }

    @OnlyIn(Dist.CLIENT)
    public void renderSelectionBox(BlockPos firstPos, BlockPos secondPos, PoseStack matrixStack,
                                   MultiBufferSource bufferSource, boolean readyToSave) {
        if (firstPos == null || secondPos == null) return;
        float minX = Math.min(firstPos.getX(), secondPos.getX());
        float maxX = Math.max(firstPos.getX(), secondPos.getX()) + 1;
        float minY = Math.min(firstPos.getY(), secondPos.getY());
        float maxY = Math.max(firstPos.getY(), secondPos.getY()) + 1;
        float minZ = Math.min(firstPos.getZ(), secondPos.getZ());
        float maxZ = Math.max(firstPos.getZ(), secondPos.getZ()) + 1;
        float r = 1.0F;
        float g = readyToSave ? 0.0F : 1.0F;
        float b = 0.0F;
        VertexConsumer vc = bufferSource.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(matrixStack, vc, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0F);
    }

    /**
     * 获取当前内存使用率
     */
    private double getMemoryUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        return (double) usedMemory / maxMemory;
    }

    /**
     * 强制清理StructureTemplateManager缓存
     */
    private void clearStructureTemplateCache(StructureTemplateManager manager) {
        try {
            if (manager.structureRepository.size() > 50) {
                manager.structureRepository.clear();
            }
        } catch (Exception e) {
            // 静默失败，继续执行
        }
    }

    /**
     * 智能内存清理
     */
    private void smartMemoryCleanup(StructureTemplateManager manager, Player player, int currentChunk, int totalChunks) {
        double memoryUsage = getMemoryUsagePercent();
        if (currentChunk % 5 == 0 || memoryUsage > MEMORY_WARNING_THRESHOLD) {
            // 清理模板管理器缓存
            clearStructureTemplateCache(manager);
            // 执行垃圾回收
            System.gc();
            try {
                Thread.sleep(memoryUsage > MEMORY_CRITICAL_THRESHOLD ? 50 : 20);
            } catch (InterruptedException ignored) {}
            // 重新检查内存使用率
            double newMemoryUsage = getMemoryUsagePercent();

            if (currentChunk % 2 == 0) {
                player.displayClientMessage(Component.literal(
                        String.format("§e进度: %d/%d (%.1f%%) - 内存: %.1f%%",
                                currentChunk, totalChunks,
                                (double)currentChunk / totalChunks * 100,
                                newMemoryUsage * 100)), true);
            }
        }
    }

    /**
     * 优化结构模板 - 直接在NBT层面移除空气方块
     * @param template 要优化的结构模板
     * @return 优化后的NBT数据
     */
    private CompoundTag optimizeStructureNBT(StructureTemplate template) {
        try {
            // 获取模板的NBT数据
            CompoundTag root = template.save(new CompoundTag());
            ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);

            // 创建旧索引到新索引的映射
            Map<Integer, Integer> oldToNewPaletteMap = new HashMap<>();
            ListTag newPalette = new ListTag();

            // 统计移除的空气方块
            int airStatesRemoved = 0;

            // 过滤调色板中的空气方块
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag state = (CompoundTag) palette.get(i);
                String blockId = state.getString("Name");

                if (!AIR_BLOCK_IDS.contains(blockId)) {
                    oldToNewPaletteMap.put(i, newPalette.size());
                    newPalette.add(state.copy());
                } else {
                    airStatesRemoved++;
                }
            }

            // 如果没有空气方块需要移除，直接返回原始数据
            if (airStatesRemoved == 0) {
                return root;
            }

            // 过滤方块列表
            ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
            ListTag newBlocks = new ListTag();

            int airBlocksRemoved = 0;
            for (Tag tag : blocks) {
                CompoundTag block = (CompoundTag) tag;
                int oldStateIndex = block.getInt("state");

                if (oldToNewPaletteMap.containsKey(oldStateIndex)) {
                    CompoundTag newBlock = block.copy();
                    newBlock.putInt("state", oldToNewPaletteMap.get(oldStateIndex));
                    newBlocks.add(newBlock);
                } else {
                    airBlocksRemoved++;
                }
            }

            // 更新模板数据
            root.put("palette", newPalette);
            root.put("blocks", newBlocks);

            return root;

        } catch (Exception e) {
            // 如果优化失败，返回null表示使用原始数据
            System.err.println("结构优化失败: " + e.getMessage());
            return null;
        }
    }

    public boolean saveStructure(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return false;
        }
        if (stack.getTag() == null) return false;
        if (!stack.hasTag() || !stack.getTag().contains(FIRST_POSITION_KEY) || !stack.getTag().contains(SECOND_POSITION_KEY)) {
            player.displayClientMessage(Component.literal("§4错误：结构未选择!"), false);
            return false;
        }

        BlockPos firstPos = BlockPos.of(stack.getTag().getLong(FIRST_POSITION_KEY));
        BlockPos secondPos = BlockPos.of(stack.getTag().getLong(SECOND_POSITION_KEY));

        BlockPos minPos = new BlockPos(
                Math.min(firstPos.getX(), secondPos.getX()),
                Math.min(firstPos.getY(), secondPos.getY()),
                Math.min(firstPos.getZ(), secondPos.getZ())
        );

        BlockPos maxPos = new BlockPos(
                Math.max(firstPos.getX(), secondPos.getX()),
                Math.max(firstPos.getY(), secondPos.getY()),
                Math.max(firstPos.getZ(), secondPos.getZ())
        );

        Vec3i totalSize = new Vec3i(
                maxPos.getX() - minPos.getX() + 1,
                maxPos.getY() - minPos.getY() + 1,
                maxPos.getZ() - minPos.getZ() + 1
        );

        final int CHUNK_SIZE = 64;

        ServerLevel serverLevel = (ServerLevel) level;
        StructureTemplateManager manager = serverLevel.getStructureManager();
        // 输出目录:在铁砧上给这个工具改名,名字就是相对 structures/ 的子目录。
        //
        // 【新的二级分类约定】
        //   改名 "bedwars/frost"  -> data/bedwars/structures/bedwars/frost/custom_xxxxxxxx_main.nbt (起床战争地图)
        //   改名 "skywars/islands"-> data/bedwars/structures/skywars/islands/...                    (空岛战争地图)
        //   不改名                -> data/bedwars/structures/custom_xxxxxxxx_main.nbt              (开始大厅)
        //
        // 所以斜杠必须保留(以前被正则当非法字符替换成了下划线,存不出多级目录)。
        String folder = "";
        if (stack.hasCustomHoverName()) {
            String raw = stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
            // 允许 a-z 0-9 _ - 和目录分隔符 /
            String cleaned = raw.replace('\\', '/').replaceAll("[^a-z0-9_/\\-]", "_");
            // 合并重复斜杠、去掉首尾斜杠,免得拼出 //// 这种非法路径
            cleaned = cleaned.replaceAll("/{2,}", "/").replaceAll("^/+", "").replaceAll("/+$", "");
            if (!cleaned.isEmpty()) folder = cleaned + "/";
        }

        String rawName = stack.getTag().contains(STRUCTURE_NAME_KEY) ?
                stack.getTag().getString(STRUCTURE_NAME_KEY) :
                "custom_" + UUID.randomUUID().toString().substring(0, 8);
        String baseName = folder + rawName;

        int xChunks = (int) Math.ceil((double)totalSize.getX() / CHUNK_SIZE);
        int yChunks = (int) Math.ceil((double)totalSize.getY() / CHUNK_SIZE);
        int zChunks = (int) Math.ceil((double)totalSize.getZ() / CHUNK_SIZE);

        boolean success = true;
        int savedChunks = 0;
        int totalChunks = xChunks * yChunks * zChunks;

        player.displayClientMessage(Component.literal("§e开始保存大型结构（已启用空气过滤），共 " + totalChunks + " 个分块..."), false);

        double initialMemory = getMemoryUsagePercent();
        player.displayClientMessage(Component.literal("§e开始时内存使用率: " +
                String.format("%.1f", initialMemory * 100) + "%"), false);

        CompoundTag mainMetadata = new CompoundTag();
        mainMetadata.putString("type", "multi_part_structure");
        mainMetadata.putString("baseName", baseName);
        mainMetadata.putInt("totalXChunks", xChunks);
        mainMetadata.putInt("totalYChunks", yChunks);
        mainMetadata.putInt("totalZChunks", zChunks);
        mainMetadata.putInt("chunkSize", CHUNK_SIZE);
        mainMetadata.putInt("totalSizeX", totalSize.getX());
        mainMetadata.putInt("totalSizeY", totalSize.getY());
        mainMetadata.putInt("totalSizeZ", totalSize.getZ());
        mainMetadata.putBoolean("optimized", true); // 标记已优化

        int totalAirBlocksRemoved = 0;

        for (int x = 0; x < xChunks; x++) {
            for (int y = 0; y < yChunks; y++) {
                for (int z = 0; z < zChunks; z++) {
                    StructureTemplate template = null;
                    try {
                        BlockPos chunkStart = minPos.offset(
                                x * CHUNK_SIZE,
                                y * CHUNK_SIZE,
                                z * CHUNK_SIZE
                        );
                        Vec3i chunkSize = new Vec3i(
                                Math.min(CHUNK_SIZE, totalSize.getX() - x * CHUNK_SIZE),
                                Math.min(CHUNK_SIZE, totalSize.getY() - y * CHUNK_SIZE),
                                Math.min(CHUNK_SIZE, totalSize.getZ() - z * CHUNK_SIZE)
                        );
                        String chunkName = baseName + String.format("_part%02d%02d%02d", x, y, z);
                        ResourceLocation structureId = ResourceUtil.createInstance(chunkName);

                        // 创建模板并填充世界数据
                        template = new StructureTemplate();
                        template.fillFromWorld(serverLevel, chunkStart, chunkSize, true, Blocks.STRUCTURE_VOID);
                        template.setAuthor("wzz");

                        // 获取原始方块数量
                        int blocksBefore = 0;
                        if (!template.palettes.isEmpty()) {
                            blocksBefore = template.palettes.get(0).blocks().size();
                        }

                        // 优化NBT数据
                        CompoundTag optimizedNBT = optimizeStructureNBT(template);
                        int blocksAfter = blocksBefore;

                        // 【修复】分块元数据必须【合并进结构 NBT】,不能单独写一次文件。
                        // 原实现随后调用 saveMetadata(manager, chunkName, ...),
                        // 它和 saveStructureToFile 写的是同一个路径
                        // (都是 manager.getPathToGeneratedStructure(id, ".nbt")),
                        // 于是刚写好的 palette/blocks/size 被这份纯元数据整个覆盖 ->
                        // 表现为"过滤空气把结构也清了"。
                        CompoundTag finalNBT = (optimizedNBT != null)
                                ? optimizedNBT
                                : template.save(new CompoundTag());

                        if (optimizedNBT != null) {
                            blocksAfter = optimizedNBT.getList("blocks", Tag.TAG_COMPOUND).size();
                        }

                        // 元数据作为附加键塞进同一份 NBT(加载器只读 size/palette/blocks,多余键无害)
                        finalNBT.putString("baseName", baseName);
                        finalNBT.putInt("chunkX", x);
                        finalNBT.putInt("chunkY", y);
                        finalNBT.putInt("chunkZ", z);
                        finalNBT.putBoolean("optimized", optimizedNBT != null);
                        finalNBT.putInt("blocksBeforeOptimization", blocksBefore);
                        finalNBT.putInt("blocksAfterOptimization", blocksAfter);

                        // 只写一次
                        saveStructureToFile(manager, structureId, finalNBT);

                        int removedThisChunk = blocksBefore - blocksAfter;
                        totalAirBlocksRemoved += removedThisChunk;

                        savedChunks++;

                        // 智能内存清理
                        smartMemoryCleanup(manager, player, savedChunks, totalChunks);

                    } catch (Exception e) {
                        player.displayClientMessage(Component.literal("§c分块 " + (savedChunks + 1) + " 保存失败: " + e.getMessage()), false);
                        success = false;
                    } finally {
                        // 立即清空template引用
                        if (template != null) {
                            template = null;
                        }
                    }

                    // 检查内存压力，如果过高则延长等待时间
                    double currentMemory = getMemoryUsagePercent();
                    int sleepTime = currentMemory > MEMORY_CRITICAL_THRESHOLD ? 10 :
                            currentMemory > MEMORY_WARNING_THRESHOLD ? 5 : 3;

                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        // 保存主元数据
        try {
            mainMetadata.putInt("totalAirBlocksRemoved", totalAirBlocksRemoved);
            StructureLoader.saveMetadata(manager, baseName + "_main", mainMetadata);
            player.displayClientMessage(Component.literal("§a主元数据文件保存成功"), false);
        } catch (Exception e) {
            player.displayClientMessage(Component.literal("§c主元数据文件保存失败: " + e.getMessage()), false);
            success = false;
        }

        // 最终清理
        performFinalMemoryCleanup(manager, player);

        if (success) {
            player.displayClientMessage(Component.literal("§a结构保存完成! 共 " + savedChunks + " 个分块，主名称: " + baseName), false);
            if (totalAirBlocksRemoved > 0) {
                player.displayClientMessage(Component.literal("§a已优化! 共移除 " + totalAirBlocksRemoved + " 个空气方块"), false);
                player.displayClientMessage(Component.literal("§e优化率: " + String.format("%.1f%%",
                        (double)totalAirBlocksRemoved / (totalSize.getX() * totalSize.getY() * totalSize.getZ()) * 100)), false);
            }
        } else {
            player.displayClientMessage(Component.literal("§6结构部分保存完成，但有部分分块保存失败"), false);
        }

        return success;
    }

    /**
     * 将优化后的NBT直接保存到结构文件
     */
    private void saveStructureToFile(StructureTemplateManager manager, ResourceLocation structureId, CompoundTag nbt) {
        try {
            // 使用 getPathToGeneratedStructure 获取文件路径
            Path structurePath = manager.getPathToGeneratedStructure(structureId, ".nbt");

            // 确保父目录存在
            Files.createDirectories(structurePath.getParent());

            // 直接写入NBT文件
            try (OutputStream outputStream = new FileOutputStream(structurePath.toFile())) {
                NbtIo.writeCompressed(nbt, outputStream);
            }

        } catch (IOException e) {
            // 【修复】原来兜底调用 manager.save(structureId) 是无效的:
            // template 是 new 出来的,从未注册进 manager,save 只会写出空结构(或失败)。
            // 这里如实报错即可,不要写出一个空文件把好数据盖掉。
            System.err.println("保存结构文件失败: " + structureId + " -> " + e.getMessage());
            throw new RuntimeException("结构文件写入失败: " + structureId, e);
        }
    }

    /**
     * 保存完成后的最终内存清理
     */
    private void performFinalMemoryCleanup(StructureTemplateManager manager, Player player) {
        // 强制清理缓存
        clearStructureTemplateCache(manager);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

        player.displayClientMessage(Component.literal("§e最终内存使用率: " + String.format("%.1f", memoryUsagePercent) + "%"), false);

        if (memoryUsagePercent > 80) {
            player.displayClientMessage(Component.literal("§c建议重启游戏以释放内存"), false);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        if (player.isShiftKeyDown()) {
            cancelSelection(stack);
            if (!level.isClientSide) player.displayClientMessage(Component.literal("§e已取消选择!"), false);
            return InteractionResult.SUCCESS;
        }

        if (tag.contains(READY_TO_SAVE_KEY) && tag.getBoolean(READY_TO_SAVE_KEY)) {
            if (!level.isClientSide) {
                long timer = System.currentTimeMillis();
                boolean success = saveStructure(level, player, stack);
                player.displayClientMessage(
                        Component.literal(success ? "§a保存成功!" : "§c保存失败!"),
                        false
                );
                player.displayClientMessage(
                        Component.literal("§e用时 " + ((System.currentTimeMillis() - timer) / 1000) + " 秒"),
                        false
                );
            }
            //cancelSelection(stack);
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();

        if (!tag.contains(FIRST_POSITION_KEY)) {
            tag.putLong(FIRST_POSITION_KEY, pos.asLong());
            if (!level.isClientSide) player.displayClientMessage(
                    Component.literal("第一个位置 " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                    false
            );
        }

        else if (!tag.contains(SECOND_POSITION_KEY)) {
            tag.putLong(SECOND_POSITION_KEY, pos.asLong());
            tag.putBoolean(READY_TO_SAVE_KEY, true);
            if (!level.isClientSide) player.displayClientMessage(
                    Component.literal("第二个位置 " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                    false
            );
            if (!level.isClientSide) player.displayClientMessage(
                    Component.literal("再次右键一个方块保存结构（自动过滤空气），shift右键方块取消"),
                    false
            );
        }
        else {
            cancelSelection(stack);
            tag.putLong(FIRST_POSITION_KEY, pos.asLong());
            if (!level.isClientSide) player.displayClientMessage(
                    Component.literal("第一个位置重置为 " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                    false
            );
        }

        return InteractionResult.SUCCESS;
    }

    private void cancelSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(FIRST_POSITION_KEY);
            tag.remove(SECOND_POSITION_KEY);
            tag.remove(READY_TO_SAVE_KEY);
        }
    }
}