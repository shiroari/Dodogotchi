package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.config.Indicator
import io.b3.dodogotchi.config.IndicatorStrategy
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.ZoneId
import java.time.ZonedDateTime

@Tag("junit5")
class StrategyTest {

    @Test
    fun shouldReturnEmptyIfBodyEmpty() {
        val json = JsonObject()
        val conf = Config()
        val event = JiraHandler(conf).handle(json, dayOfMonth25())

        assertEquals(0, event.level)
    }

    @ParameterizedTest
    @EnumSource(Indicator::class)
    fun shouldReturnEmptyIfThereAreNoIssues(indicator: Indicator) {
        val json = JsonObject().put("issues", JsonArray())
        val conf = Config(indicator = indicator)
        val event = JiraHandler(conf).handle(json, dayOfMonth25())

        assertEquals(0, event.level)
        assertEquals("There are no issues. Go grab some coffee.", event.message)
    }

    @ParameterizedTest
    @EnumSource(Indicator::class)
    fun shouldReturnEmptyIfExpectedFieldNotFound(indicator: Indicator) {
        val json = JsonObject().put("issues",
                JsonArray(listOf(JsonObject())))
        val conf = Config(indicator = indicator)
        val event = JiraHandler(conf).handle(json, dayOfMonth25())

        assertEquals(0, event.level)
        assertEquals("There are no issues. Go grab some coffee.", event.message)
    }

    @Test
    fun shouldExtractCreatedTimeWhenUsingThroughput() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT, indicatorThresholdInDays = 0)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(4, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldExtractStartedTimeWhenUsingSpeed() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("changelog",
                                JsonObject().put("histories", JsonArray(listOf(
                                        JsonObject()
                                                .put("created", "2017-06-22T08:00:11.000+0500")
                                                .put("items", JsonArray(listOf(JsonObject().put("field", "status")))),
                                        JsonObject()
                                                .put("created", "2017-06-25T08:00:11.000+0500")
                                                .put("items", JsonArray(listOf(JsonObject().put("field", "status"))))
                                        )))
                                ))))

        val conf = Config(indicator = Indicator.SPEED, indicatorThresholdInDays = 0)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(4, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldExtractStatusTimeWhenUsingStatus() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("changelog",
                                JsonObject().put("histories", JsonArray(listOf(
                                        JsonObject()
                                                .put("created", "2017-06-22T08:00:11.000+0500")
                                                .put("items", JsonArray(listOf(JsonObject().put("field", "status")))),
                                        JsonObject()
                                                .put("created", "2017-06-19T08:00:11.000+0500")
                                                .put("items", JsonArray(listOf(JsonObject().put("field", "status"))))
                                )))
                        ))))

        val conf = Config(indicator = Indicator.STATUS, indicatorThresholdInDays = 0)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(4, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldReturnMaxOfCreationTimeWhenUsingThroughputMax() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-19T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = IndicatorStrategy.MAX,
                indicatorThresholdInDays = 2)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(5, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldReturnAvgOfCreationTimeWhenUsingThroughputAvg() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-19T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = IndicatorStrategy.AVG,
                indicatorThresholdInDays = 2)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(3, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldReturnSumOfCreationTimeWhenUsingThroughputSum() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-19T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = IndicatorStrategy.SUM,
                indicatorThresholdInDays = 2)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(7, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldReturnMedianOfCreationTimeWhenUsingThroughputMedian1() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = IndicatorStrategy.MEDIAN,
                indicatorThresholdInDays = 2)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(2, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldReturnMedianOfCreationTimeWhenUsingThroughputMedian2() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-19T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = IndicatorStrategy.MEDIAN,
                indicatorThresholdInDays = 2)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(3, event.level)
        assertTrue(event.message.isNotBlank())
    }

    @Test
    fun shouldReturnMedianOfCreationTimeWhenUsingThroughputMedian3() {

        val now = dayOfMonth25()

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0500")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-19T08:00:11.000+0500")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-25T08:00:11.000+0500")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = IndicatorStrategy.MEDIAN,
                indicatorThresholdInDays = 2)

        val event = JiraHandler(conf).handle(json, now)

        assertEquals(3, event.level)
        assertTrue(event.message.isNotBlank())
    }

    private fun dayOfMonth25() = ZonedDateTime.of(2017, 6, 25, 12,
            0, 0, 0, ZoneId.systemDefault())
}