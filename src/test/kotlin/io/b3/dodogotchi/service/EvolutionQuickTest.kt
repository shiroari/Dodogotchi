package io.b3.dodogotchi.service

import fj.test.Arbitrary
import fj.test.Gen
import fj.test.Property
import fj.test.Property.prop
import fj.test.Property.property
import fj.test.reflect.CheckParams
import fj.test.runner.PropertyTestRunner
import io.b3.dodogotchi.config.Config
import io.b3.dodogotchi.model.Event
import io.b3.dodogotchi.model.State
import org.junit.runner.RunWith

@RunWith(PropertyTestRunner::class)
@CheckParams(maxSize = 100)
class EvolutionQuickTest {

    private fun newKeeper(level: Int = 0,
                          levelProgress: Int = 0,
                          evolutionInternalInMin: Long = 0L,
                          evolutionStartHour: Int = 0,
                          threshold: Int = 0,
                          scale: Int = 100) = Keeper(
                    State(hp = 100,
                            level = level,
                            levelProgress = levelProgress,
                            message = "",
                            evolutionTimestamp = 1),
                    Config(indicatorThresholdInDays = threshold,
                            indicatorThresholdMaxInDays = threshold + scale - 1,
                            evolutionInternalInMin = evolutionInternalInMin,
                            evolutionStartHour = evolutionStartHour))

    companion object {
        val penaltyGen: Gen<Int> = Arbitrary.arbIntegerBoundaries
    }

    fun shouldReturnHealthFrom0To100(): Property = property<Int>(penaltyGen) { penalty ->

        val scale = 300
        val keeper = newKeeper(scale = scale)

        keeper.updateWithEvent(Event(penalty, "Hello world"))

        val state = keeper.state

        val hp = when {
            penalty < 0 -> 100
            penalty > scale -> 0
            else -> 100 * (scale - penalty) / scale
        }

        prop(hp == state.hp)
    }

}