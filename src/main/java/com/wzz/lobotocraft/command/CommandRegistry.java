package com.wzz.lobotocraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.wzz.lobotocraft.debug.prototype.queenlaser.QueenLaserPrototypeCommand;
import com.wzz.lobotocraft.debug.prototype.queenstar.QueenStarPrototypeCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;

public class CommandRegistry {
    
    private static final List<ICommand> COMMANDS = new ArrayList<>();
    
    static {
        registerCommand(new MentalValueCommand());
        registerCommand(new ResistanceCommand());
        registerCommand(new AttackCommand());
        registerCommand(new KillCommand());
        registerCommand(new EmployeeStatsCommand());
        registerCommand(new TestCommand());
        registerCommand(new TeleportToEntityCommand());
        registerCommand(new CompleteDailyWorkCommand());
        registerCommand(new CompleteCoreSuppressionCommand());
        registerCommand(new ReloadCommand());
        registerCommand(new EmployeeCommand());
        registerCommand(new LlmConfigCommand());
        registerCommand(new EgoAuthorizationCommand());
        registerCommand(new ExhibitionCommand());
        registerCommand(new DebugCommand());
        registerCommand(new QueenLaserPrototypeCommand());
        registerCommand(new QueenStarPrototypeCommand());
    }
    
    /**
     * 注册一个指令
     */
    private static void registerCommand(ICommand command) {
        COMMANDS.add(command);
    }
    
    /**
     * 将所有指令注册到游戏中
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var lobotocraftCommand = Commands.literal("lobotocraft")
                .requires(source -> source.hasPermission(2));
        for (ICommand command : COMMANDS) {
            lobotocraftCommand.then(command.build());
        }
        dispatcher.register(lobotocraftCommand);
        dispatcher.register(new ExhibitionCommand().build()
                .requires(source -> source.hasPermission(2)));
    }
}
