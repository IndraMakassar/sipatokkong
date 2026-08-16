# Sipatokkong Project Status

## Technical Overview
Sipatokkong is an Android application using **Jetpack Compose** and **Supabase**. It follows a clean MVVM architecture with manual dependency injection via a `ServiceLocator`. The backend logic is heavily reliant on Supabase's **Row Level Security (RLS)** and database triggers for notifications and data integrity.

## Status Summary

### ✅ Built / Completed
*   **Infrastructure:** Supabase project setup, database schema, RLS policies, and storage bucket
    configurations. Migrations `0001`, `0002` and `0003` are all applied to the live project.
*   **Authentication:** Phone Number + PIN login flow mapped to Supabase Auth. Role-based routing (Admin, Parent, Volunteer).
*   **Admin Features:**
    *   Create Parent/Volunteer accounts (via `noSessionClient` to preserve admin session).
    *   Create Child profiles and link them to parents.
    *   Manage & edit accounts and children: searchable lists, edit forms, active/inactive
        toggle, move a child to another parent, add/remove volunteer assignments.
*   **Parent Features:**
    *   Home dashboard with task summary and status.
    *   Task detail view.
    *   Feedback/Behavior note list and submission form.
    *   Basic notification list screen.
*   **Volunteer Features:**
    *   Create Task (Program Ajar) with media upload and rich text instructions.
    *   Recurring programs (daily / weekly / monthly / every-N-days) with an end date, plus a
        "Program Ajar Saya" screen for stopping a running program.
*   **Communication:** Feedback threads for parent-volunteer interaction.

### 🚧 In Progress / Partially Built
*   **Media Compression:** Integration of client-side compressors into the upload pipeline.
*   **Notifications:** Server-side triggers are defined, but real-time client-side handling and push notifications (FCM) are pending.
*   **Presensi (Attendance):** Relawan vertical slice for logging home visits.

### ⚠️ Pending Deployment Step
*   **Migration `0004_alat_bantu.sql` has NOT been run.** Once the branch merges, creating a program
    ajar sends an `alat_bantu` column that does not exist yet, so **task creation will fail** until
    the migration is applied. Run it before merging, as with `0003`.

### ❌ Not Built / Deferred
*   **Admin Dashboard:** Progress recaps for donors and attendance overviews.
*   **Data Export:** Exporting progress reports to CSV/PDF.
*   **Scheduled Reminders:** Automated weekly reminders for parents.
*   **Offline Mode:** WorkManager queue for uploads during intermittent connectivity.

## Domain Constraints
*   **Low Literacy:** UI must be visual-heavy with clear, simple Bahasa Indonesia labels. Mandatory demo videos/photos for tasks.
*   **Low Bandwidth:** Data frugality is a hard requirement. Media must be compressed before upload.
*   **Simple Access:** No email/password registration. Account creation is restricted to Admins; login is via Phone + PIN.

## Product Rules

### When a task leaves the parent's home screen

Defined by `visible` in `ui/home/ParentHomeViewModel.kt`:

| Occurrence | On the home list? |
|---|---|
| Today or later, **not** done | Yes |
| Today or later, **done** | Yes — dimmed, title struck through, "Selesai" chip |
| Past date, **not** done | Yes — shown as "Terlambat" |
| Past date, **done** | No — history screen only |

So a task finished today stays on the home screen for the rest of that day as proof of work, and
drops off once its date is in the past. That transition happens the next time the home screen
loads on a new calendar day — there is no timer forcing a refresh at midnight.

Deliberate side effect: a task completed today appears on **both** the home screen and the history
screen at the same time.

### Ordering within a day

Set by `TaskOccurrences.build` in `data/model/TaskOccurrence.kt`: date, then **unfinished before
finished**, then priority (high → low), then title.

Keeping finished cards on the list only works if they sink below what still needs doing —
otherwise a completed high-priority task would push an unfinished medium-priority one out of
sight, which defeats the point of showing them at all.

## Known Limitations

