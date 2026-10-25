# Sipatokkong Project Status

## Technical Overview
Sipatokkong is an Android application using **Jetpack Compose** and **Supabase**. It follows a clean MVVM architecture with manual dependency injection via a `ServiceLocator`. The backend logic is heavily reliant on Supabase's **Row Level Security (RLS)** and database triggers for notifications and data integrity.

## Status Summary

### ✅ Built / Completed
*   **Infrastructure:** Supabase project setup, database schema, RLS policies, and storage bucket
    configurations. **Migrations `0001` through `0012` are applied; `0013` is written but NOT yet run**, as of 2026-08-30 — and
    that is a fact the database records rather than a claim in this file:
    `supabase_migrations.schema_migrations` holds all twelve, and a probe query confirmed each
    function landed (`notif_snippet`, the renamed trigger bodies, `tugas_jatuh_pada`,
    `kirim_pengingat_tugas`, and the `pengingat-tugas` cron job). Query it instead of trusting
    this paragraph.

    For any future migration that replaces a trigger function, order matters and is not obvious:
    `0011` is a `create or replace` of the same `on_balasan_insert` that `0010` defines, and
    `0012` calls `notif_snippet` which only `0010` creates. No migration file records itself in
    `schema_migrations`; every entry so far was written by hand. The old duplicate-`0005`
    collision is resolved (`0007_presensi_unik_harian`).
*   **Authentication:** Email + password login through Supabase Auth, with role-based routing (Admin, Parent, Volunteer). Phone + PIN remains a product intention, not the implemented login UI.
*   **Admin Features:**
    *   Create Parent/Volunteer accounts (via `noSessionClient` to preserve admin session).
    *   Create Child profiles and link them to parents.
    *   Manage & edit accounts and children: searchable lists, edit forms, active/inactive
        toggle, move a child to another parent, add/remove volunteer assignments.
*   **Parent Features:**
    *   Home dashboard with task summary and status. Home/history distinguish loading, failed reads,
        and empty results, with retry actions. Previous list contents survive a failed refresh.
    *   Parent and volunteer bottom tabs survive navigation to a detail/form and back.
    *   Task detail view, including volunteer evaluations on completed occurrences.
    *   Local-time greeting (pagi/siang/sore/malam), refreshed on resume and every minute while visible.
    *   Feedback/Behavior note list and submission form.
    *   Notification inbox reading the real `notifications` table: unread styling, mark-as-read on
        tap, "Tandai semua" for the whole inbox, "Muat lebih banyak" past the first 50, a deep link
        into the feedback thread, and an unread count on the home-screen bell. One flat list, never
        read/unread tabs — a tapped row must stay exactly where it is. The screen itself is shared
        with the volunteer — see below.
*   **Volunteer Features:**
    *   Monitoring Parent Task Submissions: Volunteers can now view parent-submitted task executions ("Pantau Tugas Anak"), track child responsiveness, durations, parent notes, and media proof, and submit text feedback/evaluations directly back to parents.
    *   Create Task (Program Ajar) with media upload and rich text instructions.
    *   Reusable program-ajar templates, shared across volunteers: save one from the create form
        or from an existing program, then start a new program from it without re-uploading media.
    *   Home screen "Anak Binaan Saya": one card per assigned child with age, parent, condition
        note and running-programme count; tapping one opens the create form with that child set.
    *   Recurring programs (daily / weekly / monthly / every-N-days) with an end date, plus a
        "Program Ajar Saya" screen for stopping a running program.
    *   Notification inbox and a bell badge on Beranda, reading the `feedback_baru` rows
        `on_feedback_insert` fans out over `anak_relawan`. Same screen as the parent's, differing
        only in its empty-state line.
*   **Branding:** Custom app icon and splash screen using the official Sipatokkong logo.
*   **Communication:** Feedback threads for parent-volunteer interaction, with one-tap quick
    replies for the volunteer and a triaged inbox ("Perlu Ditanggapi" / "Sudah Beres").
    The open thread refreshes immediately on resume, then every 10 seconds after each refresh.
    Polling stops below RESUMED and cancels its in-flight read. Failed refreshes retain messages
    and drafts; initial failures offer Retry. Refreshes serialize with post-send reloads.

### 🚧 In Progress / Partially Built
*   **Task submission/evaluation notifications:** client routing and migrations `0015`/`0016`
    are implemented and tested locally, **not applied to production**. Submission notifications
    go to the task author, evaluations to the submitting parent. Both use the completion id,
    so inbox and push taps open the exact occurrence even outside the parent's 30-day history.
    Install the new APK before enabling the producers. Commit `0015` before applying `0016`;
    new PostgreSQL enum values cannot be used in the same transaction that adds them.
    The existing push trigger/function needs no changes. No historical notification backfill.
*   **Media Compression:** Wired up for the presensi photo **and** the parent's task proof
    (`core/media/ImageCompressor`, images only). The `tugas-demo` upload and any **video** proof
    still send raw bytes; `LightCompressor` (video) is still unused.
*   **Notifications:** The read path is done for **both** roles — every row any trigger writes is
    now reachable in the app, through one shared `NotificationScreen`. Still pending: real-time
    updates (the table is **not** in the `supabase_realtime` publication, so a subscription would
    receive nothing). **Push (FCM) now works end to end and has been seen on a real phone**, which
    this paragraph denied until 2026-08-30: `push_tokens` holds two devices, `net._http_response`
    holds `200`s from the Edge Function, and a task-creation push arrived on a parent's phone
    carrying the volunteer's and child's names — so `0010` is confirmed working, not merely applied.
    Tapping one from a cold start opens the right screen; that path crashed until the same day and
    the fix is in the Change Log. Both bells still refresh only on route entry, so a notification
    arriving while the app is open is not seen until the user navigates. In the tray, feedback pushes are
    published as Android **conversations** (long-lived shortcut + Person, so the sender's name sits
    beside their avatar on Android 11+), and notifications collapse per entity: every message in one feedback thread shares a card and accumulates into
    a conversation, and every reminder about one task replaces the last.
    `'tugas_reminder'` is written by the daily `pengingat-tugas` cron job and has its own
    `NotifType`, icon and tests. `0012` (applied) sends it on the day an occurrence falls; `0013`
    (**written, not yet run**) adds H-3 and H-1 for one-shot tasks only. **Neither has ever fired
    in production** — `kirim_pengingat_tugas()` has returned `0` on all four calls so far because no
    task fell due on those days, so `tugas_jatuh_pada` has yet to match a single row. The
    `notif_type` values `'presensi'` and `'tugas_missed'` remain declared but unused — no trigger
    writes them, and the UI renders them generically on purpose.


### ❌ Not Built / Deferred
*   **Admin Dashboard:** Progress recaps for donors. (Attendance overview is built — see above.)
*   **Data Export:** Exporting progress reports to CSV/PDF.
*   **Scheduled Reminders:** Automated weekly reminders for parents.
*   **Offline Mode:** WorkManager queue for uploads during intermittent connectivity.
*   **In-app video playback:** no media3/ExoPlayer. A demo video is watched by downloading it and
    opening it in the phone's gallery player.

## Domain Constraints
*   **Low Literacy:** UI must be visual-heavy with clear, simple Bahasa Indonesia labels. Mandatory demo videos/photos for tasks.
*   **Low Bandwidth:** Data frugality is a hard requirement. Media must be compressed before upload.
*   **Simple Access:** Account creation is restricted to Admins. Current login uses email + password; the intended Phone + PIN flow has not been implemented.

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

### How a behavior note gets closed

`feedback.status` is the enum `('open', 'in_process', 'answered')`, default `'open'`. The **only**
thing that advances it is a reply written by a **relawan**, via the `on_balasan_insert` trigger in
`supabase/migrations/0001_initial_schema.sql:386-402`, which sets `status = 'answered'`,
`answered_at = now()`, and inserts the parent's notification.

Consequences that follow from that, and are easy to re-derive wrongly:

*   **A parent's own reply changes nothing** - not the status, and no notification to the volunteer.
    A thread cannot be reopened once answered.
*   **`'in_process'` is unreachable.** No trigger and no client code writes it. The chip that
    rendered it was removed; the enum value is left in Postgres because dropping it would cost a
    migration for no gain.
*   **A note with nothing to ask still needs closing.** There is no acknowledge-without-replying
    path, by design - see the quick-replies entry in the Change Log for why.

### Which notes a volunteer sees first

`FeedbackTriage.needsAttention` in `ui/feedback/FeedbackTriage.kt`. A note is work when it is
**not** answered **and** either its `mood` is `Rewel`/`Sakit`, or its `kendala` contains one of the
three difficulty chips.

The trap: **`kendala` is almost never empty**, so "has a kendala" is not a signal. Two of the five
chips that write that column - `Lancar` and `Banyak Kemajuan` - report that things went *well*.
The concerning set is spelled out explicitly in `FeedbackChallenge.CONCERNING`.

The second tab is **"Sudah Beres"**, and `FeedbackTriage.settled` is what fills it. It was first
called "Kabar Baik", which was wrong: because `needsAttention` returns false for anything answered,
a note about a sick child moves into that tab the moment the volunteer replies. The bucket is
"nothing to do right now", not "good news" - keep any future renaming honest about that.

### The chip on a feedback row

`FeedbackTriage.stateOf` returns one of three states, and **both the chip and the tabs derive from
it**:

| State | Chip | When |
|---|---|---|
| `DIJAWAB` | "Dijawab" (green) | `status == 'answered'` |
| `PERLU_DIBALAS` | "Perlu Dibalas" (orange) | `needsAttention` is true |
| `KABAR_BAIK` | "Kabar Baik" (teal) | neither - positive and unanswered |

This sharing is the point, not a convenience. The chip used to read `feedback.status` alone while
the tabs read `needsAttention`, so a positive unanswered note sat under "Sudah Beres" wearing an
orange "Menunggu" - the row contradicting the tab directly above it. `FeedbackDisplayTest` asserts
`stateOf(fb) == PERLU_DIBALAS` **if and only if** `needsAttention(fb)`, so that split cannot come
back silently.

Note the two `stateOf` branches are independent, not ordered: `needsAttention` already returns false
for anything answered, so swapping them changes nothing. (An earlier version of this document
claimed the order was load-bearing. It is not - proved by swapping it and watching the tests stay
green.)

### What a feedback row is titled

`FeedbackSummary.headlineOf`: the free text if the parent actually typed some, else the kendala
chips, else the mood label, else "Catatan".

The trap: **`isi` is a poor headline on its own.** "Catatan Tambahan" is optional, `isi` is
`not null` in Postgres, and `CreateFeedbackViewModel` bridges the gap by backfilling the constant
`CATATAN_RUTIN`. Using `isi` directly - which the list did - made every chip-only note render with
an identical title. `subtitleOf` returns null when the chips were promoted to the headline, so they
never appear twice on one row.

### Where long text is cut off, and where it is not

**Cap a string where it sits inside something that offers a way to see more. Do not cap it where it
is the thing that gesture leads to.**

That is a rule about affordances rather than about screens, which matters because the two do not line
up: `ProgramListScreen`'s expanded instruction lives on a list screen but is a detail surface, and it
stays uncapped.

So list and card surfaces cap — `tugas.judul` at 2 lines on both the parent's home card and the
volunteer's programme card, so a programme reads the same to the person who wrote it and the person
who receives it; names and phone numbers at 1, because a second line means bad data and an ellipsis
says so; a notification title and body at 2; `presensi.catatan` at 3, the only 3, because a
one-sentence visit note fits in three lines where two would clip most of them.

And detail surfaces do not. `TaskDetailScreen` caps nothing: a low-literacy parent is being asked to
*carry out* that instruction, and ellipsising it withholds the task rather than tidying it.
`FeedbackThreadScreen`'s note and reply bubbles are uncapped for the same reason — a truncated
message with no way to expand is data loss, not an overflow fix.

Every capped `Text` also needs `overflow = TextOverflow.Ellipsis`, or Compose hard-clips mid-glyph.

### What a notification card says

`NotifDisplay.from` maps one row of `public.notifications` to one card, and every rule in it exists
because the client never writes this table: the rows are frozen snapshots taken by a trigger.

**The kind comes from `type`, the destination from `entity_table`.** They are read independently and
must stay that way - `on_balasan_insert` stores the *thread* id rather than the reply id precisely so
the deep link works, and deriving one from the other undoes that. A `type` this build does not
recognise falls to `LAINNYA` and a plain bell rather than being dropped, which is why
`Notification.type` is a `String` and not a Kotlin enum: an unknown enum constant throws inside
`decodeList` and would cost the whole inbox rather than one card.

**The three written kinds split by role.** A parent gets `tugas_baru` and `feedback_balasan`; a
volunteer gets only `feedback_baru`. Nobody sees all three, which is why the only role-specific thing
on the shared screen is its empty-state line.

**The second line is dropped when it says nothing.** `on_feedback_insert` freezes `left(isi, 120)`
into `body`, so it inherits the `CATATAN_RUTIN` problem described directly above - except a
notification row carries no `mood` or `kendala` to fall back on, so there is nothing to promote in
its place. `bodyLineOf` therefore returns null for the filler and the card renders title and date
only. Equality, not `startsWith`: a parent who types that sentence and then adds to it has said
something. There is no "no body" placeholder at all, because all three triggers source `body` from a
`not null` column and a blank one cannot occur.

### What a program-ajar template carries

`tugas_template` (migration `0006`) stores **the exercise** and nothing about the delivery. The
split is enforced by two pure functions, not by convention: `TaskTemplateInsert.from`
(`data/model/TaskTemplate.kt`) going in, and `applyTemplate` (`ui/volunteer/TemplateForm.kt`)
coming out. `TaskTemplateTest` holds both to it.

| Field | In the template? |
|---|---|
| `judul`, `instruksi`, `priority_level`, `alat_bantu` | Yes |
| `media_kind` + `media_path` | Yes - the main reason the feature exists |
| `recurrence` + `_days` / `_day_of_month` / `_interval` | Yes, the **pattern** only |
| `anak_id` | **No** - asked every time; this is the point |
| `deadline`, `starts_on`, `ends_on` | **No** - always relative to now |
| `status`, `week_of`, `stopped_at` | **No** - belong to one program, not the recipe |

Applying a template only **prefills the existing create form**. Every field stays editable, and a
recurring template defaults `starts_on` to today exactly as picking the pattern by hand does, while
leaving `ends_on` blank because it is required and nobody has chosen it yet.

### Why template media needs its own bucket

Access to `tugas-demo` is decided by the **first path segment being a child's UUID**
(`storage_child_id` + the `tugasdemo_*` policies, `0001_initial_schema.sql:404-461`). That makes it
the wrong home for shared template media, twice over:

*   A volunteer **not** assigned to the origin child cannot read it, so applying someone else's
    template would fail.
*   The target child's **parent** could not open the demo, because the path would still name a
    different child - and the parent's app calls `signedDemoUrl` itself.

So media makes two server-side copies and never passes through a phone twice:

```
save    tugas-demo/{anak_A}/...        -> tugas-template/{relawan_A}/...
apply   tugas-template/{relawan_A}/... -> tugas-demo/{anak_B}/...
```

The second copy is **required, not an optimisation** - it is what puts the object under the new
child so `is_parent_of` admits their parent.

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

*   ~~**Duplicate presensi rows are still possible in the live project.**~~ **Withdrawn 2026-08-30 —
    this was wrong.** `presensi_unik_harian` was already applied to the live project; the claim came
    from this file, and this file had never been checked against the database. Duplicates have not
    been possible for some time. Kept rather than deleted, because the mistake is the useful part:
    every "still broken in production" line here is a memory until someone queries for it.

*   **The notification inbox only grows.** `notifications` has no DELETE policy — by design, since
    rows are written by `security definer` triggers and nothing should be able to erase its own
    history — and no pruning of any kind. One row per program and per volunteer reply accumulates
    forever.

    ~~"There is no paging… the honest fix is a retention policy in the database, not paging in the
    client."~~ **Superseded 2026-08-30**, and the old recommendation was wrong twice over: a
    retention cron would delete the only record of what a parent was told, on a table deliberately
    given no DELETE policy, and it needs a scheduled service-role job that does not exist. Paging
    cost one query parameter and one button. The screen now loads 50 at a time with "Muat lebih
    banyak", so nothing is unreachable — but the table itself still never shrinks, which is the part
    of this limitation that remains true.

## Change Log

### [2026-10-25] - Recoverable Loads, Preserved Navigation, Live Chat Refresh and Task Notifications
* **What changed:** parent home/history now show honest loading/error states and working retries;
  task details can retry without leaving or losing form data. Parent and volunteer tabs are saved
  across detail navigation. Feedback threads refresh every 10 seconds only while RESUMED, preserve
  messages and drafts on failed refreshes, and serialize reads with the refresh after sending.
  Stable child/parent names are reused rather than fetched every poll. The poll rereads the thread
  and reply list, so it also sees status changes and does not miss late-committing replies.
* **Notification delivery:** new `tugas_setoran`/`tugas_evaluasi` kinds point to
  `tugas_penyelesaian.id`. The shared submission route renders the volunteer review screen or the
  parent's exact dated task according to the signed-in role. Unchanged/blank evaluations do not
  notify; real edits do. Task detail reads one completion, and optional media failures no longer
  discard an already-read evaluation. Existing one-off completion status updates remain intact.
* **Verified:** debug APK build; 384 JVM tests, including real repository HTTP fixtures for initial
  failure/retry, incoming replies, preserving drafts through failed refreshes, and historical
  evaluation routes. Lifecycle tests cover pause/resume, no overlapping polls, and cancellation.
  Six instrumentation tests passed on Android 17/API 37, including home/history error/retry states,
  stale list retention, and evaluation display. New SQL migrations passed in an isolated local
  PostgreSQL 18 database using the repository's base schema/RLS and platform stand-ins: recipients,
  duplicate attempts, blank/no-op edits, exact completion pointers, role access, no backfill, and
  preserving existing one-off versus recurring completion behavior.
* **Verified on the running app:** offline Home and History show Retry instead of empty states;
  a loaded feedback thread retains its content while offline and recovers automatically when
  connectivity returns, without a button press. Back returns to the Catatan tab. A read-only
  completion link opened the existing 27 July occurrence as completed, including its proof,
  rather than choosing the current date. No user content was written to the live backend.
