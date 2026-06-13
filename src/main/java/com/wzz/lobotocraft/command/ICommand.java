package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * 模组指令接口
 * 所有实现此接口的指令都会自动注册到 /lobotocraft 命令下
 */
public interface ICommand {
    
    /**
     * 构建指令的子节点
     * 例如: mentalvalue, resistance, attack 等
     * 
     * @return 指令的字面量构建器
     */
    LiteralArgumentBuilder<CommandSourceStack> build();
    
    /**
     * 获取指令名称
     * 例如: "mentalvalue", "resistance", "attack"
     * 
     * @return 指令名称
     */
    String getName();
}