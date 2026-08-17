package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahCombatState {
    public static final float DEFAULT_MAX_HEALTH = 6000.0F;
    public static final float MAX_POSTURE = 100.0F;
    public static final float MAX_EGO = 100.0F;
    public static final float COMBO_POSTURE_COST = 30.0F;
    public static final float POSTURE_DAMAGE_RATIO = 0.25F;
    public static final float MAX_POSTURE_DAMAGE_PER_ATTACK = 6.0F;
    public static final int POSTURE_DAMAGE_WINDOW_TICKS = 20;
    public static final float MAX_POSTURE_DAMAGE_PER_WINDOW = 8.0F;
    public static final int POSTURE_BREAK_TICKS = 8 * 20;
    public static final int POSTURE_PROTECTION_TICKS = 2 * 20;
    public static final int EGO_EXHAUSTION_TICKS = 75 * 20;

    private final float maxHealth;
    private float health;
    private float posture = MAX_POSTURE;
    private float ego;
    private GeburahPhase phase = GeburahPhase.NORMAL;
    private int postureBreakTicks;
    private int postureProtectionTicks;
    private int egoExhaustionTicks;
    private int egoDrainClock;
    private int egoPostureClock;
    private final float[] postureDamageHistory = new float[POSTURE_DAMAGE_WINDOW_TICKS];
    private int postureDamageHistoryIndex;
    private float postureDamageInWindow;

    public GeburahCombatState() {
        this(DEFAULT_MAX_HEALTH);
    }

    public GeburahCombatState(float maxHealth) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
            throw new IllegalArgumentException("maxHealth must be finite and positive");
        }
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public DamageResult applyHealthDamage(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F || phase == GeburahPhase.DEFEATED) {
            return new DamageResult(0.0F, phase, phase, false);
        }

        GeburahPhase previousPhase = phase;
        float applied = Math.min(health, amount);
        health -= applied;
        if (health > 0.0F) {
            return new DamageResult(applied, previousPhase, phase, false);
        }

        if (previousPhase == GeburahPhase.NORMAL) {
            phase = GeburahPhase.EGO;
            health = maxHealth * 0.5F;
            ego = MAX_EGO;
            egoDrainClock = 0;
            egoPostureClock = 0;
            return new DamageResult(applied, previousPhase, phase, false);
        }

        phase = GeburahPhase.DEFEATED;
        health = 0.0F;
        return new DamageResult(applied, previousPhase, phase, true);
    }

    public float applyPostureDamage(float healthDamage, GeburahActionWindow window) {
        if (!Float.isFinite(healthDamage) || healthDamage <= 0.0F || window == null
                || phase == GeburahPhase.DEFEATED || postureBreakTicks > 0
                || postureProtectionTicks > 0) {
            return 0.0F;
        }
        float calculated = Math.min(MAX_POSTURE_DAMAGE_PER_ATTACK,
                healthDamage * POSTURE_DAMAGE_RATIO * window.postureMultiplier());
        float remainingBudget = Math.max(0.0F,
                MAX_POSTURE_DAMAGE_PER_WINDOW - postureDamageInWindow);
        float damage = Math.min(calculated, remainingBudget);
        damage = Math.min(posture, Math.max(0.0F, damage));
        if (damage <= 0.0F) return 0.0F;
        posture -= damage;
        postureDamageHistory[postureDamageHistoryIndex] += damage;
        postureDamageInWindow += damage;
        if (posture <= 0.0F) beginPostureBreak();
        return damage;
    }

    public void restorePosture(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F || phase == GeburahPhase.DEFEATED) return;
        posture = Math.min(MAX_POSTURE, posture + amount);
    }

    public void restorePostureFromAttackHits(int successfulHits) {
        if (successfulHits <= 0) return;
        float additionalPosture = Math.min(6.0F, (successfulHits - 1) * 2.0F);
        restorePosture(6.0F + additionalPosture);
    }

    public boolean spendComboPosture() {
        if (phase == GeburahPhase.DEFEATED || postureBreakTicks > 0
                || posture < COMBO_POSTURE_COST) return false;
        posture -= COMBO_POSTURE_COST;
        if (posture <= 0.0F) beginPostureBreak();
        return true;
    }

    public void refundCleanRecovery() {
        restorePosture(10.0F);
    }

    public void tick(boolean hasValidTarget) {
        advancePostureDamageWindow();
        if (postureBreakTicks > 0) {
            postureBreakTicks--;
            if (postureBreakTicks == 0) {
                posture = MAX_POSTURE;
                postureProtectionTicks = POSTURE_PROTECTION_TICKS;
            }
        } else if (postureProtectionTicks > 0) {
            postureProtectionTicks--;
        }

        if (phase == GeburahPhase.EGO) {
            tickEgo(hasValidTarget);
        } else if (phase == GeburahPhase.EXHAUSTED) {
            tickExhaustion();
        }
    }

    private void advancePostureDamageWindow() {
        postureDamageHistoryIndex = (postureDamageHistoryIndex + 1)
                % POSTURE_DAMAGE_WINDOW_TICKS;
        postureDamageInWindow = Math.max(0.0F,
                postureDamageInWindow - postureDamageHistory[postureDamageHistoryIndex]);
        postureDamageHistory[postureDamageHistoryIndex] = 0.0F;
    }

    private void tickEgo(boolean hasValidTarget) {
        egoPostureClock++;
        if (egoPostureClock >= 5 * 20) {
            egoPostureClock = 0;
            restorePosture(5.0F);
        }

        if (!hasValidTarget) return;
        egoDrainClock++;
        if (egoDrainClock < 2 * 20) return;
        egoDrainClock = 0;
        ego = Math.max(0.0F, ego - 1.0F);
        if (ego <= 0.0F) enterExhaustion();
    }

    private void tickExhaustion() {
        if (egoExhaustionTicks > 0) egoExhaustionTicks--;
        ego = MAX_EGO * (EGO_EXHAUSTION_TICKS - egoExhaustionTicks) / EGO_EXHAUSTION_TICKS;
        if (egoExhaustionTicks > 0) return;
        phase = GeburahPhase.EGO;
        ego = MAX_EGO;
        egoDrainClock = 0;
        egoPostureClock = 0;
    }

    private void enterExhaustion() {
        phase = GeburahPhase.EXHAUSTED;
        ego = 0.0F;
        egoExhaustionTicks = EGO_EXHAUSTION_TICKS;
        egoDrainClock = 0;
        egoPostureClock = 0;
    }

    private void beginPostureBreak() {
        posture = 0.0F;
        postureBreakTicks = POSTURE_BREAK_TICKS;
        postureProtectionTicks = 0;
        if (phase == GeburahPhase.EGO) {
            ego = Math.max(0.0F, ego - 10.0F);
            if (ego <= 0.0F) enterExhaustion();
        }
    }

    public void setHealth(float value) {
        if (!Float.isFinite(value)) return;
        health = Math.max(0.0F, Math.min(maxHealth, value));
    }

    public void setPosture(float value) {
        if (!Float.isFinite(value)) return;
        posture = Math.max(0.0F, Math.min(MAX_POSTURE, value));
        if (posture > 0.0F) {
            postureBreakTicks = 0;
            postureProtectionTicks = 0;
        }
    }

    public void setEgo(float value) {
        if (!Float.isFinite(value)) return;
        ego = Math.max(0.0F, Math.min(MAX_EGO, value));
    }

    public void setEgoExhaustionTicks(int ticks) {
        egoExhaustionTicks = Math.max(0, Math.min(EGO_EXHAUSTION_TICKS, ticks));
        if (phase == GeburahPhase.EXHAUSTED) {
            ego = MAX_EGO * (EGO_EXHAUSTION_TICKS - egoExhaustionTicks) / EGO_EXHAUSTION_TICKS;
        }
    }

    public void setPhase(GeburahPhase phase) {
        if (phase == null) return;
        this.phase = phase;
        egoDrainClock = 0;
        egoPostureClock = 0;
        if (phase == GeburahPhase.NORMAL) {
            ego = 0.0F;
            egoExhaustionTicks = 0;
        } else if (phase == GeburahPhase.EGO) {
            ego = MAX_EGO;
            egoExhaustionTicks = 0;
        } else if (phase == GeburahPhase.EXHAUSTED) {
            ego = 0.0F;
            egoExhaustionTicks = EGO_EXHAUSTION_TICKS;
        } else {
            health = 0.0F;
            egoExhaustionTicks = 0;
        }
    }

    public float resistance(String damageColor) {
        if (phase == GeburahPhase.EXHAUSTED) return 1.5F;
        if (phase == GeburahPhase.EGO) {
            return "blue".equalsIgnoreCase(damageColor) ? 0.3F : 0.2F;
        }
        return 0.6F;
    }

    public float maxHealth() {
        return maxHealth;
    }

    public float health() {
        return health;
    }

    public float posture() {
        return posture;
    }

    public float ego() {
        return ego;
    }

    public GeburahPhase phase() {
        return phase;
    }

    public int postureBreakTicks() {
        return postureBreakTicks;
    }

    public int postureProtectionTicks() {
        return postureProtectionTicks;
    }

    public int egoExhaustionTicks() {
        return egoExhaustionTicks;
    }

    public float postureDamageInWindow() {
        return postureDamageInWindow;
    }

    public record DamageResult(float appliedDamage, GeburahPhase previousPhase,
                               GeburahPhase currentPhase, boolean defeated) {
        public boolean changedPhase() {
            return previousPhase != currentPhase;
        }
    }
}
