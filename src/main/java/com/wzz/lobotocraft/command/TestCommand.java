package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.item.debug.StructureToolSaveItem;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

public class TestCommand implements ICommand {

    private static final String FIRST_POSITION_KEY  = "first_position";
    private static final String SECOND_POSITION_KEY = "second_position";
    private static final String READY_TO_SAVE_KEY   = "ready_to_save";

    @Override
    public String getName() { return "test"; }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .then(Commands.argument("structure", StringArgumentType.string())
                        // /lobotocraft test <结构名>
                        .executes(ctx -> executeLoad(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "structure"),
                                getSourcePos(ctx.getSource()),
                                Rotation.NONE,
                                false))

                        // /lobotocraft test <结构名> autoselect
                        .then(Commands.literal("autoselect")
                                .executes(ctx -> executeLoad(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "structure"),
                                        getSourcePos(ctx.getSource()),
                                        Rotation.NONE,
                                        true)))

                        // /lobotocraft test <结构名> <坐标>
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> executeLoad(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "structure"),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                        Rotation.NONE,
                                        false))

                                // /lobotocraft test <结构名> <坐标> autoselect
                                .then(Commands.literal("autoselect")
                                        .executes(ctx -> executeLoad(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "structure"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                Rotation.NONE,
                                                true)))

                                // /lobotocraft test <结构名> <坐标> <旋转>
                                .then(Commands.argument("rotation", StringArgumentType.string())
                                        .suggests((ctx, b) -> {
                                            for (Rotation r : Rotation.values())
                                                b.suggest(r.name().toLowerCase());
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Rotation rot = parseRotation(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "rotation"));
                                            if (rot == null) return 0;
                                            return executeLoad(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "structure"),
                                                    BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                    rot, false);
                                        })

                                        // /lobotocraft test <结构名> <坐标> <旋转> autoselect
                                        .then(Commands.literal("autoselect")
                                                .executes(ctx -> {
                                                    Rotation rot = parseRotation(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "rotation"));
                                                    if (rot == null) return 0;
                                                    return executeLoad(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "structure"),
                                                            BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                            rot, true);
                                                })))));
    }

    // ------------------------------------------------------------------ //

    private static int executeLoad(CommandSourceStack source, String name,
                                   BlockPos pos, Rotation rotation, boolean autoSelect) {
        ServerLevel level = source.getLevel();
        Player player     = source.getPlayer();

        source.sendSuccess(() -> Component.literal(
                "§b正在加载结构 §f" + name +
                        " §b坐标 §f" + pos.toShortString() +
                        " §b旋转 §f" + rotation.name() +
                        (autoSelect ? " §d[自动圈选]" : "")), true);

        // 提前读取元数据，用于计算结构范围
        CompoundTag meta = StructureLoader.loadMetadata(name + "_main");

        boolean started = StructureLoader.beginLoading(
                level, player, name, pos, rotation, null,
                () -> onLoadFinished(source, player, name, pos, rotation, meta, autoSelect)
        );

        if (!started) {
            source.sendFailure(Component.literal("§c结构 " + name + " 启动失败，请检查文件是否存在。"));
            return 0;
        }
        return 1;
    }

    /**
     * 结构加载完毕后的回调：发送消息，并在 autoSelect=true 时自动圈选
     */
    private static void onLoadFinished(CommandSourceStack source, Player player,
                                       String name, BlockPos origin, Rotation rotation,
                                       CompoundTag meta, boolean autoSelect) {
        source.sendSuccess(() -> Component.literal("§a结构 §f" + name + " §a加载完毕!"), true);

        if (autoSelect && player != null && meta != null && !meta.isEmpty()) {
            BlockPos endPos = calcEndPos(origin, rotation, meta);
            applySelectionToTool(player, origin, endPos);
        }
    }

    /**
     * 根据元数据计算结构末端坐标（已考虑旋转后的偏移方向）
     */
    private static BlockPos calcEndPos(BlockPos origin, Rotation rotation, CompoundTag meta) {
        int sizeX = meta.contains("totalSizeX") ? meta.getInt("totalSizeX")
                : meta.getInt("totalXChunks") * meta.getInt("chunkSize");
        int sizeY = meta.contains("totalSizeY") ? meta.getInt("totalSizeY")
                : meta.getInt("totalYChunks") * meta.getInt("chunkSize");
        int sizeZ = meta.contains("totalSizeZ") ? meta.getInt("totalSizeZ")
                : meta.getInt("totalZChunks") * meta.getInt("chunkSize");

        // 旋转后 X/Z 互换
        int dx = sizeX - 1, dy = sizeY - 1, dz = sizeZ - 1;
        int rotDx, rotDz;
        switch (rotation) {
            case CLOCKWISE_90       -> { rotDx =  dz; rotDz =  dx; }
            case CLOCKWISE_180      -> { rotDx =  dx; rotDz =  dz; }
            case COUNTERCLOCKWISE_90-> { rotDx =  dz; rotDz =  dx; }
            default                 -> { rotDx =  dx; rotDz =  dz; }
        }
        return origin.offset(rotDx, dy, rotDz);
    }

    /**
     * 将计算出的两角点写入玩家手持的 StructureToolSaveItem 里
     */
    private static void applySelectionToTool(Player player, BlockPos first, BlockPos second) {
        ItemStack stack = findSaveTool(player);
        if (stack == null) {
            player.displayClientMessage(
                    Component.literal("§e提示: 手持结构保存工具才能自动圈选"), false);
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(FIRST_POSITION_KEY,  first.asLong());
        tag.putLong(SECOND_POSITION_KEY, second.asLong());
        tag.putBoolean(READY_TO_SAVE_KEY, true);

        player.displayClientMessage(
                Component.literal("§d已自动圈选结构: §f" +
                        first.toShortString() + " §d→ §f" + second.toShortString()), false);
    }

    private static ItemStack findSaveTool(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof StructureToolSaveItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof StructureToolSaveItem) return off;
        return null;
    }

    private static BlockPos getSourcePos(CommandSourceStack source) {
        Player p = source.getPlayer();
        return p != null ? p.blockPosition() : BlockPos.containing(source.getPosition());
    }

    private static Rotation parseRotation(CommandSourceStack source, String raw) {
        for (Rotation r : Rotation.values())
            if (r.name().equalsIgnoreCase(raw)) return r;
        source.sendFailure(Component.literal(
                "§c未知旋转: " + raw +
                        " §7(none/clockwise_90/clockwise_180/counterclockwise_90)"));
        return null;
    }
}