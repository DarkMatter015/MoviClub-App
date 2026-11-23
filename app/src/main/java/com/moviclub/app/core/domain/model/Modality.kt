package com.moviclub.app.core.domain.model

import com.moviclub.app.core.domain.GameRules

enum class Modality(
    val displayName: String,
    val multiplier: Double,
    val usesLocation: Boolean
) {
    WALKING("Caminhada", GameRules.WALKING_MULTIPLIER, true),
    RUNNING("Corrida", GameRules.RUNNING_MULTIPLIER, true),
    GYM("Academia", GameRules.GYM_MULTIPLIER, false)
}