* **Deployment / not yet verified:** `0015` and `0016` have NOT been applied to production.
  Commit `0015` before `0016`, and distribute the compatible APK before enabling the new producers.
  Real FCM delivery for the two new kinds and a live two-account submission/evaluation round trip
  still require deployment. No production messages or test submissions were created for verification.
  Phone + PIN claims in the status summary were corrected to the existing email/password UI.


### [2026-10-25] - Parent Evaluations and Greetings Follow the Actual Data and Time
* **What changed:** `TaskRepository.completionFor()` now requests `feedback_relawan` alongside
    the completion's proof link; the home-list query remains narrow. The existing detail screen
    can therefore display the volunteer's evaluation. Replaced the parent's fixed greeting with
    device-local time: morning 05:00–10:59, midday 11:00–14:59, afternoon 15:00–17:59, night
    18:00–04:59. It refreshes immediately when resumed and every minute while started.
* **Regression coverage:** A real repository request against a local HTTP projection fixture
    reproduced the old bug (evaluation returned null) before the query fix. Added date-isolation
    coverage, 12 greeting cases including boundaries/midnight, and two Compose device tests for
    displaying an evaluation without proof media and omitting the card when no evaluation exists.
    Made the detail body internal for the UI tests, using the existing debug-only Compose test host.
* **Verified:** `:app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`
    succeeded: 375 unit tests and 3 device tests, zero failures/errors/skips, on `Medium_Phone`
    (Android 17 / API 37). The first UI run exposed an old Espresso reflection call incompatible
    with API 37; upgrading test-only Espresso 3.5.1 → 3.7.0 and AndroidX JUnit 1.1.5 → 1.3.0
    resolved it. The installed app showed "Selamat sore," at device time 17:23. A real completed
    task and its proof section opened successfully; the application PID's log had no error,
    fatal exception, or ANR. A read-only authenticated query confirmed the live feedback column
    exists (HTTP 200), but all three completions visible to this parent had null evaluations.
    Instrumentation cleans up the target installation; the app was reinstalled and the existing
    parent authentication preference restored afterward.
* **Not yet verified:** A volunteer writing an evaluation and that same text appearing for the
    parent against the live backend, other Android versions, physical devices, and release builds.
    Evaluation display was checked on-device with controlled test data. Greeting boundaries were
    tested with local times, not by changing the emulator clock. No live task/evaluation rows or
    database schema were changed.

### [2026-10-25] - Local Build, Unit Tests, and Parent Runtime Smoke Test
* **What changed:** Recorded verification of master commit `6f83064`; no application source or
    database schema changes were made. Recorded the omitted parent-evaluation query field in
    Known Limitations.
* **Verified:** `:app:assembleDebug :app:testDebugUnitTest` succeeded using the cached JDK 21:
    361 tests across 25 suites, zero failures/errors/skips. Installed the resulting debug APK
    over the existing app on the `Medium_Phone` emulator (Android 17 / API 37). Cold startup
    restored the existing parent session. Opened home, history, behavior-note list and thread,
    profile, the real notification inbox, and a task detail through a notification. No fatal
    exception or application error-level log appeared during those checks. Opening the task
    notification marked that notification read; no feedback or task completion was submitted.
* **Warnings:** SDK XML metadata version mismatch and unstripped native debug symbols during
    build; emulator EGL configuration fallbacks at runtime. These did not prevent build or launch.
    The home greeting is also hardcoded to "Selamat pagi," regardless of time.
* **Not yet verified:** Fresh credential login, admin/volunteer sessions, writes/uploads,
    volunteer evaluation round-trip, actual push delivery, production migration state, physical
    devices, release builds, and other Android versions. The parent history had no recent
    completed tasks. Test results do not establish that every feature works end to end.

### [2026-09-15] - Task Submission Monitoring and Feedback for Volunteers
* **What changed:** Added a fully integrated "Pantau Tugas Anak" feature for volunteer therapists to track parent task execution proofs and provide feedback.
* **Implementation:**
    * Wrote migration `0014_tugas_penyelesaian_feedback.sql` adding `feedback_relawan` column to `tugas_penyelesaian` and creating `penyelesaian_update` RLS policy.
    * Added submission read and write data models to `Completion.kt`.
    * Implemented repository functions in `TaskRepository.kt` to fetch submissions via raw join queries and update feedback field values.
    * Created `TaskSubmissionsScreen.kt` and `TaskSubmissionsViewModel.kt` displaying task submission lists with custom status chips ("Butuh Feedback" / "Sudah Dinilai").
    * Created `SubmissionDetailScreen.kt` and `SubmissionDetailViewModel.kt` enabling details viewing (proof media, notes, responsiveness) and feedback submission.
    * Wired up navigation routes in `SipatokkongMain.kt` and an access `ActionCard` on `VolunteerHomeScreen.kt`.
* **Verified:** Built successfully via Gradle assemble (`app:assembleDebug`).
* **Not yet verified:** Tested runtime data streaming on an actual device or simulator environment.

### [2026-09-15] - Keyboard and Navigation Bar Overlay Fix for Feedback Screens
* **What changed:** Added IME and navigation bar padding to the feedback forms and conversation thread screens to prevent system overlays (keyboard and on-screen navigation keys) from obscuring inputs.
* **Implementation:** 
    * Added `Modifier.imePadding()` to the main scrollable `Column` in `CreateFeedbackScreen.kt`.
    * Added `Modifier.navigationBarsPadding()` along with `Modifier.imePadding()` to the `bottomBar` layout in `FeedbackThreadScreen.kt`.
* **Verified:** Build succeeded (`:app:compileDebugKotlin` ran cleanly).
* **Not yet verified:** Runtime visual layout check on a real device.

### [2026-09-15] - Improved Date Field Appearance in Admin Forms
* **What changed:** Updated `DateField` in `AdminFormComponents.kt` to avoid looking disabled (grayed out) when active.
* **Implementation:** 
    * Changed `enabled = false` to `enabled = true` while keeping `readOnly = true`.
    * Wrapped the `OutlinedTextField` in a `Box` with a transparent overlay to catch clicks and trigger the date picker, preventing unwanted focus, cursor, or keyboard display.
* **Verified:** Build succeeded. 
* **Not yet verified:** Runtime visual check on a real device.

### [2026-09-15] - Improvements to Admin Account Creation and Edit Screens
* **What changed:** Enhanced both "Tambah Akun" and "Edit Akun" screens for better UX and session stability.
* **UI/UX Improvements:**
    *   Added `Modifier.imePadding()` to both forms to prevent the keyboard from obscuring input fields and action buttons.
    *   Replaced manual text entry for `familyStatus` with a Material 3 `ExposedDropdownMenuBox` featuring fixed options: **Ayah**, **Ibu**, and **Wali**.
* **Session Security:**
    *   Explicitly isolated the `noSessionClient` used for registration by setting `sessionManager = null`, preventing administrative session corruption during new account creation.
* **Verified:** Build succeeded. 
* **Not yet verified:** Runtime behavior on a real device, specifically the scrolling interaction when the IME is visible on the Edit screen.

### [2026-09-14] - Custom App Icon and Splash Screen Logo
* **What changed:** Replaced the stock Android Studio green robot icon and the vector splash screen logo with the official Sipatokkong logo from assets.
* **Implementation:** 
    * Copied `logo sipatokkong.png` from `assests/` to `res/drawable/logo_sipatokkong.png`.
    * Updated `AndroidManifest.xml` to use the new PNG for both `android:icon` and `android:roundIcon`.
    * Updated `themes.xml` (Theme.Sipatokkong.Splash) to use the new logo for `windowSplashScreenAnimatedIcon`.
    * Updated `SipatokkongMain.kt` to show the logo alongside the `CircularProgressIndicator` during the post-splash session loading phase.
* **Verified:** Build succeeded. 
* **Not yet verified:** How it looks on a real device or emulator, especially the transparency of the PNG and how it scales as an app icon. Adaptive icon support is now bypassed in favor of a direct PNG for simplicity as requested.

### [2026-08-30] - The Completion Screen Stopped Hiding Its Own Controls
* **Asked whether the screen was really the simplest it could be for a parent, the answer was no** —
    and one of the three problems was one I had just added.
* **The bottom button did two different things and only its wording said which.**
    "Kirim Bukti Foto & Selesai" meant *pick a file*; "Kirim & Tandai Selesai" meant *send this,
    irreversibly*. Two similar sentences carrying that difference, on a screen built for people who
    find reading hard.
* **A wrongly chosen photo could not be changed.** The preview was a plain `AsyncImage` with no
    action, and the button had already turned into Submit — so the only way out was leaving the
    screen and coming back.
* **And my source dialog had added a tap** for everyone who only wanted the gallery, as a modal that
    covered the form it belonged to.
* **The fix was a component this app already had.** `MediaPickerBox` — the volunteer's presensi and
    program-ajar forms both use it — now carries the proof: preview in place, "Ketuk untuk ganti"
    when filled, and the source dialog reached by tapping something obviously photo-shaped rather
    than by tapping Submit.
* **So the bottom button now means one thing.** Left **enabled** without proof on purpose: tapping it
    produces "Pilih foto/video bukti terlebih dahulu.", which points at the box above. A disabled
    button would be silent about why, and silence is the worst answer for someone who cannot read
    their way out of it.
* **`MediaPickerBox` moved to `ui/components/`.** It lived inside `CreateTaskScreen`, and importing a
    volunteer screen's helper into the parent's screen would have been a wire nobody could see.
    Three of the four media surfaces in the app now share it. Three imports it orphaned were removed.
* **Verified:** 361 tests still green, zero warnings, clean build.
* **Not verified, and I stopped chasing it:** the rendered layout. The screen sits behind a parent
    login the emulator does not have, and two probe attempts were defeated by the host screens'
    Scaffolds. What stands instead is that the component is unchanged and already renders correctly
    on two screens that *are* reachable — which is weaker evidence, and worth saying so.

### [2026-08-30] - The Cancel Button Was in the Wrong Half of the Dialog
* **Reported from the device:** in the proof-source chooser, "Batal" was in the wrong place.
* **AlertDialog lays `confirmButton` and `dismissButton` out side by side on one row.** I put the two
    real choices in `confirmButton` as a full-width `Column` and left "Batal" in `dismissButton` --
    so the column took the whole row and squeezed the cancel in beside it.
* **All three now live in `confirmButton`, stacked full width in reading order**, with "Batal" last
    and visually quietest as a `TextButton`. `dismissButton` is left unset deliberately, and the
    comment says why, so the next person does not helpfully move it back.
* **Verified by screenshot this time, not by reasoning -- I had already got this dialog wrong once.**
    The composable sits behind a parent login the emulator does not have, so it was rendered through
    a throwaway probe on a screen that was reachable, photographed, and the probe removed. Confirmed
    afterwards that no probe survives and the composable is `private` again.
* **Verified:** 361 tests still green, zero warnings.

### [2026-08-30] - Asking Whether There Is Signal Before Spending a Wait On It
* **`ACCESS_NETWORK_STATE` had been declared in the manifest since the first commit and never read.**
    `core/net/Connectivity.kt` is the first thing to read it.
* **Used before expensive actions, not before every request.** Signing in, uploading proof, saving a
    visit and sending feedback each cost the user a wait — and on an upload, real data — before
    failing with a message the phone already had. Loads are deliberately left alone: they already end
    in a mapped message with a "Coba Lagi" button beside them, and a system-service hop on every
    happy path would cost more than it gives.
* **Deviated from the plan, and worth saying why.** The plan put the check *inside* `ErrorText.of`.
    That was wrong: `ErrorText` is pure and Android-free, injecting a `Context` would have cost it
    the testability that is the whole reason it is split in two, and the DNS case is already mapped
    correctly from its message. The connectivity check earns its place as a pre-flight, not as a
    refinement of a mapping that already works.
* **Fails open.** Any error reading the system service returns true, so a phone this cannot
    interrogate is allowed to try rather than being told it is offline when it is not. And a `true`
    only ever means "worth trying": `NET_CAPABILITY_INTERNET` reports connected on a captive portal,
    and a network can drop between the check and the call it guards — which is why the mapped error
    on the way back still has the last word.
* **Verified on a device, and this is the first end-to-end proof of the error work.** With the
    emulator's wifi and data disabled, a cold start renders **"Tidak ada koneksi internet. Periksa
    jaringan Anda lalu coba lagi."** above a **"Coba Lagi"** button. Before this, that same screen
    showed `Unable to resolve host "…": No address associated with hostname` beside a button
    labelled "Retry". Confirmed by `uiautomator dump` and screenshot.
* **One thing the screenshot caught that nothing else would have:** the session error pane had no
    horizontal padding. It never showed because the text used to be a single raw line; a real
    sentence runs edge to edge and reads as though it has been cut off. Padded and centred.
* **Verified:** 361 tests still green, zero warnings, APK installed and cold-started with no crash,
    plus the offline render above.
* **Not verified:** the pre-flight on the four action paths themselves — login, proof upload, visit,
    feedback — since each needs a signed-in account. What was proven is the mechanism they share:
    with no network, `activeNetwork` is null and the app says so immediately.

### [2026-08-30] - Errors a Parent Can Read, in Place of a Stack Trace
* **A wrong PIN showed supabase-kt's five-line `RestException` blob** — the error code, the
    description, the full request URL, the request headers, and the HTTP method — painted in red on
    the login screen. No signal showed `HTTP request to https://….supabase.co/… failed with message:
    Unable to resolve host "…": No address associated with hostname`.
* **The friendly text already existed and was dead code.** 38 of 39 sites read
    `e.message ?: "Gagal memuat data"`, and `message` is non-null for essentially every exception —
    so the fallback never ran. Inverting that priority is most of the fix, and it means the sentences
    each screen already had were preserved rather than rewritten.
* **New `core/util/ErrorText.kt`, split into two functions on purpose.** `RestException` cannot be
    constructed in a unit test (it needs a live Ktor `HttpResponse`), so classification lives in
    `forMessage(raw, fallback)` — a plain string in, fully testable — and `of(e, fallback)` adds only
    what a string cannot express: the exception's type.
* **Message before type, and the order is load-bearing.** `HttpRequestException` is an `IOException`
    whose message names the real cause; reading the type first would file every one of them as
    "server unreachable" and lose the distinction between that and "you have no signal".
* **The rule everything rests on: an unrecognised error yields the caller's own fallback, never the
    raw text.** A worse message beats a stack trace on a screen belonging to someone who does not
    read English. The raw text still reaches logcat.
* **Deliberately does NOT match bare `401`/`403`.** Three digits appear in uuids, paths and counts,
    and a false positive would tell someone their session had expired when it had not. A test pins
    that `row 401f2a3b-…-000000000403 not found` stays generic.
* **Swept 37 sites across 23 files mechanically**, plus three that the pattern could not reach:
    `CreateFeedbackViewModel` had **no fallback at all** (`error = e.message`), so a null message
    silently cleared the error and a failed load looked like success;
    `CreateTaskViewModel`'s was inside a string interpolation; and
    `CreatePresensiViewModel.messageFor` — the one mapping this app already had — was folded into
    `ErrorText`, its `raw.isNotBlank() -> raw` branch being itself a leak.
* **Also fixed the only English button in the app**, `SipatokkongMain`'s bare `Text("Retry")`.
* **Verified:** 361 tests (from 348), 0 failures, zero warnings. The three rules that matter were
    each confirmed by reintroducing the defect — the raw-text leak, the `401` false positive, and the
    type-before-message ordering. A grep confirms no `.message ?:` survives outside logcat lines.
* **The fixtures are the real strings**, copied from what supabase-kt actually produces, so the day
    it rewords `invalid_credentials` a test says so rather than a user discovering a generic message.
* **Known brittleness, stated rather than hidden:** the Supabase cases match on message substrings,
    because their exception types need a live `HttpResponse`. If the wording changes upstream the
    login message quietly degrades to the generic sentence — a worse message, never a wrong one.
* **Not verified:** that a wrong PIN on a real device now reads "Nomor HP atau PIN salah." and
    nothing else. Needs a signed-in phone.

### [2026-08-30] - Camera for Task Proof, and the Compression It Could Not Ship Without
* **A parent could only pick proof from the gallery**, while the button beside the picker already
    wore a camera icon and read "Kirim Bukti Foto" — a small lie since it shipped.
* **Every piece of the camera path already existed.** `ActivityResultContracts.TakePicture` +
    `CameraUriManager.createTempUri` + a `FileProvider` exporting exactly `cacheDir/camera/` have
    been working in the volunteer's presensi flow all along. This borrowed that pattern rather than
    inventing one, `rememberSaveable` on the temp URI included — handing control to the camera app
    can take the process down, and a plain `remember` returns null with the photo already on disk.
* **`android.permission.CAMERA` is still not declared, deliberately.** `TakePicture` delegates to an
    external camera app, and declaring the permission would *create* a runtime-permission
    requirement this app currently escapes.
* **The real work was compression, and it was not optional.** `TaskDetailViewModel.submit()` read
    raw bytes straight to Supabase with no size check. That survived only because a gallery pick is
    usually already small; a camera photo is 3-8 MB, against a stated constraint of 200 KB paid for
    out of a low-income family's data. Shipping capture without compression would have been a
    bandwidth regression wearing a feature's clothes, so the two are one commit.
* **`ImageCompressor` finally has a second caller** (1280x1280, quality 80, 200 KB target). Three
    consequences of it being JPEG-only, all handled: images compress and video passes through
    untouched; the extension is forced to `jpg` for a compressed image, or a picked `.webp` would be
    stored under a name that lies about its contents; and its temp prefix, hardcoded
    `"presensi_src_"`, was renamed now that it is shared.
* **Video proof still uploads raw**, recorded as debt. No new video can arrive through this change —
    the camera takes photos only — and `LightCompressor` remains an unused dependency.
* **The chooser is an AlertDialog with two full-width buttons**, not a bottom sheet. For a
    low-literacy audience a labelled target with an icon beats an affordance that has to be learned,
    and AlertDialog is what the notification sweep confirmation already uses — one pattern, not two.
* **A coupling that neither file admitted to, now written down:** the camera arrives as a
    FileProvider URI whose MIME is resolved from the file *suffix*. `CameraUriManager` creates
    `.jpg`, which resolves to `image/jpeg` and passes `onProofPicked`'s whitelist. Change the suffix
    there and every capture starts failing here with "Format tidak didukung".
* **Verified:** build clean, 348 tests still green, zero warnings, APK installed and cold-started
    with no crash.
* **Not verified, and it is the whole feature:** that the camera opens, that the photo previews and
    uploads, and — the half most likely to be silently missing — that the uploaded file is ~200 KB
    rather than several megabytes. All three need a signed-in device; check the object size in
    Supabase Storage, not just that the upload succeeded.
