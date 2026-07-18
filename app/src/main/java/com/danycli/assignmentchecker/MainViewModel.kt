package com.danycli.assignmentchecker

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.lifecycle.ViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

open class MainViewModel : ViewModel() {
    private val repository = PortalRepository()
    private val loadDashboardUseCase = LoadDashboardUseCase(repository)
    private val loadHistoryUseCase = LoadHistoryUseCase(repository)
    private val loginUseCase = LoginUseCase(repository)
    private val fetchInstructionFilesUseCase = FetchInstructionFilesUseCase(repository)
    private val downloadAssignmentUseCase = DownloadAssignmentUseCase(repository)
    private val uploadAssignmentUseCase = UploadAssignmentUseCase(repository)
    private val portalSessionUseCase = PortalSessionUseCase(repository)
    private val loadTimetableUseCase = LoadTimetableUseCase(repository)
    private val loadEnrolledCoursesUseCase = LoadEnrolledCoursesUseCase(repository)
    private val loadCourseFilesUseCase = LoadCourseFilesUseCase(repository)
    private val changePasswordUseCase = ChangePasswordUseCase(repository)
    private val fetchPasswordRulesUseCase = FetchPasswordRulesUseCase(repository)

    open suspend fun loadDashboardData(): DashboardLoadResult = loadDashboardUseCase()

    open suspend fun loadHistoricalAndProfile(): Pair<List<Assignment>, ByteArray?> = loadHistoryUseCase()

    open suspend fun loadTimetable(): List<TimetableLecture> = loadTimetableUseCase()

    open suspend fun loadEnrolledCourses(): EnrolledCoursesData = loadEnrolledCoursesUseCase()

    open suspend fun loadCourseFiles(courseCode: String, courseTitle: String): List<CourseFile> = loadCourseFilesUseCase(courseCode, courseTitle)

    open suspend fun changePassword(currentPass: String, newPass: String, confirmPass: String): Result<String> =
        changePasswordUseCase(currentPass, newPass, confirmPass)

    open suspend fun fetchPasswordRules(): String =
        fetchPasswordRulesUseCase()

    open suspend fun loadAttendanceSummary(resolvedCodes: Map<String, String>? = null): List<AttendanceSummary> = withContext(Dispatchers.IO) {
        repository.fetchAttendanceSummary(resolvedCodes)
    }

    open suspend fun loadAttendanceDetail(courseCode: String): List<AttendanceDetail> = withContext(Dispatchers.IO) {
        repository.fetchAttendanceDetail(courseCode)
    }

    open suspend fun loadGrades(): GpaSummary = withContext(Dispatchers.IO) {
        repository.fetchGrades()
    }

    open suspend fun loadMarks(courseCode: String): List<MarksCategory> = withContext(Dispatchers.IO) {
        repository.fetchMarks(courseCode)
    }

    open suspend fun fetchPageHtmlDebug(page: String): String = withContext(Dispatchers.IO) {
        repository.fetchPageHtmlDebug(page)
    }

    open suspend fun loadProfile(): StudentProfile = withContext(Dispatchers.IO) {
        repository.fetchStudentProfile()
    }

    open suspend fun loadPhotoBytes(): ByteArray? = withContext(Dispatchers.IO) {
        repository.fetchCurrentStudentPhoto()
    }

    open suspend fun loadFeeDetails(): FeeSnapshot = withContext(Dispatchers.IO) {
        repository.fetchFeeDetails()
    }

    open suspend fun login(username: String, password: String): LoginResult = loginUseCase(username, password)

    open suspend fun fetchInstructionFiles(downloadLink: String): InstructionFilesResult =
        fetchInstructionFilesUseCase(downloadLink)

    open suspend fun downloadAssignment(downloadLink: String): DownloadResult =
        downloadAssignmentUseCase(downloadLink)

    open suspend fun uploadAssignment(submitLink: String, file: File): UploadResult =
        uploadAssignmentUseCase(submitLink, file)

    open fun getCurrentStudentName(): String? = repository.getCurrentStudentName()

    open fun getPortalBaseUrl(): String = portalSessionUseCase.getPortalBaseUrl()

    open fun getPortalLoginUrl(): String = portalSessionUseCase.getPortalLoginUrl()

    open suspend fun ensureSessionValid() {
        portalSessionUseCase.ensureSessionValid()
    }

    open fun setUserAgentForSession(userAgent: String) {
        portalSessionUseCase.setUserAgentForSession(userAgent)
    }

    open fun injectCookiesFromWebView(cookieHeader: String?, url: String): Int {
        return portalSessionUseCase.injectCookiesFromWebView(cookieHeader, url)
    }

    open fun getSessionCookiesList(url: String): List<String> {
        return portalSessionUseCase.getSessionCookiesList(url)
    }

    open suspend fun isSecurityVerificationStillRequired(): Boolean {
        return portalSessionUseCase.isSecurityVerificationStillRequired()
    }

    open suspend fun syncWebViewSession(context: Context) {
        withContext(Dispatchers.Main) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            val browserUserAgent = runCatching { WebSettings.getDefaultUserAgent(context) }
                .getOrDefault(BuildConfig.PORTAL_USER_AGENT)
            setUserAgentForSession(browserUserAgent)
            val portalBaseUrl = getPortalBaseUrl()
            val portalLoginUrl = getPortalLoginUrl()
            val portalOrigin = runCatching {
                val parsed = portalLoginUrl.toHttpUrl()
                "${parsed.scheme}://${parsed.host}"
            }.getOrDefault(portalBaseUrl)
            linkedSetOf(portalBaseUrl, portalLoginUrl, portalOrigin).forEach { sourceUrl ->
                injectCookiesFromWebView(cookieManager.getCookie(sourceUrl), sourceUrl)
            }
        }
    }
}
