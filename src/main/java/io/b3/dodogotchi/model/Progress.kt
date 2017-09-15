package io.b3.dodogotchi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Progress
@JsonCreator constructor(
        @JsonProperty("evolutionLevel")
        val evolutionLevel: Int,
        @JsonProperty("evolutionStage")
        val evolutionStage: Int
)