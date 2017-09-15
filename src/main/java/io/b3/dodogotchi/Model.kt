package io.b3.dodogotchi

data class Model(
        val hp: Int,
        val evolutionLevel: Int,
        val evolutionStage: Int,
        val penalty: Int,
        val radar: Int,
        val radarThreshold: Int,
        val stagnation: Boolean,
        val updatedAt: Long,
        val evolutionTimestamp: Long
)
