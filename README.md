<p align="center">
  <!-- <img src="app/src/main/ic_launcher-playstore.png" width="200" height="200" alt="Assignly Logo"/> -->
  <img width="200" height="200" alt="logo" src="https://github.com/user-attachments/assets/24240133-4ae3-49c5-951c-91e247320900" />
</p>

<h1 align="center">Assignly — COMSATS Assignment Portal Client</h1>

<p align="center">
  A native Android client for COMSATS University Islamabad, Abbottabad Campus (CUIIT) students<br/>
  to manage, download, and submit assignments without ever opening a browser.
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

Assignly is a purpose-built Android client for the [COMSATS Student Information System (SIS)](https://sis.cuiatd.edu.pk) portal. It authenticates using your student credentials, scrapes the assignment portal, and presents everything in a clean and usable interface — with one-tap download and upload support.

No more squinting at a tiny browser, fighting with broken mobile web layouts, or missing deadlines buried inside a clunky table.

---

## Features

### Secure Login & Auto Sign-In

- Log in using your COMSATS registration number (e.g. `SP25-BCS-001`) and password.
- Credentials are stored using **Android EncryptedSharedPreferences** (AES-256 encryption) never in plain text.
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

A horizontally scrollable table surfaces all your pending and not yet submitted assignments:

| Column | Description |
|---|---|
| `#` | Row number |
| `Course Title` | The course this assignment belongs to |
| `Title` | Assignment name |
| `Start Date` | When the assignment was posted |
| `Deadline` | Full deadline with time |
| `Status` | **Pending** (green) or **Not Submitted / Closed** (red) |
| `Download` | Tap to fetch instruction files |
| `Submit` | Tap to pick and upload a file |

---

### Assignment Summary Card

A tappable card at the top of the assignments list shows at a glance:

- **Total** assignments across all states
- **Pending** count — highlighted in yellow
- **Submitted** count — highlighted in green

Tapping this card navigates to the full **Submitted Assignments** history screen.

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

### Submitted Assignments Screen

A dedicated screen lists everything you have already submitted:

- Shows **course name**, **assignment title**, **deadline**, and **submission date**.
- Colour-coded **Open / Closed** status based on a live deadline comparison.
- If the deadline is still open, a **"Change File"** button lets you re-upload a corrected submission.
- A **"Download Instructions"** button remains available regardless of deadline status.
- Instructor feedback is displayed when available.

---

### Refresh

Hit the refresh icon in the top bar at any time to re-fetch your assignments from the portal. Profile photo and student name are also re-synced on refresh.

---

### Loading Skeletons

Instead of a blank screen or a spinner, Assignly shows **animated shimmer skeleton screens** while data loads with separate skeleton layouts for the Pending and Historical screens so transitions feel natural.

---

### Network Resilience

- Automatic **retry with exponential backoff** for transient `IOException` errors (up to 3 attempts).
- Login timeout: **45 seconds** Upload timeout: **90 seconds**.
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
├── Assignment.kt          # Data model, status enum, deadline parsing
├── MainActivity.kt        # All Compose UI: login, lists, skeletons, dialogs
└── PortalRepository.kt    # All networking: login, fetch, upload, download

app/src/main/res/
├── values/strings.xml     # App name: "Assignly"
├── values/themes.xml      # NoActionBar theme
└── xml/                   # Backup & data extraction exclusion rules
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

Built by **danycli** a student who got tired of the portal's mobile experience and decided to fix it.

*Made with frustration, caffeine, and Jetpack Compose.*
