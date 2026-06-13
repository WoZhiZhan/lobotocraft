package com.wzz.lobotocraft.integration.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade客户端组件提供器
 * 在tooltip中显示异想体信息
 */
public enum AbnormalityComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        
        // 显示异想体编号和名称
        if (serverData.contains("AbnormalityCode") && serverData.contains("AbnormalityJadeName")) {
            String code = serverData.getString("AbnormalityCode");
            String name = serverData.getString("AbnormalityJadeName");
            String riskLevel = serverData.getString("RiskLevel");
            int riskColorValue = serverData.getInt("RiskLevelColor");
            TextColor riskTextColor = TextColor.fromRgb(riskColorValue);

            tooltip.add(Component.literal("[" + code + "] ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(name)
                            .withStyle(style -> style.withColor(riskTextColor)))
                    .append(Component.literal(" (" + riskLevel + ")")
                            .withStyle(ChatFormatting.GRAY)));
        }
        
        // 显示逆卡巴拉计数器（仅当不是工具类异想体时）
        if (serverData.contains("QliphothCounter") && serverData.contains("MaxQliphothCounter")) {
            int current = serverData.getInt("QliphothCounter");
            int max = serverData.getInt("MaxQliphothCounter");
            
            // 如果最大值为0，说明是工具类异想体，不显示计数器
            if (max > 0) {
                // 根据计数器状态设置颜色
                ChatFormatting counterColor;
                if (current == 0) {
                    counterColor = ChatFormatting.RED; // 危险！
                } else if (current == 1) {
                    counterColor = ChatFormatting.YELLOW; // 警告
                } else {
                    counterColor = ChatFormatting.GREEN; // 安全
                }
                if (max == 114514) {
                    tooltip.add(Component.translatable("config.jade.plugin_lobotocraft.qliphoth_counter")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("无")
                                    .withStyle(ChatFormatting.GREEN)));
                } else {
                    tooltip.add(Component.translatable("config.jade.plugin_lobotocraft.qliphoth_counter")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(current + " / " + max)
                                    .withStyle(counterColor)));
                }
            }
        }
        
        // 显示出逃能力
        if (serverData.contains("CanEscape")) {
            if (serverData.getBoolean("CanEscape")) {
                tooltip.add(Component.literal("类型: 出逃类异想体")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.literal("类型: 不会出逃类异想体")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return LobotocraftJadePlugin.QLIPHOTH_COUNTER;
    }
}