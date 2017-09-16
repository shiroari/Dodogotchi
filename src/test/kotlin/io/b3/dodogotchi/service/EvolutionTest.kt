package io.b3.dodogotchi.service

import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.model.Event
import io.b3.dodogotchi.model.State
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Tag("junit5")
class EvolutionTest {

    fun newKeeper(level: Int = 0,
                  levelProgress: Int = 0,
                  evolutionInternalInMin: Long = 0L,
                  evolutionStartHour: Int = 0) =
            Keeper(State(hp = 100,
                    level = level,
                    levelProgress = levelProgress,
                    message = "",
                    evolutionTimestamp = 0),
            Config(indicatorThresholdInDays = 2,
                    indicatorThresholdMaxInDays = 12,
                    evolutionInternalInMin = evolutionInternalInMin,
                    evolutionStartHour = evolutionStartHour))

    @Test
    fun shouldBeNewbornAndHealthyOnStartIfStateIsOk() {

        val keeper = newKeeper()

        keeper.updateWithEvent(Event(0, "Hello world"))

        val state = keeper.state

        assertAll(
                Executable { assertEquals(100, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldBeDeadOnStartIfStateIsNotOk() {

        val keeper = newKeeper()

        keeper.updateWithEvent(Event(12, "Bye bye world"))

        val state = keeper.state

        assertAll(
                Executable { assertEquals(0, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldEvolveIfStateIsOk() {

        val keeper = newKeeper()

        keeper.updateWithEvent(Event(0, ""))
        keeper.update()
        keeper.update()

        val state1 = keeper.state

        assertAll(
                Executable { assertEquals(100, state1.hp) },
                Executable { assertEquals(0, state1.level) },
                Executable { assertEquals(2, state1.levelProgress) }
        )

        for (i in 3..10) {
            keeper.updateWithEvent(Event(8, ""))
        }

        val state2 = keeper.state

        assertAll(
                Executable { assertEquals(40, state2.hp) },
                Executable { assertEquals(1, state2.level) },
                Executable { assertEquals(0, state2.levelProgress) }
        )
    }

    @Test
    fun shouldNotEvolveIfSick() {

        val keeper = newKeeper()

        for (i in 0..5) {
            keeper.updateWithEvent(Event(9, "Sick"))
        }

        val state = keeper.state

        assertAll(
                Executable { assertEquals(30, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldEvolveUpToLevel3() {

        val keeper = newKeeper(level = 2, levelProgress = 0)

        for (i in 0..11) {
            keeper.updateWithEvent(Event(0, ""))
        }

        val state = keeper.state

        assertAll(
                Executable { assertEquals(100, state.hp) },
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(9, state.levelProgress) }
        )
    }

    @Test
    fun shouldNotLoseEvolutionPointsIfDead() {

        val keeper = newKeeper(level = 2, levelProgress = 4)

        keeper.updateWithEvent(Event(12, "Die"))

        val state = keeper.state

        assertAll(
                Executable { assertEquals(0, state.hp) },
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(4, state.levelProgress) }
        )
    }

    @Test
    fun shouldResetEvolutionPointsAfterResurrecting() {

        val keeper = newKeeper(level = 2, levelProgress = 4)

        keeper.updateWithEvent(Event(12, "Die"))
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

        keeper.updateWithEvent(Event(0, ""))
        keeper.update()
        keeper.update()
        keeper.update()

        val state = keeper.state

        assertAll(
                Executable { assertEquals(100, state.hp) },
                Executable { assertEquals(0, state.level) },
                Executable { assertEquals(0, state.levelProgress) }
        )
    }

    @Test
    fun shouldDecreaseHPIfThresholdHasBeenExceeded() {

        val keeper = newKeeper()

        keeper.updateWithEvent(Event(2, "Ok"))
        assertEquals(100, keeper.state.hp)

        keeper.updateWithEvent(Event(3, "Still Ok"))
        assertEquals(90, keeper.state.hp)

        keeper.updateWithEvent(Event(10, "Sick"))
        assertEquals(20, keeper.state.hp)
    }

    @Test
    fun shouldUsePreviousStateIfEventIsEmpty() {

        val keeper = newKeeper(level = 2, levelProgress = 4)

        keeper.updateWithEvent(Event(7, ""))
        keeper.update()
        keeper.update()

        val state = keeper.state

        assertAll(
                Executable { assertEquals(50, state.hp) },
                Executable { assertEquals(2, state.level) },
                Executable { assertEquals(6, state.levelProgress) }
        )
    }

    @Test
    fun shouldAlignEvolutionTimeToConfiguration() {

        val keeper = newKeeper(evolutionStartHour = 10)

        keeper.update()

        val instant = Instant.ofEpochMilli(keeper.state.evolutionTimestamp)
        val datetime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC.normalized())

        assertEquals(10, datetime.hour)
        assertEquals(0, datetime.minute)
    }

}