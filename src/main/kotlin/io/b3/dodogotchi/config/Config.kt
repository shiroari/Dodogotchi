package io.b3.dodogotchi.config

import io.vertx.core.json.JsonObject

enum class Indicator {
    STATUS, SPEED, THROUGHPUT
}

enum class IndicatorStrategy {
    SUM, MAX, AVG, MEDIAN
}

data class Config(
        val dataDir: String,
        val url: String,
        val username: String,
        val password: String,
        val jql: String,
        val updateInternalInMin: Long,
        val evolutionInternalInMin: Long,
        val evolutionStartHour: Int,
        val indicator: Indicator = Indicator.STATUS,
        val indicatorStrategy: IndicatorStrategy = IndicatorStrategy.SUM,
        val indicatorThresholdInDays: Int = 5,
        val indicatorThresholdMaxInDays: Int = 20
) {
    companion object {
        fun parse(json: JsonObject) = Config(
                dataDir = json.getString("dataDir"),
                url = json.getString("url"),
                username = json.getString("username"),
                password = json.getString("password"),
                jql = json.getString("jql"),
                updateInternalInMin = json.getLong("updateInternalInMin"),
                evolutionInternalInMin = json.getLong("evolutionInternalInMin"),
                evolutionStartHour = json.getInteger("evolutionStartHour"),
                indicator = Indicator.valueOf(json.getString("indicator").toUpperCase()),
                indicatorStrategy = IndicatorStrategy.valueOf(json.getString("indicatorStrategy").toUpperCase()),
                indicatorThresholdInDays = json.getInteger("indicatorThresholdInDays"),
                indicatorThresholdMaxInDays = json.getInteger("indicatorThresholdMaxInDays")
        )
    }
}