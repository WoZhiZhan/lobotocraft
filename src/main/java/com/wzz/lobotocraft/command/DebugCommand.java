package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.IMentalValue;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEncounterService;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahPhase;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.EmployeeTaskType;
import com.wzz.lobotocraft.employee.FacilityPersonnelData;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.item.ego.base.IEgoLevelItem;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.SuppressorCountSyncPacket;
import com.wzz.lobotocraft.util.SuppressorCounterUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.api.DayCompletionRequirements;
import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

/**
 * Current, server-authoritative diagnostics for the modern facility systems.
 * The old top-level commands remain available for compatibility; this tree is
 * the preferred entry point for reproducing and reporting bugs.
 */
public final class DebugCommand implements ICommand {
    private static final String GEBURAH_TRAIL_TEST_TAG = "lobotocraft.geburah_trail_test";
    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .requires(source -> source.hasPermission(2))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(statusTree())
                .then(workTree())
                .then(employeeTree())
                .then(abnormalityTree())
                .then(roomTree())
                .then(elevatorTree())
                .then(facilityTree())
                .then(counterTree())
                .then(ordealTree())
                .then(suppressionTree())
                .then(departmentTree())
                .then(egoTree())
                .then(Commands.literal("performance")
                        .executes(context -> performancePath(context.getSource()))
                        .then(Commands.literal("path")
                                .executes(context -> performancePath(context.getSource()))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> statusTree() {
        return Commands.literal("status")
                .executes(context -> statusSelf(context.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> status(context.getSource(),
                                EntityArgument.getPlayer(context, "player"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> workTree() {
        return Commands.literal("work")
                .then(Commands.literal("list").executes(context -> workList(context.getSource())))
                .then(Commands.literal("start")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(context -> startWork(context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        EntityArgument.getEntity(context, "target"),
                                                        StringArgumentType.getString(context, "type")))))))
                .then(Commands.literal("status")
                        .executes(context -> workStatusSelf(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> workStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> cancelWork(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("force-complete")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("result", StringArgumentType.word())
                                        .executes(context -> forceComplete(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "result"))))))
                .then(Commands.literal("stop-continuous")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> stopContinuous(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> employeeTree() {
        return Commands.literal("employee")
                .then(Commands.literal("list").executes(context -> employeeList(context.getSource())))
                .then(Commands.literal("status")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .executes(context -> employeeStatus(context.getSource(),
                                        StringArgumentType.getString(context, "employee")))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .executes(context -> employeeCancel(context.getSource(),
                                        StringArgumentType.getString(context, "employee")))))
                .then(Commands.literal("persist")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .executes(context -> employeePersist(context.getSource(),
                                        StringArgumentType.getString(context, "employee")))))
                .then(Commands.literal("move")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", BlockPosArgument.blockPos())
                                        .executes(context -> employeeMove(context.getSource(),
                                                StringArgumentType.getString(context, "employee"),
                                                BlockPosArgument.getLoadedBlockPos(context, "target"))))))
                .then(Commands.literal("work")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .executes(context -> employeeWork(context.getSource(),
                                                        StringArgumentType.getString(context, "employee"),
                                                        EntityArgument.getEntity(context, "target"),
                                                        StringArgumentType.getString(context, "type")))))))
                .then(Commands.literal("suppress")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> employeeSuppress(context.getSource(),
                                                StringArgumentType.getString(context, "employee"),
                                                EntityArgument.getEntity(context, "target"))))))
                .then(Commands.literal("follow")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> employeeFollow(context.getSource(),
                                                StringArgumentType.getString(context, "employee"),
                                                EntityArgument.getEntity(context, "target"))))))
                .then(Commands.literal("tool")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> employeeTool(context.getSource(),
                                                StringArgumentType.getString(context, "employee"),
                                                EntityArgument.getEntity(context, "target"))))))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("employee", StringArgumentType.word())
                                .executes(context -> employeeSpawn(context.getSource(),
                                        StringArgumentType.getString(context, "employee")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> abnormalityTree() {
        return Commands.literal("abnormality")
                .then(Commands.literal("list")
                        .executes(context -> abnormalityList(context.getSource(), 128))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 2048))
                                .executes(context -> abnormalityList(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("status")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> abnormalityStatus(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("register-check")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> abnormalityRegistration(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("qliphoth")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> abnormalityQliphoth(context.getSource(),
                                                EntityArgument.getEntity(context, "target"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(Commands.literal("escape")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> abnormalityEscape(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("return")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> abnormalityReturn(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("heal")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> abnormalityHeal(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> roomTree() {
        return Commands.literal("room")
                .then(Commands.literal("status")
                        .executes(context -> roomStatus(context.getSource(), null))
                        .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                .executes(context -> roomStatus(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "marker")))))
                .then(Commands.literal("scan")
                        .executes(context -> roomScan(context.getSource(), null))
                        .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                .executes(context -> roomScan(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "marker")))))
                .then(Commands.literal("bind")
                        .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> roomBind(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "marker"),
                                                EntityArgument.getEntity(context, "target"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                .executes(context -> roomClear(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "marker")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> elevatorTree() {
        return Commands.literal("elevator")
                .then(Commands.literal("status")
                        .executes(context -> elevatorStatus(context.getSource(), null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> elevatorStatus(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("route")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .executes(context -> elevatorRoute(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                StringArgumentType.getString(context, "direction"))))))
                .then(Commands.literal("try")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .executes(context -> elevatorTry(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                StringArgumentType.getString(context, "direction"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> facilityTree() {
        return Commands.literal("facility")
                .then(Commands.literal("status").executes(context -> facilityStatus(context.getSource())))
                .then(Commands.literal("requirements")
                        .executes(context -> facilityRequirementsSelf(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> facilityRequirements(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("next-day")
                        .executes(context -> facilityNextDay(context.getSource(), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(context -> facilityNextDay(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("energy")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(context -> facilityEnergy(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "value")))))
                .then(Commands.literal("gauge")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(context -> facilityGauge(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "value"))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                        .executes(context -> facilityGaugeAdd(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("trigger-stage")
                        .executes(context -> facilityTriggerStage(context.getSource())))
                .then(Commands.literal("stage")
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> facilityStageAdd(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("sync").executes(context -> facilitySync(context.getSource())));
    }

    private LiteralArgumentBuilder<CommandSourceStack> counterTree() {
        return Commands.literal("counter")
                .then(Commands.literal("status")
                        .executes(context -> counterStatusSelf(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> counterStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> counterSet(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> counterAdd(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("full")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> counterFull(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> ordealTree() {
        return Commands.literal("ordeal")
                .then(Commands.literal("status")
                        .executes(context -> ordealStatus(context.getSource())))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .executes(context -> ordealTrigger(context.getSource(),
                                        StringArgumentType.getString(context, "tier"), ""))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(context -> ordealTrigger(context.getSource(),
                                                StringArgumentType.getString(context, "tier"),
                                                StringArgumentType.getString(context, "color"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> suppressionTree() {
        return Commands.literal("suppression")
                .then(Commands.literal("status")
                        .executes(context -> suppressionStatus(context.getSource())))
                .then(Commands.literal("sync")
                        .executes(context -> suppressionSync(context.getSource())))
                .then(Commands.literal("start")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> suppressionStart(context.getSource(),
                                        StringArgumentType.getString(context, "type")))))
                .then(Commands.literal("end")
                        .then(Commands.argument("success", BoolArgumentType.bool())
                                .executes(context -> suppressionEnd(context.getSource(),
                                        BoolArgumentType.getBool(context, "success")))))
                .then(geburahSuppressionTree());
    }

    private LiteralArgumentBuilder<CommandSourceStack> geburahSuppressionTree() {
        return Commands.literal("geburah")
                .then(Commands.literal("status")
                        .executes(context -> geburahStatus(context.getSource())))
                .then(Commands.literal("damage")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.01F))
                                .executes(context -> geburahDamage(context.getSource(),
                                        FloatArgumentType.getFloat(context, "amount")))))
                .then(Commands.literal("trail")
                        .then(Commands.literal("play")
                                .then(Commands.argument("animation", StringArgumentType.word())
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                                .executes(context -> geburahTrailPlay(context.getSource(),
                                                        StringArgumentType.getString(context, "animation"),
                                                        FloatArgumentType.getFloat(context, "yaw"), 0.12F))
                                                .then(Commands.argument("speed", FloatArgumentType.floatArg(0.05F, 2.0F))
                                                        .executes(context -> geburahTrailPlay(context.getSource(),
                                                                StringArgumentType.getString(context, "animation"),
                                                                FloatArgumentType.getFloat(context, "yaw"),
                                                                FloatArgumentType.getFloat(context, "speed")))))))
                        .then(Commands.literal("clear")
                                .executes(context -> geburahTrailClear(context.getSource()))))
                .then(Commands.literal("set")
                        .then(Commands.literal("health")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> geburahSetFloat(context.getSource(), "health",
                                                FloatArgumentType.getFloat(context, "value")))))
                        .then(Commands.literal("posture")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 100.0F))
                                        .executes(context -> geburahSetFloat(context.getSource(), "posture",
                                                FloatArgumentType.getFloat(context, "value")))))
                        .then(Commands.literal("ego")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 100.0F))
                                        .executes(context -> geburahSetFloat(context.getSource(), "ego",
                                                FloatArgumentType.getFloat(context, "value")))))
                        .then(Commands.literal("phase")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> geburahSetPhase(context.getSource(),
                                                StringArgumentType.getString(context, "value")))))
                        .then(Commands.literal("ego_cooldown")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(context -> geburahSetInteger(context.getSource(),
                                                "ego_cooldown", IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.literal("combo_depth")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 6))
                                        .executes(context -> geburahSetInteger(context.getSource(),
                                                "combo_depth", IntegerArgumentType.getInteger(context, "value")))))
                        .then(Commands.literal("kill_bonus")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 5))
                                        .executes(context -> geburahSetInteger(context.getSource(),
                                                "kill_bonus", IntegerArgumentType.getInteger(context, "value")))))
                        .then(Commands.literal("cooldown")
                                .then(Commands.argument("move", StringArgumentType.word())
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                                .executes(context -> geburahSetCooldown(context.getSource(),
                                                        StringArgumentType.getString(context, "move"),
                                                        IntegerArgumentType.getInteger(context, "ticks")))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> departmentTree() {
        return Commands.literal("department")
                .then(Commands.literal("status")
                        .executes(context -> departmentStatus(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("department", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
                                        .executes(context -> departmentSet(context.getSource(),
                                                StringArgumentType.getString(context, "department"),
                                                IntegerArgumentType.getInteger(context, "level"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> egoTree() {
        return Commands.literal("ego")
                .then(Commands.literal("status")
                        .executes(context -> egoStatusSelf(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> egoStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "§6调试命令：§fstatus、work、employee、abnormality、room、elevator、facility、counter、ordeal、suppression、department、ego、performance"), false);
        source.sendSuccess(() -> Component.literal(
                "§7输入 /lobotocraft debug 后使用 Tab 查看所有分组与参数。"), false);
        return 1;
    }

    private int statusSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令需要玩家，或显式指定 player 参数。"));
            return 0;
        }
        return status(source, player);
    }

    private int status(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal("§6[状态] §f" + player.getGameProfile().getName()
                + " §7uuid=" + player.getUUID()), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§7位置=%s %.2f %.2f %.2f 维度=%s alive=%s removed=%s",
                player.level().dimension().location(), player.getX(), player.getY(), player.getZ(),
                player.isAlive(), player.isRemoved())), false);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats ->
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§7四维=%d/%d/%d/%d 等级=%d",
                        stats.getFortitude(), stats.getPrudence(), stats.getTemperance(),
                        stats.getJustice(), stats.getEmployeeLevel())), false));
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental ->
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§7精神=%.1f/%.1f 恐慌=%s",
                        mental.getMentalValue(), mental.getEffectiveMaxMentalValue(),
                        MentalValueUtil.isPanic(player))), false));
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(daily ->
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§7公司日=%d 工作=%d/%d 休息=%s",
                        daily.getCurrentDay(), daily.getTodayWorkCount(), daily.getRequiredWorkCount(),
                        daily.isHasSleep())), false));
        WorkManager.WorkSession session = WorkManager.getWorkSession(player);
        if (session == null) {
            source.sendSuccess(() -> Component.literal("§7工作会话=无"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7工作会话=" + session.abnormality.getAbnormalityName()
                    + " / " + session.workType.getName() + " " + session.extractionCount
                    + "/" + session.abnormality.getMaxPEOutput()), false);
        }
        if (player instanceof EmployeeServerPlayer employee) {
            EmployeeRecord record = employee.employeeRecord();
            source.sendSuccess(() -> Component.literal("§7AI任务=" + record.taskType()
                    + " " + record.taskSummary() + " 导航="
                    + EmployeeRuntimeManager.navigationPurpose(record.id())), false);
        }
        return 1;
    }

    private int workList(CommandSourceStack source) {
        int count = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            WorkManager.WorkSession session = WorkManager.getWorkSession(player);
            if (session == null) continue;
            count++;
            source.sendSuccess(() -> Component.literal("§7" + player.getGameProfile().getName()
                    + " -> " + session.abnormality.getAbnormalityName() + " / "
                    + session.workType.getName() + " boxes=" + session.extractionCount), false);
        }
        int total = count;
        source.sendSuccess(() -> Component.literal("§6正式工作会话数：§f" + total), false);
        return count;
    }

    private int startWork(CommandSourceStack source, ServerPlayer player,
                          Entity target, String typeId) {
        if (!(target instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        WorkType type = parseWorkType(typeId);
        if (type == null) {
            source.sendFailure(Component.literal("§c工作类型必须是 instinct、insight、attachment 或 repression。"));
            return 0;
        }
        if (!WorkManager.startWork(player, abnormality, type)) {
            source.sendFailure(Component.literal("§c正式工作启动失败，请查看服务端返回的具体原因。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已启动正式工作会话。"), true);
        return 1;
    }

    private int workStatus(CommandSourceStack source, ServerPlayer player) {
        WorkManager.WorkSession session = WorkManager.getWorkSession(player);
        if (session == null) {
            source.sendSuccess(() -> Component.literal("§7没有正式工作会话。"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6目标=%s uuid=%s 类型=%s 进度=%d/%d 当前tick=%d 间隔=%d 成功=%d 失败=%d 强制=%s 连续=%s",
                session.abnormality.getAbnormalityName(), session.abnormality.getUUID(),
                session.workType.getName(), session.extractionCount, session.abnormality.getMaxPEOutput(),
                session.currentTick, session.extractionInterval, session.successCount,
                session.failureCount, session.forcedEnd, session.continuousMode)), false);
        return 1;
    }

    private int workStatusSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令需要玩家，或显式指定 player 参数。"));
            return 0;
        }
        return workStatus(source, player);
    }

    private int cancelWork(CommandSourceStack source, ServerPlayer player) {
        if (WorkManager.getWorkSession(player) == null) {
            source.sendFailure(Component.literal("§c该玩家没有正式工作会话。"));
            return 0;
        }
        WorkManager.cancelWork(player, "调试命令取消工作");
        source.sendSuccess(() -> Component.literal("§a已取消工作会话。"), true);
        return 1;
    }

    private int forceComplete(CommandSourceStack source, ServerPlayer player, String resultId) {
        WorkResult result = parseResult(resultId);
        if (result == null) {
            source.sendFailure(Component.literal("§c结果必须是 good、normal 或 bad。"));
            return 0;
        }
        if (WorkManager.getWorkSession(player) == null) {
            source.sendFailure(Component.literal("§c该玩家没有正式工作会话。"));
            return 0;
        }
        WorkManager.forceCompleteWork(player, result, "调试命令强制完成");
        source.sendSuccess(() -> Component.literal("§a已请求强制完成，结果=" + result.name()), true);
        return 1;
    }

    private int stopContinuous(CommandSourceStack source, ServerPlayer player) {
        WorkManager.requestStopContinuousWork(player, "调试命令停止连续工作");
        source.sendSuccess(() -> Component.literal("§a已请求停止连续工作。"), false);
        return 1;
    }

    private int employeeList(CommandSourceStack source) {
        int count = 0;
        for (EmployeeRecord record : FacilityPersonnelData.get(source.getServer()).employees()) {
            count++;
            EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
            String runtime = employee == null ? "offline" : String.format(Locale.ROOT,
                    "%.1f %.1f %.1f / %s", employee.getX(), employee.getY(), employee.getZ(),
                    employee.serverLevel().dimension().location());
            source.sendSuccess(() -> Component.literal("§7" + record.name() + " [" + record.id()
                    + "] " + record.department().id() + " task=" + record.taskType() + " " + runtime), false);
        }
        int total = count;
        source.sendSuccess(() -> Component.literal("§6员工记录数：§f" + total), false);
        return count;
    }

    private int employeeStatus(CommandSourceStack source, String name) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
        source.sendSuccess(() -> Component.literal("§6员工 " + record.name() + " §7id=" + record.id()
                + " dept=" + record.department().id() + " task=" + record.taskType()
                + " summary=" + record.taskSummary()), false);
        if (employee == null) {
            source.sendSuccess(() -> Component.literal("§7运行实体：未部署或未加载"), false);
            return 1;
        }
        status(source, employee);
        return 1;
    }

    private int employeeCancel(CommandSourceStack source, String name) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!EmployeeRuntimeManager.cancelOrder(record, "调试命令取消 AI 任务")) {
            source.sendFailure(Component.literal("§c任务不存在，或员工已经正式工作/无法取消。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已取消 AI 待命令。"), true);
        return 1;
    }

    private int employeePersist(CommandSourceStack source, String name) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!EmployeeRuntimeManager.persistNow(record.id())) {
            source.sendFailure(Component.literal("§c员工未部署，无法立即持久化。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已保存员工运行时状态。"), false);
        return 1;
    }

    private int employeeMove(CommandSourceStack source, String name, BlockPos target) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!EmployeeRuntimeManager.assignMove(record, target)) {
            source.sendFailure(Component.literal("§c无法下达移动命令（正式工作会话不可打断）。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已从当前位置重新规划到 " + target.toShortString()), false);
        return 1;
    }

    private int employeeWork(CommandSourceStack source, String name, Entity target, String typeId) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!(target instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        WorkType type = parseWorkType(typeId);
        if (type == null || !EmployeeRuntimeManager.assignWork(record, abnormality, type)) {
            source.sendFailure(Component.literal(
                    "§c无法下达工作任务：检查登记、维度、正式会话、精神状态与 AI 占用。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已下达 AI 工作任务。"), false);
        return 1;
    }

    private int employeeSuppress(CommandSourceStack source, String name, Entity target) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!(target instanceof LivingEntity living)
                || !EmployeeRuntimeManager.assignSuppress(record, living)) {
            source.sendFailure(Component.literal("§c无法下达镇压任务。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已下达 AI 镇压任务。"), false);
        return 1;
    }

    private int employeeFollow(CommandSourceStack source, String name, Entity target) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!EmployeeRuntimeManager.assignFollow(record, target)) {
            source.sendFailure(Component.literal("§c无法下达跟随任务。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已下达 AI 跟随任务。"), false);
        return 1;
    }

    private int employeeTool(CommandSourceStack source, String name, Entity target) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (!(target instanceof AbstractAbnormality abnormality)
                || !EmployeeRuntimeManager.assignToolUse(record, abnormality)) {
            source.sendFailure(Component.literal("§c无法下达工具使用任务。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已下达 AI 工具使用任务。"), false);
        return 1;
    }

    private int employeeSpawn(CommandSourceStack source, String name) {
        EmployeeRecord record = findEmployee(source, name);
        if (record == null) return 0;
        if (record.retired()) {
            source.sendFailure(Component.literal("§c已死亡/退休的员工不能重新生成。"));
            return 0;
        }
        if (EmployeeRuntimeManager.activePlayer(record.id()) != null) {
            source.sendFailure(Component.literal("§c员工已经部署。"));
            return 0;
        }
        if (EmployeeRuntimeManager.spawn(source.getServer(), record) == null) {
            source.sendFailure(Component.literal("§c员工部署失败：检查保存维度和坐标。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已重新部署 AI 员工。"), true);
        return 1;
    }

    private int abnormalityList(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        AABB box = new AABB(center).inflate(radius);
        var entities = level.getEntitiesOfClass(AbstractAbnormality.class, box,
                entity -> entity.isAlive() && !entity.isRemoved()).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(source.getPosition())))
                .limit(128)
                .toList();
        for (AbstractAbnormality abnormality : entities) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "§7#%d %s code=%s uuid=%s pos=%s hp=%.1f/%.1f escaped=%s registered=%s",
                    abnormality.getId(), abnormality.getAbnormalityName(), abnormality.getAbnormalityCode(),
                    abnormality.getUUID(), abnormality.blockPosition(), abnormality.getHealth(),
                    abnormality.getMaxHealth(), abnormality.hasEscape(),
                    AbnormalityWorkRules.isRegistered(abnormality))), false);
        }
        source.sendSuccess(() -> Component.literal("§6找到异想体：§f" + entities.size()
                + "（最多显示128个）"), false);
        return entities.size();
    }

    private int abnormalityStatus(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6%s §7id=%d uuid=%s code=%s risk=%s type=%s",
                abnormality.getAbnormalityName(), abnormality.getId(), abnormality.getUUID(),
                abnormality.getAbnormalityCode(), abnormality.getRiskLevel().name(),
                abnormality.isToolType() ? "tool" : "normal")), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§7维度=%s pos=%s hp=%.1f/%.1f qliphoth=%d/%d escaped=%s registered=%s aiWorking=%s anyWorking=%s",
                abnormality.level().dimension().location(), abnormality.blockPosition(),
                abnormality.getHealth(), abnormality.getMaxHealth(), abnormality.getQliphothCounter(),
                abnormality.getMaxQliphothCounter(), abnormality.hasEscape(),
                AbnormalityWorkRules.isRegistered(abnormality),
                WorkManager.isAiEmployeeWorking(abnormality), WorkManager.isAbnormalityBeingWorked(abnormality))), false);
        return 1;
    }

    private int abnormalityRegistration(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6登记检查：§f"
                + (AbnormalityWorkRules.isRegistered(abnormality) ? "已登记" : "未登记")), false);
        source.sendSuccess(() -> Component.literal("§7home=" + abnormality.getHomeRoomDimension()
                + " marker=" + abnormality.getHomeRoomMarker() + " code=" + abnormality.getAbnormalityCode()), false);
        return 1;
    }

    private int abnormalityQliphoth(CommandSourceStack source, Entity entity, int value) {
        if (!(entity instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        abnormality.setQliphothCounter(value);
        source.sendSuccess(() -> Component.literal("§a逆卡巴拉计数器已设为 " + value
                + "（该命令不会自动触发出逃）。"), true);
        return 1;
    }

    private int abnormalityEscape(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        abnormality.triggerEscape();
        source.sendSuccess(() -> Component.literal("§a已调用正式出逃流程。"), true);
        return 1;
    }

    private int abnormalityReturn(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        abnormality.stopEscape();
        source.sendSuccess(() -> Component.literal("§a已调用正式回收流程。"), true);
        return 1;
    }

    private int abnormalityHeal(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        abnormality.setHealth(abnormality.getMaxHealth());
        source.sendSuccess(() -> Component.literal("§a异想体生命值已恢复。"), false);
        return 1;
    }

    private int roomStatus(CommandSourceStack source, BlockPos requested) {
        ServerLevel level = source.getLevel();
        BlockPos markerPos = requested == null ? playerPosition(source) : requested;
        if (markerPos == null) return 0;
        if (!(level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)) {
            source.sendFailure(Component.literal("§c该位置不是收容室信标。"));
            return 0;
        }
        UUID bound = marker.getBoundAbnormality();
        source.sendSuccess(() -> Component.literal("§6信标 " + markerPos + " §7bound=" + bound
                + " code=" + marker.getBoundCode() + " name=" + marker.getBoundName()
                + " radius=" + marker.getRadiusXZ() + "x" + marker.getRadiusY()), false);
        source.sendSuccess(() -> Component.literal("§7scanBox=" + marker.getScanBox()), false);
        return 1;
    }

    private int roomScan(CommandSourceStack source, BlockPos requested) {
        ServerLevel level = source.getLevel();
        BlockPos markerPos = requested == null ? playerPosition(source) : requested;
        if (markerPos == null) return 0;
        if (!(level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)) {
            source.sendFailure(Component.literal("§c该位置不是收容室信标。"));
            return 0;
        }
        marker.scan(level);
        source.sendSuccess(() -> Component.literal("§a已立即扫描收容室信标。"), false);
        return 1;
    }

    private int roomBind(CommandSourceStack source, BlockPos markerPos, Entity target) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)) {
            source.sendFailure(Component.literal("§c该位置不是收容室信标。"));
            return 0;
        }
        if (!(target instanceof AbstractAbnormality abnormality)) {
            source.sendFailure(Component.literal("§c目标不是异想体。"));
            return 0;
        }
        if (!marker.bindByUuid(level, abnormality.getUUID())) {
            source.sendFailure(Component.literal("§c绑定失败：目标不存在、维度不符或收容单元未开放。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已绑定 " + abnormality.getAbnormalityName()
                + " 到 " + markerPos), true);
        return 1;
    }

    private int roomClear(CommandSourceStack source, BlockPos markerPos) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)) {
            source.sendFailure(Component.literal("§c该位置不是收容室信标。"));
            return 0;
        }
        marker.clearBinding();
        source.sendSuccess(() -> Component.literal("§a已请求清除收容室登记。"), true);
        return 1;
    }

    private int elevatorStatus(CommandSourceStack source, BlockPos requested) {
        ServerLevel level = source.getLevel();
        BlockPos pos = requested == null ? playerPosition(source) : requested;
        if (pos == null) return 0;
        if (!(level.getBlockEntity(pos) instanceof ElevatorBlockEntity elevator)) {
            source.sendFailure(Component.literal("§c该位置不是电梯方块实体。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6电梯 " + pos + " §7up=" + elevator.isTeleportUp()
                + " distance=" + elevator.getTeleportDistance() + " animation="
                + elevator.getAnimationDuration()), false);
        return 1;
    }

    private int elevatorRoute(CommandSourceStack source, BlockPos pos, String direction) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(pos) instanceof ElevatorBlockEntity elevator)) {
            source.sendFailure(Component.literal("§c该位置不是电梯方块实体。"));
            return 0;
        }
        Boolean up = parseDirection(direction);
        if (up == null) {
            source.sendFailure(Component.literal("§c方向必须是 up 或 down。"));
            return 0;
        }
        BlockPos destination = elevator.findDestinationElevator(level, up);
        source.sendSuccess(() -> Component.literal("§6电梯路线：§f" + (destination == null
                ? "无可用目的电梯" : destination.toShortString())), false);
        return destination == null ? 0 : 1;
    }

    private int elevatorTry(CommandSourceStack source, BlockPos pos, String direction) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令只能由玩家执行。"));
            return 0;
        }
        if (!player.blockPosition().closerThan(pos, ElevatorBlockEntity.INTERACTION_RADIUS + 1.0D)) {
            source.sendFailure(Component.literal("§c玩家不在该电梯交互范围内。"));
            return 0;
        }
        if (!(player.level().getBlockEntity(pos) instanceof ElevatorBlockEntity elevator)) {
            source.sendFailure(Component.literal("§c该位置不是电梯方块实体。"));
            return 0;
        }
        Boolean up = parseDirection(direction);
        if (up == null) {
            source.sendFailure(Component.literal("§c方向必须是 up 或 down。"));
            return 0;
        }
        ElevatorBlockEntity.TravelResult result = elevator.tryTravel(player, up);
        source.sendSuccess(() -> Component.literal("§6电梯测试结果：§f" + result.name()), false);
        return result == ElevatorBlockEntity.TravelResult.SUCCESS ? 1 : 0;
    }

    private int facilityStatus(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FacilityDayData data = FacilityDayData.get(level);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6设施日=%d 能源=%d/%d 融毁等级=%d 进度=%d/%d 下一考验=%s 预定表=%s",
                data.getDay(), data.getEnergy(), data.getEnergyQuota(), data.getMeltdownLevel(),
                data.getMeltdownGauge(), data.getMeltdownThreshold(), data.getNextOrdeal(),
                data.isSchedulePrepared())), false);
        source.sendSuccess(() -> Component.literal("§7黎明触发=" + data.isDawnTriggered()
                + " 正午触发=" + data.isNoonTriggered() + " activeMeltdowns="
                + data.getActiveMeltdowns().size()), false);
        if (data.isSchedulePrepared()) {
            StringBuilder schedule = new StringBuilder("§7schedule:");
            for (int i = 0; i < 10; i++) schedule.append(' ').append(i + 1).append('=').append(data.getScheduledOrdeal(i));
            source.sendSuccess(() -> Component.literal(schedule.toString()), false);
        }
        return 1;
    }

    private int facilityRequirementsSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令需要玩家，或显式指定 player 参数。"));
            return 0;
        }
        return facilityRequirements(source, player);
    }

    private int facilityRequirements(CommandSourceStack source, ServerPlayer player) {
        FacilityDayData data = FacilityManager.syncDay(player.serverLevel(), player);
        var unmet = DayCompletionRequirements.unmetMessages(player, data);
        source.sendSuccess(() -> Component.literal("§6结束 DAY " + data.getDay()
                + " 条件：§f" + (unmet.isEmpty() ? "全部满足" : "尚有 " + unmet.size() + " 项未满足")), false);
        for (Component message : unmet) {
            source.sendSuccess(() -> Component.literal("§c- ").append(message), false);
        }
        return unmet.isEmpty() ? 1 : 0;
    }

    private int facilityNextDay(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();
        FacilityDayData data = FacilityDayData.get(level);
        int target = data.getDay() + count;
        FacilityManager.beginDay(level, target);
        source.sendSuccess(() -> Component.literal("§a设施已推进到 DAY " + target), true);
        return 1;
    }

    private int facilityEnergy(CommandSourceStack source, int value) {
        FacilityDayData data = FacilityDayData.get(source.getLevel());
        data.debugSetEnergy(value);
        FacilityManager.syncAll(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("§a能源已设为 " + data.getEnergy()
                + "/" + data.getEnergyQuota()), false);
        return 1;
    }

    private int facilityGauge(CommandSourceStack source, int value) {
        FacilityDayData data = FacilityDayData.get(source.getLevel());
        data.debugSetMeltdownGauge(value);
        FacilityManager.syncAll(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("§a融毁进度已设为 " + data.getMeltdownGauge()
                + "/" + data.getMeltdownThreshold()), false);
        return 1;
    }

    private int facilityGaugeAdd(CommandSourceStack source, int amount) {
        FacilityDayData data = FacilityDayData.get(source.getLevel());
        int before = data.getMeltdownGauge();
        int stages = FacilityManager.debugAdvanceGauge(source.getLevel(), amount);
        source.sendSuccess(() -> Component.literal("§a逆卡巴拉熔毁进度 +" + amount
                + "（" + before + " -> " + data.getMeltdownGauge() + "），阶段推进 " + stages + " 次"), true);
        return 1;
    }

    private int facilityTriggerStage(CommandSourceStack source) {
        boolean changed = FacilityManager.debugTriggerCurrentStage(source.getLevel());
        source.sendSuccess(() -> Component.literal(changed
                ? "§a已执行当前融毁阶段的正式触发流程。"
                : "§e触发流程已执行，但阶段未变化（可能没有可用目标或考验未完工）。"), true);
        return 1;
    }

    private int facilityStageAdd(CommandSourceStack source, int amount) {
        int stages = FacilityManager.debugAdvanceStages(source.getLevel(), amount);
        source.sendSuccess(() -> Component.literal("§a已请求推进逆卡巴拉熔毁阶段 " + amount
                + " 次，实际推进 " + stages + " 次"), true);
        return stages > 0 ? 1 : 0;
    }

    private int facilitySync(CommandSourceStack source) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            FacilityManager.sync(player);
        }
        source.sendSuccess(() -> Component.literal("§a已同步设施状态。"), false);
        return 1;
    }

    private int counterStatusSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令需要玩家，或显式指定 player 参数。"));
            return 0;
        }
        return counterStatus(source, player);
    }

    private int counterStatus(CommandSourceStack source, ServerPlayer player) {
        int value = SuppressorCounterUtil.get(player);
        int capacity = SuppressorCounterUtil.capacity(player);
        source.sendSuccess(() -> Component.literal("§6外出计数：§f" + value + "/" + capacity
                + " §7player=" + player.getGameProfile().getName()), false);
        return 1;
    }

    private int counterSet(CommandSourceStack source, ServerPlayer player, int value) {
        SuppressorCounterUtil.set(player, value);
        syncCounter(player);
        int actual = SuppressorCounterUtil.get(player);
        source.sendSuccess(() -> Component.literal("§a外出计数已设为 " + actual + "/"
                + SuppressorCounterUtil.capacity(player)
                + (actual == value ? "" : "（输入值已按当日上限截断）")), true);
        return 1;
    }

    private int counterAdd(CommandSourceStack source, ServerPlayer player, int amount) {
        int before = SuppressorCounterUtil.get(player);
        SuppressorCounterUtil.set(player, before + amount);
        syncCounter(player);
        int after = SuppressorCounterUtil.get(player);
        source.sendSuccess(() -> Component.literal("§a外出计数变化 " + (after - before)
                + "，当前 " + after + "/" + SuppressorCounterUtil.capacity(player)), true);
        return 1;
    }

    private int counterFull(CommandSourceStack source, ServerPlayer player) {
        SuppressorCounterUtil.restoreToFull(player);
        syncCounter(player);
        source.sendSuccess(() -> Component.literal("§a外出计数已回满："
                + SuppressorCounterUtil.get(player) + "/" + SuppressorCounterUtil.capacity(player)), true);
        return 1;
    }

    private int ordealStatus(CommandSourceStack source) {
        OrdealData data = OrdealData.get(source.getLevel());
        source.sendSuccess(() -> Component.literal("§6考验状态：§factive=" + data.hasActiveOrdeal()
                + " dawn=" + data.hasActiveDawn() + " noon=" + data.hasActiveMidday()
                + " late=" + data.hasActiveLateOrdeal()), false);
        source.sendSuccess(() -> Component.literal("§7黎明 triggers=" + data.getDawnTriggersToday()
                + " serial=" + data.getDawnTriggerSerialToday()
                + " completed=" + data.getDawnCompletionsToday()
                + " nextType=" + data.getNextDawnType()
                + " [crimson=" + data.isBloodDawnActive() + ", green=" + data.isGreenDawnActive()
                + ", violet=" + data.isVioletDawnActive() + ", amber=" + data.isAmberDawnActive()
                + ", trackedAmber=" + data.getTrackedAmberDawns().size() + "]"), false);
        source.sendSuccess(() -> Component.literal("§7正午 triggers=" + data.getMiddayTriggersToday()
                + " serial=" + data.getMiddayTriggerSerialToday()
                + " completed=" + data.getMiddayCompletionsToday()
                + " nextType=" + data.getNextMiddayType()
                + " [indigo=" + data.isBlueMiddayActive() + ", violet=" + data.isVioletMiddayActive()
                + ", green=" + data.isGreenMiddayActive() + ", crimson=" + data.isCrimsonMiddayActive()
                + "]"), false);
        source.sendSuccess(() -> Component.literal("§7黄昏/午夜 tier=" + data.getLateOrdealTier()
                + " color=" + data.getLateOrdealColor()
                + " tracked=" + data.getTrackedLateOrdeals().size()), false);
        return 1;
    }

    private int ordealTrigger(CommandSourceStack source, String tierName, String colorName) {
        OrdealTier tier = parseOrdealTier(tierName);
        if (tier == null || tier == OrdealTier.NONE) {
            source.sendFailure(Component.literal("§c考验类型必须是 dawn、noon、dusk 或 midnight。"));
            return 0;
        }
        int color = parseOrdealColor(tier, colorName);
        if (color < 0) {
            source.sendFailure(Component.literal("§c颜色与考验类型不匹配。黎明：amber/crimson/green/violet；"
                    + "正午：crimson/blue/violet/green。"));
            return 0;
        }
        boolean triggered = FacilityManager.debugTriggerOrdeal(source.getLevel(), tier, color);
        source.sendSuccess(() -> Component.literal(triggered
                ? "§a已执行 " + tier.name() + " 考验的正式触发流程。"
                : "§e未触发：当天未解锁、已有考验进行中，或生成位置不可用。"), true);
        return triggered ? 1 : 0;
    }

    private static OrdealTier parseOrdealTier(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "dawn", "黎明" -> OrdealTier.DAWN;
            case "noon", "正午" -> OrdealTier.NOON;
            case "dusk", "黄昏" -> OrdealTier.DUSK;
            case "midnight", "午夜" -> OrdealTier.MIDNIGHT;
            default -> null;
        };
    }

    private static int parseOrdealColor(OrdealTier tier, String value) {
        if (value == null || value.isBlank()) return 0;
        String color = value.toLowerCase(Locale.ROOT);
        if (tier == OrdealTier.DAWN) {
            return switch (color) {
                case "amber", "琥珀" -> 0;
                case "crimson", "猩红" -> 1;
                case "green", "绿色" -> 2;
                case "violet", "紫色" -> 3;
                default -> -1;
            };
        }
        if (tier == OrdealTier.NOON) {
            return switch (color) {
                case "crimson", "猩红" -> 0;
                case "blue", "indigo", "深蓝" -> 1;
                case "violet", "紫色" -> 2;
                case "green", "绿色" -> 3;
                default -> -1;
            };
        }
        return 0;
    }

    private int suppressionStatus(CommandSourceStack source) {
        CoreSuppressionData data = CoreSuppressionData.get(source.getLevel());
        if (!data.isActive()) {
            source.sendSuccess(() -> Component.literal("§6核心抑制：§7未进行 completedMask=0x"
                    + Integer.toHexString(data.getCompletedMask())), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§6核心抑制：§f" + data.getActiveType().getDisplayName()
                + " §7owner=" + data.getOwnerName() + "/" + data.getOwnerUuid()
                + " challengeDay=" + data.getChallengeDay()), false);
        source.sendSuccess(() -> Component.literal("§7黎明=" + data.getDawnCompleted() + "/"
                + CoreSuppressionManager.REQUIRED_DAWNS + " 正午=" + data.getMiddayCompleted() + "/"
                + CoreSuppressionManager.REQUIRED_MIDDAYS + " allRequirements="
                + CoreSuppressionManager.requirementsMet(source.getServer(), data)), false);
        return 1;
    }

    private int suppressionSync(CommandSourceStack source) {
        CoreSuppressionManager.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("§a已同步核心抑制状态。"), false);
        return 1;
    }

    private int suppressionStart(CommandSourceStack source, String typeName) {
        ServerPlayer player = source.getPlayer();
        CoreSuppressionType type = CoreSuppressionType.byId(typeName);
        if (player == null || type == null || !CoreSuppressionManager.debugStartChallenge(player, type)) {
            source.sendFailure(Component.literal("§c无法开始核心抑制：类型无效、未实装或已有挑战进行中。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§e已发起 " + type.getDisplayName()
                + " 强制核心抑制，正在等待全员视觉安全确认。"), true);
        return 1;
    }

    private int suppressionEnd(CommandSourceStack source, boolean success) {
        if (!CoreSuppressionManager.debugFinishChallenge(source.getServer(), success)) {
            source.sendFailure(Component.literal("§c当前没有进行中的核心抑制。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a核心抑制已按调试结果结束：" + (success ? "完成" : "失败")), true);
        return 1;
    }

    private int geburahStatus(CommandSourceStack source) {
        EntityGeburah geburah = activeGeburah(source);
        if (geburah == null) return 0;
        var state = geburah.combatState();
        var target = geburah.getTarget();
        source.sendSuccess(() -> Component.literal("§cGeburah §fphase=" + state.phase()
                + " uuid=" + geburah.getUUID()
                + " pos=" + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                geburah.getX(), geburah.getY(), geburah.getZ())
                + " target=" + (target == null ? "none" : target.getUUID())
                + " health=" + state.health() + "/" + state.maxHealth()
                + " posture=" + state.posture()
                + " postureWindow=" + state.postureDamageInWindow()
                + " ego=" + state.ego()
                + " egoCooldown=" + state.egoExhaustionTicks()
                + " egoKillBonus=" + geburah.debugEgoKillBonus()
                + " action=" + geburah.debugAction()
                + " comboDepth=" + geburah.debugComboDepth()
                + " willCooldown=" + geburah.debugCooldown("willofbattle")
                + " egoWillCooldown=" + geburah.debugCooldown("ego_willofbattle")
                + " bloodMistCooldown=" + geburah.debugCooldown("bloodmist")
                + " egoBloodMistCooldown=" + geburah.debugCooldown("egobloodmist")
                + " bodiesCooldown=" + geburah.debugCooldown("bodieseverywhere")), false);
        return 1;
    }

    private int geburahDamage(CommandSourceStack source, float amount) {
        EntityGeburah geburah = activeGeburah(source);
        if (geburah == null) return 0;
        if (!geburah.applyDebugDamage(amount)) {
            source.sendFailure(Component.literal("§c调试伤害未能进入 Geburah 伤害流程。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已通过真实伤害入口施加 " + amount + " 点伤害。"), true);
        return 1;
    }

    private int geburahTrailPlay(CommandSourceStack source, String animation,
                                 float yaw, float speed) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This debug command requires a player."));
            return 0;
        }
        if (!EntityGeburah.isDebugTrailAnimation(animation)) {
            source.sendFailure(Component.literal("Unknown trail animation: " + animation));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        EntityGeburah geburah = findTrailTestEntity(level, player);
        if (geburah == null) {
            geburah = ModEntities.geburah.get().create(level);
            if (geburah == null) {
                source.sendFailure(Component.literal("Unable to create Geburah test entity."));
                return 0;
            }
            geburah.addTag(GEBURAH_TRAIL_TEST_TAG);
            level.addFreshEntity(geburah);
        }
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-6D) look = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 position = player.position().add(look.normalize().scale(5.0D));
        geburah.moveTo(position.x, player.getY(), position.z, yaw, 0.0F);
        geburah.debugPlayTrailAnimation(animation, yaw, speed);
        source.sendSuccess(() -> Component.literal("Geburah trail test: animation="
                + animation + " yaw=" + yaw + " speed=" + speed), false);
        return 1;
    }

    private int geburahTrailClear(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This debug command requires a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        int removed = 0;
        for (EntityGeburah geburah : level.getEntitiesOfClass(EntityGeburah.class,
                player.getBoundingBox().inflate(256.0D),
                entity -> entity.getTags().contains(GEBURAH_TRAIL_TEST_TAG))) {
            geburah.debugClearTrailAnimation();
            geburah.discard();
            removed++;
        }
        int count = removed;
        source.sendSuccess(() -> Component.literal("Removed " + count
                + " Geburah trail test entities."), false);
        return removed > 0 ? 1 : 0;
    }

    private EntityGeburah findTrailTestEntity(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(EntityGeburah.class,
                        player.getBoundingBox().inflate(256.0D),
                        entity -> entity.getTags().contains(GEBURAH_TRAIL_TEST_TAG))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private int geburahSetFloat(CommandSourceStack source, String field, float value) {
        EntityGeburah geburah = activeGeburah(source);
        if (geburah == null) return 0;
        switch (field) {
            case "health" -> geburah.debugSetHealth(value);
            case "posture" -> geburah.debugSetPosture(value);
            case "ego" -> geburah.debugSetEgo(value);
            default -> {
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal("§a已设置 Geburah " + field + "=" + value), true);
        return 1;
    }

    private int geburahSetPhase(CommandSourceStack source, String value) {
        EntityGeburah geburah = activeGeburah(source);
        if (geburah == null) return 0;
        GeburahPhase phase;
        try {
            phase = GeburahPhase.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("§c阶段必须是 normal、ego 或 exhausted。"));
            return 0;
        }
        if (phase == GeburahPhase.DEFEATED) {
            source.sendFailure(Component.literal("§c请使用 damage 测试最终击败流程。"));
            return 0;
        }
        geburah.debugSetPhase(phase);
        source.sendSuccess(() -> Component.literal("§a已设置 Geburah phase=" + phase), true);
        return 1;
    }

    private int geburahSetInteger(CommandSourceStack source, String field, int value) {
        EntityGeburah geburah = activeGeburah(source);
        if (geburah == null) return 0;
        if ("ego_cooldown".equals(field)) {
            geburah.debugSetEgoCooldown(value);
        } else if ("combo_depth".equals(field)) {
            geburah.debugSetComboDepth(value);
        } else if ("kill_bonus".equals(field)) {
            geburah.debugSetEgoKillBonus(value);
        } else {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已设置 Geburah " + field + "=" + value), true);
        return 1;
    }

    private int geburahSetCooldown(CommandSourceStack source, String move, int ticks) {
        EntityGeburah geburah = activeGeburah(source);
        if (geburah == null) return 0;
        geburah.debugSetCooldown(move, ticks);
        source.sendSuccess(() -> Component.literal("§a已设置 Geburah cooldown " + move + "=" + ticks), true);
        return 1;
    }

    private EntityGeburah activeGeburah(CommandSourceStack source) {
        EntityGeburah geburah = GeburahEncounterService.INSTANCE.getActiveEntity(source.getServer());
        if (geburah == null) {
            source.sendFailure(Component.literal("§c当前没有有效的 Geburah 遭遇实体。"));
        }
        return geburah;
    }

    private int departmentStatus(CommandSourceStack source) {
        DepartmentProgressData data = DepartmentProgressData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("§6部门扩建：§7pendingDay=" + data.pendingDay()
                + " lastDay=" + data.lastExpandedDay()
                + " last=" + data.lastExpandedDepartment().id()), false);
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            source.sendSuccess(() -> Component.literal("§7" + department.id()
                    + " level=" + data.level(department)
                    + " open=" + data.isOpen(department)
                    + " employeeCapacity=" + data.employeeCapacity(department, 0)
                    + " containmentCapacity=" + data.containmentSlotCapacity(department, 0)
                    + " eligible=" + data.canExpand(department)), false);
        }
        return 1;
    }

    private int departmentSet(CommandSourceStack source, String id, int level) {
        EmployeeDepartment department = EmployeeDepartment.byId(id.toLowerCase(Locale.ROOT));
        if (department == EmployeeDepartment.UNASSIGNED) {
            source.sendFailure(Component.literal("§c未知或不可设置的部门：" + id));
            return 0;
        }
        DepartmentProgressData data = DepartmentProgressData.get(source.getServer());
        if (!data.setLevel(department, level)) {
            source.sendFailure(Component.literal("§c等级无效；控制部不能设为 0，范围为 0..5。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a已将 " + department.displayName()
                + " 设置为 LEVEL " + data.level(department)
                + "；区块与访问状态将在下一次服务端刷新时更新。"), true);
        return 1;
    }

    private int egoStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal("§6E.G.O 状态：§f" + player.getGameProfile().getName()), false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HAND && slot != EquipmentSlot.CHEST
                    && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET
                    && slot != EquipmentSlot.HEAD) continue;
            var stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String requirements = stack.getItem() instanceof IEgoLevelItem ego
                    ? " req=" + ego.getRequiredLevels() : "";
            source.sendSuccess(() -> Component.literal("§7" + slot.getName() + "="
                    + stack.getHoverName().getString() + " projection="
                    + EgoLoadoutService.isProjection(stack) + requirements), false);
        }
        AiEgoRepositoryData repository = AiEgoRepositoryData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("§7AI分配 weapon="
                + repository.assignedCode(player.getUUID(), "weapon") + " armor="
                + repository.assignedCode(player.getUUID(), "armor")), false);
        return 1;
    }

    private int egoStatusSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该命令需要玩家，或显式指定 player 参数。"));
            return 0;
        }
        return egoStatus(source, player);
    }

    private int performancePath(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "§6客户端性能日志：§flogs/lobotocraft-client-performance.log"), false);
        source.sendSuccess(() -> Component.literal(
                "§7阈值：100ms=SLOW，250ms=SEVERE，1000ms=FREEZE；请在发生卡死后提交该文件。"), false);
        return 1;
    }

    private static void syncCounter(ServerPlayer player) {
        MessageLoader.getLoader().sendToPlayer(player, new SuppressorCountSyncPacket(
                SuppressorCounterUtil.get(player), SuppressorCounterUtil.capacity(player)));
    }

    private static EmployeeRecord findEmployee(CommandSourceStack source, String name) {
        EmployeeRecord record = FacilityPersonnelData.get(source.getServer()).findByName(name);
        if (record == null) source.sendFailure(Component.literal("§c找不到 AI 员工：" + name));
        return record;
    }

    private static WorkResult parseResult(String value) {
        for (WorkResult result : WorkResult.values()) {
            if (result.name().equalsIgnoreCase(value)
                    || result.getDisplayName().equals(value)) return result;
        }
        return null;
    }

    private static WorkType parseWorkType(String value) {
        for (WorkType type : WorkType.values()) {
            if (type.name().equalsIgnoreCase(value)
                    || type.getName().equalsIgnoreCase(value)
                    || type.getDisplayName().equals(value)) return type;
        }
        return null;
    }

    private static Boolean parseDirection(String value) {
        if ("up".equalsIgnoreCase(value) || "上".equals(value)) return true;
        if ("down".equalsIgnoreCase(value) || "下".equals(value)) return false;
        return null;
    }

    private static BlockPos playerPosition(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c控制台执行时必须显式提供方块坐标。"));
            return null;
        }
        return player.blockPosition();
    }
}