*   **The history screen only covers the last 30 days.** `history` is derived from the same
    `occurrences` list as the home screen, and that list is only built for
    `today − PAST_WINDOW_DAYS (30)` through `today + FUTURE_WINDOW_DAYS (14)` in
    `ParentHomeViewModel.refresh()`. Completions older than 30 days are not visible anywhere in
    the app, despite the screen being called "Riwayat".

    Kept on purpose for now — 30 days covers current needs. If it ever needs lifting (donor
    reporting is the likely trigger), the clean fix is to build history straight from the
    `tugas_penyelesaian` table instead of from expanded recurrence rules: every row already
    carries its own `occurrence_date`, so no rule expansion and no date window are needed at all.

## Change Log

### [2026-08-16] - Teaching Aids ("Alat Bantu") on a Program Ajar
* **What changed:** A task can now carry several household teaching aids. The volunteer picks them
    from a fixed list when creating the program; the parent sees them read-only on the task detail
    screen. This resolves the long-ambiguous "kolom media ajar" request, finally explained by a
    teammate: *a task may have several aids, make it a dropdown, the volunteer fills it, and the
    item list is fixed.*
* **The list is grounded in this project, not a generic therapy catalogue.** Every entry is a cheap
    or already-owned household item, because the families served are low-income and clinical
    equipment would put the homework out of reach. Several trace directly to the app's own
    material — fabric textures and vowel practice are its example tasks, and "starting to hold a
    spoon" is the README's example of progress.
* **Stored as `text[]`, not a Postgres enum.** The option list lives as an app constant, so an enum
    would make adding one item need both an APK release *and* a migration; `text[]` needs only the
    release. Array columns are already proven here by `recurrence_days smallint[]`.
* **Unknown stored values are displayed, not dropped.** `TeachingAid.labelsOf` passes through any
    value it does not recognise, so if the list changes in a later release, tasks saved under the
    old list keep showing their aid rather than silently losing it.
* **Verified:** `assembleDebug` green; 41 unit tests pass (9 new). The pass-through test was also
    run against a naive `mapNotNull` implementation and **failed** as intended, so it genuinely
    guards that behaviour rather than passing either way.
* **Not yet verified:** all runtime behaviour, and migration `0004` has not been applied.

### [2026-08-16] - Repo Cleanup + First Observed Stop-Hook Firing
* **What changed:** No code. Local checkout moved back to `master` (`fea4f77`) and the merged
    branches were deleted locally and on GitHub: `test/gabungan`, `feat/kemandirian-durasi`,
    `fix/urutan-tugas-selesai`.
* **Safety check worth recording:** `git branch -d` refused to delete `test/gabungan`, because the
    local merge commit is not an ancestor of `master` — GitHub created its own merge commits for
    PRs #13/#14. Before forcing with `-D`, both Change Log entries that had conflicted during that
    local merge were confirmed present in `master` (only their order differs). Nothing was lost.
* **The Stop hook fired for the first time** — previously listed as "not yet verified". It works.
* **…and it was a false positive, exactly as predicted.** Nothing was written this turn; `git pull`
    rewrote file mtimes, and `stop_gate.py` compares mtimes. Both `0003_kemandirian_durasi.sql` and
    `project_status.md` landed in the same second (`03:02:58`) from that one pull, differing only
    by milliseconds — enough to trip the gate.
* **If this becomes annoying**, the fix is to compare against `git diff --name-only` /
    `git status --porcelain` rather than mtimes, so pure git operations stop triggering it. Left
    alone for now: the `stop_hook_active` guard already caps it at one nudge per turn.
* **Verified:** Branch state after cleanup — 2 local branches, 4 remote, working tree clean,
    `master` at `fea4f77`.
* **Not yet verified:** Still nothing runtime. The four merged changes (independence scale,
    duration, "Media Ajar" label, completed-task ordering) have never been exercised on a device.

### [2026-08-16] - Finished Occurrences Now Sort Below Unfinished Ones
* **The problem:** `TaskOccurrences.build` sorted by date → priority → title, ignoring `isDone`.
    Inside a day, a **completed high-priority** task therefore sat above an **unfinished
    medium-priority** one. Keeping finished cards on the home screen is only defensible while they
    sink below what still needs attention, so this undercut that whole decision.
* **Fix:** Added `isDone` as the second comparator (Kotlin orders `false` before `true`).
* **Tests:** `TaskOccurrences.build` had no coverage at all despite deciding what parents see.
    Added `TaskOccurrencesTest` — 9 cases covering ordering, per-occurrence completion matching on
    `(tugas_id, occurrence_date)`, unique list keys, and overdue logic.
