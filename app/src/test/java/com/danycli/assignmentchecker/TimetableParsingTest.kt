package com.danycli.assignmentchecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

class TimetableParsingTest {

    private val repository = PortalRepository()

    private fun invokeParseTimetable(html: String): List<TimetableLecture> {
        val method: Method = PortalRepository::class.java.getDeclaredMethod("parseTimetableFromHtml", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(repository, html) as List<TimetableLecture>
    }

    @Test
    fun testListLayoutParsing() {
        val html = """
            <table>
                <tr>
                    <th>Day</th>
                    <th>Time</th>
                    <th>Subject</th>
                    <th>Room</th>
                    <th>Teacher</th>
                </tr>
                <tr>
                    <td>Monday</td>
                    <td>08:30-10:00</td>
                    <td>CSC301 Theory of Automata</td>
                    <td>A-12</td>
                    <td>Dr. Hassan</td>
                </tr>
            </table>
        """.trimIndent()

        val lectures = invokeParseTimetable(html)
        assertEquals(1, lectures.size)
        assertEquals("Monday", lectures[0].day)
        assertEquals("08:30 AM", lectures[0].startTime)
        assertEquals("10:00 AM", lectures[0].endTime)
        assertEquals("CSC301 Theory of Automata", lectures[0].courseName)
        assertEquals("A-12", lectures[0].room)
    }

    @Test
    fun testMatrixLayoutParsing() {
        val html = """
            <table>
                <thead>
                    <tr>
                        <th>Time</th>
                        <th>Monday</th>
                        <th>Tuesday</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>08:30 - 10:00</td>
                        <td>CSC301<br/>Automata<br/>Room A-12</td>
                        <td></td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val lectures = invokeParseTimetable(html)
        assertEquals(1, lectures.size)
        assertEquals("Monday", lectures[0].day)
        assertEquals("08:30 AM", lectures[0].startTime)
        assertEquals("10:00 AM", lectures[0].endTime)
        assertTrue(lectures[0].courseName.contains("CSC301"))
        assertTrue(lectures[0].room.contains("A-12"))
    }

    @Test
    fun testTimeNormalization() {
        val method: Method = PortalRepository::class.java.getDeclaredMethod("normalizeTime", String::class.java)
        method.isAccessible = true
        
        fun normalize(time: String) = method.invoke(repository, time) as String

        assertEquals("08:30 AM", normalize("08:30"))
        assertEquals("01:30 PM", normalize("01:30"))
        assertEquals("08:30 AM", normalize("8:30"))
        assertEquals("02:00 PM", normalize("2:00 PM"))
    }
}
