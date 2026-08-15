package com.lobotocraft.facility.data;

import java.util.UUID;

/** Immutable terminal projection returned to lifecycle callers. */
public record MeltdownResolution(UUID target, FacilityDayData.ActiveMeltdown instance,
                                 MeltdownEndReason reason) {
}