* **Adds no tests, and not for lack of looking.** The launcher, the dialog and `ImageCompressor` all
    need `Context`/`Uri`, and this repo has no Robolectric. Nothing pure was extracted because
    nothing here is pure.

### [2026-08-30] - Feedback Pushes Are Conversations Now, So the Name Sits Beside the Face
* **The request:** in the tray, the sender's name sat on the title line — visually under the app-name
    row — while the avatar was thrown to the far right. Put the parent's name **next to the person's
    avatar**, not next to the app logo.
* **That layout is not a styling option.** Android 11+ draws a notification in the **Conversations**
    section, avatar and name together with a reply affordance, only when it carries a `shortcutId`
    naming a **long-lived shortcut with a `Person` attached**. `MessagingStyle` alone — which this
    app has used all along — has never been enough, and this repo published no shortcuts at all.
* **`PushNotifier` now publishes one long-lived shortcut per feedback thread** and points the
    notification at it. `pushDynamicShortcut`, not `setDynamicShortcuts`: it rotates the
    least-recently-used entry out itself at the per-app cap, so a foundation with many parents
    cannot overflow it and lose newer conversations.
* **Only the MESSAGING branch gets one.** A task reminder is not a conversation, and a shortcut
    would promote it into the Conversations section above the messages it is not.
* **Both required calls fail silently when missing**, which is the trap worth recording:
    `setLongLived(true)` and `setPerson(...)`. Omit either and Android renders the old layout with
    no error, no log, and nothing to grep for. If the card ever reverts, those are the first two
    lines to read.
* **The name beside the avatar is the parent's, via a new pure `NotifDisplay.senderOf`.** Migration
    `0010` writes titles as `"<pengirim> · <anak>"`; a conversation card has room for one name and it
    is the human who wrote the message. A title with no separator comes back whole, which is exactly
    right for the fallback wordings where the title already *is* the best name available.
* **The child is not lost, only kept off that one surface.** The in-app inbox renders the full title
    straight from the row, and the thread screen carries its own `"<anak> · <ortu>"` header.
* **The avatar is deliberately still derived from the FULL title.** `AvatarNaming.colorFor` hashes
    the whole string, so feeding it the split name would silently repaint every existing
    conversation; `initialOf` returns the same letter either way, because the person's name comes
    first. The card changes what it says without changing what it looks like.
* **Two of my own tests were wrong before the code was.** One asserted a nonsensical expectation
    through a stray `let`; the other predicted the wrong result for a degenerate title that the
    trigger cannot produce. Both were rewritten to pin what actually occurs — including an honest
    one recording that a *person's name* containing the separator would be truncated, since
    `substringBefore` takes the first, and that migration `0010` builds titles from `full_name`
    where this app has no way to enter one.
* **A real side effect at every API level:** a published dynamic shortcut appears when the user
    long-presses the app icon. That is a launcher affordance this app has never had — arguably
    useful, and definitely a visible change nobody asked for, so it is written down rather than
    discovered.
* **Verified:** 348 tests (from 340), 0 failures, zero warnings. The five new pure tests were each
    confirmed to fail with the behaviour reverted. APK installed and cold-started with no crash.
* **NOT verified, and it is the headline again:** the actual layout. It needs a real push arriving
    on a signed-in **Android 11+** phone, and no phone was connected. `adb shell dumpsys shortcut`
    showed nothing published, which is correct on a signed-out emulator where no push can arrive —
    and is also the command to check first if the conversation styling does not appear.

### [2026-08-30] - "Mark as Read" Was Sending an Empty Body, and Had Been All Along
* **The report:** tapping a notification leaves it unread, and "Tandai semua" clears the marks until
  you leave the screen and come back. Both paths fail identically — the local flip works, the
  database write does not.
* **This is the defect I claimed to fix in Part 1** (`6545bff`), on a phone running that code. That
  fix moved the write to `ServiceLocator.appScope` so a Back press could not cancel it. It addressed
  a real bug and was not this one. I had listed it under "not verified" at the time, and the lesson
  is precise: **I verified the mechanism I suspected and never the outcome.**
* **The cause was one default value.** `NotificationReadUpdate`'s only property carried `= true`.
  kotlinx.serialization omits any property equal to its default unless `encodeDefaults` is on, and
  supabase-kt serialises with `Json { ignoreUnknownKeys = true }`, which leaves it off. The PATCH
  body was therefore **`{}`** — no columns to set. PostgREST accepts that without complaint, so
  nothing failed, nothing was written, and the next `refresh()` read the row back unread.
* **Passing the value explicitly would not have helped**, which is what makes this class of bug
  nasty: `NotificationReadUpdate(isRead = true)` still equals the default, so it is still dropped.
* **Proved before fixing, in JUnit, with no device and no login.** `kotlinx-serialization-json` is a
  direct dependency, so a test could serialise the payload with the same `Json` configuration
  supabase-kt uses. It failed with `expected:<{"is_read":true}> but was:<{}>` — the whole bug in one
  assertion, and it now stands as the regression guard.
* **Fixed by removing the default** rather than by `@EncodeDefault` or a global `encodeDefaults`.
  Removing it makes the empty body unrepresentable instead of merely unlikely, and needs no
  experimental opt-in.
* **Turning on `encodeDefaults` globally would have been the dangerous fix**, and is worth recording
  as rejected: most `Insert` payloads default their optional fields to `null`, so PostgREST would
  start receiving explicit `null`s where it currently receives nothing — turning "let the column
  default apply" into "write null" across `TaskInsert`, `FeedbackInsert`, `PresensiInsert` and the
  rest. One silent write bug traded for a family of them.
* **Swept every `*Insert`/`*Update` payload for the same trap.** Two hits, `TaskInsert.recurrence`
  and `TaskTemplateInsert.recurrence`, both `= "once"` — and **neither is a bug**, because both
  columns are `not null default 'once'`, so the value the server applies is the value that was
  dropped. That safety is invisible and unenforced, so both now carry a comment saying it holds only
  while the two defaults agree, and pointing at this bug as what happens when there is no column
  default to catch the omission.
* **The 15 existing `NotificationInboxTest` cases passed throughout**, and that is the most useful
  thing about them here: they cover the pure list helpers, the RLS was checked, the coroutine scope
  was checked — the bug lived in the one layer between all of them, the serialised request body.
* **Verified:** the new test fails before the fix and passes after, which is the cause proven rather
  than argued. Full suite **340 tests (from 338), 0 failures, zero warnings**.
* **Not verified, and it needs a signed-in device:** that the mark now survives leaving and
  re-entering the screen, that the home bell badge clears, and — the one I would bet against — that
  the revert-and-banner path works in airplane mode. That branch has been dead code for the life of
  the feature: with a `{}` body the request never failed, so `onFailure` never ran once. Written
  correctly is not the same claim as executed once.
* **Rows marked read before this fix stay unread in the database.** Nothing backfills them and
  nothing should — the app cannot know which were actually seen. Expect one large sweep the first
  time each user opens the inbox after updating.

### [2026-08-30] - Three Replies in One Thread Are Now One Tray Card, Not Three
* **The symptom, from a device:** three messages in one feedback thread produced three separate
    cards in the phone's notification tray. They are one conversation and belong in one card —
    which is what `MessagingStyle` exists for, and the app was already using it.
* **Two causes, both one-liners, and the first is the whole fix.**
    `PushPayload.notificationIdFor` preferred `notification_id` — unique per `notifications` row —
    over the entity id, so every reply claimed its own tray slot. The thread id was already in hand
    (`NotifTarget.FeedbackThread.feedbackId` **is** `entity_id`) and the function already computed
    it, merely as a fallback. Inverting that precedence collapses a conversation into one slot.
* **`notificationId` keeps a real purpose as the fallback:** a row that points at no entity relates
    to no other row, so it has earned its own slot. Merging those would hide one behind another for
    no reason.
* **Task reminders collapse too, and there it means replace rather than merge.** A reminder is not
    a message from a person so it stays `PushStyle.EXPANDABLE`; H-3 → H-1 → today now leave one card
    saying where the task stands, rather than three saying where it stood on three mornings.
* **The second cause was that nothing read the card already on screen.** `MessagingStyle` was
    rebuilt from scratch with exactly one `addMessage`. It now reads back the posted notification
    via `NotificationManagerCompat.getActiveNotifications()` +
    `MessagingStyle.extractMessagingStyleFromNotification`, appends, and caps at six — the system
    renders only the last handful anyway, and uncapped the notification's extras would grow for the
    life of a long thread.
* **That read is wrapped in `runCatching` on purpose.** It touches a system service that can throw
    or return nothing on some OEM builds, and the extractor returns null for anything not built with
    MessagingStyle. Every one of those degrades to "start a fresh conversation" — today's behaviour.
    None may cost the user a notification.
* **Deliberately did NOT set `setOnlyAlertOnce`.** A merged card must still buzz on each new
    message. Silently updating a card nobody is looking at would be worse than the three cards being
    replaced.
* **Opening the conversation in-app now clears its card.** `setAutoCancel(true)` only covered
    arriving *by* the notification; reaching the thread from the inbox or the home screen left the
    card sitting there — and now that messages accumulate, a left-behind card would keep growing
    with replies already read. Added to `FeedbackThreadScreen` and `TaskDetailScreen`, in the
    composable rather than the ViewModel, since the ViewModels here stay free of Android types.
* **Both sides derive the slot from one shared `PushPayload.trayIdForEntity`**, and a test pins that
    they agree. A cancel computed differently from the post would miss silently, leaving exactly the
    stale card it exists to remove — the kind of bug that looks like nothing at all.
* **The `PendingIntent` request code is the same id, and that is now right by construction rather
    than by care.** Every message in one conversation routes to the same destination, so
    `FLAG_UPDATE_CURRENT` refreshes an intent that was already identical. The old comment warning
    about shared request codes handing back one another's extras was rewritten to say so.
* **`PushPayloadTest`'s `two different rows do not collide` asserted the behaviour being removed.**
    Rewritten rather than deleted, keeping a note of what it used to claim and why that changed —
    it *was* correct, for a design that produced three cards.
* **Verified:** 338 tests (from 333), 0 failures, zero warnings. The five new tray-slot tests were
    each confirmed to fail with the old precedence restored. APK installed and cold-started on
    `emulator-5554` through a `feedback_thread` deep link with no crash, which exercises the new
    cancel effect.
* **NOT verified, and it is the headline behaviour:** the merge itself. Proving it needs two real
    pushes arriving at a signed-in device, and the phone was disconnected by then. Also unverified:
    that the cancel actually clears a card, for the same reason. The accumulation and the cancel are
    Android glue with no Robolectric in this repo, so they carry no unit tests either — the pure
    part was factored out as far as it honestly goes, and the rest is a device check.

### [2026-08-30] - Reminders Before a One-Shot Task Is Due, and What "Deadline" Actually Means Here
* **Asked whether a "deadline approaching" reminder existed, the answer was no.** `0012` reminds on
    the day an occurrence falls and nothing sooner — its only predicate is
    `tugas_jatuh_pada(t, hari_ini)`, a same-day equality test. For a task assigned a week ahead that
    is one notification, on the morning it is already due.
* **`deadline` is not a deadline, and that finding shaped the whole feature.** The field appears
    only in the non-recurring branch of the create form (`CreateTaskScreen.kt:331-351`) and
    `CreateTaskViewModel.kt:428-430` writes `null` for every recurring type. For a `once` task it
    **is** the occurrence date (`Task.kt:204-213`). So "approaching deadline" and "approaching
    occurrence" are the same question here, and `tugas_jatuh_pada` already answers it — the new
    migration needed no date arithmetic of its own, only a shifted argument.
* **`0013` adds H-3 and H-1 for `once` tasks only.** A daily programme is "due tomorrow" every
    single day, so an H-1 rule on it would send two notifications a day per task, forever; three
    active tasks would be six a day. That is how people learn to turn notifications off, and it
    would cost this app the only channel it has to reach a parent.
* **No new `notif_type` value, and the reasoning is a dependency worth recording.** `tugas_reminder`
    is reused. The dedupe key is (task, type, today) and a `once` task has exactly **one**
    occurrence, so its three reminders fall on three different days and cannot suppress each other.
    **That safety ends if the lookahead is ever extended to recurring tasks** — one task could then
    occupy two buckets on one day and the per-day dedupe would silently drop one. The fix at that
    point would need the target occurrence date in the dedupe, which `notifications` has no column
    for. Said in the migration itself, not only here.
* **No client change at all.** `tugas_reminder` already maps to `NotifType.TUGAS_REMINDER` with its
    alarm icon, the row already carries `entity_table = 'tugas'` so the deep link works, and
    `styleFor` already gives it the expandable tray layout. Verified by reading each of those,
    rather than assumed.
* **The completion check uses the occurrence being reminded about, not today.** `hari_ini` there
    would be wrong in both directions: it would keep nagging about a task the parent finished early,
    and it would suppress a real H-3 reminder whenever some unrelated occurrence happened to be
    completed today.
* **Two comments elsewhere had gone false and were rewritten rather than left.** `Notification.kt`
    still described `tugas_reminder` as a value nothing writes — it is now the proof that the
    String-not-enum defence works, since `0012` began writing it long after builds that had never
    heard of it had shipped. And `NotificationRepository`'s `id` tiebreaker was justified against a
    *hypothetical* batch writer; `kirim_pengingat_tugas()` inserts every reminder in one statement,
    so all rows share the identical transaction `now()` and the tie is now real every morning.
* **A trap recorded for whoever finds "the reminder never arrives".** The Deadline field is labelled
    "(opsional)" and `submit()` never validates it. Left empty, `toRecurrenceRule` falls back to
    `created_at`, so a one-shot task is due **the day it was created** — already past, so H-3 and
    H-1 can never fire for it. The same fallback is why such a task shows "Terlambat" on the
    parent's home the next morning. Left as-is by decision; written into `0013`'s footer so it is
    diagnosable as a filled-in field rather than a broken job.
* **Verified:** build clean, 333 tests still pass, zero warnings — which only proves nothing was
    broken, since this change is one SQL function and adds no Kotlin.
* **NOT verified, and this is the second migration in a row handed over unexecuted.** I have no
    database credentials and the Supabase CLI here is not authenticated. Worth stating plainly:
    `0012` was handed over the same way and `kirim_pengingat_tugas()` has since been called four
    times, returning `0` every time, because no task fell due on those days — so
    `tugas_jatuh_pada` has still never matched a single row in production. "It reads correctly" has
    already been shown not to mean "it fires".
* **No tests, and not for lack of trying to find an honest one.** This is one SQL function; the repo
    is JUnit4-only with no Postgres in the test path. Kotlin assertions about `RecurrenceRule` would
    test the Kotlin, not the SQL that must match it, and would read as coverage that does not exist.
    What replaces them is the dry-run query in `0013`'s footer, which shows exactly which tasks
    would be notified and in which bucket without sending anything.

### [2026-08-30] - Tapping a Push From a Cold Start Killed the App, and My First Fix Did Not Work
* **The symptom, reported from a real device:** a volunteer creates a task, the parent's phone
    buzzes, the parent taps it — the app opens for two or three seconds and closes again.
* **This was my regression, introduced in Part 1**, not by the deep link Part 2 added. Before the
    `graphStart` latch, `startDestination` was hardcoded to `"login"`, so the `NavHost` composed on
    the very first frame and `navigate()` always had a graph. The latch that fixed the login flash
    is what opened this. Part 2 only made it *frequent*: before it only a feedback push could reach
    this path, and now every task notification does.
* **Confirmed from the device's crash buffer before touching any code**, rather than fixed on a
    hypothesis: `IllegalArgumentException: Cannot navigate to tugas_detail/… Navigation graph has
    not been set`, thrown at `SipatokkongMain.kt:106` — the `navigate()` inside the push effect.
* **The mechanism.** On a cold start the first composition has `graphStart == null`, so the NavHost
    does not compose and the controller has no graph. Effects then run: `LaunchedEffect(resolved)`
    assigns `graphStart`, which only *schedules* a recomposition, and the push effect runs in that
    same pass and navigates against nothing.
* **My first fix was wrong, was shipped to the device, and failed there.** Adding `graphStart` as a
    key and returning early while null looks obviously correct and is not, for two reasons worth
    keeping:
    1. Reading `graphStart` **inside the coroutine reads the live value**, not the key the effect
       was launched with. `LaunchedEffect(resolved)` assigns it during the same effect pass, so by
       the time the body ran the guard already saw non-null while no NavHost had composed.
    2. Even a composition-time snapshot would only be reasoning about effect *ordering*, and
       whether `NavHost` installs its graph during composition or in an effect of its own is a
       library implementation detail that is free to change.
* **What found the failure was the defensive `runCatching` I had added alongside** — it turned the
    crash into a logged warning, and the warning said the graph still was not set. Without that log
    line the "fix" would have looked like a success: no crash, and a notification tap that silently
    did nothing. That is the exact failure mode a swallowed navigation invites, and it is why the
    catch logs loudly rather than quietly.
* **The working fix waits for the graph instead of predicting it:**
    `navController.currentBackStackEntryFlow.first()` before navigating. It emits only once a graph
    is set and a destination is current, and emits immediately when that is already true — so it is
    correct regardless of how the frames fall, and costs nothing on the warm path.
* **Verified on the reporter's own signed-in phone, not on the emulator.** Installed, force-stopped,
    and relaunched with the tray intent reproduced exactly:
    ```
    adb shell am start -n com.antoniusindra.sipatokkong/.MainActivity \
        --es push_route "tugas_detail/00000000-0000-0000-0000-000000000000"
    ```
    Result: no crash, no navigation warning, and a screenshot showing the app open **on the task
    detail screen** reading "Tugas tidak ditemukan." — correct, because the id was deliberately
    fake. That command needs no real push and is the repro to reuse.
* **Also learned from the device, and it corrects this document:** FCM is not untested any more.
    `net._http_response` holds five `200`s, `push_tokens` holds two rows, and a real task-creation
    push arrived on a real phone with the name in its title — so migration `0010` is confirmed
    working end to end, not just applied.
* **Migrations `0010`, `0011` and `0012` are applied**, verified by query rather than by memory, and
    all twelve are now recorded in `supabase_migrations.schema_migrations`.
* **Still not verified:** the daily reminder. `kirim_pengingat_tugas()` has now been called four
    times and returned `0` every time, because no task fell due on the days it ran — so the second
    call returning `0` proved nothing about the dedupe either. `tugas_jatuh_pada` has still never
    matched a row.