* **Verified:** `assembleDebug` green; 26 unit tests pass (9 new + 16 `RecurrenceTest` + 1 sample).
    The ordering test was also run against the *unfixed* code and **failed** as expected — proof it
    actually catches the regression rather than passing either way.
* **Not yet verified:** Runtime appearance. The `@Preview` in `ParentHomeScreen` shows the new
    order without a device, but on-device confirmation still waits on a parent account.
### [2026-08-16] - Independence Scale + Working Duration on the Parent's Completion Form
* **What changed:** The child-response question now measures **independence** (Mandiri / Dengan
    Bantuan / Dengan Bantuan Penuh / Belum Bisa) instead of mood, and a new **duration** question
    was added as time buckets rather than a minutes field — one tap, no number to estimate.
* **Both are now real columns.** Migration `0003` adds `kemandirian` and `durasi` to
    `tugas_penyelesaian`. Previously the answer was concatenated into `catatan` as
    `"Respon anak: ...."`, so it could never be counted or charted. `catatan` now holds only what
    the parent actually typed.
* **No backfill, on purpose.** The old values measured mood, the new ones measure independence —
    they are different dimensions. Legacy rows keep their text in `catatan` and leave both new
    columns NULL; inventing a mapping would fabricate data nobody collected.
* **Layout:** four options no longer fit across one phone-width row, so the cards became a 2×2
    grid. The volunteer's demo media at the top of the screen is now labelled "Media Ajar".
* **Deliberately not built:** the requested "kolom media ajar" beyond that label. The request came
    from a teammate and nobody on hand could say what it meant, so building a column or a second
    upload flow risked expensive rework. Needs clarification before anything more is added.
* **Verified:** `assembleDebug` green; 23 unit tests pass, including a new `CompletionEnumTest`
    that parses `0003_*.sql` and asserts every Kotlin `dbValue` matches its Postgres enum label in
    exact order. That test was also run against a deliberately typo'd value and **failed** as
    intended — a one-character drift now breaks the build instead of failing an insert after a
    parent has filled in the form.
* **Migration status:** `0003` was applied to the live project before merging, which is the safe
    order — the columns are nullable and the code on `master` does not send them, so they simply
    sat unused until this branch lands. Merging first would have broken **every** completion in
    the gap, including ordinary one-shot tasks.
* **Not yet verified:** all runtime behaviour. Applying the migration removes the database
    blocker, but nothing has exercised the new columns yet; that still needs a parent account.

### [2026-08-16] - Documented Task Visibility Rules + 30-Day History Cap
* **What changed:** No code. Added a **Product Rules** section spelling out exactly when a finished
    task leaves the parent's home screen, and a **Known Limitations** section.
* **Why:** The rule only existed inside `ParentHomeViewModel.visible` and had to be re-derived by
    reading code every time it was questioned.
* **Limitation recorded:** the history screen silently covers only the last 30 days, because it
    reuses the home screen's occurrence window (`PAST_WINDOW_DAYS`). Older completions are not
    visible anywhere. **Left as-is on purpose** — written down precisely because it is a decision
    rather than an oversight, along with the fix to reach for if donor reporting ever needs it.
* **Verified:** Constants re-read from source before documenting (`PAST_WINDOW_DAYS = 30`,
    `FUTURE_WINDOW_DAYS = 14`), so the numbers in this document match the code.

### [2026-08-16] - Fix: Home Never Reloaded After Completing a Task
* **The bug:** Marking a task done saved correctly, but the card on the parent home did not change
    at all. Root cause was stale data, not styling: `ParentHomeViewModel` only loaded in `init`,
    and its instance is scoped to the `orangtua_home` back-stack entry, which survives navigation
    to the detail screen. Returning from the detail screen reused the same instance with the old
    list, so the completion was invisible until the app was restarted.
* **Fix:** Dropped `init { refresh() }` and drive loading from the screen with
    `LaunchedEffect(Unit) { parentVm.refresh() }` on both `orangtua_home` and `parent_history` —
    the same pattern the admin manage screens use. Loading in both places would have meant two
    network round-trips per entry, which this data-frugal app should not pay.
