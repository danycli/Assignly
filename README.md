<p align="center">
  <!-- <img src="app/src/main/ic_launcher-playstore.png" width="200" height="200" alt="Assignly Logo"/> -->
  <img width="200" height="200" alt="logo" src="https://github.com/user-attachments/assets/24240133-4ae3-49c5-951c-91e247320900" />
</p>

<h1 align="center">Assignly — COMSATS Assignment Portal Client</h1>

<p align="center">
  A native Android client for COMSATS University Islamabad, Abbottabad Campus (CUIIT) students<br/>
  to manage, download, and submit assignments — and view your weekly timetable — without ever opening a browser.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen?style=flat-square" alt="Platform"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square" alt="Language"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square" alt="UI"/>
  <img src="https://img.shields.io/badge/Min%20SDK-API%2026-orange?style=flat-square" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/Target%20SDK-API%2035-blue?style=flat-square" alt="Target SDK"/>
</p>

---

> **Battle-tested.** This app has been personally used by the developer to submit real assignments through the COMSATS student portal. Every feature has been validated in production, not just on paper.

---

## Overview

Assignly is a purpose-built Android client for the [COMSATS Student Information System (SIS)](https://sis.cuiatd.edu.pk) portal. It authenticates using your student credentials, scrapes the assignment portal, and presents everything in a clean and usable interface — with one-tap download and upload support, a full weekly timetable planner, background sync with push notifications, and automatic update detection.

No more squinting at a tiny browser, fighting with broken mobile web layouts, or missing deadlines buried inside a clunky table.

---

## Features

### Secure Login & Auto Sign-In

- Log in using your COMSATS registration number (e.g. `SP25-BCS-001`) and password.
- Credentials are stored using **Android EncryptedSharedPreferences** (AES-256 encryption) — never in plain text.
- On app launch, Assignly automatically signs you in using saved credentials. No re-typing required.
- Existing plain-text credentials from older versions are automatically migrated to encrypted storage.
- Logout clears all saved credentials and session cookies immediately.

---

### Student Welcome Card

Upon logging in, a personalised card greets you with:

- Your **full name**, parsed from the portal.
- Your **profile photo**, fetched directly from the portal.
- A **dynamic status message** that adapts to your workload:

| Pending Assignments | Message Tone |
|---|---|
| 3 or more | Sarcastic roast |
| 1–2 | Motivational nudge |
| None | Genuine appreciation |
| No assignments posted yet | A gentle jab at your professors |

---

### Pending Assignments Table

A horizontally scrollable table surfaces your currently actionable pending assignments:

| Column | Description |
|---|---|
| `#` | Row number |
| `Course Title` | The course this assignment belongs to |
| `Title` | Assignment name |
| `Start Date` | When the assignment was posted |
| `Deadline` | Full deadline with time |
| `Status` | **Pending** |
| `Download` | Tap to fetch instruction files |
| `Submit` | Tap to pick and upload a file |

---

### Assignment Summary Card

A tappable card at the top of the assignments list shows at a glance:

- **Total** assignments across all states
- **Pending** count — highlighted in yellow
- **History** count — highlighted in green

Tapping this card navigates to the full **Assignment History** screen.

---

### Weekly Timetable Planner

A fully integrated timetable viewer fetches your semester schedule directly from the portal:

- **Swipe navigation** — swipe left and right to smoothly move between days of the week.
- **Pill-style day selector** — tap Mon through Sat to jump to any day. The current day is automatically selected on open.
- **Smart HTML parsing** — extracts clean structured data from the portal's raw timetable, including multi-word lab names (e.g. "DLD Microprocessor Lab", "Physics Lab").
- **Compact class cards** showing course name, session type (Lecture/Lab), time, room, and instructor.
- **Live "Now" indicator** — highlights the class currently in progress.
- **Next Class widget** — shows your upcoming class at a glance when viewing today's schedule.
- **Offline caching** — timetable data is cached locally so it loads instantly on subsequent opens.
- **Performance optimised** — pre-grouped data, memoized computations, and keyed lists for buttery smooth scrolling.

---

### Download Instruction Files

- Tap **Download** on any assignment to fetch instruction files attached by your professor.
- A dialog lists all available files, each with its own **Download** button.
- Files are saved to a location of your choice via Android's native file picker.
- Handles both direct file URLs and **ASP.NET PostBack-based** download triggers.
- Follows redirect chains up to 6 hops deep to resolve the actual file.
- Detects and skips HTML error pages masquerading as valid downloads.

---

### Submit / Upload Assignments

- Tap **Upload File** on any pending assignment to open the file picker.
- Accepts any file type — the portal supports `.zip`, `.rar`, `.doc`, `.docx`, and `.pdf`.
- Handles both standard form submissions and ASP.NET PostBack-based upload flows.
- Preserves the original file extension when uploading.
- On success, **automatically refreshes** the assignment list so your submission is immediately reflected.
- Provides clear error messages for: wrong file format, file too large, assignment closed, network failure, or server timeout.
- Upload timeout is **90 seconds**, with retry logic for transient network errors.

---

### Assignment History Screen

A dedicated screen lists submitted items and missed closed submissions:

- Shows **course name**, **assignment title**, **deadline**, and **submission state**.
- Missed deadlines appear with a **Not Submitted** tag.
- Colour-coded **Open / Closed** status based on a live deadline comparison.
- If the deadline is still open, a **"Change File"** button lets you re-upload a corrected submission.
- A **"Download Instructions"** button remains available regardless of deadline status.
- Instructor feedback is displayed when available.

---

### Background Sync & Push Notifications

Assignly works even when you're not looking:

- **Periodic background sync** via WorkManager fetches new assignments every 6 hours (configurable in settings).
- **New assignment alerts** — push notifications for newly posted assignments you haven't seen yet.
- **Deadline reminders** — automatic reminders at 24 hours, 6 hours, and 1 hour before each deadline.
- **Upload & download progress** — notification channels for ongoing file transfers.
- **Smart notification permission flow** — prompts for notification access on first launch (Android 13+), and gently re-prompts on the 3rd app open of the day if notifications are still disabled.
- All notification channels are individually configurable in app settings.

---

### Automatic Update Detection

- Checks the **GitHub Releases API** for newer versions using version code tags (e.g. `vc12`).
- **In-app update dialog** — shown every time you open the app when a newer version is available, with a direct link to download.
- **Background update notification** — fires once per day via the background sync worker as a passive reminder.
- Supports update download via the [Assignly website](https://assignly-web.vercel.app/) with automatic fallback to GitHub releases.

---

### Refresh

Hit the refresh icon in the top bar at any time to re-fetch your assignments and timetable from the portal. Profile photo and student name are also re-synced on refresh.

---

### Loading Skeletons

Instead of a blank screen or a spinner, Assignly shows **animated shimmer skeleton screens** while data loads — with separate skeleton layouts for the Pending and Historical screens so transitions feel natural.

---

### Settings

A dedicated settings screen lets you configure:

- **Background sync** toggle and interval (1–24 hours)
- **Notification preferences** — independently toggle assignment alerts, deadline reminders, upload notifications, and update notifications
- **Download behaviour** — ask every time, or download directly
- **Theme mode** — Light, Dark, or follow System default

---

### Network Resilience

- Automatic **retry with exponential backoff** for transient `IOException` errors (up to 3 attempts).
- Login timeout: **45 seconds**. Upload timeout: **90 seconds**.
- Descriptive error messages distinguish between: timeout, no internet, wrong credentials, CAPTCHA triggered, and generic server errors.

---

### CAPTCHA Detection

If the COMSATS portal triggers a CAPTCHA (e.g. after too many failed attempts), Assignly detects this and shows a clear message advising you to log in via browser first to clear it.

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
| Build System | Gradle (Kotlin DSL) |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 21 (configured via Gradle toolchain)
- An active COMSATS student account at [sis.cuiatd.edu.pk](https://sis.cuiatd.edu.pk)

### Build & Run

```bash
git clone https://github.com/danycli/assignmentchecker.git
cd assignmentchecker
./gradlew assembleDebug
```

Or open the project in Android Studio and hit **Run**.

### Release Build

```bash
./gradlew assembleRelease
```

Release builds enable R8 minification and resource shrinking. Android `Log` calls are stripped via ProGuard rules.

---

## Project Structure

```
app/src/main/java/com/danycli/assignmentchecker/
├── MainActivity.kt              # App entry, navigation, update & notification dialogs
├── MainViewModel.kt             # ViewModel bridging use cases to UI
├── PortalRepository.kt          # All networking: login, fetch, upload, download, timetable
├── PortalUseCases.kt            # Clean architecture use cases
│
├── Assignment.kt                # Assignment data model, status enum, deadline parsing
├── Timetable.kt                 # TimetableLecture data model
├── SettingsModels.kt            # Settings enums (DownloadBehavior)
├── ThemeMode.kt                 # Theme mode enum
├── UiModels.kt                  # Shared UI models
│
├── AppSettingsStore.kt          # App preferences (sync, notifications, theme)
├── AssignmentCacheStore.kt      # Offline assignment cache
├── TimetableCacheStore.kt       # Offline timetable cache
├── CredentialsStore.kt          # Encrypted credential storage
├── NotificationPromptStore.kt   # Daily open tracking & notification prompt state
├── UpdateNotificationStore.kt   # Update notification dedup
│
├── AssignmentNotifications.kt   # Notification channels & assignment alerts
├── AssignmentReminderWorker.kt  # Deadline reminder WorkManager worker
├── BackgroundSyncWork.kt        # Periodic sync scheduler & worker
├── DownloadWork.kt              # Background download worker
├── UploadWork.kt                # Background upload worker
├── DownloadQueueStore.kt        # Download queue persistence
├── UploadQueueStore.kt          # Upload queue persistence
├── DownloadNotifier.kt          # Download progress notifications
├── UploadNotifier.kt            # Upload progress notifications
│
├── AppUpdateChecker.kt          # GitHub Releases API version checker
├── UpdateNavigationManager.kt   # Update download URL resolution
├── UpdateNotifier.kt            # Update available notification
├── NotificationGate.kt          # Notification permission check utility
│
├── SecurityUtils.kt             # Crypto utilities
├── IoRetry.kt                   # Retry with backoff helper
├── UiUtils.kt                   # UI constants & helpers
├── SettingsScreen.kt            # Settings UI
│
└── ui/
    ├── AssignmentsScreen.kt     # Pending assignments UI
    ├── HistoryScreen.kt         # Assignment history UI
    ├── LoginScreen.kt           # Login screen UI
    ├── TimetableScreen.kt       # Timetable bottom sheet UI
    ├── CommonUi.kt              # Shared UI components & skeletons
    └── theme/                   # Material 3 theming
```

---

## Registration Number Format

```
SP25-BCS-001
│    │    └── Roll number (zero-padded)
│    └────── Program code (e.g. BCS, BEE, BBA)
└─────────── Session (SP = Spring, FA = Fall + 2-digit year)
```

The app automatically normalises your input to uppercase and parses the three parts separately to populate the portal's session dropdown, program dropdown, and roll number field.

---

## Disclaimer

This is an **unofficial third-party client** for the COMSATS SIS portal. It is not affiliated with, endorsed by, or maintained by COMSATS University. Use it responsibly and in accordance with your institution's policies.

---

## Author

Built by **danycli** — a student who got tired of the portal's web experience and decided to fix it.

*Made with frustration, caffeine, and Jetpack Compose.*