* **No unit test, and I will not pretend otherwise.** The bug is Compose effect-versus-composition
    ordering; reproducing it needs the Compose runtime, and this repo is JUnit4 only. The adb repro
    above is what replaces a test, and it is written down here for that reason.

### [2026-08-30] - Closing the Text-Overflow Gaps, and a Daily Reminder That Finally Writes an Unused Enum Value
* **The text-cap sweep in Part 1 missed three `Text` composables, and I found them by auditing
    rather than by guessing.** A script over every `maxLines` in `ui/` turned up six without an
    `overflow`; three were false positives (`OutlinedTextField` needs none, and `RichTextDisplay`
    already sets it internally). The three real ones were all in `TemplateListScreen` — template
    name, template title, and the teaching-aid label row. Without `overflow` Compose clips
    mid-glyph, so a long name ended in half a letter with nothing to say it had been cut.
* **The heads-up popup was uncapped, and the fix could not wait for the database.** `notif_snippet`
    in migration `0010` caps and strips markers server-side, but `0010` is still unapplied, and its
    own footer says rows written before it are deliberately never backfilled. So the server cap can
    only ever cover rows written after it runs.
* **`NotifDisplay.snippet` is therefore a deliberate second implementation of a server-side rule,**
    which is normally a mistake. It is right here because it is the only thing that cleans rows this
    client did not write: pre-`0010` rows, and every row written while `0010` is pending. It is also
    the single point both surfaces read — the inbox card through `from`, and the push tray through
    `PushPayload.parse` — so one function fixes both. Without it `BigTextStyle.bigText` expands a
    500-character instruction into a wall of text in the notification shade.
* **What it strips, and what it deliberately does not.** Block markers (`###`, `- `, `1. `) only at
    **line starts**, matching the SQL's `n` flag; inline `**`, `__`, backtick anywhere; every
    whitespace run collapsed. A `#` or `-` mid-sentence is ordinary text and survives — "nilai 5 - 3
    = 2" must not become "nilai 5 3 = 2". Both halves of that rule now have a test.
* **My first version of the end-to-end test was wrong and the suite caught it.** I put `###` in the
    middle of a single-line fixture and asserted it vanished. It does not, correctly — the rule is
    line-leading only. The fixture became a multi-line raw string, which is also the shape the
    column actually holds, and a second test now pins the mid-sentence case from the other side.
* **`tugas_reminder` gets its own `NotifType`.** The catch-all `LAINNYA`'s KDoc literally names a
    "`tugas_reminder` cron job" as the hypothetical it exists for. Now that one writes the value,
    leaving it generic would waste the distinction: `TUGAS_BARU` answers "something arrived" and
    `TUGAS_REMINDER` answers "something is due now", and a parent scanning a list needs to tell a
    task they have already seen from one they have not. Same colours, different glyph — the colour
    says "about a task", the alarm says "due now".
* **Migration `0012` writes it, and the duplicated recurrence logic is the interesting part.**
    `RecurrenceRule` already answers "does this task fall on this day" in Kotlin with 16 tests
    behind it, and `tugas_jatuh_pada` is a second implementation that can drift. It is still the
    right trade because the two are not the same size: the Kotlin has to **enumerate a range** —
    walking days, clamping short months, anchoring an interval so the cadence never slips — and a
    cron job only ever asks about **one day**, which collapses all of that into a per-type
    predicate. Fifteen lines against a hundred. Every branch names the Kotlin it mirrors.
* **Three details in that SQL that are easy to get wrong.** `least()` ignores nulls in Postgres,
    which is exactly the "no limit from that side" behaviour `effectiveEnd` gets from its when/else
    chain. `stopped_at` is exclusive, so the last live day is `stopped_at - 1`. And the interval
    branch is a `CASE` rather than `interval >= 1 and ... % interval = 0`, because Postgres does not
    promise to evaluate `AND` left to right and the second half divides by it.
* **"Today" is computed in WITA, not UTC,** or a job running at 23:00 UTC would ask about tomorrow's
    tasks for half the people it is meant to remind. The cron expression carries the offset
    (`0 23 * * *` = 07:00 Makassar); the function carries the date arithmetic.
* **The job is idempotent within a day** — it skips any task that already has a `tugas_reminder`
    written today, compared in WITA. That is what makes it safe to run by hand while testing, and
    safe for pg_cron to retry after a failure. The migration ends with the queries to prove it: run
    it twice, the second must return 0.
* **`tugas_missed` deliberately not built.** A second job telling a parent they failed yesterday is
    a different product decision from a reminder, and it is the kind that makes people turn
    notifications off.
* **Verified:** `:app:testDebugUnitTest` + `:app:assembleDebug`, **333 tests (from 322), 0 failures,
    zero compiler warnings**. The four snippet rules were each checked by removing them and
    confirming the intended tests failed. APK installed and cold-started on `emulator-5554` with no
    fatal exception.
* **Not verified, and this time it includes the whole migration.** `0012` has never been run — I
    cannot reach the database — so `tugas_jatuh_pada` has executed exactly zero times. Its SQL is
    reasoned against the Kotlin line by line, and that is not the same as having run. Neither is
    the cron schedule, the pg_cron extension being enabled, or the reminder appearing in a tray.
* **Order now matters across three migrations:** `0010`, then `0011`, then `0012`. `0012` calls
    `notif_snippet`, which `0010` defines.

### [2026-08-30] - Notifications That Arrive Somewhere, and Triage That Tells the Truth
* **A task notification opened nothing, and the data had been there all along.** `on_tugas_insert`
    has always written `entity_table = 'tugas'` and `entity_id = new.id`, and `push-notify`
    forwards every column into the FCM payload. The client discarded it at one line in
    `NotifDisplay.targetOf`. What was genuinely missing was not the task id but the **occurrence
    date**: `notifications` has no date column, and a `tugas_baru` row is written when the
    programme is created, which is rarely a day any of its occurrences falls on.
* **The constraint that decided the design is the push, not the inbox.** `PushPayload.routeFor`
    runs inside `SipatokkongMessagingService` while building a `PendingIntent`, where a suspend
    `getTask()` is impossible. So resolving the date before navigating would have worked for a
    tapped card and not for a tapped push — and those two must never disagree, which is the
    invariant `PushPayloadTest` exists to defend. Instead `tugas_detail` now declares
    `occurrenceDate` an **optional query argument**, following the idiom `create_program_ajar`
    already uses in the same file, and `TaskDetailViewModel` resolves it. That resolution is free:
    the recurrence rule it needs is on the `Task` that function already fetches on its first line.
* **`RecurrenceRule.occurrenceNearest(today)`** is the new pure function — next occurrence on or
    after today, else the most recent past one. It lives on the rule rather than beside
    `TaskOccurrences` because the answer depends on the private `effectiveEnd`, which encodes the
    `stoppedOn - 1` exclusivity; a helper outside the class would have to re-derive that and drift.
* **Two subtleties in it that a simpler version gets wrong.** `ONCE` needs its own branch, so no
    probe width can ever hide a one-shot task assigned two years ago. And the past side is anchored
    on the rule's own end rather than on today, so a programme that finished 1500 days ago opens
    its final day instead of erroring. `NEAREST_PROBE_DAYS = 1000` is not arbitrary:
    `CreateTaskViewModel.onIntervalChange` caps the interval field with `take(3)`, so 999 days is
    the widest gap between occurrences this app can create.
* **A parent's reply now reopens the thread — migration `0011`, one UPDATE, no schema change.**
    `feedback_status` has declared `'in_process'` since `0001` and nothing had ever written or read
    it. `0010`'s comment said it deliberately left `status` alone on a parent reply; that reasoning
    was right about a thread still waiting and blind to a thread already `answered`, where leaving
    it alone keeps the note out of the volunteer's list forever. `0011` says so in place rather
    than quietly replacing the comment.
* **Free text is now a triage signal, and it needs no keyword list.** `CreateFeedbackViewModel`
    backfills an untouched "Catatan Tambahan" with `CATATAN_RUTIN`, so the app already records the
    *act* of typing. A parent who typed had something to say. This over-triages on purpose:
    "Alhamdulillah anak senang sekali" costs a volunteer one read, while "anak demam 3 hari" filed
    under Kabar Baik because the mood chip said Senang costs a child. Those errors are not
    symmetric. **Expect past notes to reclassify in bulk when the APK updates.**
* **`hasTypedNote` extracted to `FeedbackVocabulary`,** because that predicate had already been
    written twice (`FeedbackSummary.headlineOf`, `NotifDisplay.bodyLineOf`) and this was the third
    caller. Also moved `FeedbackChallenge`'s KDoc back onto `FeedbackChallenge`: two KDoc blocks
    had stacked above `CATATAN_RUTIN`, so only the second was attached and the first documented
    nothing.
* **Names on the feedback screens, and the gap was wider than reported.** The thread screen said
    only "Detail Feedback"; the **volunteer's feedback list was equally anonymous** — four assigned
    children, four rows, no way to tell them apart. Both now carry "Andi · Ibu Sari", child first.
    That is deliberately the reverse of `0010`'s notification title: a tray entry answers "who is
    speaking", these screens are organised around the child.
* **New `ChildRepository.visibleChildOptions()`** — id and name, **without** the `is_active`
    filter the other list methods carry. That filter is right for a picker and wrong for labelling
    history: the note about a child who has since left is exactly the one whose context is hardest
    to reconstruct. Names are fetched only for a volunteer; a parent has one child and every note
    is their own, so for them it would be two round trips to restate what they already know.
* **No RLS change was needed in either direction,** verified against `0001:181-244`:
    `is_relawan_of` gates both the feedback and `anak_select`, and `can_view_profile` admits a
    volunteer to their children's parents. `0010` already recorded this precedent.
* **Three silent breakages caught by design review rather than by the compiler.**
    `NotificationScreen`'s `isActionable` read `target is FeedbackThread`, which would have
    compiled untouched and left *read* task rows inert while unread ones navigated. `PushPayload`'s
    tray-id fallback used `as? FeedbackThread`, so two task pushes without a `notification_id`
    would have collided on one tray slot. And `NotificationViewModel`'s comment justifying the
    `appScope` split inverted — it argued the split was needed *because* task notifications went
    nowhere.
* **A comment and a test of mine both claimed something false, and re-running the defect proved
    it.** I wrote that the order of the `in_process` and `answered` checks was "the entire fix". It
    is not: one column holds one value, so the branches are mutually exclusive and swapping them
    changes nothing. Both were corrected to say so.
* **A test was mislabelled in the same way.** "A program that ended long before the probe" used 400
    days against a 1000-day probe, so it passed with or without the `effectiveEnd` anchoring it
    claimed to pin. It now uses 1500 days, and was verified to fail without that anchoring.
* **Verified:** `:app:testDebugUnitTest` + `:app:assembleDebug --rerun-tasks`, **322 tests (from
    287), 0 failures, zero compiler warnings**. Every new rule was checked by reintroducing the
    defect it guards — nine separate reversions across triage, recurrence, push and naming, each
    confirmed to fail the intended test and only that test. The APK was installed on
    `emulator-5554` and cold-started: the rewritten nav graph constructs with no
    `IllegalArgumentException`, which is the one part of the routing change verifiable without
    signing in.
* **Not verified, and it is most of this.** Everything behind sign-in: that a task notification
    opens the right day, that a push tapped from the tray reaches the same screen, that a parent's
    reply moves a thread back to "Perlu Ditanggapi" (which additionally **requires `0011` to be
    applied**), and that the names render correctly for a deactivated child.
* **Debt, deliberately not paid here and recorded so it is not rediscovered as a mystery:**
    `ResultExt.rethrowCancellation` is defined, tested four times and **called zero times**,
    against 64 `runCatching` sites in `app/src/main`. It was written for a repo-wide sweep that has
    not happened. Deferred by the maintainer's explicit choice so this diff stays reviewable; a
    40-file mechanical change would bury it. Either sweep it or delete it — shipping it unused is
    the one option that stays wrong.

### [2026-08-30] - Measuring the Cold Start Instead of Guessing at It
* **The wait before the home screen was never network-bound, and I had been saying it was.** I had
    described it as "an auth-storage read plus one `profiles` SELECT" — one round trip. Measured on
    `emulator-5554` (API 37, debug build, **signed out**, so zero of it can be network): session
    resolution takes **3.3-3.9 s**, split roughly half in `createSupabaseClient` and half inside
    Auth. It is class loading, not latency.
* **`ServiceLocator.sessionManager` is a `by lazy`, and `ServiceLocator.init()` only stores a
    Context.** So nothing built the Supabase client until the first composition read the session
    state — on the main thread. The splash was covering an app that had not started working yet.
* **`SipatokkongApp.warmSession()` starts that work in `Application.onCreate`, on
    `ServiceLocator.appScope`.** Measured by logging which thread ran the lazy initialiser and for
    how long: **main paid 943 / 957 / 983 ms without it, and 202 / 293 / 348 ms with it** — about
    700 ms of class loading taken off the thread the splash and the first frame need.
* It does **not** reliably make the background thread win the race — `by lazy` is `SYNCHRONIZED` and
    main frequently still runs the initialiser. The gain is overlap, not ownership. Recording this
    because the obvious reading of the code is that it moves the work, and it does not.
* **Two hypotheses tested and killed, both mine.** `install(Realtime)` is genuinely unused (the only
    matches in the app are my own `elapsedRealtime` calls) and looked like the cause — removing it
    measured **no improvement** (3.4-3.9 s), so it stays. And warming `ServiceLocator.supabase`
    before `sessionManager` measured no better than warming `sessionManager` alone (~340 ms), so the
    simpler form is kept.
* **I also had the splash-hold mechanism wrong at first** and said so: I claimed the ~1 s main-thread
    block happened inside `setKeepOnScreenCondition`. It does not — first composition reads the
    lazy before the first pre-draw, so composition was paying it. Same thread, different caller.
* **The 2 s splash bound is being blown through on this emulator**, since resolution takes 3.3 s+.
    The constant is left alone deliberately: it is a ceiling on staring at a logo, not an estimate
    of the work, and raising it to cover a debug-build number would lengthen a real stall rather
    than fix one. Past the bound what the user needs is the in-app Retry button.
* **`MainActivity.SPLASH_MAX_HOLD_MS` carried the wrong explanation in code, so I rewrote it.** Its
    KDoc justified the 2 s with "the reported wait is 1-2 s (an auth-storage read plus one
    `profiles` SELECT)" — the same claim the measurement just refuted. A comment that argues for a
    constant from a false premise is worse than no comment, because the next person will adjust the
    constant to fit it. It now states the measured figure and says why the number stays anyway.
* **The largest remaining lever is R8, and it is untouched.** `app/build.gradle.kts` still carries
    `optimization { enable = false }` on release **from the initial commit** — the AGP project
    template default, not a reaction to a breakage (`git log -S` confirms). The shipped APK
    therefore loads the whole unshrunk Ktor + supabase-kt graph. Not turned on here: there is no
    release `signingConfig` and no `proguard-rules.pro`, so it cannot be built, installed or
    verified in this session, and enabling shrinking blind on a kotlinx-serialization app is how you
    ship a release that crashes on first decode.
* **Rejected again, now with a correction to the reason.** A cached profile/role would remove the
    query, and my earlier objection ("a stale role routes into the wrong graph") is weaker than I
    stated: `EditAccountViewModel` carries `role` over untouched and no screen in the app can change
    an existing account's role. The reasons that survive are that the wait is not the query, that a
    persisted identity is a second thing `signOut()` must clear on a shared phone, and that an
    optimistic route has to be unwound when the guess is wrong.
* **`ProfileScreen` reads `profile.address`, `profile.phone` and `profile.familyStatus` off the
    SessionManager profile as a fallback**, so narrowing `loadProfile`'s `select *` to the routing
    columns would blank those fields. Combined with latency not being the bottleneck, the narrowing
    I offered earlier is **withdrawn** — it would trade a visible regression for nothing.
* **Verified:** `:app:testDebugUnitTest` + `:app:assembleDebug --rerun-tasks`, **287 tests, 0
    failures, zero compiler warnings**; final APK installed on `emulator-5554` and confirmed to emit
    exactly one startup line and no leftover probes.
* **Adds no tests, and I will not pretend otherwise** — a warm-up is a side effect on process
    startup, unobservable from JUnit without Robolectric. What replaces a test here is the logcat
    line, which makes the thing it improves measurable on any device at any time.
* **Not verified:** all of the above signed *in*, where the `profiles` SELECT is added on top; and
    the numbers in a release build, which is what users actually run.

### [2026-08-30] - The Sweep That Was Cancelled, and Three Things Around Startup
* **"Tandai semua dibaca" never reached the database, and it was my bug.** I shipped it yesterday
    **unverified**, listed it under "not verified", and this is what that gap was hiding. Found by
    the user on a device, not by me.
* **My first diagnosis was wrong.** I assumed `filter { eq("is_read", false) }` on an UPDATE was not
    being applied. Checked against the supabase-kt sources: `filter` lives on
    `PostgrestRequestBuilder` — the base class `update` uses — mutates `params` by reference, and
    reaches the wire as `PATCH /rest/v1/notifications?is_read=eq.false`. All five update call sites
    in the repo are the identical shape. Nothing was wrong with it.
* **The real cause was coroutine cancellation.** `markAllRead` ran on `viewModelScope`. The button's
    whole purpose is "clear the badge and leave", so the next gesture is Back — which pops the
    destination, clears its `ViewModelStore`, and cancels the PATCH, often before it reached the
    socket. `runCatching` caught the `CancellationException`, so the failure branch wrote a banner
    onto a ViewModel already being destroyed: no error shown, no write, rows unread again on
    re-entry. That also explains why `markRead` worked — tapping a feedback card navigates
    *forward*, so the screen stays on the back stack. It had the identical bug, unexercised.
* **Fixed by splitting the write from its presentation by lifetime.** New `ServiceLocator.appScope`
    (matching the two process-lifetime scopes `SessionManager` and `PushRegistrar` already own)
    runs the write with `async`; `viewModelScope` only `await`s the outcome. Cancelling the awaiter
    does not cancel the awaited job. Screen alive: it reverts and banners as before. Screen gone:
    the write still lands and there is nobody to apologise to, which is correct.
* **New `Result.rethrowCancellation()`**, because `runCatching` swallowing `CancellationException`
    is what made this invisible — it turns "cancelled" into "failed" everywhere in the app.
