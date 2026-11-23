package com.moviclub.app.core.domain.calculator

import com.moviclub.app.core.domain.model.Modality
import kotlin.math.roundToInt

object PointsCalculator {
    fun calculate(
        modality: Modality,
        activeMinutes: Double,
        distanceKilometers: Double,
        averageIntensity: Double
    ): Int {
        require(activeMinutes >= 0.0) { "Active minutes cannot be negative." }
        require(distanceKilometers >= 0.0) { "Distance cannot be negative." }
        require(averageIntensity >= 0.0) { "Intensity cannot be negative." }

        val scoredDistance = if (modality.usesLocation) distanceKilometers else 0.0
        return (
            (activeMinutes * 10 + scoredDistance * 25 + averageIntensity * 5) *
                modality.multiplier
            ).roundToInt()
    }
}
