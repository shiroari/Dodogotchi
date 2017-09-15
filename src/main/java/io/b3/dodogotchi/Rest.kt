package io.b3.dodogotchi

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler

class Rest(private val keeper: Keeper, private val vertx: Vertx) {

    companion object {
        const val SERVER_PORT = 9090
    }

    fun start(): Future<HttpServer> {
        val f: Future<HttpServer> = Future.future()
        val router = createRouter(vertx)
        vertx.createHttpServer()
                .requestHandler( { router.accept(it) } )
                .listen(SERVER_PORT, f.completer())
        return f
    }

    private fun createRouter(vertx: Vertx) = Router.router(vertx).apply {
        route("/api/state").handler(handleGetState)
        get("/*").handler(StaticHandler.create()
                .setWebRoot("webroot"))
        //.setAllowRootFileSystemAccess(true)
        //.setWebRoot("src/main/resources/webroot"))
    }

    //
    // Handlers

    private val handleGetState = { cx: RoutingContext ->
        val resp = cx.response()
        resp.statusCode = 200
        resp.headers().set("Content-Type", "application/json; charset=utf-8")
        resp.headers().set("Access-Control-Allow-Origin", "*")
        resp.end(Json.encodePrettily(keeper.model))
    }

}