package com.danycli.assignmentchecker

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class AssignmentLogicTest {

    @Test
    fun `test Assignment isOpen returns true for future deadline`() {
        // Deadline is in the future relative to the default 'now'
        val futureDeadline = "Dec 31 ,2099 23:59"
        val assignment = Assignment(
            courseTitle = "Test Course",
            assignmentTitle = "Test Assignment",
            deadline = futureDeadline,
            downloadLink = "",
            submitLink = "",
            status = AssignmentStatus.PENDING
        )
        assertTrue(assignment.isOpen())
    }

    @Test
    fun `test Assignment isOpen returns false for past deadline`() {
        // Deadline is in the past
        val pastDeadline = "Jan 01 ,2000 00:00"
        val assignment = Assignment(
            courseTitle = "Test Course",
            assignmentTitle = "Test Assignment",
            deadline = pastDeadline,
            downloadLink = "",
            submitLink = "",
            status = AssignmentStatus.PENDING
        )
        assertFalse(assignment.isOpen())
    }

    @Test
    fun `test detectNewAssignments identifies added assignments in pending`() {
        val a1 = Assignment("Course 1", "Assignment 1", "Jan 01 ,2025 00:00", "link1", "slink1")
        val a2 = Assignment("Course 2", "Assignment 2", "Jan 01 ,2025 00:00", "link2", "slink2")
        val a3 = Assignment("Course 3", "Assignment 3", "Jan 01 ,2025 00:00", "link3", "slink3")

        val previousSnapshot = CachedAssignmentsSnapshot(
            pendingAssignments = listOf(a1),
            historicalAssignments = emptyList(),
            studentName = "Test",
            cachedAtEpochMs = System.currentTimeMillis()
        )

        val currentPending = listOf(a1, a2)
        val currentHistorical = listOf(a3)

        val newAssignments = detectNewAssignments(previousSnapshot, currentPending, currentHistorical)

        // Currently detectNewAssignments ONLY filters 'pending' list.
        // So only a2 is detected as new.
        assertEquals(1, newAssignments.size)
        assertTrue(newAssignments.any { it.assignmentTitle == "Assignment 2" })
    }

    @Test
    fun `test assignmentDeadlineEpoch parsing`() {
        val deadline = "Oct 25 ,2023 14:30"
        val epoch = assignmentDeadlineEpoch(deadline)
        assertNotNull(epoch)
        // Check if it's reasonably close to the expected value or just not null
        assertTrue(epoch!! > 0)
    }

    @Test
    fun `test detectNewAssignments returns empty for no changes`() {
        val a1 = Assignment("Course 1", "Assignment 1", "Jan 01 ,2025 00:00", "link1", "slink1")
        val snapshot = CachedAssignmentsSnapshot(listOf(a1), emptyList(), "Test", System.currentTimeMillis())
        val new = detectNewAssignments(snapshot, listOf(a1), emptyList())
        assertTrue(new.isEmpty())
    }
}
