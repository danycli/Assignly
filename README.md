<p align="center">
  <img width="200" height="200" alt="Assignly Logo" src="https://github.com/user-attachments/assets/24240133-4ae3-49c5-951c-91e247320900"/>
</p>

<h1 align="center">Assignly — COMSATS Student Portal Client</h1>

<h3 align="center">
🏆 1st Prize Winner • Tech Fusion 2026
</h3>

<p align="center">
A native Android client for COMSATS University Islamabad, Abbottabad Campus (CUIATD)<br/>
to manage academic life, track attendance, view results, and submit assignments without ever opening a browser.
</p>

<p align="center">

<img src="https://img.shields.io/badge/🏆_Tech_Fusion-2026_Winner-F6C343?style=for-the-badge" />
<img src="https://img.shields.io/badge/🥇-1st_Prize-success?style=for-the-badge" />

</p>

<p align="center">

<img src="https://img.shields.io/badge/Platform-Android%208.0+-brightgreen?style=flat-square"/>
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square"/>
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square"/>
<img src="https://img.shields.io/badge/Min%20SDK-API%2026-orange?style=flat-square"/>
<img src="https://img.shields.io/badge/Target%20SDK-API%2035-blue?style=flat-square"/>

</p>

---

> ## 🏆 Award Recognition
>
> **Assignly won 🥇 1st Prize at Tech Fusion 2026**, a university technology competition recognizing innovative software projects with real-world impact.
>
> The project was selected for transforming the COMSATS Student Information System into a modern native Android experience focused on usability, performance, and productivity for students.

## Overview

