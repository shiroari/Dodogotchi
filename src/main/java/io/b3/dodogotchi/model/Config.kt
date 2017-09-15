package io.b3.dodogotchi.model

import io.vertx.core.json.JsonObject

data class Config(
        val url: String,
        val username: String,
        val password: String,
        val jql: String,
        val updateInternalInMin: Long,
        val evolutionInternalInMin: Long,
        val evolutionStartHour: Int,
        val overdueThresholdInDays: Int,
        val stagnationThreshold: Int,
        val deathThresholdInDays: Int
) {
    companion object {
        fun parse(json: JsonObject) = Config(
                json.getString("url"),
                json.getString("username"),
                json.getString("password"),
                json.getString("jql"),
                json.getLong("updateInternalInMin"),
                json.getLong("evolutionInternalInMin"),
                json.getInteger("evolutionStartHour"),
                json.getInteger("overdueThresholdInDays"),
                json.getInteger("stagnationThreshold"),
                json.getInteger("deathThresholdInDays")
        )
    }
}