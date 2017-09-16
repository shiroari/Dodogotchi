package io.b3.dodogotchi

import io.b3.dodogotchi.model.Config
import io.b3.dodogotchi.model.State
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
import java.nio.file.Paths

@Suppress("unused")
class MainVerticle : AbstractVerticle() {

    override fun start(startFuture: Future<Void>?) {
        loadConfig()
            .compose { json ->
                val conf = Config.parse(json)
                loadState(conf).compose { state ->
                    run(state, conf)
                }
            }
            .setHandler { ar ->
                if (ar.succeeded()) {
                    startFuture?.complete()
                } else {
                    startFuture?.fail(ar.cause())
                }
            }
    }

    private fun run(initState: State, conf: Config): Future<CompositeFuture> {

        val keeper = Keeper(initState, conf)
        val rest = Rest(keeper, vertx)
        val updater = Updater(keeper, conf, vertx)

        keeper.addListener { _: State?, newState: State ->
            println(newState)
            saveState(newState, conf)
        }

        return CompositeFuture.all(
                updater.start(),
                rest.start())
    }

    private fun loadConfig() : Future<JsonObject> {

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

        val cmRet = ConfigRetriever.getConfigAsFuture(
                ConfigRetriever.create(vertx, cmOpts))

        println("Trying to load configmap [$namespace/$name] ...")

        return cmRet.recover { err ->

            println("WARN: ${err.localizedMessage}")

            val path = System.getProperty("config", "config.yaml")

            println("Fallback to ${path}")

            val file = ConfigStoreOptions()
                    .setType("file")
                    .setFormat("yaml")
                    .setConfig(JsonObject().put("path", path))

            val fileOpts = ConfigRetrieverOptions()
                    .setScanPeriod(0)
                    .addStore(file)

            ConfigRetriever.getConfigAsFuture(
                    ConfigRetriever.create(vertx, fileOpts))
        }
    }

    private fun getDataFile(conf: Config) = Paths.get(conf.dataDir, "state").toString()

    private fun loadState(conf: Config) : Future<State> {
        val f = Future.future<Buffer>()

        vertx.fileSystem().readFile(getDataFile(conf))  { ar ->
            if (ar.succeeded()) {
                f.complete(ar.result())
            } else {
                println("WARN: State cannot be loaded from [${conf.dataDir}].")
                f.fail(ar.cause())
            }
        }

        return f.map { buf ->
            try {
                Json.decodeValue(buf, State::class.java)
            } catch (err : Exception) {
                println(err.localizedMessage)
                throw err
            }
        }.otherwise {
            State(100,
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    0,
                    0)
        }
    }

    private fun saveState(state: State, conf: Config) : Future<Void> {
        val f = Future.future<Void>()
        vertx.fileSystem()
                .writeFile(getDataFile(conf), Json.encodeToBuffer(state)) { ar ->
                    if (ar.succeeded()) {
                        f.complete()
                    } else {
                        println("WARN: State cannot be saved to [${conf.dataDir}].")
                        f.fail(ar.cause())
                    }
                }
        return f
    }

}

