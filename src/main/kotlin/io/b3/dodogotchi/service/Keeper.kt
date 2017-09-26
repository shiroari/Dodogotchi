package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.model.Event
import io.b3.dodogotchi.model.Progress
import io.b3.dodogotchi.model.State
import io.b3.dodogotchi.onStateChange
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
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

        require(conf.indicatorThresholdInDays >=0 )
        require(conf.indicatorThresholdMaxInDays < 365)
        require(conf.indicatorThresholdInDays < conf.indicatorThresholdMaxInDays)

        val now = Instant.now().toEpochMilli()
        val lastState = state

        val hp = if (event != null) {
            val scale = conf.indicatorThresholdMaxInDays - conf.indicatorThresholdInDays + 1
            val penalty = Math.max(0, event.level)
            val delta = Math.max(0, scale - penalty)
            MAX_HEALTH * delta / scale
        } else {
            lastState.hp
        }

        var level = lastState.level
        var levelProgress = lastState.levelProgress
        var evolutionTimestamp = lastState.evolutionTimestamp

        if (hp > 0 && lastState.hp <= 0) {
            level = 0
            levelProgress = 0
            evolutionTimestamp = 0
        }

        if (evolutionTimestamp > 0) {

            val sick = (hp < 40) // must be in sync with js

            if (!sick && ((now - evolutionTimestamp) >= Duration.ofMinutes(conf.evolutionInternalInMin).toMillis())) {

                levelProgress++

                if (levelProgress > 9) {
                    levelProgress = 0
                    level++
                    if (level > 2) {
                        levelProgress = 9
                        level = 2
                    }
                }

                evolutionTimestamp = getEvolutionTimestamp()
            }

        } else {

            evolutionTimestamp = getEvolutionTimestamp()

        }

        return State(hp,
                level,
                levelProgress,
                event?.message?:state.message,
                evolutionTimestamp)
    }

    private fun getEvolutionTimestamp(): Long {
        return ZonedDateTime.now()
                .withHour(conf.evolutionStartHour)
                .withMinute(0)
                .withSecond(0)
                .toInstant()
                .toEpochMilli()
    }

}