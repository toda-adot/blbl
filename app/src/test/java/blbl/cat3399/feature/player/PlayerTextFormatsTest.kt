package blbl.cat3399.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTextFormatsTest {
    @Test
    fun pickQnByQualityOrder_should_pick_exact_match_when_available() {
        val picked = pickQnByQualityOrder(availableQns = listOf(64, 80, 112, 120), desiredQn = 112)
        assertEquals(112, picked)
    }

    @Test
    fun pickQnByQualityOrder_should_fallback_to_best_not_above_desired() {
        val picked = pickQnByQualityOrder(availableQns = listOf(80, 120), desiredQn = 112)
        assertEquals(80, picked)
    }

    @Test
    fun pickQnByQualityOrder_should_pick_lowest_when_all_available_are_above_desired() {
        val picked = pickQnByQualityOrder(availableQns = listOf(120, 127), desiredQn = 80)
        assertEquals(120, picked)
    }
}
