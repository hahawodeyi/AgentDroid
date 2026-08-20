package com.appia.ai.trigger

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerSchedulerTest {

    private fun millisAt(hour: Int, minute: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `future time today stays today`() {
        val now = millisAt(8, 0)
        val result = TriggerScheduler.nextTriggerMillis(18, 30, now)
        assertEquals(millisAt(18, 30), result)
    }

    @Test
    fun `past time today rolls to tomorrow`() {
        val now = millisAt(18, 0)
        val result = TriggerScheduler.nextTriggerMillis(8, 0, now)
        assertEquals(millisAt(8, 0, dayOffset = 1), result)
    }

    @Test
    fun `exact current time rolls to tomorrow`() {
        val now = millisAt(8, 0)
        val result = TriggerScheduler.nextTriggerMillis(8, 0, now)
        assertTrue(result > now)
    }
}
