package com.wzz.lobotocraft.client.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.core.GeburahBladeTrailRenderer;
import com.wzz.lobotocraft.client.core.GeburahBladeTrailState;
import com.wzz.lobotocraft.client.core.GeburahEgoHitEffects;
import com.wzz.lobotocraft.client.core.GeburahSpecialEffects;
import com.wzz.lobotocraft.client.config.ClientVisualConfig;
import com.wzz.lobotocraft.client.damage_border.DamageBorderHud;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class LobotocraftClientCommand {
    private LobotocraftClientCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lobotocraft_client")
                .then(Commands.literal("damage_border")
                        .executes(context -> report(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> set(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("geburah_trail_markers")
                        .executes(context -> reportTrailMarkers(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setTrailMarkers(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("geburah_trail_surface")
                        .executes(context -> reportTrailSurface(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setTrailSurface(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("geburah_ego_attack_trial")
                        .executes(context -> reportEgoTrial(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setEgoTrial(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("geburah_special_visuals")
                        .executes(context -> reportSpecialVisuals(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setSpecialVisuals(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled"))))));
    }

    private static int report(net.minecraft.commands.CommandSourceStack source) {
        boolean enabled = DamageBorderHud.isEnabled();
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft_client.damage_border.status",
                Component.translatable(enabled ? "options.on" : "options.off")), false);
        return 1;
    }

    private static int set(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        DamageBorderHud.setEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(
                "command.lobotocraft_client.damage_border.set",
                Component.translatable(enabled ? "options.on" : "options.off")), false);
        return 1;
    }

    private static int reportTrailMarkers(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Geburah trail markers: "
                + GeburahBladeTrailRenderer.debugMarkers()), false);
        return 1;
    }

    private static int reportSpecialVisuals(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Geburah special visuals: "
                + ClientVisualConfig.GEBURAH_SPECIAL_VISUALS.get() + " (false = legacy particles)"), false);
        return 1;
    }
    private static int setSpecialVisuals(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        ClientVisualConfig.GEBURAH_SPECIAL_VISUALS.set(enabled);
        ClientVisualConfig.SPEC.save();
        GeburahSpecialEffects.clearAccents();
        return reportSpecialVisuals(source);
    }

    private static int reportEgoTrial(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Geburah EGO slash/stab trial: "
                + ClientVisualConfig.GEBURAH_EGO_ATTACK_TRIAL.get()
                + " (false = legacy; ground impact unchanged)"), false);
        return 1;
    }

    private static int setEgoTrial(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        ClientVisualConfig.GEBURAH_EGO_ATTACK_TRIAL.set(enabled);
        ClientVisualConfig.SPEC.save();
        GeburahBladeTrailState.clear();
        GeburahEgoHitEffects.clear();
        return reportEgoTrial(source);
    }

    private static int setTrailMarkers(net.minecraft.commands.CommandSourceStack source,
                                       boolean enabled) {
        GeburahBladeTrailRenderer.setDebugMarkers(enabled);
        source.sendSuccess(() -> Component.literal("Geburah trail markers: " + enabled), false);
        return 1;
    }

    private static int reportTrailSurface(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Geburah trail surface: "
                + GeburahBladeTrailRenderer.trailSurfaceEnabled()), false);
        return 1;
    }

    private static int setTrailSurface(net.minecraft.commands.CommandSourceStack source,
                                       boolean enabled) {
        GeburahBladeTrailRenderer.setTrailSurfaceEnabled(enabled);
        source.sendSuccess(() -> Component.literal("Geburah trail surface: " + enabled), false);
        return 1;
    }
}
