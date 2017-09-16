package io.b3.dodogotchi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Progress
@JsonCreator constructor(
        @JsonProperty("level")
        val level: Int,
        @JsonProperty("levelProgress")
        val levelProgress: Int
)