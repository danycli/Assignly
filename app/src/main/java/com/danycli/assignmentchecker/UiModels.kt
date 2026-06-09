package com.danycli.assignmentchecker

data class InstructionFileDialogState(
    val assignment: Assignment,
    val files: List<InstructionFile>
)

enum class ScreenType {
    PENDING, DOWNLOADS, HISTORICAL, SETTINGS
}

enum class AppPage {
    LOGIN, PENDING, DOWNLOADS, HISTORICAL, SETTINGS
}

enum class PendingDueFilter {
    ALL, TODAY, NEXT_3_DAYS, NEXT_7_DAYS
}

enum class PendingStatusFilter {
    ALL, OPEN, CLOSED
}
