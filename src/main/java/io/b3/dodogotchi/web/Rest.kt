package io.b3.dodogotchi.web

import io.b3.dodogotchi.model.Progress
import io.b3.dodogotchi.service.Keeper
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.DecodeException
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
        get("/api/state").handler(handleGetState)
        get("/api/progress").handler(handleGetProgress)
        post("/api/progress").handler(handlePostProgress)
        get("/*").handler(StaticHandler.create()
                .setWebRoot("webroot"))
        //.setAllowRootFileSystemAccess(true)
        //.setWebRoot("src/main/resources/webroot"))
    }

    private fun writeJson(resp: HttpServerResponse, obj: Any) {
        resp.headers().set("Content-Type", "application/json; charset=utf-8")
        resp.end(Json.encodePrettily(obj))
    }

    //
    // Handlers

    private val handleGetState = fun (cx: RoutingContext) {
        val resp = cx.response()
        resp.statusCode = 200
        writeJson(resp, keeper.state)
    }

    private val handleGetProgress = fun (cx: RoutingContext) {
        val resp = cx.response()
        resp.statusCode = 200
        writeJson(resp, keeper.progress)
    }

    private val handlePostProgress = fun (cx: RoutingContext) {

        val req = cx.request()
        val resp = cx.response()

        val buf = Buffer.buffer()

        req.handler { data ->
            buf.appendBuffer(data)
        }

        req.endHandler {
            try {
                keeper.progress = Json.decodeValue(buf, Progress::class.java)
                resp.statusCode = 200
                writeJson(resp, keeper.progress)
            } catch (err: DecodeException) {
                resp.statusCode = 500
                resp.end()
                throw err
            }
        }
    }

}