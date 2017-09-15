package io.b3.dodogotchi

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.rxjava.core.Vertx
import rx.Observable
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.concurrent.TimeUnit

data class AppConfig(
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
)

data class Event(
    val overdueMax: Int,
    val overdueNumber: Int
)

@Suppress("unused")
class Server : AbstractVerticle() {

    companion object {
        const val SERVER_PORT = 9090
        const val MAX_HEALTH = 100
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .appendOffset("+HHmm", "0")
                .toFormatter()
    }

    private val config : AppConfig
        get() = _config!!

    private var _config : AppConfig? = null

    private var state = loadState()

    private fun createRouter() = Router.router(vertx).apply {

        // api
        route("/api/state").handler(handlerState)

        // webapp
        get("/*").handler(StaticHandler.create()
                .setWebRoot("webroot"))
                //.setAllowRootFileSystemAccess(true)
                //.setWebRoot("/Users/shiroari/Workplace/sandbox/javascript/tabagotchi"))
    }

    override fun start(startFuture: Future<Void>?) {

        val future : Future<JsonObject> = loadConfig()

        future.setHandler { ar ->

            if (ar.failed()) {

                startFuture?.fail(ar.cause())

            } else {

                val json = ar.result()

                _config = AppConfig(
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

                val router = createRouter()
                val serverPort = config().getInteger("http.port", SERVER_PORT)
                vertx.createHttpServer()
                        .requestHandler { router.accept(it) }
                        .listen(serverPort) { result ->
                            if (result.succeeded()) {
                                startFuture?.complete()
                            } else {
                                startFuture?.fail(result.cause())
                            }
                        }

                val scheduler = io.vertx.rxjava.core.RxHelper.scheduler(Vertx(vertx))
                Observable.interval(0, config.updateInternalInMin,
                        TimeUnit.MINUTES, scheduler).subscribe(this::update)
            }
        }

    }

    // Handlers
    private val handlerState = Handler<RoutingContext> { cx ->
        val resp = cx.response()
        resp.statusCode = 200
        resp.headers().set("Content-Type", "application/json; charset=utf-8")
        resp.headers().set("Access-Control-Allow-Origin", "*")
        resp.end(Json.encodePrettily(state))
    }

    // Updater

    private val client : HttpClient by lazy {
        val baseUrl = URI.create(config.url)
        val ssl = (baseUrl.scheme == "https")
        val port = if (baseUrl.port > 0) { baseUrl.port } else { if (ssl) 443 else 80 }
        vertx.createHttpClient(HttpClientOptions()
                .setDefaultHost(baseUrl.host)
                .setDefaultPort(port)
                .setSsl(ssl))
    }

    private fun update(tick: Long) {

        val baseUrl = config.url
        val username = config.username
        val password = config.password
        val jql = URLEncoder.encode(config.jql, "UTF-8")

        val requestUri = "$baseUrl/rest/api/2/search?jql=$jql&expand=changelog&os_username=$username&os_password=$password"

        client.get(requestUri, { resp ->

            println("RESPONSE: " + resp.statusMessage())

            val buf = Buffer.buffer()

            resp.handler { data ->
                buf.appendBuffer(data)
            }.exceptionHandler { err ->
                println("RESPONSE ERROR: " + err)
                state = handleResponse(null)
            }.endHandler {
                val event = parseResponse(buf.toJsonObject())
                val newState = handleResponse(event)
                println(newState)
                state = newState
            }

        }).exceptionHandler { err ->
            println("REQUEST ERROR: " + err)
            state = handleResponse(null)
        }.end()

    }

    private fun parseResponse(json: JsonObject): Event {

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
                .filter { it > config.overdueThresholdInDays }
                .sorted()

        if (found.isEmpty()) {
            return Event(0, 0)
        }

        return Event(Math.toIntExact(found[found.size - 1]), found.size)
    }

    private fun handleResponse(data: Event?) : State {

        val now = Instant.now().toEpochMilli()

        var penalty = data?.overdueMax?:state.penalty
        var radar = data?.overdueNumber?:state.radar
        var hp = MAX_HEALTH - (MAX_HEALTH * penalty / config.deathThresholdInDays)
        var stagnation = (penalty > config.stagnationThreshold)

        var evolutionLevel = state.evolutionLevel
        var evolutionStage = state.evolutionStage
        var evolutionTimestamp = state.evolutionTimestamp

        if (hp <= 0) {
            hp = 0
            evolutionLevel = 0
            evolutionStage = 0
        }

        if (!stagnation
                && state.updatedAt != 0L
                && (now - evolutionTimestamp >= Duration.ofMinutes(config.evolutionInternalInMin).toMillis())) {

            if (evolutionLevel < 2) {
                if (evolutionStage < 9) {
                    evolutionStage++
                } else if (evolutionStage == 9) {
                    evolutionStage = 0
                    evolutionLevel++
                }
            } else if (evolutionLevel == 2) {
                evolutionStage = 9
            }

            evolutionTimestamp = Instant.now().toEpochMilli()
        }

        return State(hp,
                evolutionLevel,
                evolutionStage,
                penalty,
                radar,
                config.overdueThresholdInDays,
                stagnation,
                Instant.now().toEpochMilli(),
                evolutionTimestamp)
    }

    private fun loadState() : State {
        return State(100,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                0)
    }

    private fun loadConfig() : Future<JsonObject> {

//        val store0 = ConfigStoreOptions()
//                .setType("configmap")
//                .setConfig(JsonObject()
//                        .put("namespace", "dodogotchi")
//                        .put("name", "dodogotchi")
//                )

        val path = System.getProperty("config", "config.yaml")

        val store = ConfigStoreOptions()
                .setType("file")
                .setFormat("yaml")
                .setConfig(JsonObject().put("path", path))

        val options = ConfigRetrieverOptions()
                .addStore(store)

        val retriever = ConfigRetriever.create(vertx, options)

        return ConfigRetriever.getConfigAsFuture(retriever)
    }
}