* **Behaviour change:** A finished occurrence now **stays visible** on the home list instead of
    disappearing, rendered dimmed with a struck-through title and a "Selesai" chip. Past dates
    still drop off once done; they live in the history screen.
* **Also:** The "Lihat Semua" link is now labelled "Riwayat". It has always led to a
    completed-only screen, so the old label promised a full list it never showed — which is why
    it looked empty. Carried over from PR #9 and missed during the merge.
* **Verified:** `assembleDebug` and `testDebugUnitTest` green (16 `RecurrenceTest` still pass);
    installed on the emulator and the app launches with no fatal exception.
* **Not yet verified:** The fix itself. Confirming it needs a **parent account with tasks** —
    complete a task and watch the card change without restarting. No parent credentials are
    available from this machine.

### [2026-08-15] - First Runtime Smoke Test
* **What changed:** No code. Credentials landed in `local.properties`, so the app was built,
    installed and exercised on an emulator (Pixel 9, API 36) for the first time this cycle.
* **Verified (runtime):**
    *   Supabase connectivity is real — a persisted session was restored and the profile fetched
        from the database (signed in as *Relawan Kevin*, role RELAWAN).
    *   The merge of PR #10/#11 holds up at runtime, not just at compile time: `CreateTaskScreen`
        shows master's rich-text toolbar **and** the new "Pola Pengulangan" section together.
    *   Recurrence form logic: selecting *Mingguan* reveals the seven day chips and swaps
        "Deadline" for "Tanggal Mulai/Selesai"; the start date auto-fills to today.
    *   `ProgramListScreen` loads and its query succeeds (empty state, not an error state).
* **Not yet verified — no database WRITE has been exercised at all:** creating a program,
    completing an occurrence, and every admin edit screen. Migration `0002` therefore still has
    no runtime confirmation either, since nothing has attempted to write `occurrence_date`.
