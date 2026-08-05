package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class MentalValueClientEventHandler {
    private static final float HEAD_DOWN_PITCH = 72.0F;
    private static boolean postureActive;
    private static float pitchBeforePanic;

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (isPanicking(player) && !MentalValueEventHandler.isPrudencePanic(player)) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            postureActive = false;
            return;
        }

        if (MentalValueEventHandler.isPrudencePanic(player)) {
            if (!postureActive) {
                postureActive = true;
                pitchBeforePanic = player.getXRot();
            }
            float pitch = panicPitch(player.tickCount, player.getUUID().hashCode());
            player.setXRot(pitch);
            player.setYHeadRot(player.getYRot()
                    + panicYaw(player.tickCount, player.getUUID().hashCode()));
        } else if (postureActive) {
            player.setXRot(pitchBeforePanic);
            player.xRotO = pitchBeforePanic;
            player.setYHeadRot(player.getYRot());
            player.yHeadRotO = player.getYRot();
            postureActive = false;
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getCamera().getEntity() != player
                || !MentalValueEventHandler.isPrudencePanic(player)) return;

        float time = player.tickCount + (float) event.getPartialTick();
        int hash = player.getUUID().hashCode();
        event.setPitch(panicPitch(time, hash));
        event.setYaw(event.getYaw() + panicYaw(time, hash));
        event.setRoll(event.getRoll() + Mth.sin(time * 1.83F + phase(hash)) * 2.25F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        postureActive = false;
        pitchBeforePanic = 0.0F;
    }

    private static boolean isPanicking(Player player) {
        return MentalValueUtil.isPanic(player);
    }

    private static float panicPitch(float time, int hash) {
        float phase = phase(hash);
        float shake = Mth.sin(time * 0.91F + phase) * 5.0F
                + Mth.sin(time * 2.17F + phase) * 1.5F;
        return Mth.clamp(HEAD_DOWN_PITCH + shake, -90.0F, 90.0F);
    }

    private static float panicYaw(float time, int hash) {
        return Mth.sin(time * 1.37F + phase(hash)) * 4.0F;
    }

    private static float phase(int hash) {
        return (hash & 255) * (Mth.TWO_PI / 255.0F);
    }
}
