package com.danycli.assignmentchecker

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class DashboardLoadResult(
    val pendingAssignments: List<Assignment>,
    val historicalAssignments: List<Assignment>,
    val profilePhoto: ByteArray?,
    val weakestAttendanceInsight: AttendanceInsight?
)

class LoadDashboardUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(): DashboardLoadResult {
        return withContext(Dispatchers.IO) {
            val (pending, submitted) = repository.fetchAssignments()
            DashboardLoadResult(
                pendingAssignments = pending,
                historicalAssignments = submitted,
                profilePhoto = repository.fetchCurrentStudentPhoto(),
                weakestAttendanceInsight = repository.fetchLowestAttendanceInsight()
            )
        }
    }
}

class LoadHistoryUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(): Pair<List<Assignment>, ByteArray?> {
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val historicalDeferred = async { repository.fetchHistoricalAssignments() }
                val photoDeferred = async { repository.fetchCurrentStudentPhoto() }
                Pair(historicalDeferred.await(), photoDeferred.await())
            }
        }
    }
}

class LoginUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(username: String, password: String): LoginResult {
        return withContext(Dispatchers.IO) {
            repository.login(username, password)
        }
    }
}

class FetchInstructionFilesUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(downloadLink: String): InstructionFilesResult {
        return withContext(Dispatchers.IO) {
            repository.fetchInstructionFiles(downloadLink)
        }
    }
}

class DownloadAssignmentUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(downloadLink: String): DownloadResult {
        return withContext(Dispatchers.IO) {
            repository.downloadAssignment(downloadLink)
        }
    }
}

class UploadAssignmentUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(submitLink: String, file: File): UploadResult {
        return withContext(Dispatchers.IO) {
            repository.uploadAssignment(submitLink, file)
        }
    }
}

class PortalSessionUseCase(private val repository: PortalRepository) {
    fun getPortalBaseUrl(): String = repository.getPortalBaseUrl()

    fun getPortalLoginUrl(): String = repository.getPortalLoginUrl()

    fun setUserAgentForSession(userAgent: String) {
        repository.setUserAgentForSession(userAgent)
    }

    fun injectCookiesFromWebView(cookieHeader: String?, url: String): Int {
        return repository.injectCookiesFromWebView(cookieHeader, url)
    }

    suspend fun isSecurityVerificationStillRequired(): Boolean {
        return withContext(Dispatchers.IO) {
            repository.isSecurityVerificationStillRequired()
        }
    }
}

class LoadTimetableUseCase(private val repository: PortalRepository) {
    suspend operator fun invoke(): List<TimetableLecture> {
        return withContext(Dispatchers.IO) {
            repository.fetchTimetable()
        }
    }
}