* **Deliberately did not add `count` or `select` to the update.** `select()` would return every
    updated row — 300 JSON notifications for a busy parent, over the connection that file exists to
    conserve. `count` is cheap but there is nothing correct to do with a zero: the caller only runs
    when the *local* unread count is positive, so a server zero means the local list disagreed
    (another device swept, or a refresh landed mid-flight), not that anything failed — reverting on
    it would un-read rows that really are read.
* **The login screen was drawn on every launch by construction.** `startDestination` was hardcoded
    to `"login"` and `SignedIn` fell into the same `else` branch as `SignedOut`, so the `NavHost`
    composed the login screen first and a `LaunchedEffect` then navigated away with a 400 ms slide.
    There was no session for which the correct home was the start destination.
* **And a worse defect underneath it: backgrounding the app destroyed the whole back stack.** The
    `Loading` branch rendered *instead of* the `NavHost`, and supabase-kt's Android lifecycle
    observer pushes `Initializing` on **every** `onStop` — so backgrounding the app three screens
    deep tore the `NavHost` out of composition and returned to one. The old
    `LaunchedEffect(sessionState)` also re-fired `popUpTo(0)` on every `SignedIn` **emission**, not
    just transitions.
* **Two more doors led out of a valid session.** `RefreshFailure` was mapped to `SignedOut` — on the
    patchy data this app is written for, a refresh that cannot reach the network says nothing about
    whether the stored session is valid. And `NotAuthenticated` carries an `isSignOut` flag that was
    being discarded, so "initialisation finished, storage empty" was read as a real sign-out.
* **New `SessionTransition`, one rule: once this process has resolved a profile, only an explicit
    sign-out may undo it.** That single branch kills the reopen flash **without any caching** —
    `Profile` is a data class, so a re-resolved `SignedIn` compares equal and `MutableStateFlow`
    conflates it: no emission, no recomposition. Plus `onProfileLoadFailed`, so only the *first*
    profile failure becomes an error pane; a later reload failure leaves the working session alone.
* **New `SessionRouting`** and a latched `graphStart`, so the graph opens on the right root and later
    root changes navigate only when the root actually **changes**. The new `when` over
    `SessionManager.State` is **exhaustive with no `else`** — the bug being fixed was literally
    `SignedIn` falling into one, so a future fifth state should be a compile error.
* **Considered and rejected: caching the profile in DataStore.** After the above it solves nothing
    on reopen. It would only help a cold start, where a stale **role** routes into the wrong graph
    and every query then fails under RLS — far worse than a spinner. And a persisted identity is a
    second thing `signOut()` must clear on a shared phone.
* **Also deleted a `println` of the signed-in user's auth uid and a `printStackTrace`** from
    `SessionManager`. They shipped identity to logcat on a shared-phone app.
* **There was no splash screen at all** — `themes.xml` was one empty style. On Android 12+ the system
    improvised one from the launcher icon; on **every device below 12** there was a blank window and
    then a bare spinner. That is exactly "on some phones no logo, just buffering", and the phones
    were the pre-12 ones. Added `androidx.core:core-splashscreen`, which backports it to API 23 —
    and, more importantly, is the only way to **hold** the splash until the session resolves, which
    removes the spinner and the flash together. Bounded at 2 s: a held splash swallows touches, so
    one with no way out is worse than a spinner.
* **The splash icon is `ic_launcher_foreground`, not `@mipmap/ic_launcher`.** The adaptive icon
    carries its own tile; API 31+ masks that to a circle and the backport does not mask at all, so
    the same asset would be a circle on new phones and a hard-edged square on exactly the old ones
    this fix is for.
* **Text caps, under a stated rule rather than scattered numbers:** *cap a string where it sits
    inside something that offers a way to see more; do not cap it where it is the thing that gesture
    leads to.* So `tugas.judul` on the home card is 2 lines (the reported bug) while `TaskDetailScreen`
    caps nothing — a low-literacy parent is being asked to *carry out* that instruction, and
    ellipsising it withholds the task. `FeedbackThreadScreen`'s reply bubbles stay uncapped for the
    same reason: a truncated chat message with no way to expand is data loss, not an overflow fix.
    Names and phone numbers get 1 line, because a second line means bad data and an ellipsis says so.
    `presensi.catatan` gets 3 — the only 3, because a one-sentence visit note fits in three lines
    where two would clip most of them.
* **The feedback row you asked me to check was already correct** — 1 line plus ellipsis on both its
    headline and subtitle, since the day it was written.
* **Tests: 287, up from 263** (12 `SessionTransitionTest`, 8 `SessionRoutingTest`, 4 `ResultExtTest`).
    The twenty session tests are the highest-value ones here, because they encode the exact rules
    that were wrong. One of my own test assertions was wrong and the suite caught it: `assertSame`
    on a `Result`, which is a value class and is re-boxed on the way through.
* **Verified:** `:app:testDebugUnitTest` + `:app:assembleDebug` from `--rerun-tasks`, **287 tests,
    0 failures, zero compiler warnings**. The splash was **installed and photographed on
    `emulator-5554`** — teal field, mark centred and legible, no blank window and no bare spinner.
* **Not verified.** Everything behind sign-in, which is most of it: that the sweep now survives a
    Back press, that reopening the app keeps you on the screen you left, that a failed refresh no
    longer bounces you to login, and that a long programme title now wraps at two lines. Also not
    verified: the splash on a **pre-12** device — the emulator is API 37, and the backport is the
    half of that fix that matters most.
* **A behaviour change to watch for:** a genuinely dead token now surfaces as an error on the screen
    you are on, rather than bouncing you to the login screen. On this network that is the better
    trade, but it is the change most likely to read as "it used to log me out".
* **Found in passing, not fixed:** `ProfileViewModel.saveProfile` never touches `SessionManager`, so
    editing your own profile already did not refresh the "Halo, {nama}!" header. Pre-existing and
    out of scope for this change.

### [2026-08-30] - A Logo, and Notifications That Say Who Is Talking
* **The launcher icon was still the stock Android Studio green robot**, untouched since the first
    commit — twelve files, all dated `init`. Replaced with the app's own mark: two rounded forms
    propping each other up, which is the literal meaning of *sipatokkong* in Bugis/Makassar and also
    the shape of the volunteer → parent → volunteer loop the README diagrams. The supporting form is
    drawn **second** so it overlaps the join; z-order is what makes it read as "holding up" rather
    than "touching", and the legs are unequal because a symmetric tent reads as a roof.
* **The agreed colours turned out not to be buildable, and the deviation is deliberate.** The plan
    said teal supporting, orange supported. Measured, **BrandOrange is illegible on any light
    field** — 1.6:1 on `IconBoxBg`, 2.05:1 on `AppBackground`, 2.1:1 on white — and there is no
    field on which literal BrandTeal *and* literal BrandOrange both clear 4.5:1. So the tile is
    `BrandTealDeep` and the supporting form is `IconBoxBg`, a teal-family token already in the
    palette. No new colour was invented. A dark tile also holds its edge against a light wallpaper,
    where a near-white icon reads as an empty slot.
* **A separate `ic_launcher_monochrome.xml`**, rather than the template's alias to the foreground.
    The system reads that layer's *alpha* as a mask, so aliasing fed the stock drop-shadow
    gradient's partial alpha into it and produced a ghosted silhouette.
* **Deleted the ten `ic_launcher*.webp` files and their five folders.** `anydpi` is a density
    qualifier that outranks every specific bucket, and `minSdk 27` is past the API 26 adaptive icons
    need — so the XML wins on 100% of supported devices and the webps were unreachable. They were
    not a fallback and could not have shown the robot.
* **Left the notification bell alone.** The small icon is hard-masked to a flat silhouette, and this
    mark's two forms are told apart by colour and overlap — flattened to white at 24dp they fuse
    into a meaningless wedge, and it would cost the instant "this is a notification" signal a bell
    carries for exactly this audience.
* **Migration `0010`: notifications now name people.** `Kak Dewi memberi tugas untuk Andi`,
    `Sari Wulandari · Andi`. Every name added is **already readable by the recipient** through
    `can_view_profile` and `anak_select`, so this discloses nothing new — it only stops the app
    pretending it does not know. The joins are cheap: `on_tugas_insert` already selected from `anak`,
    and `on_balasan_insert` already read `profiles` for the author's role, so the volunteer's name
    there was free.
* **A parent's reply finally notifies the volunteers.** `on_balasan_insert` wrapped everything in
    `if author_role = 'relawan'`, so a volunteer started a conversation and never learned it was
    answered. It now fans out over `anak_relawan` and **deliberately leaves `status` alone** — a
    parent answering does not make the thread answered, and moving it would empty the volunteer's
    "Perlu Ditanggapi" list, which is the opposite of the point. Reuses `feedback_balasan`, so no
    enum change. Also added an `if not found` guard: a reply on a deleted thread previously reached
    the INSERT with a null `recipient_id`, and the not-null constraint rolled back the reply itself.
* **`tugas.instruksi` is markdown, and slicing it raw would have shipped the markers.**
    `CreateTaskScreen` gives volunteers a toolbar that writes literal `###`, `**` and `- `, so a
    parent's tray would have read `### Langkah 1 - Ajak anak **memegang** sendok`. New
    `notif_snippet()` strips line-leading block markers — the `n` regex flag is load-bearing, it is
    what makes `^` match at every line start — strips inline emphasis, and collapses newlines,
    because a notification body is one line and an instruction is not.
* **The sender name needed no new plumbing at all.** After `0010` the `title` *is* the sender's
    name, and the existing chain already carries it: trigger → `0009`'s `jsonb_build_object` → the
    Edge Function's generic `dataPayload` → FCM → `PushPayload` → `NotifDisplay.from` → `Person`. No
    new column, no new FCM field, no Edge Function redeploy. **The migration alone changes the words
    on already-installed APKs.**
* **`MessagingStyle` + a generated letter avatar**, for the two kinds that really are one person
    writing to another. The colour is deterministic per name rather than one fixed teal: a volunteer
    with four assigned children gets notes from four parents, and four identical discs must be read
    letter by letter while four coloured ones are told apart at a glance. `String.hashCode` is
    specified by the language, so the mapping is stable across devices and testable against exact
    values. **The colour means nothing** — not role, not priority — and the KDoc says so.
* **Three traps in the avatar, each with a test.** `firstOrNull()` on `"😀 Sari"` returns half a
    surrogate pair and draws a tofu box, so it walks code points; `"ß".uppercase()` is two
    characters, not one; and a name may contain no letter at all. Non-Latin works free — `"عائشة"`
    gives `"ع"`.
* **A wrong comment corrected.** `PushNotifier` claimed *"The title still shows on the lock screen;
    the detail waits for an unlock."* False: on a **secure** lock screen `VISIBILITY_PRIVATE`
    redacts the entire notification down to the app name. Which is why putting a parent's name in
    the title costs nothing — and why the real exposure is a lock screen with **no PIN**, where
    there is no redaction at all and nothing in the API changes that.
* **Three stale justifications rewritten, not merely stale comments.** `NotifDisplay`'s
    `FALLBACK_TITLE_FEEDBACK_BARU` was justified as "byte-identical to what the trigger writes,
    because that trigger writes a constant" — `0010` makes that false, and a wrong *justification*
    is worse than a wrong comment because the next reader will trust it. Same for `PushPayload`'s
    KDoc and one test comment. The four fallback constants themselves are unchanged: still sensible
    generic wording, and now pure defence.
* **Tests: 263, up from 247** (12 in `AvatarNamingTest`, 4 for `styleFor`). Fixtures standing in for
    live trigger rows moved to the new format; fixtures exercising the **blank-title fallback** kept
    the old strings, and their comments now say which is which — twice during this change a
    search-and-replace hit a fallback assertion, and the suite caught it both times.
* **Verified:** `:app:testDebugUnitTest` + `:app:assembleDebug` from `--rerun-tasks`, **263 tests,
    0 failures, zero compiler warnings**. The icon was **installed and photographed on
    `emulator-5554`** (uninstall-then-install, because the launcher caches icons by package): it
    renders, both forms are legible at launcher size, and it is visibly not the robot.
* **Not verified.** The **monochrome layer** — themed icons would not turn on through `adb`, so the
    separate mask file is written but unseen. Migration `0010` is **not applied**; nothing has run
    it, so no notification has yet been written with a name in it. And the whole push appearance —
    `MessagingStyle`, the avatar, `BigTextStyle`, the markdown stripping, and the new
    parent-replies-to-volunteer path — is untested on a device, because that needs sign-in and no
    credentials are available from this machine.

### [2026-08-30] - One Inbox, Cleared in One Tap, and Reachable Past Fifty
* **The question was whether to split the inbox into read/unread tabs. The answer is no**, and the
    reasons are worth keeping because they are the same reasons this list works the way it does.
    The steady state is "everything read", so the unread tab would be empty almost always. The
    50-row cap is applied **server-side, before any split**, so a client-side "read" tab would hold
    `50 − unread` rows, silently truncated. And read/unread is already drawn three ways per row —
    tinted background, teal dot, and whether the card is clickable at all — with the bell showing
    the count before the screen is even opened.
* **The decisive one: marking read currently never moves anything.** `markRead` is a positional
    `items.map`; the row flips colour in place. Under tabs the row being tapped would leave the tab
    being looked at. That is a behaviour regression, not a layout choice, and this repo already made
    the opposite call once — the parent's home keeps a finished task visible for the rest of the day
    precisely so cards do not vanish under the reader.
* **Two real problems were underneath the question, and only one is about volume.** There was no way
    to clear a pile of unread — a bell reading 12 took twelve taps, and mark-all-read did not exist
    anywhere in the app. And volunteers genuinely overflow the window: `on_feedback_insert` fans one
    row out per assigned volunteer per parent note, so a volunteer with ~10 assigned children whose
    parents each file ~2 notes a week reaches ~80 rows a month and passes 50 in under three weeks.
    Parents have neither problem — one program ajar is one notification, even for a year-long daily
    programme.
* **New `NotifInbox`**, beside `NotifDisplay` and free of Compose and Supabase types: `unreadIds`,
    `allRead`, `revertToUnread`, `appendPage`, `hasMore`. `NotifDisplay` answers what one row shows;
    this answers what happens to the list, which is a different question with different failure
    modes. Everything worth an `if` lives here, because with no coroutines-test in this suite the
    ViewModel cannot be tested at all.
* **A failed sweep reverts by captured ids, not by restoring a snapshot.** A `refresh()` or a single
    `markRead` can land while the bulk write is in flight; reinstating an earlier list would
    resurrect rows that are gone and undo a mark that already succeeded.
* **`appendPage` dedupes by id, and that is load-bearing.** `LazyColumn` is keyed by id and **throws**
    on a duplicate, and offset paging makes a duplicate reachable: a row inserted above the window
    shifts the server's page boundary so the boundary row comes back twice. Without the dedupe the
    screen crashes rather than showing the row twice. The copy already held wins, so an optimistic
    read flag survives a stale page.
* **Added an `id` tiebreaker to the query.** `created_at` alone is not a total order and Postgres
    breaks ties arbitrarily, so with pages a tied row can be returned twice or skipped. Rare today —
    each trigger writes one row per recipient per transaction — but a `tugas_reminder` cron would
    stamp a whole batch with the same `now()`.
* **`markAllRead` filters on `is_read = false` rather than not filtering at all.** It is deliberately
    the call `markRead`'s KDoc warns about, so the KDoc says so: the blast radius *is* the feature,
    since "semua" must include rows older than the loaded page. The filter earns itself three times
    over — it writes only rows that change (300 unread-free rows cost 0 tuples, not 300, on a table
    with no DELETE policy), it is an exact leading-column match for `idx_notif_recipient`, and a
    filterless PATCH is textually indistinguishable from the accident.
* **A confirmation dialog, which is unusual for mark-all-read and deliberate here.** There is no undo
    and nowhere to put one — this app has zero Snackbars and no mark-unread method — rows never move
    or leave the list so the tint and dot are the *only* record of what is new, and the action
    reaches rows that are not on screen, which nothing but a sentence can explain. No count is shown
    anywhere: the client knows only the loaded number while the action does more, so a figure would
    be a precise lie.
* **Found while wiring the app bar: it had no `actions` block, and therefore no
    `actionIconContentColor`.** Without one the new button would inherit from
    `MaterialTheme.colorScheme` and be painted from the user's wallpaper on Android 12+ — the same
    `dynamicColor` bug the `containerColor` comment three lines above already documents, unreachable
    until now only because there was nothing in that slot.
* **Both new failures use `ActionErrorBanner`, never `ErrorRetry`** — the list stays on screen and
    correct in both cases. A failed "Muat lebih banyak" leaves `hasMore` true on purpose, because
    the button itself is the retry.
* **Tests: 247, up from 231** (16 new in `NotificationInboxTest`). **Verified by reintroducing the
    defects:** removing the dedupe broke 2, reverting every row instead of the captured ids broke 2,
    and an off-by-one at the page boundary (`>` instead of `>=`) broke 1.
* **Verified:** `:app:testDebugUnitTest` and `:app:assembleDebug` from `--rerun-tasks`, **247 tests,
    0 failures, zero compiler warnings**; installed to `emulator-5554`.
* **Not verified - none of it has been seen running.** Sign-in is required and no credentials are
    available from this machine. Unproven: that the button appears and is teal rather than a
    wallpaper colour, that Batal changes nothing, that a sweep leaves every row in place, that the
    badge clears on return, that the mark survives a real round trip, that the revert-plus-banner
    path works offline, and the whole of paging — for which the cheap test is to set
    `PAGE_SIZE = 5L` temporarily rather than seeding a hundred rows.

### [2026-08-30] - The Date Fields Were a Half-Finished Copy
* **Every date field on the Buat Program Ajar form was dead.** Tapping "Deadline (opsional)",
    "Tanggal Mulai" or "Tanggal Selesai" did nothing at all.
* **The cause: `DeadlineField` was `AdminFormComponents.DateField` with the second half missing.**
    It set `showPicker = true` and then **never read it** — there was no `DatePickerDialog` anywhere
    in the composable. It also took no callback, so even a chosen date had no way back to the
    ViewModel. `CreateTaskViewModel` has had `onDeadlineChange`, `onStartsOnChange` and
    `onEndsOnChange` the whole time, wired to nothing. The unused
    `import androidx.compose.material3.DatePickerDialog` at the top of the file was the fingerprint.
