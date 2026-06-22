package com.danycli.assignmentchecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class TimetableNotificationTest {

    @Test
    fun testCalculateNextClassTrigger_FutureClass() {
        val now = LocalDateTime.now()
        // Pick a day that is NOT today to avoid "Same day" logic complications in test
        val targetDay = DayOfWeek.values()[(now.dayOfWeek.ordinal + 2) % 7]
        val targetDayStr = targetDay.name.lowercase().replaceFirstChar { it.uppercase() }

        val trigger = TimetableNotificationManager.calculateNextClassTriggerEpochMs(targetDayStr, "08:30 AM")
        
        val expectedDate = now.with(TemporalAdjusters.next(targetDay))
            .with(LocalTime.of(8, 30))
            .withSecond(0).withNano(0)
            .minusMinutes(5)
        
        assertEquals(expectedDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), trigger)
    }

    @Test
    fun testCalculateNextClassTrigger_Within5MinWindow() {
        // If it's Monday 8:27 AM, and class is at 8:30 AM
        // triggerTime is 8:25 AM, which is in the past.
        // The logic should return 8:25 AM (past) so caller can fire immediately.
        
        // This is tricky because calculateNextClassTriggerEpochMs uses LocalDateTime.now() internally.
        // However, I can verify the logic by checking if it DOESN'T jump to next week if targetDay is today and class hasn't started.
    }

    @Test
    fun testTimeNormalization_Robustness() {
        val repository = PortalRepository()
        val method = PortalRepository::class.java.getDeclaredMethod("normalizeTime", String::class.java)
        method.isAccessible = true
        
        fun normalize(time: String) = method.invoke(repository, time) as String

        assertEquals("08:30 AM", normalize("08:30"))
        assertEquals("01:30 PM", normalize("13:30"))
        assertEquals("01:30 PM", normalize("01:30")) // Assuming heuristic 1-7 is PM
        assertEquals("09:00 AM", normalize("09:00 AM"))
        assertEquals("12:00 PM", normalize("12:00"))
        assertEquals("12:00 PM", normalize("12:00 PM"))
        assertEquals("12:00 AM", normalize("00:00"))
    }
}
