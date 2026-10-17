package com.wzz.lobotocraft.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.wzz.lobotocraft.combat.DamageCalculation.*;

class DamageCalculationTest {
    private Result resolve(Color color, float amount, double resistance, boolean mental) {
        return DamageCalculation.resolve(new Input(color, amount, resistance, mental, 1, 0,
                Float.POSITIVE_INFINITY, false, false));
    }
    @Test void fourColorsUseOneResistanceAndKeepTheirChannels() {
        assertEquals(new Result(5,0,0,0), resolve(Color.RED,10,.5,true));
        assertEquals(new Result(0,5,0,0), resolve(Color.WHITE,10,.5,true));
        assertEquals(new Result(5,5,0,0), resolve(Color.BLACK,10,.5,true));
        assertEquals(new Result(5,0,0,0), resolve(Color.PALE,10,.5,true));
        assertEquals(new Result(5,0,0,0), resolve(Color.WHITE,10,.5,false));
    }
    @Test void negativeResistanceRestoresCorrespondingChannelsExactlyOnce() {
        assertEquals(new Result(0,0,0,20), resolve(Color.WHITE,10,-2,true));
        assertEquals(new Result(0,0,5,5), resolve(Color.BLACK,10,-.5,true));
        assertEquals(new Result(0,0,5,0), resolve(Color.RED,10,-.5,false));
        assertEquals(new Result(0,0,0,0), resolve(Color.PALE,10,0,true));
    }
    @Test void shieldAndPanicRecoveryAreNotDamageThenUndo() {
        assertEquals(new Result(10,0,0,0), DamageCalculation.resolve(
                new Input(Color.BLACK,10,1,true,1,0,100,false,true)));
        assertEquals(new Result(0,0,0,10), DamageCalculation.resolve(
                new Input(Color.WHITE,10,1,true,1,0,100,true,false)));
    }
    @Test void paleKeepsExistingPercentComponentAndFinalCap() {
        assertEquals(new Result(11,0,0,0), DamageCalculation.resolve(
                new Input(Color.PALE,10,.5,true,1,6,100,false,false)));
        assertEquals(new Result(6,6,0,0), DamageCalculation.resolve(
                new Input(Color.BLACK,10,1,true,2,0,6,false,false)));
        assertEquals(new Result(6,0,0,0), DamageCalculation.resolve(
                new Input(Color.PALE,10,1,true,1,6,6,false,false)));
    }
    @Test void invalidAmountsNeverPoisonHealthOrMental() {
        assertEquals(new Result(0,0,0,0), resolve(Color.BLACK,Float.NaN,1,true));
        assertEquals(new Result(0,0,0,0), resolve(Color.WHITE,-1,1,true));
        assertEquals(new Result(10,0,0,0), resolve(Color.RED,10,Double.NaN,true));
        assertEquals(Float.MAX_VALUE, resolve(Color.RED,Float.MAX_VALUE,10,false).healthDamage(),
                "finite overflow must saturate, not become zero damage");
    }
}