Assignly is an all-in-one Android client for the [COMSATS Student Information System (SIS)](https://sis.cuiatd.edu.pk) portal. It transforms the clunky web experience into a native mobile app — complete with a central dashboard, attendance insights, result tracking, fee management, and a smart timetable.

No more fighting with broken mobile web layouts, missed deadlines, or forgotten lecture rooms.

---

## Key Features

### Central Academic Dashboard

The heart of the app. A unified hub that surfaces your entire academic status:

- **Student Identity Card**: Shows your profile photo, registration number, program, and section.
- **Smart Badge**: Automatically detects and highlights your **Scholarship Status**.
- **Academic Snapshot**: At-a-glance view of your current **CGPA**, average **Attendance**, and total **Registered Credits**.
- **Quick Shortcuts**: One-tap access to all portal modules (Timetable, Result, Fee, etc.).

---

### Timetable & Class Tracker

A live timetable that ensures you're never late for a lecture:

- **Live "Now" Indicator**: Highlights the class currently in progress with a real-time progress bar.
- **Next Class Widget**: Shows exactly what's coming up next and how long until it starts.
- **Swipeable Days**: Smoothly navigate between Monday and Saturday.
- **Offline Caching**: Your schedule is saved locally; check your room number even without internet.
- **Smart Reminders**: Automated notifications before each class starts.

---

### Attendance Tracker

Stay on top of your presence:

- **Aggregate Percentage**: See your total attendance average across all courses.
- **Detailed Logs**: Drill down into any course to see a full history of presents, absences, and leaves.
- **Attendance Insights**: Displays a sarcastic (but helpful) status message based on your attendance levels.

---

### Assignments Management

Manage your submissions with ease:

- **Clean Search & Filters**: Quickly find assignments by subject or filter by **Due Today**, **Next 3 Days**, or **Next 7 Days**.
- **Long-Press Actions**: Copy titles or jump directly to the course portal via a context menu.
- **Instruction Downloads**: One-tap fetch for instruction files (handles redirects and PostBack triggers).
- **Flexible Uploads**: Pick any file and upload with background support. Refresh is automatic.
- **Submission History**: A dedicated screen for submitted and closed assignments with instructor feedback.

---

### Academic Results & Marks

Check your performance without the stress:

- **Result History**: View your GPA/CGPA and course grades for every completed semester.
- **Internal Marks**: Real-time view of Sessional 1, Sessional 2, Quizzes, and Assignments marks.
- **Marks Notifications**: Get notified the moment a professor uploads a new sessional or quiz result.

---

### Downloads Manager

Keep all your course files organized locally:

- **Centralized Storage**: Access all your downloaded PDFs, PPTs, and Word docs in one place.
- **Offline Access**: View your files even without internet connectivity.
- **File Actions**: Rename, share, or delete downloaded files directly from the app.
- **Concurrent Downloads**: Safely download multiple files simultaneously with a resilient background queue.

---

### Secure & Private

- **Biometric App Lock**: Secure your academic data with Fingerprint/Face ID.
- **Encrypted Storage**: Credentials are stored using **Android AES-256 encryption**.
- **Password Management**: Change your portal password directly from the app with built-in rule validation.
- **Session Sync**: Smart WebView session syncing for CAPTCHA handling and login persistence.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Networking | OkHttp 4 |
| HTML Parsing | Jsoup 1.18 |
| Credential Storage | AndroidX Security Crypto (EncryptedSharedPreferences) |
| Background Work | AndroidX WorkManager |
| Biometrics | AndroidX Biometric |
| Build System | Gradle (Kotlin DSL) |
| Target SDK | Android 15 (API 35) |

---

## Project Structure

```
app/src/main/java/com/danycli/assignmentchecker/
├── MainActivity.kt              # App entry, biometric lock & navigation
├── MainViewModel.kt             # Shared state & portal communication bridge
├── PortalRepository.kt          # Networking layer (Login, Scrapers, Uploads)
├── PortalUseCases.kt            # Clean architecture business logic
│
├── AppSettingsStore.kt          # User preferences & theme settings
├── CredentialsStore.kt          # Encrypted credential persistence
├── AssignmentCacheStore.kt      # Offline caches for all portal data
├── TimetableCacheStore.kt       # (Attendance, Marks, Grades, Profile, etc.)
│
├── AssignmentNotifications.kt   # System notifications & alerts
├── BackgroundSyncWork.kt        # Periodic data refresh worker
├── DownloadWork.kt              # Background file download worker
├── UploadWork.kt                # Background submission worker
│
├── AppUpdateChecker.kt          # GitHub Releases version checker
├── SecurityUtils.kt             # Crypto & Biometric helpers
│
└── ui/
    ├── DashboardScreen.kt       # Central hub (inside AssignmentsScreen.kt)
    ├── AssignmentsScreen.kt     # Pending assignments & filters
    ├── HistoryScreen.kt         # Submitted assignment logs
    ├── TimetableScreen.kt       # Live class schedule
    ├── CourseDetailScreen.kt    # Course content & premium UI details
    ├── DownloadsScreen.kt       # Downloaded files manager (Rename, Share, Delete)
    ├── AttendanceScreen.kt      # Attendance logs & insights
    ├── GradesScreen.kt          # Result history (GPA/CGPA)
    ├── MarksScreen.kt           # Internal sessionals/quizzes marks
    ├── FeeScreen.kt             # Fee status & challan downloads
    ├── ProfileScreen.kt         # Detailed student information
    ├── LoginScreen.kt           # Portal authentication
    ├── AppLockScreen.kt         # Biometric security UI
    └── theme/                   # Material 3 design system
```

---

## Registration Number Format

```
SP25-BCS-001
│    │    └── Roll number (zero-padded)
│    └────── Program code (e.g. BCS, BEE, BBA)
└─────────── Session (SP = Spring, FA = Fall + 2-digit year)
```

The app automatically normalises your input and handles the portal's complex session selection logic for you.

---

---

# 🏅 Achievements

<div align="center">

| Award | Event | Year |
|:------:|:------|:----:|
| 🥇 **1st Prize** | **Tech Fusion** | **2026** |

</div>

> This recognition reflects the project's focus on solving real problems faced by COMSATS students through thoughtful design, modern Android development, and practical functionality.


## Disclaimer

This is an **unofficial third-party client** for the COMSATS SIS portal. It is not affiliated with, endorsed by, or maintained by COMSATS University. Use it responsibly and in accordance with your institution's policies.

---

## Author

Built by **danycli** — because academic life is hard enough without a bad website.

*Made with frustration, caffeine, and Jetpack Compose.*
