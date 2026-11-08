package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportRestorePlannerTest {

    private val target = ViewportState(firstVisiblePage = 18, zoom = 4.0f, scrollX = 300, scrollY = 62825)
    private val reset = ViewportState(0, 2.42f, 0, 0)

    private fun planner(stableMs: Long = 150, timeoutMs: Long = 3000) =
        ViewportRestorePlanner(target, startMs = 1000, timeoutMs = timeoutMs, stableMs = stableMs)

    @Test
    fun `tolerances - zoom 0_01 and scroll 2 px`() {
        val p = planner()
        assertTrue(p.matches(target.copy(zoom = 4.009f, scrollX = 302, scrollY = 62823)))
        assertFalse(p.matches(target.copy(zoom = 4.02f)))
        assertFalse(p.matches(target.copy(scrollX = 303)))
        assertFalse(p.matches(target.copy(scrollY = 62822)))
        assertFalse(p.matches(target.copy(firstVisiblePage = 17)))
    }

    @Test
    fun `retries until match then reveals after stable window`() {
        val p = planner(stableMs = 100)
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, reset, false))
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1016, reset, false))
        // збіг з'явився, але ще не стабільний
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1032, target, false))
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1100, target, false))
        assertEquals(RestoreDecision.MATCHED, p.onTick(1132, target, false))
    }

    @Test
    fun `stale match that is reset to page 0 does not reveal`() {
        val p = planner(stableMs = 100)
        // стара позиція ще збігається, потім бібліотека скидає скрол
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, target, false))
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1050, reset, false))
        // таймер стабільності почався заново
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1066, target, false))
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1160, target, false))
        assertEquals(RestoreDecision.MATCHED, p.onTick(1170, target, false))
    }

    @Test
    fun `zero stable window reveals on first match`() {
        val p = planner(stableMs = 0)
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, reset, false))
        assertEquals(RestoreDecision.MATCHED, p.onTick(1016, target, false))
    }

    @Test
    fun `times out after 3 s without a match`() {
        val p = planner()
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(3999, reset, false))
        assertEquals(RestoreDecision.TIMED_OUT, p.onTick(4000, reset, false))
    }

    @Test
    fun `match at the deadline wins only if stable, otherwise times out`() {
        val p = planner(stableMs = 100)
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(3900, target, false))
        assertEquals(RestoreDecision.MATCHED, p.onTick(4000, target, false))

        val q = planner(stableMs = 100)
        assertEquals(RestoreDecision.TIMED_OUT, q.onTick(4000, target, false))
    }

    @Test
    fun `user touch interrupts immediately even when matching`() {
        val p = planner()
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, reset, false))
        assertEquals(RestoreDecision.INTERRUPTED, p.onTick(1016, reset, true))
        assertEquals(RestoreDecision.INTERRUPTED, planner().onTick(1000, target, true))
    }

    @Test
    fun `touch has priority over timeout`() {
        assertEquals(RestoreDecision.INTERRUPTED, planner().onTick(9000, reset, true))
    }
}
