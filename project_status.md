# Sipatokkong Project Status

## Technical Overview
Sipatokkong is an Android application using **Jetpack Compose** and **Supabase**. It follows a clean MVVM architecture with manual dependency injection via a `ServiceLocator`. The backend logic is heavily reliant on Supabase's **Row Level Security (RLS)** and database triggers for notifications and data integrity.

## Status Summary

### ✅ Built / Completed
*   **Infrastructure:** Supabase project setup, database schema (migrations), RLS policies, and storage bucket configurations.
*   **Authentication:** Phone Number + PIN login flow mapped to Supabase Auth. Role-based routing (Admin, Parent, Volunteer).
*   **Admin Features:**
    *   Create Parent/Volunteer accounts (via `noSessionClient` to preserve admin session).
    *   Create Child profiles and link them to parents.
*   **Parent Features:**
    *   Home dashboard with task summary and status.
    *   Task detail view.
    *   Feedback/Behavior note list and submission form.
    *   Basic notification list screen.
*   **Volunteer Features:**
    *   Create Task (Program Ajar) with media upload and rich text instructions.
*   **Communication:** Feedback threads for parent-volunteer interaction.

### 🚧 In Progress / Partially Built
*   **Media Compression:** Integration of client-side compressors into the upload pipeline.
*   **Notifications:** Server-side triggers are defined, but real-time client-side handling and push notifications (FCM) are pending.
*   **Presensi (Attendance):** Relawan vertical slice for logging home visits.

### ❌ Not Built / Deferred
*   **Admin Dashboard:** Progress recaps for donors and attendance overviews.
*   **Data Export:** Exporting progress reports to CSV/PDF.
*   **Scheduled Reminders:** Automated weekly reminders for parents.
*   **Offline Mode:** WorkManager queue for uploads during intermittent connectivity.

## Domain Constraints
*   **Low Literacy:** UI must be visual-heavy with clear, simple Bahasa Indonesia labels. Mandatory demo videos/photos for tasks.
*   **Low Bandwidth:** Data frugality is a hard requirement. Media must be compressed before upload.
*   **Simple Access:** No email/password registration. Account creation is restricted to Admins; login is via Phone + PIN.

## Change Log

### [2026-08-09] - UI Refinement: TaskCard Alignment
* **TaskCard Layout Update:** Realigned the `TaskCard` component to improve information hierarchy.
    *   Moved the task title to be in line with the task icon and status chip.
    *   Switched to a `Row`-first layout for the card content, ensuring instructions and deadlines are indented relative to the icon.
    *   Maintained consistent styling across the Home and History screens by updating the shared `internal` component.
* **Visual Progress Tracking:** Refactored the behavior note/feedback system to be more visual.
    *   Added **Mood Selector** (😊, 😐, 😢, 🤒) for quick child status reporting.
    *   Implemented **Quick-Select Chips** for common therapy hurdles (e.g., "Sulit Fokus").
    *   Simplified input from three mandatory fields to a mood + chips + optional notes structure.
* **UI/UX Consistency:**
    *   Centralized brand colors (Teal, Orange, Status colors) in `Color.kt`.
    *   Renamed UI titles to formal "Catatan Perkembangan Anak" to maintain professionalism while improving accessibility.
    *   Updated list and thread views to prominently display mood icons.
* **Data Model Update:** Added `mood` field to `Feedback` entities in the Kotlin layer. (Note: Supabase schema requires corresponding `mood` column).

### [2026-08-08] - Enhanced Rich Text Support & Volunteer UX
* **Auto-Incrementing Lists:** Numbered lists in `RichTextUtil` now automatically increment (e.g., typing `1.` on every line renders as `1.`, `2.`, `3.`).
* **Smarter Formatting Toolbar:**
    *   **Bold**: Now wraps selected text with `**`.
    *   **Block Tags**: Headers and Lists now automatically ensure they start on a new line.
    *   **Streamlined UI**: Removed the redundant Numbered List button to simplify the volunteer interface, relying on auto-incrementing manual input.
* **Cursor-Aware Editing:** Improved `TextFieldValue` integration for precise tag insertion and selection handling.

### [2026-08-08] - Parent UI Refactor & Communication Integration
*   **Bottom Navigation Update:** Removed the "History" tab and introduced "Catatan" (Notes & Feedback) as a primary navigation tab for Parents.
*   **Integrated Feedback:** Moved `FeedbackListScreen` directly into the "Catatan" tab and removed the Home screen FAB to de-clutter the UI.
*   **New History View:** Created `ParentHistoryScreen` to house completed tasks, accessible via a new "Lihat Semua" button in the Home dashboard's task section.
*   **Component Optimization:** Refactored `TaskCard` and internal styles to be shared between Home and History screens.
*   **Code Cleanup:** Centralized brand colors (MutedText, BrandTeal, etc.) in `Color.kt` and eliminated redundant declarations across multiple screens to stabilize the build.

### [2026-08-08] - Initial Foundation & UI Scaffolding
*   Established project structure and manual DI.