* **The grey was a symptom of the same thing.** The field used `enabled = false`, which paints the
    whole control in the disabled palette — grey text, grey border, grey label. On something the
    user is meant to tap that reads as "switched off", which is precisely how it looked and roughly
    what it was.
* **Editable dates are now `readOnly`, not disabled**, so they keep normal colours and still refuse
    typing; the tap is caught through the field's `interactionSource` rather than a `clickable`
    modifier, which is the supported route on a text field. `onPick` is optional, and its absence is
    what makes a field read-only — so `CreatePresensiScreen` keeps its deliberately fixed visit date
    (commit b2981bc, "make the date cannot change") and keeps the grey, where grey is telling the
    truth.
* **An optional date can be unset again.** With a date chosen, the trailing calendar icon becomes a
    clear button. Without it, one stray tap on a field labelled "opsional" made it permanent for the
    life of the form.
* **Also removed two duplicate imports** in the same file (`Close`, `CircularProgressIndicator`) —
    leftovers from merge `a4df279`, harmless but noise.
* **Verified:** `:app:testDebugUnitTest` and `:app:assembleDebug` green, 231 tests, zero warnings;
    installed to `emulator-5554`.
* **Not verified:** nobody has opened the form and tapped the field. The picker cannot be reached
    without signing in, and no credentials are available from this machine. Unproven: that the
    dialog opens, that a chosen date appears in the field, that the clear button works, that the
    presensi date is still locked, and that the field no longer looks disabled.

### [2026-08-30] - What Production Actually Had, and Push Going Live
* **Three migrations this document called pending had already been applied.** `0005_feedback_mood`,
    `0006_tugas_template` and `0007_presensi_unik_harian` were all live, put there through the
    dashboard. Found the hard way: the combined script aborted with `relation "tugas_template"
    already exists`, which is also why it was wrapped in a transaction -- nothing was left half
    applied.
* **The duplicate-presensi bug was already fixed, and this document said otherwise.** The entry of
    2026-08-29 stated the unique index "has not been applied, so until it is, a double tap on Simpan
    still writes two rows". `presensi_unik_harian` was in fact already in place. That claim was
    repeated confidently several times without ever being checked; the only source for it was this
    file, and this file was wrong.
* **The root cause was not the prose, it was the absence of anything to check it against.** With no
    `supabase_migrations.schema_migrations`, the sole record of what production had run was written
    by hand here -- and it was wrong about all three. Rather than guess again, the state was measured
    directly (`to_regclass`, `pg_extension`, `pg_trigger`, `information_schema.columns`) and only the
    genuinely missing objects were applied.
* **Applied today:** `0008_push_tokens` and `0009_push_on_notification` -- `pg_net`, the
    `push_tokens` table with its two `security definer` write functions and RLS, and the
    `trg_notification_push` trigger. Plus `supabase_migrations.schema_migrations`, backfilled with
    `0001`-`0009`. **That table is the durable fix**: from here the live schema can be queried
    instead of remembered, and `supabase db push` is safe for the next migration.
* **The Vault placeholder was caught before it could fail silently.** `01_vault_secrets.sql` was run
    before its `<SERVICE_ROLE_KEY>` placeholder had been replaced, so `push_hook_auth` held the
    literal string. Because `pg_net` is fire-and-forget, the trigger would have sent
    `Bearer <SERVICE_ROLE_KEY>`, the function would have returned 401, and **nothing anywhere would
    have reported it** -- pushes would simply never arrive. Detected by checking the secret's length
    and first three characters rather than its value, then corrected and confirmed to decode to
    `role = service_role`.
* **Verified on emulator-5554** (Google Play image), after `:app:installDebug`: Firebase initialises,
    the app starts with no crash, the `sipatokkong_notifikasi` channel is registered at
    `mImportance=4` (HIGH), and logcat shows `SipatokkongPush: new FCM token` -- so
    `SipatokkongMessagingService` is wired and receiving from FCM. The token was correctly **not**
    registered, because nobody is signed in yet; that is `PushRegistrar`'s designed behaviour.
* **Not verified - no push has been sent or received yet.** Everything downstream of sign-in is
    untested: token registration into `push_tokens`, the trigger firing, the Edge Function minting
    its OAuth token and reaching FCM, the tray notification, the tap route, the shared-phone leak
    test, offline sign-out, pruning, the denied-permission path, and Doze. Testing needs someone to
    sign in on the emulator; no credentials are available from this machine.

### [2026-08-30] - Push Notifications: Everything Except the Database Half
* **The app can now be told something happened while it is closed.** The chain is
    `notifications` INSERT -> `pg_net` trigger -> Edge Function `push-notify` -> FCM HTTP v1 ->
    `SipatokkongMessagingService` -> tray -> tap -> `feedback_thread/{id}`.
* **Push is a delivery channel for rows that already exist, not a second notification system.**
    `public.notifications` stays authoritative: every push is caused by a row insert, carries that
    row's own `entity_table`/`entity_id`, and routes through the existing `NotifDisplay.targetOf`.
    `PushPayload` delegates its wording to `NotifDisplay.from` rather than reimplementing it, so the
    tray and the inbox cannot describe the same row differently - there is a test asserting the
    if-and-only-if.
* **`push_tokens` is keyed on `token` alone, not `(user_id, token)`.** An FCM token identifies an app
    *installation*, not a person. With `token` as the key the next sign-in **steals** the row, so it
    stops mattering whether the previous user's sign-out ran at all. Keyed on the pair, a failed
    sign-out leaves both rows live and the previous user's notifications - another family's child's
    therapy notes - land on the phone the new user is holding. On a foundation's shared phone that is
    a leak, not an annoyance.
* **Writes go through two `security definer` functions because the steal cannot be an RLS policy.**
    PostgREST's merge-duplicates needs the UPDATE policy's `using (...)` to admit the row that
    already exists, and `user_id = auth.uid()` is false for exactly the row being taken over; the
    only policy that would work is `using (true)`, which would let anyone reassign any token.
* **Data-only payloads, never a `notification` block.** With one, the SDK builds the notification
    itself when the app is backgrounded and `onMessageReceived` is never called - behaviour splits
    across the SDK's path and ours, which is the largest source of "worked in development" in FCM.
    One path in both states is also what makes parsing and routing pure and testable.
    `android.priority = HIGH` on every send is therefore mandatory, not tuning: a normal-priority
    data message is held by Doze until the next maintenance window.
* **The prune footgun is defused twice.** FCM returns `INVALID_ARGUMENT` for a malformed *message*
    as well as a dead token, so one coding bug on our side would return it for **every** token and a
    naive prune would empty the whole registry in a single request - silently disabling push for
    everyone with nothing anywhere that looks like an error. Only a field violation naming
    `message.token` prunes, and no single invocation may remove more than half the tokens.
* **The Edge Function has two independent auth gates.** The anon key ships inside the APK and every
    parent holds a valid user JWT, so "requires a JWT" alone would make this an open push relay any
    user could aim at any other user with arbitrary text. It requires `role == 'service_role'` **and**
    a matching `x-push-secret` header; the second survives someone later deploying `--no-verify-jwt`.
* **Deletion is an action, never a reaction.** `SessionManager` maps `RefreshFailure` to `SignedOut`
    too, so a registrar that unregistered on every `SignedOut` would throw the registration away
    whenever the network wobbled. `PushRegistrar` only ever registers on `SignedIn`; the delete lives
    in `signOut()` and must run **before** `auth.signOut()`, because `unregister_push_token` gates on
    `auth.uid()` and would otherwise match nothing and fail silently.
* **`MainActivity` gained `launchMode="singleTop"`**, which is load-bearing rather than tidy: under
    the default mode a notification tap while the app is open builds a second Activity, rebuilding
    the whole Compose tree and discarding the nav back stack.
* **The push route effect is declared after the root session effect on purpose.** That one navigates
    with `popUpTo(0) { inclusive = true }` on every session change and would wipe a destination
    applied first. Running second lands the push route on top of home, which is also the back
    behaviour we want.
* **The duplicate `0005` is resolved.** `0005_presensi_unik_harian.sql` is now
    `0007_presensi_unik_harian.sql`; the new work is `0008_push_tokens.sql` and
    `0009_push_on_notification.sql`. Also added `supabase/.temp/` to `.gitignore`, which covered
    neither that directory nor any `.env`.
* **Tests: 231, up from 208** (23 new in `PushPayloadTest`). The Android-bound half -
    `FirebaseMessagingService`, `NotificationManagerCompat`, `PendingIntent`, `SDK_INT`,
    `onNewIntent` - is not coverable by this suite and the class KDoc says so, the same honest gap
    `MediaDownloadRulesTest` names for `MediaStore`. The Edge Function has no test harness at all.
* **Verified:** `:app:testDebugUnitTest` and `:app:assembleDebug` green, **231 tests, 0 failures,
    zero compiler warnings**. The `google-services` plugin **does** work with AGP 9.0.1, so the
    manual `FirebaseApp.initializeApp` escape hatch was not needed. The Edge Function is deployed and
    `ACTIVE` (`verify_jwt: true`), and an unauthenticated POST to it returns **401**, so the deploy
    and the first auth gate are confirmed working end to end.
* **Not verified - the database half is not applied, so no push has ever been sent.** Migrations
    `0005`, `0006`, `0007`, `0008` and `0009` are all still unapplied; the Vault secrets do not exist
    yet; and `supabase db push` is deliberately **not** run, because `supabase_migrations
    .schema_migrations` may be empty (0001-0004 look to have been applied through the dashboard) and
    a push would then try to re-run `0001` and fail partway through `create type public.user_role`.
    That reconciliation query is the next step and needs the database password.
    Also unproven: everything on a device - token registration, the tray notification, the tap route,
    the shared-phone leak test, offline sign-out, pruning, the denied-permission path, and Doze.
