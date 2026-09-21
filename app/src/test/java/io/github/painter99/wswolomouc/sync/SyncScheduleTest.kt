package io.github.painter99.wswolomouc.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sync schedule tests (M1.7, PRD G5/F2.3/F1.5) — pure JVM, tabulated.
 *
 * Rules under test:
 *  - default spec: 15 min interval (WorkManager periodic minimum), 5 min flex
 *  - interval below the WorkManager minimum (e.g. 10 min) is clamped to 15 min
 *  - a larger interval (e.g. 30 min) is kept as-is
 *  - a non-positive interval is clamped to the minimum
 *  - flex is always positive and strictly smaller than the interval
 */
class SyncScheduleTest {

    @Test
    fun `default spec is 15 min interval with 5 min flex`() {
        val spec = SyncSchedule.spec()

        assertEquals(15L, spec.intervalMinutes)
        assertEquals(5L, spec.flexMinutes)
    }

    @Test
    fun `interval below WorkManager minimum is clamped to 15 min`() {
        val spec = SyncSchedule.spec(intervalMinutes = 10L)

        assertEquals(15L, spec.intervalMinutes)
    }

    @Test
    fun `larger interval is kept`() {
        val spec = SyncSchedule.spec(intervalMinutes = 30L)

        assertEquals(30L, spec.intervalMinutes)
        assertEquals(5L, spec.flexMinutes)
    }

    @Test
    fun `non-positive interval is clamped to minimum`() {
        assertEquals(15L, SyncSchedule.spec(intervalMinutes = 0L).intervalMinutes)
        assertEquals(15L, SyncSchedule.spec(intervalMinutes = -5L).intervalMinutes)
    }

    @Test
    fun `flex is always positive and smaller than interval`() {
        for (interval in listOf(10L, 15L, 20L, 30L, 60L)) {
            val spec = SyncSchedule.spec(intervalMinutes = interval)
            assertEquals(true, spec.flexMinutes in 1 until spec.intervalMinutes)
        }
    }
}
