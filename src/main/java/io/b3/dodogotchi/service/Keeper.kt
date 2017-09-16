package io.b3.dodogotchi.service

import io.b3.dodogotchi.model.Config
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
        get() = Progress(evolutionLevel = state.evolutionLevel,
                evolutionStage = state.evolutionStage)
        set(value) = updateState(state.copy(evolutionLevel = value.evolutionLevel,
                    evolutionStage = value.evolutionStage))

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

        var penalty = event?.overdueMax?: state.penalty
        var radar = event?.overdueNumber?: state.radar

        var hp = MAX_HEALTH - (MAX_HEALTH * penalty / conf.deathThresholdInDays)
        var stagnation = (penalty > conf.stagnationThreshold)

        var evolutionLevel = state.evolutionLevel
        var evolutionStage = state.evolutionStage
        var evolutionTimestamp = state.evolutionTimestamp

        if (hp <= 0) {
            hp = 0
            evolutionLevel = 0
            evolutionStage = 0
        }

        if (!stagnation
                && state.updatedAt != 0L
                && (now - evolutionTimestamp >= Duration.ofMinutes(conf.evolutionInternalInMin).toMillis())) {

            if (evolutionLevel < 2) {
                if (evolutionStage < 9) {
                    evolutionStage++
                } else if (evolutionStage == 9) {
                    evolutionStage = 0
                    evolutionLevel++
                }
            } else if (evolutionLevel == 2) {
                evolutionStage = 9
            }

            evolutionTimestamp = Instant.now().toEpochMilli()
        }

        return State(hp,
                evolutionLevel,
                evolutionStage,
                penalty,
                radar,
                conf.overdueThresholdInDays,
                stagnation,
                Instant.now().toEpochMilli(),
                evolutionTimestamp)
    }

}