* **Push is best-effort; the inbox remains the source of truth.** On the Xiaomi/Oppo/Vivo hardware
    these families own, aggressive app-killing is the default and MIUI's Autostart can block FCM
    outright, and an app in the stopped state receives nothing whatever the payload.
    `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was deliberately **not** added - Play-policy restricted,
    no eligible use case here, and it would not fix the OEM problem anyway.

### [2026-08-29] - The Volunteer Inbox, and One Screen for Both Roles
* **Rows were being written to volunteers and shown to nobody.** `on_feedback_insert` fans a
    `feedback_baru` row out over `anak_relawan` on every parent note - one per assigned volunteer -
    and the volunteer's top bar had no bell, no icon of any kind, and no inbox. Same shape as the
    parent gap closed in the previous entry: the database had been doing its half all along.
* **No new data layer, no migration.** `NotificationRepository` deliberately carries no
    `recipient_id` filter because `notif_select` is that filter, so all three of its functions
    already returned the right rows for a volunteer. Not one line of it changed.
* **The parent-named types became role-neutral and the screen is now shared.** `ParentNotifDisplay`
    -> `NotifDisplay`, `ParentNotifType` -> `NotifType`, `ParentNotification` -> `NotificationItem`,
    and the screen/ViewModel/UiState likewise; the test file follows to `NotificationDisplayTest`.
    Role differences are **one parameter** (`emptyText`), following `FeedbackListScreen`'s pattern of
    parent-biased defaults and per-role behaviour at the call site. Deliberately **no
    `role: UserRole`** - `targetOf` already warns that display must derive from the row, and a role
    parameter reinvites exactly that bug.
* **New `FEEDBACK_BARU` kind**, with its own icon and a green tile. Without it a volunteer's inbox
    would be 100% generic bells, since `feedback_baru` was the one value mapped to `LAINNYA` - and a
    test asserted that on purpose, back when only a parent had an inbox. That assertion is rewritten,
    not deleted. `iconFor` and `fallbackTitleFor` are exhaustive `when`s with no `else`, so adding
    the constant was a compile error until both branches existed.
* **The identical-row problem, found before writing any of it.** `on_feedback_insert` freezes
    `left(isi, 120)` into `body`, and `CreateFeedbackViewModel` backfills an empty note with
    `CATATAN_RUTIN` - and mood-plus-chips with no typing is the quickest way to use that form, so it
    is the common one. Rendered literally, a volunteer's inbox repeats one identical sentence down
    its entire length: the exact defect `FeedbackSummary.headlineOf` fixed on the feedback list two
    entries ago, reintroduced on a new screen. `NotificationItem.body` is now `String?` and
    `bodyLineOf` drops the filler, so the card shows title and date alone. Equality, not
    `startsWith` - a parent who types that sentence and then adds to it has said something.
* **`FALLBACK_BODY` deleted as unreachable.** All three triggers source `body` from a `not null`
    column (`tugas.judul`, `feedback.isi`), so no row can arrive without one. It was also the source
    of a small pre-existing lie: a card with an empty body invited "Ketuk untuk melihat detail."
    while its ripple was deliberately disabled.
* **The bell is a shared component now.** Both top bars would have held character-identical blocks,
    and the line most likely to drift between two copies is the one nobody looks at - the
    `contentDescription`. `ui/components/NotificationBell.kt` holds it once, which also removed
    `ParentHomeScreen`'s cross-package import of `NotifDisplay` rather than merely renaming it.
* **Tab 0 only, on both homes.** Not on the volunteer's tab 2: that tab *is* the feedback list, and
    a badge counting unread `feedback_baru` rows hovering above the same notes rendered below would
    show two numbers that legitimately disagree - unread is not unanswered.
* **`refreshUnread()` is its own launch with its own `runCatching`**, called first from `refresh()`
    and never folded into the existing `coroutineScope { Triple(...) }`. That block is all-or-nothing
    - `coroutineScope` propagates a child's failure - so a notifications outage as a fourth `async`
    would replace the volunteer's entire Beranda with an error message, over a badge. It sets no
    error on failure at all; the count simply stays where it was.
* **Overlap accepted, not overlooked.** Volunteers already have a triaged Feedback tab showing these
    same threads, with better titles than a notification row can produce. The inbox answers a
    different question - the tab says what is *unanswered*, the inbox says what is *new since I last
    looked* - and only the inbox has per-row read state. Recorded here because the next reader will
    otherwise wonder why both exist.
* **Tests: 208, up from 200** (7 new in `NotificationDisplayTest`, 1 in `VolunteerHomeStateTest`,
    3 existing ones rewritten). **Verified by reintroducing the defects:** switching `bodyLineOf` to
    `startsWith` broke 1; comparing before trimming instead of after broke 2; mapping `feedback_baru`
    back to `LAINNYA` broke 3.
* **Verified:** `:app:testDebugUnitTest` and `:app:assembleDebug`, both from `--rerun-tasks`,
    **208 tests, 0 failures, zero compiler warnings**. `Icons.Filled.Feedback` confirmed present in
    the pinned icon set at first compile.
* **Not verified - nothing was run on a device or emulator, and no preview was rendered.** The whole
    manual pass is outstanding, and it needs a volunteer with at least one assigned child plus that
    child's parent to create the data, since the client cannot insert a notification. Unproven: that
    the bell shows and then clears, that the green card renders with no gap where the dropped body
    would be, that the filler-note case actually occurs as predicted end to end, that the deep link
    lands on the right thread, that two volunteers on one child get independent counts, that the
    badge survives an offline Beranda while the summary card fails, and that nothing regressed on the
    parent's side of the now-shared screen.

### [2026-08-29] - The Notification Screen Stopped Making Things Up
* **The rows existed the whole time; the app just never asked for them.** Three `security definer`
    triggers have been writing to `public.notifications` since `0001`: `on_tugas_insert` to the
    child's parent, `on_feedback_insert` to every assigned volunteer, `on_balasan_insert` back to
    the parent. `ParentNotificationScreen` rendered a hardcoded `sampleNotifications` list of four
    invented entries and never opened a connection. A parent's real reply notification was written,
    stored, and shown to nobody.
* **New: `Notification` model, `NotificationRepository`, `ParentNotifDisplay`, `ParentNotificationViewModel`.**
    The screen keeps its layout, empty state and unread styling; only its data source changed.
* **`Notification.type` is a `String`, not a Kotlin enum, and that is load-bearing.**
    `kotlinx.serialization` throws on an unknown enum constant, and it throws inside `decodeList` -
    one unrecognised row would take down the whole inbox rather than one card. `notif_type` already
    declares three values nothing writes; `ParentNotifDisplay.kindOf` maps anything unrecognised to
    a generic `LAINNYA`. Four tests cover exactly that.
* **`TUGAS_REMINDER` is gone from the UI.** Nothing writes `tugas_reminder` - no trigger, no cron
    job - so the enum described a notification that cannot exist, and two of the four fake entries
    were that kind. The Postgres enum is untouched; only the UI stopped claiming it.
* **The list query has no `recipient_id` filter, on purpose.** `notif_select` is exactly
    `recipient_id = auth.uid()` and, unlike `presensi_select`, carries no `is_admin()` override, so
    a client filter could only narrow a set that is already one user's rows - and a stale id in
    `SessionManager` would silently return an empty inbox. Same reasoning
    `PresensiRepository.listForCurrentVolunteer()` already documents.
* **`notif_update` gates rows, not columns.** A client may legally rewrite `title`, `body` and
    `type` on its own rows, so `markRead` sends a one-field `NotificationReadUpdate` rather than the
    decoded row echoed back, and carries a mandatory `eq("id", ...)` - an unfiltered UPDATE also
    passes RLS and would mark the entire inbox read in one request.
* **Tapping a feedback notification opens its thread; a task notification does not navigate.**
    `on_balasan_insert` stores the thread id (`fb.id`), not the reply id, so the deep link is exact.
    A task row has no honest destination: the route is `tugas_detail/{tugasId}/{occurrenceDate}` and
    the notification carries no date - a recurring program has dozens of occurrences and none of
    them is the right answer. The card gets no ripple when it will not act.
* **Mark-as-read is optimistic and reverts on failure**, with an `ActionErrorBanner` over a list
    that stays put - not `ErrorRetry`, which would wipe the inbox over one failed PATCH. Marked on
    tap, never all-on-open: the unread dot is the only way a parent finds the reply they have not
    read yet.
* **The bell now carries a count**, from `unreadCountForCurrentUser()` - a `head = true` +
    `Count.EXACT` query that returns no response body at all. It rides the home screen's existing
    `LaunchedEffect(Unit)` re-entry refresh, so returning from the inbox updates it with no new
    wiring. Loaded in its own `runCatching` so a failing count cannot wipe the task list.
* **Fixed a latent wallpaper bug while here.** The screen used
    `MaterialTheme.colorScheme.background`, and `dynamicColor` is still `true`, so on Android 12+ it
    rendered in the user's wallpaper palette instead of `AppBackground` - the identical bug the
    feedback screens hit in the previous entry. It never showed up in Android Studio because the
    `@Preview` passes `dynamicColor = false`. That preview now previews the **card**, not the
    screen, since previewing the screen would build the real ViewModel.
* **Tests: 200, up from 182** (18 new in `ParentNotificationTest`). **Verified by reintroducing the
    defects:** making `kindOf` throw on an unknown value broke 3; keying `targetOf` on `type`
    instead of `entity_table` broke 1; dropping the blank-`entityId` check broke 1. The plan
    predicted 2 for the middle one - only 1 fired, because the other candidate row has a matching
    type and table and so maps identically under the defect. Reported as measured, not as planned.
* **Verified:** `:app:testDebugUnitTest` and `:app:assembleDebug`, both clean from `--rerun-tasks`,
    **200 tests, 0 failures, zero compiler warnings**, APK produced.
* **Not verified - nothing was run on a device or emulator.** No credentials are available from this
    machine, and creating the data needs a *volunteer* account (the client cannot insert a
    notification; only triggers can) before a *parent* account can look at it. Unproven: that real
    rows render, that the badge shows and then clears on return, that the deep link lands on the
    right thread, that the background is grey rather than wallpaper-tinted on Android 12+, that a
    failed mark-as-read reverts with the list still on screen, and that the empty state appears for
    a parent with no notifications.

### [2026-08-29] - Merge a4df279 Left master Unable to Compile
* **Found while trying to build the notification work: `master` did not compile, and had not since
    PR #18 was merged over PR #17.** Seven errors, none of them in notification code. This was not
    introduced by the change above - verified by stashing that work entirely and rebuilding, which
    produced the identical seven errors.
* **All four were merge artifacts, not design problems.** `TaskRepository` lost
    `import java.time.LocalDate` and `java.util.UUID`, which `mediaPath` uses. `CreateTaskScreen`
    ended up importing `ExperimentalLayoutApi` and `FlowRow` twice. `MediaPickerBox` stayed
    `private` while `CreatePresensiScreen` had started calling it, and the two sides of its
    signature had diverged - the caller still passed a single `mediaUri` where the definition now
    takes `hasMedia` + `previewUrl`.
* **One of them was a live feature regression, not just a compile error.** `onCatatKunjungan` had
    been dropped from both `VolunteerHomeScreen` and `VolunteerBeranda`, and from the nav call site,
    so the volunteer's "Catat Kunjungan" button no longer reached the `create_presensi` route -
    which was still registered and reachable by nothing. Restored the parameter through both
    signatures and rewired it in `SipatokkongMain`.
* **Fixed at the user's explicit direction**, after reporting the breakage and its scope, rather
    than folded in silently: these are four files no part of the notification feature touches.
* **Verified:** the tree compiles and the full suite passes. **Not verified:** that the restored
    "Catat Kunjungan" button actually opens the presensi form on a device - it is wired to the same
    route the screen was always registered under, but nobody has pressed it.

### [2026-08-29] - One Presensi Per Child Per Day, and a Migration Number Used Twice
* **A double tap on Simpan wrote two visits.** `presensi` shipped in `0001` with no uniqueness
    at all, so the second insert succeeded silently. That is worse than a cosmetic duplicate:
    every number on the admin's `RekapAbsensi` is a row count, and `presensi` has SELECT / INSERT /
    UPDATE policies but **deliberately no DELETE policy**, so nothing in the app can remove the
    extra row afterwards. The inflated recap is permanent.
* **Fixed in the database, because that is the only place it can be fixed.** New
    `0005_presensi_unik_harian.sql` adds a unique index on `(relawan_id, anak_id, tanggal)`,
    mirroring the constraint `tugas_penyelesaian` already carries on `(tugas_id, occurrence_date)`
    for the same reason. A client-side check would still lose the race between two taps, and could
    not protect the rows already in the table.
* **Nothing is lost by refusing the second insert.** `presensi_update` lets a volunteer correct
    their own row, so a mis-logged visit is still editable - the constraint only blocks a duplicate,
    not a correction.
* **The raw error was unreadable, so it is translated.** A 23505 arrives from Postgrest as a
    message naming the index; `CreatePresensiViewModel.messageFor` maps it to "Kunjungan untuk anak
    ini pada tanggal tersebut sudah dicatat." and otherwise falls through to the original text.
* **Two files are now numbered `0005`:** `0005_feedback_mood.sql` and
    `0005_presensi_unik_harian.sql`. Both are committed, both are unapplied, and they touch
    different tables so the order between them does not matter - but a migration runner that keys on
    the numeric prefix will not be happy about it, and one of the two should be renumbered to `0007`
    before anyone runs `supabase db push`. Recorded in Known Limitations rather than renamed here,
    because renaming a committed migration is a decision about the live project, not a tidy-up.
* **Verified:** the migration SQL, the repository doc comment, and the `messageFor` mapping were
    read end to end this session and agree with each other - the string the index comment promises
    is the string the ViewModel actually returns.
* **Not verified - none of it was run.** The migration has **not been applied** to the live project,
    so the duplicate is still possible in production today. No build and no test run happened this
    session; the suite stands at 182 tests from earlier work, and **none of them cover
    `messageFor`** - the duplicate-message path is currently unproven by anything except reading it.
    Nothing was tried on a device.

### [2026-08-29] - The Parent's Feedback View, and a Contradiction I Introduced
* **The contradiction was mine, from the previous change.** A row could read "Sudah Beres" in its
    tab and orange "Menunggu" in its chip at the same time, because the two came from different
    notions: the chip from `feedback.status`, the tabs from `needsAttention`. A positive unanswered
    note satisfies both. I had offered "Tidak Mendesak" the day before precisely to avoid this, then
    shipped "Sudah Beres" without checking it against the chip.
* **Fixed by removing the second source, not by rewording.** `FeedbackTriage.stateOf` now returns
    `DIJAWAB` / `PERLU_DIBALAS` / `KABAR_BAIK`, and the chip and the tabs both derive from it. See
    the Product Rules table above. A three-state chip also helps the **parent**: their happy report
    no longer wears a nagging orange "Menunggu" - the same problem the quick-replies change existed
    to solve.
* **Rows could all be titled identically.** `FeedbackRow` used `fb.isi`, but "Catatan Tambahan" is
    optional and gets backfilled with a constant, so a parent who reports with mood + chips - the
    quickest way to use the form, hence the common one - got a list where every row read the same.
    `FeedbackSummary.headlineOf` now picks the most specific thing the note actually says, and the
    literal was lifted to a named `CATATAN_RUTIN` so the writing and display sides cannot drift.
* **Dates were raw ISO.** The list printed `createdAt.take(10)` - "2026-08-29". New
    `core/util/DateDisplay.kt` gives "Hari Ini" / "Kemarin" / "29 Agu 2026", returning **null** for
    an unusable value so the line disappears instead of showing something meaningless. It replaces
    `ProgramListScreen`'s private `ShortDate`, so this removes a duplicate rather than adding a
    sixth date formatter to the five that already existed.
* **The feedback area was wallpaper-coloured.** All three screens used
    `MaterialTheme.colorScheme.background`, and `dynamicColor` is still `true`, so on Android 12+
    they rendered in the user's wallpaper palette instead of `AppBackground` - which is exactly why
    they looked unlike the rest of the app. Now they import the brand colours directly, the same
    workaround `ParentHomeScreen` and `VolunteerHomeScreen` already use. `Theme.kt` is untouched.
* **The thread screen was hiding data it had already fetched.** `FeedbackReply.createdAt` was read
    from the database and never rendered, so a parent could not tell an hour-old reply from a
    three-week-old one; the note's own date and status were likewise unused despite being in hand.
    All three now show. `ReplyBubble` used `weight(1f)`, which stretched a one-word reply to full
    width - a block, not a bubble; now `weight(1f, fill = false)`.
* **The form was the only parent screen with no cards at all**, floating three bare sections on the
    background. Now carded like everything else, with the off-palette `Color(0xFFF5F5F5)` and
    `Color.Black` replaced by `IconBoxBg` and `MutedText`. Also fixes the typo "Catatan **terhasil**
    disimpan!", and adds a line explaining that filling in the note is what gives it a title later.
* **A claim of mine turned out to be false, and the test proved it.** I wrote in the code that
    `stateOf`'s branch order was load-bearing. Swapping the branches left every test green, because
    `needsAttention` already returns false for answered notes - the branches are independent. The
    comment is corrected rather than left as confident-sounding fiction.
* **Tests:** 140 total, up from 128 (12 new in `FeedbackDisplayTest`). **Verified by reintroducing
    the original defects:** making the chip read `status` alone broke 2 tests including the
    if-and-only-if invariant, and dropping the `CATATAN_RUTIN` check broke 3.
* **Verified:** clean `assembleDebug` + `testDebugUnitTest` with **zero compiler warnings**.
* **Not yet verified - nothing seen on screen.** The emulator session ended during an earlier
    reinstall and no credentials are available from this machine. Ideally checked with a **parent**
    account, since that is what was asked for. Unproven: that rows are no longer identical, that
    dates read "Hari Ini", that the background is grey rather than lavender, that no "Menunggu" chip
    survives inside the "Sudah Beres" tab, and that short replies now look like bubbles.
* **`FeedbackListScreen` is shared**, so the volunteer's feedback tab gets the new colours and the
    three-state chip too.

### [2026-08-29] - "Kabar Baik" Was Not Always Good News
* **The label was inaccurate, not just plain.** `FeedbackTriage.needsAttention` returns false for
    **anything answered**, so a note reading "anak demam, terapi dilewat" moved into the "Kabar
    Baik" tab the moment the volunteer replied to it. The tab holds "nothing to do right now",
    which is not the same thing as good news.
* **Renamed to "Sudah Beres"**, and `FeedbackTriage.goodNews` renamed to `settled` - the function
    name carried the same wrong idea, and leaving it would have re-seeded the mistake for the next
    reader.
* **Both empty states rewritten.** The second tab's subtitle was the generic `emptyText` ("Belum
    ada catatan dari orang tua."), which is impossible at that point: the `ui.items.isEmpty()`
    branch above already catches "no notes at all", so an empty second tab means every note is
    sitting in the first one. It now says so: "Semua catatan masih menunggu tanggapan Anda."
* **Deliberately kept at two tabs.** Splitting answered-but-concerning notes into a third tab would
    make "Kabar Baik" literally true, but three labels do not fit a narrow phone and this app's
    users are low-literacy. Two honest labels beat three precise ones here.
* **Verified:** clean `assembleDebug` + `testDebugUnitTest` green with **zero compiler warnings**;
    128 tests still pass, `FeedbackTriageTest` updated to the new name.
* **Not yet verified:** nothing seen on screen. The emulator session ended during an earlier
    reinstall and no credentials are available from this machine. The triage tabs *were* seen
    working before this rename ("Perlu Ditanggapi (0)" / "Kabar Baik (2)"), so only the new strings
    and the new empty-state subtitle are unproven.

### [2026-08-29] - Ticking "Simpan Sebagai Template" Is Now One Tap
* **The complaint:** "saya selalu lupa centang simpan sebagai template". It was not carelessness -
    the control was built in a way that made it easy to miss and tiring to use even when
    remembered. Four measurable things:
    1. It was **item 10 of 14** on a form that scrolls about three screens, and the **only section
       without a `SectionLabel`** - a bare `Row` with no card or divider, sitting directly under
       the far more prominent submit button.
    2. **Ticking it was not enough; the name had to be typed.** `templateName` started `""` with no
       default and no suggestion, although `judul` was already filled in just above it.
    3. **A blank name rejected the entire save.** The error even offered *"atau hapus centang"* as
       the way out - the feature framed as something to abandon so the real work could proceed.
       (It also quoted `"Simpan sebagai template"` while the label read `"Simpan juga sebagai
       template"`.)
    4. **No second chance from that screen:** `isSuccess` triggers `onBack()` in the same frame.
* **The name now fills itself in, two ways, because each covers the other's gap.** Ticking the box
    copies `judul` into the field so the text is actually *visible* - for a low-literacy reader what
    is on screen has to be what gets saved - and `effectiveTemplateName` derives
    `templateName.ifBlank { judul }` at save time, which covers ticking the box before typing a
    judul, or deliberately clearing the field.
* **The submit-blocking validation is gone.** With a name that can never be blank the rule had no
    reason to exist. The field is now labelled "Nama Template (opsional)", matching the form's other
    optional sections.
* **The checkbox became a card with a `SectionLabel`.** Its position was already right - last thing
    before the submit button - so **what changed is its visual weight, not where it sits**. It was
    the only part of the form that looked like a footnote instead of a feature.
* **It stops offering to duplicate a template you already have.** The form now loads the volunteer's
    template titles on open, following `loadAidSuggestions` exactly (fired from `init`, failure
    swallowed - a convenience must never block a form that can still be filled in). When one matches
    the judul, the card is **replaced** by "Sudah ada template untuk judul ini." rather than
    silently vanishing; UI that disappears without explanation is worse than UI that explains
    itself. No new query or policy: `TaskRepository.templates()` and its RLS already existed.
* **The second entry point had the identical flaw.** "Jadikan Template" on `ProgramListScreen`
    opened a dialog whose name field started empty even though the dialog was holding the whole
    `Task`. It now prefills from `task.judul`, and its Simpan button is no longer born disabled.
* **Two dead ends closed on the way:**
    * `dismissTemplateWarning()` had no caller anywhere. Removed.
    * `TemplateListScreen`'s empty state is the **only text in the app that explains how a template
      is made**, and it offered no button - read the sentence, back out, go find the form. It now
      passes an `action`, the way `ProgramListScreen`'s empty state already did.
* **Tests:** 128 total, up from 121 (7 new in `CreateTaskTemplateFieldTest`). **Both rules were
    verified by reverting the code and confirming the failure:** dropping the `ifBlank` fallback
    broke 3 tests, and dropping the non-blank-judul guard broke 1.
* **Verified:** clean `assembleDebug` + `testDebugUnitTest` with **zero compiler warnings**.
* **Not yet verified - none of this has been seen running.** The emulator's session ended when the
    app was reinstalled during the previous change and no credentials are available from this
    machine, so every item below is compile-and-test verified only:
    * That the card actually reads as a distinct block on a real screen.
    * Ticking once and saving **without typing anything**, and the template appearing in the list.
    * Ticking, clearing the name, and the save still succeeding under the judul.
    * The "Sudah ada template untuk judul ini." replacement appearing for a duplicate judul.
    * The prefilled "Jadikan Template" dialog.
* **Deliberately not built:** a post-save prompt ("you just made this - keep it as a template?").
    The checkbox was kept by choice. Worth knowing if it is ever revisited: this app has **no
    post-action prompt pattern at all** - zero `Snackbar` anywhere, and all three existing
    `AlertDialog`s are pre-action gates - so it would be a genuinely new interaction here.
* **Also left alone:** the home card is still titled "Pakai Template", framing templates purely as
    something to consume. Nothing on the home screen hints they can be created.

### [2026-08-29] - "Anak Binaan Saya" Became a Real Section
* **What it was:** a heading and a row of bare white pills, each holding a `ChildCare` icon and a
    name. Four things were wrong at once, confirmed against a screenshot from the running emulator
    rather than guessed at: it used a different visual vocabulary from every card above it
    (`Modifier.background(Color.White, CircleShape)`, no elevation, no ripple, 16dp icon - against
    white cards with 16dp radius, 2dp elevation and 48dp tiles); it carried **only a name**, which a
    volunteer already knows; it was not tappable although everything above it was; and being the
    last item in a scrolling column it left the page ending in a large empty area.
* **The `id` was thrown away at the call site.** `ChildChips(names = ui.children.map { it.nama })`
    handed the composable a `List<String>`, so it *structurally* could not show anything else or
    navigate anywhere. That is why this is a rewrite rather than a restyle.
* **One card per child**, built on the exact `ActionCard` values so the page reads as one design:
    48dp initial-letter avatar (a **circle**, not the 14dp tile - it stands for a person, not a
    category), name, "6 tahun - Ibu Siti", a `StatusChip` with the running-programme count, and the
    condition note clamped to two lines.
* **A child with no running programme gets the orange chip**, not the teal one. That is the single
    most actionable thing on the screen and the old design could not express it at all.
* **The per-child count is derived from `activePrograms`, never `programs`.** The hero card
    directly above prints `activeCount`; counting raw rows would let the cards sum to more than the
    total printed inches above them. There is a test asserting the two agree.
* **First age display in the app.** `tanggal_lahir` has existed since `0001` but was **write-only**
    - it appeared in two admin form fields and nowhere else. `catatan_kondisi` had likewise never
    been rendered read-only. Under one year the label counts **months**, because "0 tahun" says
    nothing about a baby and this app serves toddlers.
* **Three queries now, and the screen is faster than when it did two.** `refresh()` ran its two
    calls sequentially; parent names would have made three. All three are now `async` inside one
    `coroutineScope`, so the added data costs no wall-clock time on the slow connections this app
    targets. The comment says so, so nobody "simplifies" it back.
* **No new RLS.** `can_view_profile` (`0001_initial_schema.sql:195-215`) already admits "I am a
    volunteer; target is the parent of a child assigned to me", so `profilesByRole("orangtua")`
    returns exactly the right parents and nothing more.
* **A new projection instead of widening `ChildOption`.** `AssignedChild` carries the extra columns;
    `ChildOption` still feeds the create-program dropdown and the parent's home untouched. The extra
    columns ride on the request that was already being made, so no round trip was added.
* **Fixed in passing:** `childrenForCurrentVolunteer()` has no `order()` and never had one, so the
    chip order could differ between loads. The new query sorts by name.
* **Tapping a card opens the create form with that child chosen.** The route gained a second
    optional argument (`create_program_ajar?templateId={templateId}&anakId={anakId}`); both plain
    `navigate("create_program_ajar")` callers still match unchanged. Order-independent by
    construction - `loadChildren` uses `selectedAnakId ?: list.first()`, so an explicit choice is
    never overwritten by the default whichever finishes first.
* **Tests:** 121 total, up from 111 (10 new in `AssignedChildTest`). **Both new rules were verified
    by reverting the code and confirming the failure:** counting raw `programs` broke the
    agrees-with-the-total test, and naive year subtraction broke both the birthday-not-yet-reached
    and the months cases. `VolunteerHomeStateTest` was migrated from `ChildOption` to
    `AssignedChild`.
