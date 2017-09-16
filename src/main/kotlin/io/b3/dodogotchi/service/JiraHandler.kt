package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.config.Indicator
import io.b3.dodogotchi.config.IndicatorStrategy
import io.b3.dodogotchi.model.Event
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

class JiraHandler(private val conf: Config) : Handler {

    companion object {
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .appendOffset("+HHmm", "Z")
                .toFormatter()
    }

    override fun fetch(client: HttpClient): Future<Event> {

        val baseUrl = conf.url
        val username = conf.username
        val password = conf.password
        val jql = URLEncoder.encode(conf.jql, "UTF-8")

        val requestUri = "$baseUrl/rest/api/2/search?jql=$jql&expand=changelog&os_username=$username&os_password=$password"

        val f = Future.future<Event>()

        client.get(requestUri, { resp ->

            println("RESPONSE: ${resp.statusCode()} ${resp.statusMessage()}")

            val buf = Buffer.buffer()

            resp.handler { data ->
                buf.appendBuffer(data)
            }.exceptionHandler { err ->
                f.fail(err)
            }.endHandler {
                val body = buf.toJsonObject()
                when (resp.statusCode()) {
                    200 -> {
                        val now = LocalDateTime.now()
                        val event = handle(body, now)
                        f.complete(event)
                    }
                    else -> {
                        f.fail(Exception("Unexpected response: $body"))
                    }
                }
            }

        }).exceptionHandler { err ->
            f.fail(err)
        }.end()

        return f
    }

    internal fun handle(json: JsonObject, now: LocalDateTime): Event {

        val issues = json.getJsonArray("issues", JsonArray())
                .map(this::asJsonObject)

        val stats = when (conf.indicator) {
            Indicator.STATUS -> getStatusTime(issues, now)
            Indicator.SPEED -> getStartedTime(issues, now)
            Indicator.THROUGHPUT -> getCreatedTime(issues, now)
        }

        val level: Long = when (conf.indicatorStrategy) {
            IndicatorStrategy.SUM -> stats.sum()
            IndicatorStrategy.MAX -> stats.max() ?: 0L
            IndicatorStrategy.AVG -> {
                val avg = stats.average()
                if (avg == Double.NaN) {
                    0L
                } else {
                    avg.toLong()
                }
            }
            IndicatorStrategy.MEDIAN -> {
                val sorted = stats.sorted()
                val len = sorted.size
                when {
                    len == 0 -> 0L
                    len % 2 == 1 -> sorted[len/2]
                    else -> (sorted[len/2 - 1] + sorted[len/2]) / 2
                }
            }
        }

        val msg: String = if (level < conf.indicatorThresholdInDays) "" else {
            when (conf.indicatorStrategy) {
                IndicatorStrategy.MAX ->
                    "You have one issue stuck for more that $level days"
                IndicatorStrategy.SUM, IndicatorStrategy.AVG, IndicatorStrategy.MEDIAN ->
                    "You have issues stuck for more that $level days"
            }
        }

        return Event(Math.toIntExact(level), msg)
    }

    private fun getCreatedTime(issues: Iterable<JsonObject>, now: LocalDateTime): Iterable<Long> {

        return issues.mapNotNull { it.getJsonObject("fields") }
                .mapNotNull { hist -> hist.getString("created") }
                .map { toDateTime(it) }
                .map { toDays(it, now) }
    }

    private fun getStartedTime(issues: Iterable<JsonObject>, now: LocalDateTime): Iterable<Long> {

        fun hasStatus(items: JsonArray): Boolean = items.map(this::asJsonObject)
                .any { it.getString("field") == "status" }

        return issues.mapNotNull { it.getJsonObject("changelog") }
                .mapNotNull { it.getJsonArray("histories")
                        .map(this::asJsonObject)
                        .filter { hist -> hasStatus(hist.getJsonArray("items")) }
                        .mapNotNull { hist -> hist.getString("created") }
                        .map { toDateTime(it) }
                        .sorted()
                        .firstOrNull()
                }
                .map { toDays(it, now) }
    }

    private fun getStatusTime(issues: Iterable<JsonObject>, now: LocalDateTime): Iterable<Long> {

        fun hasStatus(items: JsonArray): Boolean = items.map(this::asJsonObject)
                .any { it.getString("field") == "status" }

        return issues.mapNotNull { it.getJsonObject("changelog") }
                .mapNotNull { it.getJsonArray("histories")
                        .map(this::asJsonObject)
                        .filter { hist -> hasStatus(hist.getJsonArray("items")) }
                        .map { hist -> hist.getString("created") }
                        .map { toDateTime(it) }
                        .sorted()
                        .lastOrNull()
                }
                .map { toDays(it, now) }
    }

    private fun toDateTime(dateString: String): LocalDateTime {
        return LocalDateTime.parse(dateString, DATE_TIME_FORMAT)
    }

    private fun toDays(datetime: LocalDateTime, now: LocalDateTime): Long {
        val zone = ZoneOffset.UTC
        val secs = now.toEpochSecond(zone) - datetime.toEpochSecond(zone)
        return Duration.ofSeconds(secs).toDays()
    }

    private fun asJsonObject(obj: Any): JsonObject = (obj as JsonObject)
}