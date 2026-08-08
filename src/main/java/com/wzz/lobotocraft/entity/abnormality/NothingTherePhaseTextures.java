package com.wzz.lobotocraft.entity.abnormality;

final class NothingTherePhaseTextures {
    private NothingTherePhaseTextures() {
    }

    static String forPhase(int phase) {
        return switch (phase) {
            case 1 -> "nothings_egg";
            case 2 -> "no_things_have_man";
            default -> "no_things_have";
        };
    }
}
