package ge.gogichaishvili.backgammon

import ge.gogichaishvili.backgammon.animation.*
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.*
import org.junit.Assert.*
import org.junit.Test

class ThemeAndMotionTest {
    @Test fun themesHaveStableUniqueIdsAndUnknownIdFallsBack() {
        assertEquals(listOf("classic", "royal", "ocean"), ThemeRegistry.all.map { it.id })
        assertEquals(3, ThemeRegistry.all.map { it.palette }.distinct().size)
        assertEquals(ThemeRegistry.classic, ThemeRegistry.resolve("future-removed-skin"))
    }
    @Test fun reducedMotionDisablesEveryVisualEffect() {
        MotionKind.entries.forEach {
            assertEquals(0, MotionPolicy(AnimationSpeed.RELAXED, reducedMotion = true).duration(it))
        }
    }
    @Test fun speedPolicyIsOrderedAndDeterministic() {
        MotionKind.entries.forEach {
            val quick = MotionPolicy(AnimationSpeed.QUICK).duration(it)
            val normal = MotionPolicy().duration(it)
            val relaxed = MotionPolicy(AnimationSpeed.RELAXED).duration(it)
            assertTrue(quick in 1 until normal)
            assertTrue(relaxed > normal)
            assertEquals(it.baseMillis, normal)
        }
    }
}