* **Blocker found:** the signed-in volunteer has **no child assigned** ("Belum ada anak yang
    ditugaskan"), and demo media is mandatory, so no task can be created from this account. Assign
    a child to that volunteer via Admin → Kelola Data Anak before end-to-end testing.

### [2026-08-15] - Migration 0002 Applied
* **What changed:** No code. `supabase/migrations/0002_recurring_tugas.sql` was run against the
    live Supabase project, so `tugas_penyelesaian.occurrence_date` now exists and the unique
    constraint is `(tugas_id, occurrence_date)`.
* **Why it mattered:** Until this ran, marking *any* task done would fail — including ordinary
    one-shot tasks, because completions now always send that column. That regression is cleared.
* **Verified:** Reported applied by the maintainer. Not independently confirmed from this machine;
    no query was run against the database.
* **Remaining blocker:** `local.properties` still has no `SUPABASE_URL` / `SUPABASE_ANON_KEY`, so
    nothing in the app has been exercised at runtime yet.

### [2026-08-15] - Merge Verification of PRs #10–#12
* **What changed:** No code. PRs #10, #11 and #12 merged into `master` without git conflicts.
    Because each branch had been built in isolation from `d20e226`, the combination had never
    been compiled — a clean-room build was run to rule out a semantic conflict.
* **Verified:** `clean` + `assembleDebug` + `testDebugUnitTest` on merged `master` (`d244efa`):
    build succeeded with no errors or warnings, 16 `RecurrenceTest` cases passed, APK produced.
    `SipatokkongMain.kt` — the only file both feature PRs touched — merged correctly: both route
    sets present, no duplicate imports or composables, and only the 2-argument `tugas_detail`
    route survived.
* **Not yet verified:** Runtime behaviour of either feature. `local.properties` still has no
    Supabase credentials, and migration `0002` has not been applied.

### [2026-08-15] - Recurring Program Ajar (PR #11)
* **What changed:** One `tugas` row now models a whole PROGRAM. The recurrence rule is stored as
    columns; occurrence dates are computed on the client rather than stored as rows. Completions
    are keyed per `(tugas_id, occurrence_date)`, replacing the old `unique(tugas_id)`.
* **Why this shape:** One row per occurrence would mean 90 rows for a 3-month daily program, and
    `on_tugas_insert` would fire 90 notifications at once. Rule-based keeps it at one.
* **Decisions worth remembering:** monthly-on-the-31st clamps to the last day of short months
    rather than skipping them; `stoppedOn` is exclusive, so stopping today also cancels today.
* **Verified:** Build green; 16 unit tests covering daily, weekly multi-day, monthly across
    February, interval cadence, and the `endsOn`/`stoppedOn` bounds.
* **Not yet verified:** All runtime behaviour. Requires migration `0002` plus credentials.

### [2026-08-15] - Admin Manage & Edit (PR #10)
* **What changed:** Admin can now list, search and edit parent/volunteer accounts and children —
    previously data could only be created, never corrected.
* **Schema limits that shaped it:** there is no `DELETE` policy for `profiles` or `anak`, so
    "delete" is an `is_active` toggle; and another user's email/password cannot be changed from
    the app at all, since that needs the Admin API with the `service_role` key.
* **Side effect:** `profilesByRole()` now filters `is_active`, so deactivated accounts no longer
    appear in the "Tambah Data Anak" dropdowns.
* **Verified:** Build green, no warnings.
* **Not yet verified:** All runtime behaviour.

### [2026-08-15] - Status Document Automation
* **What changed:** Added `.claude/settings.json` plus two stdlib-Python hooks. `SessionStart`
    injects this document into the agent's context; `Stop` blocks the agent from ending a turn
    while any watched source file is newer than this file. Rules documented in `agent.md`.
* **Also:** Bumped Android Gradle Plugin `9.0.0-alpha06` → `9.0.1`. Kept out of the feature PRs
    because build-infrastructure changes should not be buried inside a feature diff.
* **Verified:** Both hook scripts exercised with synthetic payloads — normal case, blocked case,
    and the `stop_hook_active` anti-loop guard. `assembleDebug` still green.
* **Not yet verified:** Whether the hooks actually fire inside Claude Code. The settings watcher
    only tracks directories that existed at session start, so `/hooks` must be opened once (or the
    CLI restarted) before they take effect.

### [2026-08-09] - Profile Screen Redesign & Global Styling
* **Profile Screen UX Overhaul:**
    *   Redesigned `ParentProfileScreen` header to match the `WeeklyProgressCard` aesthetic, featuring a `BrandTeal` gradient and subtle geometric decorations.
    *   Standardized section headers by removing "weird" teal coloring from "Informasi Kontak", now using `onSurface` color for better readability and M3 compliance.
    *   Improved layout consistency with `20.dp` rounded corners and white cards over `AppBackground`.
    *   Updated the Logout button to a high-contrast Material 3 `errorContainer` style.
* **Global Style Centralization:**
    *   Promoted `ProgressGradTop`, `ProgressGradBottom`, and `IconBoxBg` to `Color.kt` to ensure visual consistency across the entire parent module.
    *   Refactored `ParentHomeScreen` to consume these global color constants.

### [2026-08-09] - UI/UX Consistency & Material 3 Refinement
* **Standardized Backgrounds:** Centralized `AppBackground` (0xFFF6F7F9) in `Color.kt` and applied it to all parent module screens (`ParentHomeScreen`, `FeedbackListScreen`, `TaskDetailScreen`, etc.).
* **Material 3 TopAppBar Integration:**
    *   Ensured every page in the parent module has a `TopAppBar`.
    *   Refactored `ParentHomeScreen` to use a single `TopAppBar` that dynamically updates its title based on the active tab (Home, Catatan, Profil).
    *   Removed nested `Scaffold` issues by introducing a `useScaffold` flag in `FeedbackListScreen`.
* **Brand Theme Alignment:**
    *   Updated `SipatokkongTheme` to use `BrandTeal` as the primary color and `BrandOrange` as secondary.
    *   Standardized status chip colors (Green, Orange, Red, Teal) across the application by moving them to `Color.kt`.
* **Parent Profile Refinement:**
    *   Updated `ParentProfileScreen` to use Material 3 semantic colors (`onBackground`, `primaryContainer`, etc.) instead of hardcoded values.
    *   Integrated the profile screen seamlessly into the home dashboard's navigation flow with consistent top bar actions.

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
