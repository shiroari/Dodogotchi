package io.b3.jumper

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler


@Suppress("unused")
class Server : AbstractVerticle() {

    private fun createRouter() = Router.router(vertx).apply {

        // proxy
        route("/rest/*").handler(handlerProxy)
        route("/secure/*").handler(handlerProxy)
        get("/s/*").handler(handlerProxy)

        // webapp
        get("/*").handler(StaticHandler.create()
                //.setWebRoot("webroot"))
                .setAllowRootFileSystemAccess(true)
                .setWebRoot("/Users/shiroari/Workplace/sandbox/javascript/tabagotchi"))
    }

    private val client : HttpClient by lazy {
        val host = config().getString("target.host", "localhost")
        val port = config().getInteger("target.port", 8080)
        val ssl = config().getBoolean("target.ssl", false)
        vertx.createHttpClient(HttpClientOptions()
                .setDefaultHost(host)
                .setDefaultPort(port)
                .setSsl(ssl))
    }

    override fun start(startFuture: Future<Void>?) {
        val router = createRouter()
        val serverPort = config().getInteger("http.port", 9090)
        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(serverPort) { result ->
                    if (result.succeeded()) {
                        startFuture?.complete()
                    } else {
                        startFuture?.fail(result.cause())
                    }
                }
    }

    //
    // Proxy
    //
    private val handlerProxy = Handler<RoutingContext> { cx ->

        val req = cx.request()
        val resp = cx.response()

        val cReq = client.request(req.method(), req.uri(), { cRes ->
                    resp.statusCode = cRes.statusCode()
                    resp.headers().setAll(cRes.headers())
                    resp.isChunked = true
                    cRes.handler({ data ->
                        req.response().write(data)
                    })
                    cRes.endHandler({ req.response().end() })
                })

        cReq.headers().setAll(req.headers())

        cReq.headers().set("Origin", config().getString("headers.origin"))
        cReq.headers().set("Host", config().getString("headers.host"))
        cReq.headers().set("Referer", config().getString("headers.referer"))
        cReq.headers().set("Cookie", config().getString("headers.cookie"))

        cReq.isChunked = true
        req.handler({ data ->
            cReq.write(data)
        })

        req.endHandler({
            cReq.end()
        })

    }

}