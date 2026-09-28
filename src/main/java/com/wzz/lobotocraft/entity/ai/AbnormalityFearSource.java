package com.wzz.lobotocraft.entity.ai;

import net.minecraft.server.level.ServerPlayer;

/** Supplies the original-style horror result for an employee's first encounter. */
public interface AbnormalityFearSource {
    AbnormalityFearResponse encounterFear(ServerPlayer employee);
}
