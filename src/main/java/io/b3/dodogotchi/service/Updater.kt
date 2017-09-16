package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.rxjava.core.RxHelper
import rx.Observable
import java.net.URI
import java.util.concurrent.TimeUnit
import io.vertx.rxjava.core.Vertx as JavaVertx

class Updater(private val keeper: Keeper, private val conf: Config, private val vertx: Vertx) {

    private val handler : Handler = JiraHandler()

    private val client : HttpClient by lazy {
        val baseUrl = URI.create(conf.url)
        val ssl = (baseUrl.scheme == "https")
        val port = if (baseUrl.port > 0) { baseUrl.port } else { if (ssl) 443 else 80 }
        vertx.createHttpClient(HttpClientOptions()
                .setDefaultHost(baseUrl.host)
                .setDefaultPort(port)
                .setSsl(ssl))
    }

    fun start() : Future<Void> {
        val scheduler = RxHelper.scheduler(JavaVertx(vertx))
        Observable.interval(0, conf.updateInternalInMin,
                TimeUnit.MINUTES, scheduler).subscribe(this::update)
        return Future.succeededFuture()
    }

    private fun update(tick: Long) {
        handler.fetch(client, conf).setHandler { ar ->
            if (ar.succeeded()) {
                keeper.updateWithEvent(ar.result())
            } else {
                println(ar.cause()?.localizedMessage?:"Unknown error")
                keeper.update()
            }
        }
    }
}