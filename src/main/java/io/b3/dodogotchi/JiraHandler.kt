package io.b3.dodogotchi

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

class JiraHandler: Handler {

    companion object {
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .appendOffset("+HHmm", "Z")
                .toFormatter()
    }

    override fun fetch(client: HttpClient, conf: Config): Future<Event> {

        val baseUrl = conf.url
        val username = conf.username
        val password = conf.password
        val jql = URLEncoder.encode(conf.jql, "UTF-8")

        val requestUri = "$baseUrl/rest/api/2/search?jql=$jql&expand=changelog&os_username=$username&os_password=$password"

        val f = Future.future<Event>()

        client.get(requestUri, { resp ->

            println("RESPONSE: " + resp.statusMessage())

            val buf = Buffer.buffer()

            resp.handler { data ->
                buf.appendBuffer(data)
            }.exceptionHandler { err ->
                f.fail(err.cause)
            }.endHandler {
                val event = handle(buf.toJsonObject(), conf)
                f.complete(event)
            }

        }).exceptionHandler { err ->
            f.fail(err.cause)
        }.end()

        return f
    }

    private fun handle(json: JsonObject, conf: Config): Event {

        val now = LocalDateTime.now()
        val zone = ZoneOffset.UTC

        val issues = json.getJsonArray("issues", JsonArray())

        fun asJsonObject(obj: Any): JsonObject = (obj as JsonObject)

        fun hasStatus(items: JsonArray): Boolean = items.map(::asJsonObject)
                .any { it.getString("field") == "status" }

        val found = issues.map(::asJsonObject)
                .map { it.getJsonObject("changelog") }
                .map { it.getJsonArray("histories")
                        .map(::asJsonObject)
                        .filter { hist -> hasStatus(hist.getJsonArray("items")) }
                        .map { hist -> hist.getString("created") }
                        .map { created -> LocalDateTime.parse(created, DATE_TIME_FORMAT) }
                        .sorted()
                        .last()
                }
                .map { now.toEpochSecond(zone) - it.toEpochSecond(zone) }
                .map { Duration.ofSeconds(it).toDays() }
                .filter { it > conf.overdueThresholdInDays }
                .sorted()

        if (found.isEmpty()) {
            return Event(0, 0)
        }

        return Event(Math.toIntExact(found[found.size - 1]), found.size)
    }

}