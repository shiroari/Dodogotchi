package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.model.Event
import io.b3.dodogotchi.model.State
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Tag("junit5")
class EvolutionTest {

    private fun newKeeper(level: Int = 0,
                          levelProgress: Int = 0,
                          evolutionInternalInMin: Long = 0L,
                          evolutionStartHour: Int = 0,
                          threshold: Int = 0,
                          scale: Int = 100,
                          lastEvolutionTimestamp: Long = 1) =
            Keeper(State(hp = 100,
                    level = level,
                    levelProgress = levelProgress,
                    message = "",
                    evolutionTimestamp = lastEvolutionTimestamp),
            Config(indicatorThresholdInDays = threshold,
                    indicatorThresholdMaxInDays = threshold + scale - 1,
                    evolutionInternalInMin = evolutionInternalInMin,
                    evolutionStartHour = evolutionStartHour))

    @Test
    fun shouldBeNewbornAndHealthyOnStart() {

        val keeper = newKeeper()

        val state = keeper.state

        assertAll(
                Executable { assertEquals(100, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldEvolveAfterGetting9Pt() {

        val keeper = newKeeper(scale = 10)

        // Progressing up to 9 points on level 0

        for (i in 1..9) { // gives 9 cycles
            keeper.updateWithEvent(Event(0, "Newbie"))
        }

        val state1 = keeper.state

        assertAll(
                Executable { assertEquals(0, state1.level) },
                Executable { assertEquals(9, state1.levelProgress) }
        )

        // Getting next level

        keeper.updateWithEvent(Event(0, "Getting older"))

        val state2 = keeper.state

        assertAll(
                Executable { assertEquals(1, state2.level) },
                Executable { assertEquals(0, state2.levelProgress) }
        )

        // Progressing up to 9 points on level 1

        for (i in 1..9) { // gives more 9 cycles
            keeper.updateWithEvent(Event(0, "Forever young"))
        }

        val state3 = keeper.state

        assertAll(
                Executable { assertEquals(1, state3.level) },
                Executable { assertEquals(9, state3.levelProgress) }
        )
    }

    @ParameterizedTest
    @ValueSource(ints = intArrayOf(0, 1, 2, 3, 4, 5, 6))
    fun shouldEvolveIfHealthEqualsOrGreaterThen40Percent(penalty: Int) {

        val keeper = newKeeper(scale = 10)

        for (i in 0..30) {
            keeper.updateWithEvent(Event(penalty, "It's fine"))
        }

        val state = keeper.state

        assertAll(
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(9, state.levelProgress) }
        )
    }

    @ParameterizedTest
    @ValueSource(ints = intArrayOf(7, 8, 9, 10))
    fun shouldNotEvolveIfHealthLessThen40Percent(penalty: Int) {

        val keeper = newKeeper(scale = 10)

        for (i in 0..30) {
            keeper.updateWithEvent(Event(penalty, "Sick"))
        }

        val state = keeper.state

        assertAll(
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldEvolveUpToLevel3() {

        val keeper = newKeeper(level = 2, levelProgress = 9)

        for (i in 1..10) {
            keeper.updateWithEvent(Event(0, "No more levels"))
        }

        val state = keeper.state

        assertAll(
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(9, state.levelProgress) }
        )
    }

    @Test
    fun shouldNotLoseEvolutionPointsIfDead() {

        val keeper = newKeeper(level = 2, levelProgress = 4, scale = 10)

        keeper.updateWithEvent(Event(10, "Die"))

        val state = keeper.state

        assertAll(
                Executable { assertEquals(0, state.hp) },
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(4, state.levelProgress) }
        )
    }

    @Test
    fun shouldResetEvolutionPointsAfterResurrecting() {

        val keeper = newKeeper(level = 2, levelProgress = 4, scale = 10)

        keeper.updateWithEvent(Event(10, "Die"))
        keeper.updateWithEvent(Event(0, ""))

        val state = keeper.state

        assertAll(
                Executable { assertEquals(100, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldEvolveWithLimitedSpeed() {

        val keeper = newKeeper(evolutionInternalInMin = 1440L)

        for (i in 1..10) {
            keeper.updateWithEvent(Event(0, ""))
        }

        val state = keeper.state

        assertAll(
                Executable { assertEquals(100, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(1, state.levelProgress) }
        )
    }

    @Test
    fun shouldUsePreviousStateIfEventIsEmpty() {

        val keeper = newKeeper(level = 2, levelProgress = 4, scale = 10)

        keeper.updateWithEvent(Event(5, ""))
        keeper.update()
        keeper.update()

        val state = keeper.state

        assertAll(
                Executable { assertEquals(50, state.hp) },
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(7, state.levelProgress) }
        )
    }

    @Test
    fun shouldAlignEvolutionTimeOnStart() {

        val keeper = newKeeper(evolutionStartHour = 10, lastEvolutionTimestamp = 0)

        keeper.update()

        val instant = Instant.ofEpochMilli(keeper.state.evolutionTimestamp)
        val datetime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())

        assertEquals(10, datetime.hour)
        assertEquals(0, datetime.minute)
    }

    @Test
    fun shouldAlignEvolutionTimeWhenEvolutionHappened() {

        val keeper = newKeeper(evolutionStartHour = 10, lastEvolutionTimestamp = 1)

        keeper.update()

        val instant = Instant.ofEpochMilli(keeper.state.evolutionTimestamp)
        val datetime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())

        assertEquals(10, datetime.hour)
        assertEquals(0, datetime.minute)
    }

    @Test
    fun shouldNotUpdateEvolutionTimeIfNoEvolutionHappened() {

        val now = Instant.now().toEpochMilli() + Duration.ofMinutes(5).toMillis()

        val keeper = newKeeper(lastEvolutionTimestamp = now)

        keeper.update()

        assertEquals(now, keeper.state.evolutionTimestamp)
    }

}