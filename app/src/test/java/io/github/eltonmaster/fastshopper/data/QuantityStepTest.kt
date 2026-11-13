package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityStepTest {
    @Test fun plusAddsOne() = assertEquals(3.0, QuantityStep.step(2.0, 1)!!, 0.0)
    @Test fun minusTakesOne() = assertEquals(2.0, QuantityStep.step(3.0, -1)!!, 0.0)

    @Test fun noAmountCountsAsOne() = assertEquals(2.0, QuantityStep.step(0.0, 1)!!, 0.0)
    @Test fun negativeCountsAsOne() = assertEquals(2.0, QuantityStep.step(-1.0, 1)!!, 0.0)

    @Test fun fractionStepsFromWhereItIs() = assertEquals(1.5, QuantityStep.step(0.5, 1)!!, 0.0)
    @Test fun fractionStepsDown() = assertEquals(0.5, QuantityStep.step(1.5, -1)!!, 0.0)
    @Test fun repeatedStepsDontDrift() =
        assertEquals(2.1, QuantityStep.step(QuantityStep.step(0.1, 1)!!, 1)!!, 0.0)

    @Test fun minusBelowOneIsRefused() = assertNull(QuantityStep.step(1.0, -1))
    @Test fun minusOnNoAmountIsRefused() = assertNull(QuantityStep.step(0.0, -1))
    @Test fun minusOnHalfIsRefused() = assertNull(QuantityStep.step(0.5, -1))

    @Test fun minusDisabledAtOne() = assertFalse(QuantityStep.canDecrease(1.0))
    @Test fun minusDisabledWithoutAmount() = assertFalse(QuantityStep.canDecrease(0.0))
    @Test fun minusDisabledForHalf() = assertFalse(QuantityStep.canDecrease(0.5))
    @Test fun minusEnabledAtTwo() = assertTrue(QuantityStep.canDecrease(2.0))
    @Test fun minusEnabledForOneAndAHalf() = assertTrue(QuantityStep.canDecrease(1.5))

    @Test fun collapsedTapsAddUp() = assertEquals(5.0, QuantityStep.step(2.0, 3)!!, 0.0)
}
