package com.wzz.lobotocraft.debug.prototype.queenlaser;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.command.ICommand;
import com.wzz.lobotocraft.network.MessageLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Throwaway visual prototype. Keep it isolated from the real abnormality lifecycle. */
public final class QueenLaserPrototypeCommand implements ICommand {
    private static final double PREVIEW_RANGE = 48.0D;
    private static final double MAX_RANGE = 72.0D;

    @Override
    public String getName() {
        return "queen_laser";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .requires(source -> source.hasPermission(2))
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("preview")
                        .executes(context -> start(context.getSource(), false)))
                .then(Commands.literal("max")
                        .executes(context -> start(context.getSource(), true)))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Queen laser prototype: /lobotocraft queen_laser <preview|max|clear>"), false);
        return 1;
    }

    private static int start(CommandSourceStack source, boolean maximum) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This prototype must be aimed by a player."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        double range = maximum ? MAX_RANGE : PREVIEW_RANGE;
        Vec3 origin = player.getEyePosition().add(look.scale(1.8D));
        Vec3 desiredEnd = origin.add(look.scale(range));
        HitResult blockHit = level.clip(new ClipContext(origin, desiredEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation().subtract(look.scale(0.04D))
                : desiredEnd;
        if (origin.distanceToSqr(end) < 1.0D) {
            end = origin.add(look.scale(1.0D));
        }

        int mode = maximum ? QueenLaserPrototypePacket.MODE_MAX
                : QueenLaserPrototypePacket.MODE_PREVIEW;
        int duration = QueenLaserTimeline.totalTicks(maximum);
        QueenLaserPrototypePacket packet = QueenLaserPrototypePacket.start(origin, end, mode, duration);
        for (ServerPlayer viewer : level.players()) {
            MessageLoader.getLoader().sendToPlayer(viewer, packet);
        }

        source.sendSuccess(() -> Component.literal(maximum
                ? "Started MAX queen laser prototype (6.7 s cast + 10 s beam, visual only)."
                : "Started queen laser preview (6.7 s cast + 6 s beam, visual only)."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        for (ServerPlayer viewer : source.getLevel().players()) {
            MessageLoader.getLoader().sendToPlayer(viewer, QueenLaserPrototypePacket.clear());
        }
        source.sendSuccess(() -> Component.literal("Cleared the queen laser prototype."), false);
        return 1;
    }
}
