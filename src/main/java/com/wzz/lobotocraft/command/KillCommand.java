package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.EntityCoreSuppressionNpc;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.event.listener.BlueMiddayEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class KillCommand implements ICommand {

    private static final float DAMAGE_AMOUNT = 9999.0F;
    private static final int KILLS_PER_TICK = 64;
    private static final Queue<LivingEntity> KILL_QUEUE = new ConcurrentLinkedQueue<>();

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .executes(context -> execute(context.getSource()));
    }

    @Override
    public String getName() {
        return "kill";
    }

    private static int execute(CommandSourceStack source) {
        int count = queueAllSuppressionTargets(source.getServer());

        int finalCount = count;
        source.sendSuccess(() ->
                        Component.literal("已将 " + finalCount + " 个出逃异想体/生物加入清理队列"),
                true);
        return count;
    }

    public static int queueAllSuppressionTargets(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isKillTarget(entity)) {
                    KILL_QUEUE.add((LivingEntity) entity);
                    count++;
                }
            }
        }
        return count;
    }

    public static int queueActiveOrdealTargets(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                String packageName = entity.getClass().getPackageName();
                if (packageName.contains(".entity.ordeal") || BlueMiddayEvent.isBlueMiddaySpawn(entity)) {
                    KILL_QUEUE.add(living);
                    count++;
                }
            }
        }
        BlueMiddayEvent.endTrial();
        return count;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (int i = 0; i < KILLS_PER_TICK; i++) {
            LivingEntity target = KILL_QUEUE.poll();
            if (target == null) {
                return;
            }
            if (!target.isAlive() || target.isRemoved()) {
                continue;
            }
            target.hurt(target.damageSources().genericKill(), DAMAGE_AMOUNT);
        }
    }

    private static boolean isKillTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)
                || living instanceof Player
                || living instanceof EntityClerk
                || living instanceof EntityCoreSuppressionNpc
                || !living.isAlive()) {
            return false;
        }
        if (living instanceof AbstractAbnormality abnormality) {
            return abnormality.hasEscape();
        }
        return true;
    }
}
