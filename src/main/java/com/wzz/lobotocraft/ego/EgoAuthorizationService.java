package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Enumerates every registered ordinary E.G.O definition, including unloaded abnormalities. */
public final class EgoAuthorizationService {
    private EgoAuthorizationService() {
    }

    public static List<Definition> allOrdinaryEgos(MinecraftServer server) {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        ModEntities.ENTITIES.getEntries().forEach(registryObject -> {
            Entity entity = null;
            try {
                entity = registryObject.get().create(server.overworld());
                if (!(entity instanceof IAbnormality abnormality)) return;
                String code = abnormality.getAbnormalityCode();
                if (code == null || code.isBlank() || definitions.containsKey(code)) return;
                List<ItemStack> weapons = ordinaryCopies(abnormality.getEGOWeaponStacks());
                List<ItemStack> armor = ordinaryCopies(abnormality.getEGOArmorStacks());
                if (weapons.isEmpty() && armor.isEmpty()) return;
                definitions.put(code, new Definition(code, weapons, armor,
                        abnormality.getWeaponDevelopmentMaxCount(),
                        abnormality.getArmorDevelopmentMaxCount()));
            } catch (RuntimeException exception) {
                ModLogger.LOGGER.warn("无法枚举实体 {} 的 E.G.O 定义",
                        registryObject.getId(), exception);
            }
        });
        return List.copyOf(definitions.values());
    }

    public static Definition findOrdinaryEgo(MinecraftServer server, String abnormalityCode) {
        if (server == null || abnormalityCode == null || abnormalityCode.isBlank()) return null;
        return allOrdinaryEgos(server).stream()
                .filter(definition -> abnormalityCode.equals(definition.abnormalityCode()))
                .findFirst()
                .orElse(null);
    }

    /** Grants a suppression reward to the credited player and unlocks the same E.G.O for all AI. */
    public static SuppressionGrantResult grantSuppressionReward(ServerPlayer player,
                                                                 String abnormalityCode) {
        if (player == null) return SuppressionGrantResult.missingDefinition();
        return grantSuppressionReward(player.server, player, abnormalityCode);
    }

    /** AI suppression has no personal owner, but still grants the facility-wide AI research. */
    public static SuppressionGrantResult grantSuppressionReward(MinecraftServer server,
                                                                 @Nullable ServerPlayer player,
                                                                 String abnormalityCode) {
        if (server == null) return SuppressionGrantResult.missingDefinition();
        Definition definition = findOrdinaryEgo(server, abnormalityCode);
        if (definition == null) {
            ModLogger.LOGGER.warn("Suppression reward has no ordinary E.G.O definition for {}",
                    abnormalityCode);
            return SuppressionGrantResult.missingDefinition();
        }

        boolean[] personalCapability = {false};
        int[] personalUnlocks = {0};
        if (player != null) {
            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
                personalCapability[0] = true;
                if (!definition.weapons().isEmpty() && data.researchPersonalEgoSpecs(
                        definition.abnormalityCode(), "weapon", EgoItemSpec.fromStacks(definition.weapons()))) {
                    personalUnlocks[0]++;
                }
                if (!definition.armor().isEmpty() && data.researchPersonalEgoSpecs(
                        definition.abnormalityCode(), "armor", EgoItemSpec.fromStacks(definition.armor()))) {
                    personalUnlocks[0]++;
                }
            });
        }

        AiEgoRepositoryData repository = AiEgoRepositoryData.get(server);
        int aiResearchAdded = 0;
        if (!definition.weapons().isEmpty()) {
            aiResearchAdded += repository.researchToMaximum(definition.abnormalityCode(), "weapon",
                    definition.weapons(), definition.weaponMaximum());
        }
        if (!definition.armor().isEmpty()) {
            aiResearchAdded += repository.researchToMaximum(definition.abnormalityCode(), "armor",
                    definition.armor(), definition.armorMaximum());
        }
        return new SuppressionGrantResult(true, personalCapability[0], personalUnlocks[0],
                aiResearchAdded);
    }

    private static List<ItemStack> ordinaryCopies(List<ItemStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : source) {
            if (stack != null && !stack.isEmpty() && !SpecialEgoCatalog.isSpecialItem(stack)) {
                result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    public record Definition(String abnormalityCode, List<ItemStack> weapons,
                             List<ItemStack> armor, int weaponMaximum, int armorMaximum) {
        public Definition {
            weapons = copy(weapons);
            armor = copy(armor);
        }

        private static List<ItemStack> copy(List<ItemStack> source) {
            return source.stream().map(ItemStack::copy).toList();
        }
    }

    public record SuppressionGrantResult(boolean definitionFound, boolean personalCapabilityFound,
                                         int personalUnlocks, int aiResearchAdded) {
        private static SuppressionGrantResult missingDefinition() {
            return new SuppressionGrantResult(false, false, 0, 0);
        }
    }
}
