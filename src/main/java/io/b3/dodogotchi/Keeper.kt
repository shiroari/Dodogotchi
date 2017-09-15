package io.b3.dodogotchi

import java.time.Duration
import java.time.Instant

class Keeper(initModel: Model, private val conf: Config) {

    companion object {
        const val MAX_HEALTH = 100
    }

    lateinit var model: Model

    init {
        updateModel(initModel)
    }

    fun update() {
        updateModel(handleEventInt(null))
    }

    fun updateWithEvent(event: Event) {
        updateModel(handleEventInt(event))
    }

    private fun updateModel(newState: Model) {
        println(newState)
        model = newState
    }

    private fun handleEventInt(event: Event?) : Model {

        val now = Instant.now().toEpochMilli()

        var penalty = event?.overdueMax?: model.penalty
        var radar = event?.overdueNumber?: model.radar

        var hp = MAX_HEALTH - (MAX_HEALTH * penalty / conf.deathThresholdInDays)
        var stagnation = (penalty > conf.stagnationThreshold)

        var evolutionLevel = model.evolutionLevel
        var evolutionStage = model.evolutionStage
        var evolutionTimestamp = model.evolutionTimestamp

        if (hp <= 0) {
            hp = 0
            evolutionLevel = 0
            evolutionStage = 0
        }

        if (!stagnation
                && model.updatedAt != 0L
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

        return Model(hp,
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