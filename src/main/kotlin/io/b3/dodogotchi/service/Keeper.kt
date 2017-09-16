package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.model.Event
import io.b3.dodogotchi.model.Progress
import io.b3.dodogotchi.model.State
import io.b3.dodogotchi.onStateChange
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class Keeper(initState: State, private val conf: Config) {

    companion object {
        const val MAX_HEALTH = 100
    }

    private val stateHolder: AtomicReference<State> = AtomicReference()
    private val listeners = CopyOnWriteArrayList<onStateChange>()

    val state: State
        get() = stateHolder.get()

    var progress: Progress
        get() = Progress(level = state.level, levelProgress = state.levelProgress)
        set(value) = updateState(state.copy(level = value.level, levelProgress = value.levelProgress))

    init {
        updateState(initState)
    }

    fun addListener(listener: onStateChange) {
        listeners.add(listener)
    }

    private fun onChange(oldState: State?, newState: State) {
        listeners.forEach { f -> f(oldState, newState) }
    }

    fun update() {
        updateState(handleEventInt(null))
    }

    fun updateWithEvent(event: Event) {
        updateState(handleEventInt(event))
    }

    private fun updateState(newState: State) {
        val oldState: State? = stateHolder.get()
        stateHolder.set(newState)
        onChange(oldState = oldState, newState = newState)
    }

    private fun handleEventInt(event: Event?) : State {

        val now = Instant.now().toEpochMilli()

        var hp = if (event == null) {
            state.hp
        } else {
            Math.min(0, MAX_HEALTH * (conf.indicatorThresholdMaxInDays - event.level) / (conf.indicatorThresholdMaxInDays
                    - conf.indicatorThresholdInDays))
        }

        val sick = (hp < 40) // must be in sync with js

        var level = state.level
        var levelProgress = state.levelProgress
        var evolutionTimestamp = state.evolutionTimestamp

        if (hp > 0 && state.hp <= 0) {
            level = 0
            levelProgress = 0
        }

        if (!sick && ((now - evolutionTimestamp)
                >= Duration.ofMinutes(conf.evolutionInternalInMin).toMillis())) {

            if (levelProgress < 9) {
                levelProgress++
            } else {
                levelProgress = 0
                level++
            }

            if (level > 2) {
                level = 2
                levelProgress = 9
            }

            evolutionTimestamp = Instant.now().toEpochMilli()
        }

        return State(hp,
                level,
                levelProgress,
                event?.message?:state.message,
                evolutionTimestamp)
    }

}