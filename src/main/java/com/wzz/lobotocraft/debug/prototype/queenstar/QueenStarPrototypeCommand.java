package com.wzz.lobotocraft.debug.prototype.queenstar;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.command.ICommand;
import com.wzz.lobotocraft.network.MessageLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Throwaway visual prototype for a model-free Queen of Hatred normal attack. */
public final class QueenStarPrototypeCommand implements ICommand {
    private static final double RANGE = 28.0D;
    private static final double BLOCKS_PER_TICK = 0.55D;

    @Override
    public String getName() {
        return "queen_star";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .requires(source -> source.hasPermission(2))
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("original")
                        .executes(context -> start(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Queen star prototype: /lobotocraft queen_star <original|clear>"), false);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This prototype must be aimed by a player."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(look.scale(1.25D));
        Vec3 desiredEnd = start.add(look.scale(RANGE));
        HitResult blockHit = level.clip(new ClipContext(start, desiredEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation().subtract(look.scale(0.06D))
                : desiredEnd;
        if (start.distanceToSqr(end) < 0.25D) {
            end = start.add(look.scale(0.5D));
        }

        int travelTicks = Mth.clamp((int) Math.ceil(start.distanceTo(end) / BLOCKS_PER_TICK), 8, 80);
        QueenStarPrototypePacket packet = QueenStarPrototypePacket.start(start, end, travelTicks);
        for (ServerPlayer viewer : level.players()) {
            MessageLoader.getLoader().sendToPlayer(viewer, packet);
        }

        source.sendSuccess(() -> Component.literal(
                "Started original-style queen star prototype (visual only)."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        for (ServerPlayer viewer : source.getLevel().players()) {
            MessageLoader.getLoader().sendToPlayer(viewer, QueenStarPrototypePacket.clear());
        }
        source.sendSuccess(() -> Component.literal("Cleared the queen star prototype."), false);
        return 1;
    }
}
