package io.b3.dodogotchi.service

import fj.P2
import fj.P3
import fj.test.Arbitrary
import fj.test.Gen
import fj.test.Property
import fj.test.Property.*
import fj.test.reflect.CheckParams
import fj.test.runner.PropertyTestRunner
import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.config.Indicator
import io.b3.dodogotchi.config.IndicatorStrategy
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(PropertyTestRunner::class)
@CheckParams(maxSize = 100)
class StrategyQuickTest {

    companion object {
        val thresholdGen : Gen<Int> = Gen.choose(0, 10)
        val inspectorFieldGen: Gen<Indicator> = Arbitrary.arbEnumValue(Indicator::class.java)
        val inspectorStrategyGen: Gen<IndicatorStrategy> = Arbitrary.arbEnumValue(IndicatorStrategy::class.java)
        val gen2: Gen<P2<IndicatorStrategy, Int>> = Arbitrary.arbP2(inspectorStrategyGen, thresholdGen)
        val gen3: Gen<P3<Indicator, IndicatorStrategy, Int>> = Arbitrary.arbP3(inspectorFieldGen, inspectorStrategyGen, thresholdGen)
    }

    fun shouldNotReportIfDiffLessThenThreshold(): Property = property<P3<Indicator, IndicatorStrategy, Int>>(gen3) { p3 ->

        val now = ZonedDateTime.of(2017, 6, 25, 12,
                0, 0, 0, ZoneId.systemDefault())

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject()
                                .put("fields", JsonObject().put("created", "2017-06-22T08:00:11.000+0500"))
                                .put("changelog",
                                        JsonObject().put("histories", JsonArray(listOf(
                                                JsonObject()
                                                        .put("created", "2017-06-22T08:00:11.000+0500")
                                                        .put("items", JsonArray(listOf(JsonObject().put("field", "status")))),
                                                JsonObject()
                                                        .put("created", "2017-06-23T08:00:11.000+0500")
                                                        .put("items", JsonArray(listOf(JsonObject().put("field", "status"))))
                                        )))
                                ))))

        val conf = Config(indicator = p3._1(),
                indicatorStrategy = p3._2(),
                indicatorThresholdInDays = p3._3())

        val event = JiraHandler(conf).handle(json, now)

        impliesBoolean(p3._3() >= 4, "You have one issue in progress and you are doing great!" == event.message)
    }

    fun shouldEvalThroughput(): Property = property<P2<IndicatorStrategy, Int>>(gen2) { p2 ->

        val today = 25

        val now = ZonedDateTime.of(2017, 6, today, 12,
                0, 0, 0, ZoneId.systemDefault())

        val json = JsonObject().put("issues",
                JsonArray(listOf(
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-22T08:00:11.000+0200")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-30T12:00:11.000+0200")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-19T00:00:11.000+0200")),
                        JsonObject().put("fields",
                                JsonObject().put("created", "2017-06-13T00:00:11.000+0200")))
                ))

        val conf = Config(indicator = Indicator.THROUGHPUT,
                indicatorStrategy = p2._1(),
                indicatorThresholdInDays = p2._2())

        val event = JiraHandler(conf).handle(json, now)

        val overdueDays = listOf(22, 30, 19, 13)
                .map { issueDay -> today - issueDay - conf.indicatorThresholdInDays + 1 }
                .map { it.toLong() }
                .filter { it > 0 }

        val res = when (conf.indicatorStrategy) {
            IndicatorStrategy.MAX -> if (overdueDays.isEmpty()) 0L else overdueDays.max()!!
            IndicatorStrategy.SUM -> overdueDays.sum()
            IndicatorStrategy.AVG -> if (overdueDays.isEmpty()) 0L else Math.round(overdueDays.average())
            IndicatorStrategy.MEDIAN -> {
                when (overdueDays.size) {
                    0 -> 0L
                    1 -> overdueDays.first()
                    2 -> (overdueDays.first() + overdueDays.last()) / 2
                    3 -> overdueDays[1]
                    else -> -1
                }
            }
        }.toInt()

        prop(res >= 0 && res == event.level)
    }

    // todo: num of items in message
    // todo: more median tests
}