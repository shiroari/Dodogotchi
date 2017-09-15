package io.b3.dodogotchi

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.json.JsonObject

@Suppress("unused")
class MainVerticle : AbstractVerticle() {

    override fun start(startFuture: Future<Void>?) {
        loadConfig()
            .compose { json ->
                val conf = Config.parse(json)
                startWithConfig(conf)
            }
            .setHandler { ar ->
                if (ar.succeeded()) {
                    startFuture?.complete()
                } else {
                    startFuture?.fail(ar.cause())
                }
            }
    }

    private fun startWithConfig(conf: Config): Future<CompositeFuture> {
        return loadModel().compose { model ->

            val keeper = Keeper(model, conf)
            val rest = Rest(keeper, vertx)
            val updater = Updater(keeper, conf, vertx)

            CompositeFuture.all(updater.start(),
                    rest.start())
        }
    }

    private fun loadModel() : Future<Model> {
        return Future.succeededFuture(Model(100,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                0))
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

