package io.b3.dodogotchi

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.model.State
import io.b3.dodogotchi.service.JiraHandler
import io.b3.dodogotchi.service.Keeper
import io.b3.dodogotchi.service.Updater
import io.b3.dodogotchi.web.Rest
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

@Suppress("unused")
class MainVerticle : AbstractVerticle() {

    private val log = LoggerFactory.getLogger(MainVerticle::class.java)

    override fun start(startFuture: Future<Void>?) {
        loadConfig()
                .compose { json ->
                    val conf = Config.parse(json)
                    loadState(conf).compose { state ->
                        run(state, conf)
                    }
                }
                .setHandler(startFuture?.completer())
    }

    private fun run(initState: State, conf: Config): Future<Void> {

        val keeper = Keeper(initState, conf)
        val rest = Rest(keeper, vertx)
        val handler = JiraHandler(conf)
        val updater = Updater(handler, keeper, conf, vertx)

        keeper.addListener { _: State?, newState: State ->
            log.debug("Saving new state... $newState")
            saveState(newState, conf)
        }

        return CompositeFuture.all(updater.start(), rest.start())
                .mapEmpty()
    }

    private fun loadConfig(): Future<JsonObject> {
        return loadK8sConfig().recover { err ->
            val path = System.getProperty("config", "config.yaml")
            log.warn("Failed to load configmap: ${err.localizedMessage}. Fallback to $path")
            loadFileConfig(path)
        }
    }

    private fun loadK8sConfig(): Future<JsonObject> {
        val namespace = "dodogotchi"
        val name = "dodogotchi"

        val configmap = ConfigStoreOptions()
                .setType("configmap")
                .setConfig(JsonObject()
                        .put("namespace", namespace)
                        .put("name", name)
                )

        val cmOpts = ConfigRetrieverOptions()
                .setScanPeriod(0)
                .addStore(configmap)

        log.info("Trying to load configmap [$namespace/$name] ...")

        return ConfigRetriever.getConfigAsFuture(
                ConfigRetriever.create(vertx, cmOpts))
    }

    private fun loadFileConfig(path: String): Future<JsonObject> {

        val file = ConfigStoreOptions()
                .setType("file")
                .setFormat("yaml")
                .setConfig(JsonObject().put("path", path))

        val fileOpts = ConfigRetrieverOptions()
                .setScanPeriod(0)
                .addStore(file)

        return ConfigRetriever.getConfigAsFuture(
                ConfigRetriever.create(vertx, fileOpts))
    }

    private fun getDataFile(conf: Config) = Paths.get(conf.dataDir, "state").toString()

    private fun loadState(conf: Config): Future<State> {
        val f = Future.future<Buffer>()
        vertx.fileSystem()
                .readFile(getDataFile(conf), f.completer())
        return f.map { buf ->
            Json.decodeValue(buf, State::class.java)
        }.recover { err ->
            if (err.cause is NoSuchFileException) {
                log.warn("State not found in [${conf.dataDir}]. Using default state.")
            } else {
                log.error("State cannot be loaded from [${conf.dataDir}]. Using default state.", err)
            }
            Future.succeededFuture(State(hp = 100,
                    level = 0,
                    levelProgress = 0,
                    message = "",
                    evolutionTimestamp = 0))
        }
    }

    private fun saveState(state: State, conf: Config): Future<Void> {
        val f = Future.future<Void>()
        vertx.fileSystem()
                .writeFile(getDataFile(conf), Json.encodeToBuffer(state),
                        f.completer())
        return f.recover { err ->
            log.warn("State cannot be saved to [${conf.dataDir}]", err)
            Future.failedFuture(err)
        }
    }

}

