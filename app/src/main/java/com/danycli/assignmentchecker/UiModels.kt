package com.danycli.assignmentchecker

data class InstructionFileDialogState(
    val assignment: Assignment,
    val files: List<InstructionFile>
)

enum class ScreenType {
    PENDING, DOWNLOADS, HISTORICAL, SETTINGS, ATTENDANCE, GRADES, MARKS, PROFILE, FEE, ENROLLED_COURSES, ASSIGNMENTS, TIMETABLE, CHANGE_PASSWORD, INSTRUCTION_FILES, UPLOADING
}

enum class AppPage {
    LOGIN, PENDING, DOWNLOADS, HISTORICAL, SETTINGS, ATTENDANCE, GRADES, MARKS, PROFILE, FEE, ENROLLED_COURSES, ASSIGNMENTS, TIMETABLE, CHANGE_PASSWORD
}

enum class PendingDueFilter {
    ALL, TODAY, NEXT_3_DAYS, NEXT_7_DAYS
}

enum class PendingStatusFilter {
    ALL, OPEN, CLOSED
}

enum class UpdateCheckResult {
    UPDATE_AVAILABLE, UP_TO_DATE, ERROR
}
