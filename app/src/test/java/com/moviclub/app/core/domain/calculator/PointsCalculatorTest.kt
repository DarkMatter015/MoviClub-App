package com.moviclub.app.core.domain.calculator

import com.moviclub.app.core.domain.model.Modality
import org.junit.Assert.assertEquals
import org.junit.Test

class PointsCalculatorTest {
    @Test
    fun gymWorkoutDoesNotScoreDistance() {
        val withoutDistance = PointsCalculator.calculate(
            modality = Modality.GYM,
            activeMinutes = 30.0,
            distanceKilometers = 0.0,
            averageIntensity = 2.0
        )
        val withDistance = PointsCalculator.calculate(
            modality = Modality.GYM,
            activeMinutes = 30.0,
            distanceKilometers = 12.0,
            averageIntensity = 2.0
        )

        assertEquals(403, withoutDistance)
        assertEquals(withoutDistance, withDistance)
    }
}
