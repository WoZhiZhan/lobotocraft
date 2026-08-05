package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoAuthorizationService;
import com.wzz.lobotocraft.ego.EgoItemSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EgoAuthorizationCommand implements ICommand {
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .then(Commands.literal("grant-all")
                        .executes(context -> grantAll(context.getSource(),
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> grantAll(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("ai-max")
                        .executes(context -> maximizeAi(context.getSource())));
    }

    @Override
    public String getName() {
        return "ego";
    }

    private int grantAll(CommandSourceStack source, ServerPlayer target) {
        ListCounter counter = new ListCounter();
        target.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
            for (EgoAuthorizationService.Definition definition
                    : EgoAuthorizationService.allOrdinaryEgos(source.getServer())) {
                if (!definition.weapons().isEmpty()) {
                    counter.total++;
                    if (data.researchPersonalEgoSpecs(definition.abnormalityCode(), "weapon",
                            EgoItemSpec.fromStacks(definition.weapons()))) counter.added++;
                }
                if (!definition.armor().isEmpty()) {
                    counter.total++;
                    if (data.researchPersonalEgoSpecs(definition.abnormalityCode(), "armor",
                            EgoItemSpec.fromStacks(definition.armor()))) counter.added++;
                }
            }
        });
        source.sendSuccess(() -> Component.literal("已为 " + target.getGameProfile().getName()
                + " 授予全部普通 E.G.O 使用权限（新增 " + counter.added
                + "，总计 " + counter.total + " 类）。"), true);
        return counter.total;
    }

    private int maximizeAi(CommandSourceStack source) {
        AiEgoRepositoryData repository = AiEgoRepositoryData.get(source.getServer());
        int types = 0;
        int added = 0;
        for (EgoAuthorizationService.Definition definition
                : EgoAuthorizationService.allOrdinaryEgos(source.getServer())) {
            if (!definition.weapons().isEmpty()) {
                types++;
                added += repository.researchToMaximum(definition.abnormalityCode(), "weapon",
                        definition.weapons(), definition.weaponMaximum());
            }
            if (!definition.armor().isEmpty()) {
                types++;
                added += repository.researchToMaximum(definition.abnormalityCode(), "armor",
                        definition.armor(), definition.armorMaximum());
            }
        }
        int finalTypes = types;
        int finalAdded = added;
        source.sendSuccess(() -> Component.literal("全部普通 E.G.O 已获得 AI 许可并达到研发上限（"
                + finalTypes + " 类，新增库存 " + finalAdded + "）。"), true);
        return types;
    }

    private static final class ListCounter {
        private int total;
        private int added;
    }
}
