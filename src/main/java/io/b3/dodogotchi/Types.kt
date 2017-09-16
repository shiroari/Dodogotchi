package io.b3.dodogotchi

import io.b3.dodogotchi.model.State
import io.vertx.core.Future

typealias onStateChange = (oldState: State?, newState: State) -> Future<Void>