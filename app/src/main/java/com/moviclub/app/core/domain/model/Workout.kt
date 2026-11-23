package com.moviclub.app.core.domain.model

data class Workout(
    val id: String,
    val modality: Modality,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val averageIntensity: Double
)
