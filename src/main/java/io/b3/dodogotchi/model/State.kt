package io.b3.dodogotchi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class State
@JsonCreator constructor(
        @JsonProperty("hp")
        val hp: Int,
        @JsonProperty("evolutionLevel")
        val evolutionLevel: Int,
        @JsonProperty("evolutionStage")
        val evolutionStage: Int,
        @JsonProperty("penalty")
        val penalty: Int,
        @JsonProperty("radar")
        val radar: Int,
        @JsonProperty("radarThreshold")
        val radarThreshold: Int,
        @JsonProperty("stagnation")
        val stagnation: Boolean,
        @JsonProperty("updatedAt")
        val updatedAt: Long,
        @JsonProperty("evolutionTimestamp")
        val evolutionTimestamp: Long
)
