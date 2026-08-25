# Sipatokkong — Klub Belajar Sipatokkong

> A lightweight Android app that keeps children's therapy going **at home**, connecting volunteer therapists,
> parents, and a foundation admin — built for underprivileged families with low literacy and limited internet
> quota.

Sipatokkong (`com.antoniusindra.sipatokkong`) bridges the gap between therapy sessions. A volunteer therapist
runs a session and leaves weekly homework with a demo video; the parent carries it out at home, marks it done,
uploads proof, and reports how the child responded; the foundation admin oversees accounts and reports progress
to donors. The whole loop is designed to be **fast, data-frugal, and simple to log into**.

---

## Table of Contents
- [Overview](#overview)
- [User Flow](#user-flow)
- [Roles & Features](#roles--features)
- [Tech Stack](#tech-stack)
- [Architecture & Security](#architecture--security)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Project Status & Roadmap](#project-status--roadmap)
- [License](#license)

---

## Overview

Children from underprivileged families often receive therapy only during short volunteer visits, and the
progress fades because there's no structured follow-through at home. Sipatokkong closes that gap with a simple
three-role continuity loop:

```
Relawan (volunteer) ──creates homework──▶ Orang Tua (parent) ──does it at home + feedback──▶ Relawan
        ▲                                                                                        │
        └──────────────────────── Admin (foundation) oversees & reports ◀────────────────────────┘
```

**Design constraints (hard requirements):** end users are low-income, low-literacy, and low-bandwidth. The app
must therefore stay lightweight, be frugal with mobile data, and offer a dead-simple login.

---

## User Flow

The full flow across all three roles:

![Sipatokkong user flow — Volunteer, Parent, Admin](assests/SIPATOKKONG%20%281%29.jpg)

Parent dashboard reference:

![Parent dashboard mockup](assests/dashboard%20orang%20tua.png)

---

## Roles & Features

### 👩‍⚕️ Relawan — Volunteer Therapist
- **Attendance / session log (presensi):** record each home visit with date, session notes, and a proof photo.
- **Create weekly homework (tugas):** assign a task to a child with a title, instructions, priority, and
  deadline — **a demo photo or video is mandatory** so the parent knows exactly what to do.
- **Monitor task status:** dashboard of assigned tasks with `assigned / submitted / done / overdue` status and
  a done/pending filter.
- **Notifications:** get alerted when a parent submits feedback or misses a task.
- **Save media to the phone:** any photo/video in the app can be downloaded into the gallery
  (`Pictures/Sipatokkong`, `Movies/Sipatokkong`). Videos ask for confirmation first and show their
  size, because data is scarce for these families.

### 👨‍👩‍👧 Orang Tua — Parent
The parent module, exactly as scoped:

- **Home summary (important overview)**
  - A list of tasks ordered by priority (by urgency / deadline).
  - The latest feedback with its status — showing whether it's already answered or still in process.
- **Home task list**
  - View the volunteer's task instructions (complete with the volunteer's photo/video).
  - A **"Selesai" (Done)** button to mark a task as completed.
- **Behavior notes & feedback (list & input form)**
  - Weekly/daily input: *How is the child after therapy?* (e.g. "calmer", "starting to hold a spoon").
  - Obstacle input: what problems the parent faced doing the therapy at home.
- **Self-documentation (input form)**
  - Upload a photo/video of the child doing the task at home as proof of continuity.
- **Notifications**
  - Weekly/daily task reminders.
  - Replies to submitted feedback.
- **Save media to the phone**
  - Download the volunteer's demo photo/video, and the proof the parent sent, into the gallery.
  - This is also how a demo **video** is watched: there is no in-app player, so the file is saved
    and opened with the phone's own gallery app.

### 🗂️ Admin — Foundation (Yayasan)
- **Account registration:** create volunteer and parent accounts (there is no public sign-up).
- **Attendance recap:** overview of volunteer attendance across children.
- **Child progress report:** progress recap suitable for donor reporting.
- **Export data.**

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language / UI | Kotlin, Jetpack Compose (Material 3), Material Icons Extended |
| Navigation | Navigation Compose |
| State | ViewModel + StateFlow, manual dependency injection (`ServiceLocator`) |
| Backend | Supabase — Auth, Postgres, Storage, Realtime |
| Supabase client | `supabase-kt` BOM `3.5.0` (Postgrest, Auth, Storage, Realtime) on Ktor `3.2.0` (OkHttp engine) |
| Serialization / async | kotlinx.serialization `1.8.0`, kotlinx.coroutines `1.10.1` |
| Media | Coil 3 (image loading), Compressor + LightCompressor (client-side compression) |
| Offline / uploads | WorkManager upload queue, DataStore Preferences |
| Build | Android Gradle Plugin `9.0.0-alpha06`, Gradle `9.4.1`, Kotlin `2.2.10` |
| SDK | `minSdk 27`, `targetSdk 36`, `compileSdk 36.1` |

**Bandwidth budget:** media is compressed on-device before upload (target: images ≤ 200 KB, videos ≤ 30 s /
480p) to respect users' limited data quotas.

---

## Architecture & Security

- **Row Level Security (RLS) is the only authorization layer.** The Android client ships with only the public
  **anon key**, so every access rule is enforced in Postgres via RLS policies and `SECURITY DEFINER` helper
  functions (`is_admin`, `is_parent_of`, `is_relawan_of`, `can_view_profile`). See
  [`supabase/migrations/0001_initial_schema.sql`](supabase/migrations/0001_initial_schema.sql).
- **Login = "Nomor HP + PIN".** The phone number is mapped to an email alias `"{phone}@sipatokkong.local"` for
  Supabase Auth. There is **no public sign-up** — the admin creates all accounts.
- **Storage** uses three private buckets (`presensi`, `tugas-demo`, `dokumentasi`) with a
  `{anak_id}/{yyyy}/{mm}/{uuid}.{ext}` path convention; access is gated by the child ID in the first path
  segment.
- **Notifications** are written server-side only, by database triggers (e.g. a new `tugas` notifies the parent;
  new `feedback` notifies assigned volunteers; a volunteer reply notifies the parent).

---

## Project Structure

```
app/src/main/java/com/antoniusindra/sipatokkong/
├── MainActivity.kt
├── SipatokkongApp.kt              # Application class
├── core/
│   ├── di/ServiceLocator.kt       # manual DI
│   ├── auth/SessionManager.kt     # session state
│   └── supabase/SupabaseModule.kt # Supabase client setup
├── data/
│   ├── model/                     # Profile, UserRole (mirrors Postgres enums)
│   └── repository/                # AuthRepository, ProfileRepository
└── ui/
    ├── SipatokkongMain.kt         # root navigation
    ├── auth/login/                # LoginScreen + LoginViewModel
    ├── admin/                     # CreateAccountScreen + ViewModel
    ├── home/                      # AdminHomeScreen
    └── theme/                     # Color, Theme, Type

supabase/migrations/
└── 0001_initial_schema.sql        # schema + RLS + storage + triggers
```

---

## Getting Started

### Prerequisites
- **JDK 11+** — the IntelliJ IDEA bundled JBR works
  (`/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home` on macOS).
- **Android SDK** with platform 36 and platform-tools.
- A **Supabase project** (Auth + Postgres + Storage enabled).

### 1. Configure secrets
Create/edit `local.properties` in the project root (this file is git-ignored — **never commit it, and never put
the `service_role` key here**):

```properties
sdk.dir=/path/to/your/android-sdk
SUPABASE_URL=https://YOUR-PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-public-key
```

These are exposed to the app as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY`.

### 2. Apply the database schema
Run [`supabase/migrations/0001_initial_schema.sql`](supabase/migrations/0001_initial_schema.sql) against your
Supabase project (via the Supabase SQL editor or the Supabase CLI).

### 3. Build & run
```bash
# build a debug APK
./gradlew :app:assembleDebug

# or install onto a connected device / running emulator
./gradlew :app:installDebug
```
Then launch the app on an Android device (USB or wireless debugging) or an emulator (API 27+).

---

## Project Status & Roadmap

> ⚠️ Early development — the backend and foundations are in place; most role screens are still being built.

**Done**
- [x] Project setup & tooling
- [x] Database schema + RLS + storage buckets + triggers (Phase 1)
- [x] Authentication & role-based routing (Phase 2)
- [x] Admin: create-account screen (in progress)
- [x] Relawan: presensi (attendance) vertical slice
- [x] Admin: attendance recap (visits per volunteer, by date range)

**Planned**
- [ ] Relawan: create/manage tugas (homework) with mandatory demo media
- [ ] Orang Tua: task list, "Selesai", self-documentation upload
- [ ] Feedback threads + notifications
- [ ] Admin: progress dashboard + export
- [ ] Scheduled task reminders

---

## License

No license file is present yet. Add a `LICENSE` before distributing.

---

*Built for Klub Belajar Sipatokkong. UI and communication are in Bahasa Indonesia; this README is in English for
contributors.*
