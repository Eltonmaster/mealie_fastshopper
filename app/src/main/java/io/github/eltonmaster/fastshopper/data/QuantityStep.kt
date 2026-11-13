package io.github.eltonmaster.fastshopper.data

import kotlin.math.roundToLong

/** Rules for the +/− stepper on a list row (unit-tested). */
object QuantityStep {
    private const val EPSILON = 1e-9

    /** Mealie stores "no amount" as 0; the stepper treats that as 1. */
    fun effective(quantity: Double): Double = if (quantity <= EPSILON) 1.0 else quantity

    /** − stops at 1 (or below, for fractions): removing an item stays on swipe. */
    fun canDecrease(quantity: Double): Boolean = effective(quantity) - 1.0 > EPSILON

    /**
     * The quantity after one tap of + (delta 1) or − (delta −1), or several collapsed taps.
     * Fractions step from where they are, so 0.5 becomes 1.5. Returns null when the step would
     * go to 0 or below.
     */
    fun step(quantity: Double, delta: Int): Double? {
        val next = effective(quantity) + delta
        if (next <= EPSILON) return null
        // Keep repeated steps from drifting (0.1 + 1 + 1 …) without changing real fractions.
        return (next * 1_000_000).roundToLong() / 1_000_000.0
    }
}
