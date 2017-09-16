package io.b3.dodogotchi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class State
@JsonCreator constructor(
        @JsonProperty("hp")
        val hp: Int,
        @JsonProperty("level")
        val level: Int,
        @JsonProperty("levelProgress")
        val levelProgress: Int,
        @JsonProperty("message")
        val message: String,
        @JsonProperty("evolutionTimestamp")
        val evolutionTimestamp: Long
)
