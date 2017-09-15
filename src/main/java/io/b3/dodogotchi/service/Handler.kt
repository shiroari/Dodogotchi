package io.b3.dodogotchi.service

import io.b3.dodogotchi.model.Config
import io.b3.dodogotchi.model.Event
import io.vertx.core.Future
import io.vertx.core.http.HttpClient

interface Handler {
    fun fetch(client: HttpClient, conf: Config) : Future<Event>
}