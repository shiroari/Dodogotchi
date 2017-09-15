package io.b3.dodogotchi

import io.vertx.core.Future
import io.vertx.core.http.HttpClient

interface Handler {
    fun fetch(client: HttpClient, conf: Config) : Future<Event>
}