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
import io.vertx.core.logging.LoggerFactory
import java.io.IOException
import java.net.URLEncoder
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

class JiraHandler(private val conf: Config) : Handler {

    private val log = LoggerFactory.getLogger(JiraHandler::class.java)

    companion object {
        private val UTF_8 = "UTF-8"
        private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .appendOffset("+HHmm", "Z")
                .toFormatter()
    }

    private fun fetchError(): IOException = IOException("Cannot fetch data from jira")

    override fun fetch(client: HttpClient): Future<Event> {

        val baseUrl = conf.url
        val username = URLEncoder.encode(conf.username, UTF_8)
        val password = URLEncoder.encode(conf.password, UTF_8)
        val jql = URLEncoder.encode(conf.jql, UTF_8)

        val requestUri = "$baseUrl/rest/api/2/search?jql=$jql&expand=changelog"
        val requestUriWithAuth = "$requestUri&os_username=$username&os_password=$password"

        val f = Future.future<Event>()

        log.debug("Sending request: $requestUri")

        client.get(requestUriWithAuth, { resp ->

            log.debug("Response: ${resp.statusCode()} ${resp.statusMessage()}")

            val buf = Buffer.buffer()

            resp.handler { data ->
                buf.appendBuffer(data)
            }.exceptionHandler { err ->
                log.error(err.message)
                f.fail(fetchError())
            }.endHandler {
                val body = buf.toJsonObject()
                when (resp.statusCode()) {
                    200 -> {
                        val event = handle(body, ZonedDateTime.now())
                        log.info("Response has been successfully processed: $event")
                        f.complete(event)
                    }
                    else -> {
                        log.error("Unexpected response: $body")
                        f.fail(fetchError())
                    }
                }
            }

        }).exceptionHandler { err ->
            log.error(err.message)
            f.fail(fetchError())
        }.end()

        return f
    }

    internal fun handle(json: JsonObject, now: ZonedDateTime): Event {

        val issues = json.getJsonArray("issues", JsonArray())
                .map(this::asJsonObject)

        val stats = when (conf.indicator) {
            Indicator.STATUS -> getStatusTime(issues, now)
            Indicator.SPEED -> getStartedTime(issues, now)
            Indicator.THROUGHPUT -> getCreatedTime(issues, now)
        }

        val anomalies = stats.map { it - conf.indicatorThresholdInDays + 1 }
                .filter { it > 0 }

        val level: Long = when (conf.indicatorStrategy) {
            IndicatorStrategy.SUM -> anomalies.sum()
            IndicatorStrategy.MAX -> anomalies.max() ?: 0L
            IndicatorStrategy.AVG -> {
                val avg = anomalies.average()
                if (avg == Double.NaN) {
                    0L
                } else {
                    avg.toLong()
                }
            }
            IndicatorStrategy.MEDIAN -> {
                val sorted = anomalies.sorted()
                val len = sorted.size
                when {
                    len == 0 -> 0L
                    len % 2 == 1 -> sorted[len/2]
                    else -> (sorted[len/2 - 1] + sorted[len/2]) / 2
                }
            }
        }

        val nIssues = stats.count()
        val nAnomalies = anomalies.count()

        val msg: String = if (nIssues == 0) {
            "There are no issues. Go grab some coffee."
        } else if (nAnomalies == 0) {
            if (nIssues == 1) {
                "You have one issue in progress and you are doing great!"
            } else {
                "You have $nIssues issues in progress and you are doing great!"
            }
        } else {
            if (nAnomalies == 1) {
                "You have one issue in progress that doesn't look good."
            } else {
                "You have $nAnomalies issues in progress that don't look good."
            }
        }

        return Event(Math.toIntExact(level), msg)
    }

    private fun getCreatedTime(issues: Iterable<JsonObject>, now: ZonedDateTime): Iterable<Long> {

        return issues.mapNotNull { it.getJsonObject("fields") }
                .mapNotNull { hist -> hist.getString("created") }
                .map { toDateTime(it) }
                .map { toDays(it, now) }
    }

    private fun getStartedTime(issues: Iterable<JsonObject>, now: ZonedDateTime): Iterable<Long> {

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

    private fun getStatusTime(issues: Iterable<JsonObject>, now: ZonedDateTime): Iterable<Long> {

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

    private fun toDateTime(dateString: String): ZonedDateTime {
        return ZonedDateTime.parse(dateString, DATE_TIME_FORMAT)
    }

    private fun toDays(datetime: ZonedDateTime, now: ZonedDateTime): Long {
        val secs = now.toEpochSecond() - datetime.toEpochSecond()
        return Duration.ofSeconds(secs).toDays()
    }

    private fun asJsonObject(obj: Any): JsonObject = (obj as JsonObject)
}