* **Follow-up fix, found by a reader of the screenshot and not by me.** The condition note was a
    sibling of the avatar `Row`, so it started at the card's left padding **underneath the avatar**
    instead of lining up with the name -- with the live data (`catatan_kondisi` is literally "1")
    it read as a stray character nobody could explain. Two changes: `padding(start = 60.dp)` to
    align it with the name (48dp avatar + the Row's 12dp gap), and a `"Kondisi: "` prefix. Unlike
    "5 program" or "6 tahun", a free-text note describes nothing about itself, so it needs the
    label to be legible at all.
* **Verified, on the emulator, with screenshots before and after:** clean `assembleDebug` +
    `testDebugUnitTest` with **zero compiler warnings**; the greeting is not clipped at scroll 0;
    real data displayed ("0 bulan - Kevin ortu", "5 program", and the condition note); and
    **tapping the card opened the create form with "Kevin 2" already selected**. The feedback
    triage tabs from the previous change were also seen working ("Perlu Ditanggapi (0)" /
    "Kabar Baik (2)").
* **Not yet verified - the condition-note fix above has NOT been seen on screen.** Reinstalling
    ended the emulator's session and no credentials are available from this machine, so the
    alignment and the "Kondisi: " label are compile-and-test verified only. The screenshot that
    proved the rest of the card predates this fix.
* **Not yet verified - the signed-in account only has one child, with programmes, a birth date and
    a parent, so every fallback path is untested on screen:**
    * The **orange "Belum ada program"** chip.
    * A child with **no birth date** or **no resolvable parent** - i.e. the subtitle collapsing to
      one half or to nothing. Covered by unit tests, not by eyes.
    * A child with a **blank name**, i.e. the avatar falling back to the `ChildCare` icon.
    * **More than one child**, so the stacked-card rhythm and the new name sort are unseen.
    * A **long condition note**, so the two-line clamp and its ellipsis are unseen.
* **Unrelated debt confirmed, not fixed:** the screenshot of `CreateTaskScreen` came out in
    lavender and purple-grey, not brand teal. That is the `dynamicColor = true` issue already
    recorded in this document - but it was written up as affecting **admin** screens, and this
    confirms it hits **volunteer** screens too wherever `MaterialTheme.colorScheme` is used instead
    of the `ui.theme` constants. Still a one-line change that alters every screen, so it still
    needs a deliberate decision rather than being slipped into an unrelated change.

### [2026-08-29] - Reusable Program Ajar Templates
* **The problem:** a volunteer repeating a latihan - same exercise, different child, or the same
    child weeks later - refilled the entire form and **re-uploaded the demo media**. That last part
    is the expensive one: `submit()` does a raw `openInputStream(...).readBytes()` and uploads it
    verbatim, so a 50 MB video is paid for again over a slow connection every single time.
* **A template stores the exercise, never the delivery.** No `anak_id`, no `deadline`, no
    `starts_on`/`ends_on`, no `status`. See the Product Rules table above for the full split; the
    rule lives in two pure functions with a test on each rather than scattered through a ViewModel.
* **Applying a template prefills the form that already exists.** No second create screen - the
    route just gained an optional argument (`create_program_ajar?templateId={templateId}`), so both
    existing `navigate("create_program_ajar")` callers still open an empty form unchanged. Every
    prefilled field stays editable, which was the explicit requirement.
* **Templates are shared foundation-wide** (`template_select` admits any relawan), and **that is
    the opposite of how aid suggestions work** - those are deliberately private, per the
    2026-08-16 entry. The difference is deliberate: an aid suggestion is inferred from a
    volunteer's own history, whereas a template is something they chose to publish. The privacy
    consequence is real and is stated in the UI at both save points: the demo media travels too.
* **The shared choice forced a new storage bucket.** See "Why template media needs its own bucket"
    above - keeping template media in `tugas-demo` breaks for exactly the cross-volunteer case
    sharing exists for, *and* leaves the receiving parent unable to open the demo at all. Verified
    that `copy(from, to, destinationBucket)` exists in supabase-kt 3.5.0 by reading the dependency
    source (`BucketApi.kt:169`) rather than assuming.
* **`template_delete` is the first DELETE policy in this schema.** Every other table has none on
    purpose, because they record care that was delivered. A template records nothing that happened,
    so removing one loses no history. The migration says so, in case it later reads as an oversight.
* **A failed template must not fail the program.** By the time the template is written the `tugas`
    row exists and `on_tugas_insert` has already notified the parent, so `saveTemplate` never
    throws - it returns a warning that replaces the success toast. Reporting it as "Gagal menyimpan
    program ajar" would have invited the volunteer to submit again and hand the family a duplicate.
* **Removed a duplicate rather than adding one.** `ActionErrorBanner` was private inside
    `ProgramListScreen`; the template list needed the same thing, so it moved to
    `ui/components/ActionErrorBanner.kt` - the same move `EmptyState` got for the same reason. Its
    doc now records why it exists separately from `ErrorRetry`: mixing the two is a bug this repo
    has already shipped once.
* **Also fixed while in there:** `CreateTaskScreen`'s instruction box seeds its `TextFieldValue`
    once and owns the cursor, so a template's `instruksi` reached the ViewModel but left the box on
    screen empty. It is now synced explicitly.
* **Tests:** 111 total, up from 99 (12 new in `TaskTemplateTest`). **Both directions were verified
    by reverting the code and confirming the failure:** leaking `anakId` into the template broke 2
    tests, and dropping the recurrence-clearing rule broke 1 - so they catch the regression rather
    than passing either way.
* **Verified:** clean `assembleDebug` + `testDebugUnitTest`, **zero compiler warnings**.
* **Not yet verified - nothing was run on a device or against the database:**
    * **Migration `0006` has NOT been run.** Neither has `0005`.
    * **The whole storage-copy design is untested at runtime.** The argument for it is read off the
      RLS policies and the supabase-kt source, which is not the same as having seen a copy succeed.
      The decisive test needs **two volunteer accounts assigned to different children**: volunteer B
      applies volunteer A's template, saves, and then **the parent of B's child opens the task and
      the video plays**. If the second copy is wrong, that last step is where it shows.
    * Whether `storage.objects` INSERT policy is enough for a `copy` destination, or whether the
      API also needs something the schema does not grant.
    * Every pixel: the template list, the save-as-template checkbox, the naming dialog, and the
      media picker showing inherited media.
    * Deleting a template leaves its object in the bucket - there is no DELETE policy on
      `storage.objects` anywhere, so this is consistent with existing behaviour, not new.

### [2026-08-29] - Closing a Positive Behavior Note Without Writing an Essay
* **The question that started it:** if a parent's catatan perilaku is positive and there is nothing
    to ask, should the volunteer reply, or leave it? Reading the schema settled it: **leaving it is
    not a neutral choice.** A note is closed only by a volunteer's reply, so a positive one sits
    under an orange "Menunggu" chip on the parent's home screen forever, and never leaves the
    volunteer's inbox. For a low-literacy parent whose participation runs on trust, being visibly
    ignored after reporting good news discourages the exact habit this app exists to build.
* **Quick replies, not a new status.** Three canned sentences (`ui/feedback/QuickReplies.kt`) sit
    above the composer for volunteers. Each sends a **real** reply, so the existing
    `on_balasan_insert` trigger flips the note to "Dijawab" and notifies the parent. **No migration,
    no RLS change, no new trigger, no acknowledge path to maintain** - and the parent gets a
    sentence to read rather than a chip quietly changing colour. Shown only when
    `!isParent && status != "answered"`: a parent's reply advances nothing, and an answered note has
    nothing left to close.
* **`sendReply(text: String? = null)`** - a chip passes its sentence straight in rather than filling
    the text field first, so a tap cannot be lost to a recomposition landing between two state
    updates. A chip reply deliberately does **not** clear a half-typed message. Also added an
    `isSending` re-entry guard the typed path never had.
* **The volunteer inbox is now two tabs**, "Perlu Ditanggapi" and "Kabar Baik", via
    `enableTriage = true` (default `false`, so the parent's screen is untouched - a parent sees only
    their own child and has nothing to triage). Pure client-side filter over the list already
    fetched: **no new column and no second query**, which this data-frugal app should not pay for.
* **The obvious triage rule is wrong, and the tests say so.** `kendala` is almost never empty - two
    of its five chips (`Lancar`, `Banyak Kemajuan`) report *success*. Treating a non-empty `kendala`
    as trouble would have put nearly every note in the urgent tab and defeated the split.
    `FeedbackChallenge.CONCERNING` names the three that actually are difficulties. Matching splits
    the joined string rather than substring-searching it, so a future chip that embeds one of these
    names cannot fire it by accident.
* **Migration `0005` declares `feedback.mood`, which no migration ever did.** The column has been
    written on every parent feedback insert since `e7cd324` and is a required form field, but it
    only exists in production because someone added it through the Supabase dashboard - so `0001`
    stopped describing production some time ago. A database rebuilt from these migrations alone
    would reject every catatan perilaku with PGRST204. `text`, not an enum, for the same reason
    `tugas.alat_bantu` is `text[]`. `add column if not exists` makes it safe to run on the live
    project.
* **Removed a duplicate rather than adding a layer.** `getMoodEmoji` was copied **verbatim** into
    two screens and the mood strings were hardcoded in three places, with the challenge chips in a
    fourth. All four now read `FeedbackMood` / `FeedbackChallenge` in
    `data/model/FeedbackVocabulary.kt`, following the `TeachingAid` pattern.
* **Also removed the dead `"in_process"` chip** from `FeedbackListScreen`. Nothing has ever written
    that status, so the blue "Diproses" badge could never render and implied a workflow that does
    not exist. The enum value stays in Postgres - dropping it costs a migration and buys nothing.
* **Tests:** 99 total, up from 90 (9 new in `FeedbackTriageTest`). **Both triage rules were verified
    by reverting the code and confirming the failure:** adding `Lancar` to `CONCERNING` broke 2
    tests, and removing the answered-note guard broke 2 - so they genuinely catch the regression
    rather than passing either way. The suite also pins the stored chip/mood strings and the emoji
    mapping, so a rename that would orphan existing rows breaks the build instead of the data.
* **Verified:** clean `assembleDebug` + `testDebugUnitTest` green with **zero compiler warnings**
    (the first pass used the deprecated `TabRow`; switched to `PrimaryTabRow`).
* **Not yet verified - nothing was run on a device or against the database:**
    * **Migration `0005` has NOT been run,** and it was not confirmed whether `mood` actually exists
      in production. If it does, `0005` is only a bookkeeping catch-up. If it does not, then every
      catatan perilaku has been failing to save all along and this is a live bug fix. **Check this
      before merging** - it decides which of those two stories is true.
    * The quick-reply round trip: that a chip tap really does insert the reply, that the trigger
      flips `status`/`answered_at`, and that the parent's chip turns green. Needs a parent **and** a
      volunteer account; none has ever been available from this machine.
    * Every pixel. The tab row, the chip row above the composer, and how the chips wrap on a narrow
      phone are all untested on a screen.
    * **The parent will not see the notification** the reply generates. `ParentNotificationScreen`
      still renders a hardcoded `sampleNotifications` list and never reads the `notifications`
      table. The row is written; it is just not displayed yet. The status change on the home screen
      *is* visible now.

### [2026-08-16] - Volunteer Screens: A Real Home, and Six Flow Fixes
* **The volunteer had no home screen.** It was an unstyled `Box` of four buttons written inline in
    the nav graph, on a route named `user_home` reached through the role `when`'s `else` branch. It
    was also the only screen left showing English copy and a raw enum: `Welcome, Kevin (RELAWAN)`.
    Replaced with `VolunteerHomeScreen`, built on the parent screens' visual language — grey canvas,
    flat teal-titled app bar, gradient summary card, white 16dp cards at 2dp elevation.
* **Volunteers now have a bottom nav** (Beranda / Feedback / Profil), like parents. This also closes
    a dead end: `FeedbackListScreen` has no `navigationIcon`, so as a pushed destination the
    volunteer could only leave it with the system back gesture. As a tab, that cannot happen.
* **`ParentProfileScreen` → `ProfileScreen`.** It turned out to be role-neutral apart from a
    hardcoded "Orang Tua" label and the `familyStatus` row, so it now takes `roleLabel` and
    `showFamilyStatus` and volunteers reuse it instead of getting a second near-identical screen.
* **`ProgramListScreen` restyled** to match: white elevated cards, 48dp icon tile, shared
    `StatusChip` (imported rather than copied — it is `internal` and this is one module, so no
    fifth badge variant was created). Cards now expand in place to show instruction and priority —
    **priority was collected on the create form and then never displayed anywhere.**
* **Six flow defects fixed:**
    1. A failed "Hentikan Program" shared one `error` field with load failures, so it replaced the
       whole list with a full-screen red message. Now a separate `actionError` banner; list stays.
    2. No retry anywhere — added `ErrorRetry` and wired it in.
    3. `CreateTaskViewModel.retryLoadChildren()` existed but had no caller at all. Wired.
    4. `ProgramListViewModel` had `init { refresh() }` *and* a screen `LaunchedEffect` — two round
       trips per entry, the exact anti-pattern `ParentHomeViewModel` documents. Removed the init.
    5. `FeedbackListScreen` had no `LaunchedEffect` at all, so it showed stale data after replying
       to a thread. Added, and its init-load removed.
    6. Its empty text was parent-voiced ("Belum ada catatan/feedback.") on a screen titled
       "Feedback Orang Tua". Now a parameter.
* **New shared components:** `ui/components/EmptyState.kt` and `ErrorRetry.kt`. `EmptyState` is the
    good icon+headline+subtext shape that was locked inside `ParentNotificationScreen` as a private
    composable; that screen now uses the shared one, so this removes a duplicate rather than adding.
* **A test caught its own weakness.** The first `summary` test passed with the branch order
    reversed, because it never exercised the case where both children and programs are empty — the
    only case where the order matters. Rewritten. Separately, `!isStopped` in the active-program
    filter proved redundant: `RecurrenceRule.effectiveEnd` already folds `stopped_at` into the end
    date, so `isFinished` covers it. Dropped the clause.
* **Tests:** 43, up from 32. Both derived-state rules verified by reverting the code and confirming
    the failure.
* **Not verified:** anything on screen. Layout, colours and spacing are untested on a device.
* **Debt deliberately left, so it is not forgotten:**
    * `MainActivity` uses `SipatokkongTheme { }`, and `dynamicColor` defaults to `true` — so on
      Android 12+ `MaterialTheme.colorScheme` comes from the user's *wallpaper* and `BrandTeal` is
      never applied. Parent screens (and now the volunteer ones) dodge this by importing brand
      colours directly; **admin screens do not, and render in arbitrary hues.** Flipping the flag is
      one line but changes every screen, so it needs a deliberate decision.
    * `SectionLabel`, `DeadlineField`/`DateField` and `ChildDropdown`/`OptionDropdown` are still
      duplicated between `ui/volunteer/` and `ui/admin/AdminFormComponents.kt`.
    * `CreateTaskScreen` is still 677 lines, the longest file in the app.
    * Dark mode remains unsupported: `darkTheme` defaults to `false`, `DarkColorScheme` is still the
      template purple, and `Color.White`/`Color.Black` are hardcoded in ~10 places.
### [2026-08-16] - Feature: Volunteers Can Add Their Own Teaching Aids
* **The gap:** `TeachingAid` is an app constant, so an aid missing from the twelve built-ins could
    only be added by editing code and shipping a new APK — unworkable for something a therapist
    finds in the field.
* **Most of the work was already done.** `alat_bantu` is `text[]` rather than an enum, and
    `labelsOf` passes unrecognised values through untouched, so a volunteer-typed aid stores and
    renders correctly with **zero changes to the parent's screen**.
* **What was added:** the aid dropdown is now a searchable combobox. Typing filters the list;
    already-picked aids are pinned above the results so a filter never hides the selection being
    built; a `Tambah "…"` row commits new text. Aids used before are offered on the next task, so
    they are typed once, not every time.
* **Guard against a fragmented list:** `TeachingAid.resolve` folds a retyped built-in back onto its
    stored value ("Cermin"/"CERMIN"/"cermin" → `cermin`), and typed text is trimmed, its inner
    whitespace collapsed, and capped at 40 characters.
* **Known limit — suggestions do not travel between volunteers.** `tugas_select` grants a volunteer
    only `relawan_id = auth.uid()`, so the suggestion query sees only their own history. Sharing
    aids across the team would need a new RLS policy or a shared catalogue table; neither is built.
* **Two self-contradictions caught before landing:** the query was being cleared on every selection,
    which made the pinned section pointless and snapped the menu from a few rows to twelve mid-tap —
    it now survives a pick. And the anchor had to become `PrimaryEditable`: with
    `PrimaryNotEditable` the popup takes IME focus and the keyboard closes on every keystroke.
* **Tests:** 79 total, up from 41. Three were verified by reverting the code under test and
    confirming the failure — `resolve`'s built-in folding, the pinned selection, and case-insensitive
    suggestion dedup.
* **Not verified:** anything on-screen. The layout, keyboard behaviour and menu height are untested
    on a device.

### [2026-08-16] - Fix: Wrapped Aid Chips Were Touching
* **The bug:** Both `FlowRow`s added for teaching aids omitted `verticalArrangement`. `FlowRow`
    only spaces chips horizontally, so once they wrapped onto a second line the rows sat flush
    against each other at 0dp. With twelve aids and labels as long as "Buku cerita bergambar",
    wrapping is the normal case, not an edge case.
* **The answer was already in this repo.** `CreateFeedbackScreen` has set
    `verticalArrangement = Arrangement.spacedBy(8.dp)` on its `FlowRow` all along; the new code
    simply failed to follow an established pattern. Both now use the same values, so the aid chips
    and the "kendala" chips read alike.
* **Also:** chip padding went from `10.dp × 6.dp` to `12.dp × 8.dp` — `labelMedium` at 6dp vertical
    sat tight against the chip edge.
* **Fixed in both places,** including the volunteer's selection chips. That screen had the
    identical defect and nobody had reported it yet, most likely because it had not been opened
    with several aids selected.
* **Verified:** `assembleDebug` green, 41 tests still pass, and all three `FlowRow`s in the
    codebase now declare `verticalArrangement`.
* **Not yet verified:** how it actually looks. This is a layout change and no screen was seen from
    this machine.

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
* **Not yet verified:** all runtime behaviour. (Migration `0004` was applied on 2026-08-16.)

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
