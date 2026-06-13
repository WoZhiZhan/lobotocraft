package com.wzz.lobotocraft.item.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.util.EnvironmentUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
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

import java.util.UUID;

import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_LESS;

public class StructureToolSaveItem extends Item {
    private static final String FIRST_POSITION_KEY = "first_position";
    private static final String SECOND_POSITION_KEY = "second_position";
    private static final String STRUCTURE_NAME_KEY = "structure_name";
    private static final String READY_TO_SAVE_KEY = "ready_to_save";

    private static final double MEMORY_WARNING_THRESHOLD = 0.85;
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.95;

    public StructureToolSaveItem() {
        super(new Properties().fireResistant().rarity(Rarity.EPIC).stacksTo(1));
        if (EnvironmentUtil.isClient()) {
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
        String baseName = stack.getTag().contains(STRUCTURE_NAME_KEY) ?
                stack.getTag().getString(STRUCTURE_NAME_KEY) :
                "custom_" + UUID.randomUUID().toString().substring(0, 8);

        int xChunks = (int) Math.ceil((double)totalSize.getX() / CHUNK_SIZE);
        int yChunks = (int) Math.ceil((double)totalSize.getY() / CHUNK_SIZE);
        int zChunks = (int) Math.ceil((double)totalSize.getZ() / CHUNK_SIZE);

        boolean success = true;
        int savedChunks = 0;
        int totalChunks = xChunks * yChunks * zChunks;

        player.displayClientMessage(Component.literal("§e开始保存大型结构，共 " + totalChunks + " 个分块..."), false);

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

                        // 正确获取或创建模板
                        template = manager.getOrCreate(structureId);
                        template.fillFromWorld(serverLevel, chunkStart, chunkSize, true, Blocks.STRUCTURE_VOID);
                        template.setAuthor("wzz");

                        // 保存分块元数据
                        CompoundTag chunkMetadata = new CompoundTag();
                        chunkMetadata.putString("baseName", baseName);
                        chunkMetadata.putInt("chunkX", x);
                        chunkMetadata.putInt("chunkY", y);
                        chunkMetadata.putInt("chunkZ", z);
                        StructureLoader.saveMetadata(manager, chunkName, chunkMetadata);

                        // 保存结构模板
                        manager.save(structureId);

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
        } else {
            player.displayClientMessage(Component.literal("§6结构部分保存完成，但有部分分块保存失败"), false);
        }

        return success;
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
                    Component.literal("再次右键一个方块保存结构，shift右键方块取消"),
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