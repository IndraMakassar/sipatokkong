# Sipatokkong Project Status

## Technical Overview
Sipatokkong is an Android application using **Jetpack Compose** and **Supabase**. It follows a clean MVVM architecture with manual dependency injection via a `ServiceLocator`. The backend logic is heavily reliant on Supabase's **Row Level Security (RLS)** and database triggers for notifications and data integrity.

## Status Summary

### ✅ Built / Completed
*   **Infrastructure:** Supabase project setup, database schema, RLS policies, and storage bucket
    configurations. Migrations `0001` through `0004` are applied to the live project.
    **`0005` (presensi unique index) is written but NOT yet applied** — apply it before merging.
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
    *   Presensi: "Catat Kunjungan" form (child, date, session note, mandatory compressed proof
        photo) plus a Presensi tab listing the volunteer's own visit history.
*   **Media:** Any photo/video (demo tugas, parent documentation, presensi) can be downloaded to
    the phone's gallery — `Pictures/Sipatokkong` and `Movies/Sipatokkong`.
*   **Communication:** Feedback threads for parent-volunteer interaction.
*   **Admin Reporting:** Rekap Absensi Relawan — visits per volunteer over a date range, with
    volunteers who logged nothing shown at zero.

### 🚧 In Progress / Partially Built
*   **Media Compression:** Wired up for the presensi photo upload only (`core/media/ImageCompressor`).
    The `tugas-demo` and `dokumentasi` uploads still send raw bytes; `LightCompressor` (video) is
    still unused.
*   **Notifications:** Server-side triggers are defined, but real-time client-side handling and push notifications (FCM) are pending.
    The `notif_type` value `'presensi'` remains declared but unused — no trigger writes it.


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

### [2026-08-27] - Presensi: Switch from Gallery to Camera
* **What changed:** The proof photo for volunteer visits is now captured directly using the camera instead of being picked from the gallery.
* **Implementation:**
    *   Added `FileProvider` to `AndroidManifest.xml` with authority `${applicationId}.fileprovider`.
    *   Created `res/xml/file_paths.xml` for temporary camera storage.
    *   Implemented `CameraUriManager` to handle temporary `Uri` generation.
    *   Updated `CreatePresensiScreen.kt` to use `ActivityResultContracts.TakePicture()` and managed the temporary `Uri` with `rememberSaveable`.
    *   Fixed `meta-data` name in `AndroidManifest.xml` to `android.support.FILE_PROVIDER_PATHS`.
* **Verified:** `assembleDebug` succeeded; `CameraUriManager` and `CreatePresensiScreen` compile correctly.
* **Not yet verified:** Runtime camera capture, `FileProvider` permission handling on physical devices, and photo saving to the temporary URI.

### [2026-08-26] - Semua Media Bisa Diunduh ke Galeri HP
* **Yang diminta:** setiap gambar/video bisa diunduh dan tersimpan di HP. Inventarisnya mengubah
    bentuk pekerjaan: **tiga dari empat permukaan media tidak pernah ditampilkan sama sekali**, jadi
    sebagian besar kerjanya adalah memunculkan media yang selama ini tersembunyi.
* **`dokumentasi` berhenti jadi write-only.** Bukti yang dikirim orang tua selama ini diunggah lalu
    hilang dari UI selamanya — tidak ada read model, tidak ada signed URL, tidak ada layar.
    Sekarang ada model `Documentation`, `signedDocumentationUrl`, `getDocumentation`, dan bagian
    "Bukti yang Anda kirim" di `TaskDetailScreen` untuk tugas yang sudah selesai. **Tanpa migrasi** —
    policy `dokumentasi_read` sudah mengizinkannya sejak `0001`.
* **Foto presensi akhirnya terlihat.** `PresensiRepository.signedFotoUrl` selama ini kode mati
    dengan nol pemanggil. Kartu kunjungan kini mengembang di tempat untuk menampilkan fotonya —
    dimuat **hanya saat kartu dibuka**, karena tidak ada apa pun yang menulis `foto_thumb_path`,
    jadi daftar bertumpuk thumbnail berarti mengunduh semua foto ukuran penuh hanya untuk menggulir.
* **Video demo bisa ditonton, tanpa menambah pemutar.** Video wajib diunggah tapi selama ini
    mustahil dilihat — hanya ikon `PlayCircle` mati. Sekarang bisa diunduh ke `Movies/Sipatokkong`
    dan ditonton lewat aplikasi galeri bawaan. **Pemutar video di dalam aplikasi tetap tidak ada;
    unduhan adalah jalan keluarnya, bukan pemutar.**
* **Dua cabang penyimpanan, keduanya wajib di minSdk 27.** API 29+ menulis lewat `MediaStore`
    dengan `RELATIVE_PATH` + `IS_PENDING`, tanpa izin apa pun. API 27–28 menulis ke direktori publik
    (butuh `WRITE_EXTERNAL_STORAGE`, dideklarasikan dengan `maxSdkVersion="28"`) lalu memanggil
    `MediaScannerConnection` — tanpa pemindaian itu file ada di disk tapi tak pernah muncul di
    Galeri, yang akan terbaca pengguna sebagai "unduhannya gagal". Ini **permintaan izin runtime
    pertama** di aplikasi ini.
* **Video dikonfirmasi dulu dengan ukurannya**, foto langsung. `tugas-demo` mengizinkan 50 MB dan
    hemat data adalah syarat keras project ini; dialog untuk foto 200 KB cuma gesekan.
* **Streaming, bukan `ByteArray`.** `downloadAuthenticated(path): ByteArray` ada dan lebih pendek,
    tapi menahan video 50 MB di RAM berisiko OOM justru di HP kelas bawah yang jadi sasaran. Jalur
    yang dipakai `downloadAuthenticatedToAsFlow` → file cache → stream ke MediaStore, dengan progres
    nyata dari `DownloadStatus.Progress`.
* **Satu mesin state, bukan tiga.** `MediaDownloadHandler` disusun ke dalam ViewModel yang butuh,
    supaya tiga layar tidak menyalin enam puluh baris yang sama. Kegagalan unduh memakai jalurnya
    sendiri — satu unduhan gagal tidak boleh menghapus layar yang sedang dilihat.
* **Verified:** `assembleDebug` hijau; **132 tes unit lulus, 17 baru**. Tiga aturan dibuktikan
    dengan membalik kodenya: fallback ekstensi tak dikenal (1 gagal), locale eksplisit pada format
    ukuran (2 gagal), dan konfirmasi sebelum unduh video (1 gagal).
* **Not yet verified — semua yang runtime.** Tidak ada satu file pun yang benar-benar pernah
    terunduh dari mesin ini. Yang paling rawan dan belum tersentuh: **cabang API 27–28** — dialog
    izin, penulisan ke direktori publik, dan `MediaScannerConnection`. Belum diuji juga: file
    muncul di album Galeri, video hasil unduhan benar-benar bisa diputar, penamaan anti-tabrakan
    di jalur legacy, dan perilaku saat koneksi putus di tengah unduhan.

### [2026-08-25] - Presensi: The Volunteer's Attendance Form + Admin Recap
* **The gap:** `presensi` shipped complete in migration `0001` — table, indexes, RLS, a private
    storage bucket, even a `notif_type` value — and then nothing was ever built against it. Not one
    Kotlin file mentioned it. The status doc listed it as "In Progress", which read as half-built
    when the client side was at zero.
* **What was added:** a "Catat Kunjungan" form for the volunteer (child, date defaulting to today,
    session note, mandatory proof photo), a "Presensi" tab holding their own visit history, and an
    admin **Rekap Absensi Relawan** screen — visits per volunteer over a date range.
* **Compression stopped being optional.** `id.zelory:compressor` has been declared in
    `libs.versions.toml` since the start and never imported; nothing needed it, because
    `tugas-demo` allows 50 MB. The `presensi` bucket allows **6 MB and images only**, and a modern
    phone photo routinely exceeds that — so without compression this feature would simply fail on
    real devices. `core/media/ImageCompressor` re-encodes to JPEG at ≤200 KB, which is also what
    the bandwidth budget asked for all along.
* **Migration `0005` adds one unique index** on `(relawan_id, anak_id, tanggal)`. There was no
    constraint at all, so a double tap wrote two rows and inflated every recap number — and
    `presensi` deliberately has no DELETE policy, so a duplicate could never be removed from the
    app. The client maps the resulting `23505` to "Kunjungan untuk anak ini pada tanggal tersebut
    sudah dicatat." rather than surfacing a raw Postgrest error. Same reasoning, same shape as the
    constraint `0002` put on `tugas_penyelesaian`.
* **The upload path is now shared, not copied.** `TaskRepository.uploadTo` was the only
    implementation of the `{anak_id}/{yyyy}/{MM}/{uuid}.{ext}` convention, and getting the first
    segment wrong makes *every* storage policy reject the upload. It moved to
    `data/repository/StorageUpload.kt` and both repositories call it. Likewise `SectionLabel`,
    `ChildDropdown`, `DeadlineField` and `MediaPickerBox` went from `private` to `internal` in
    `CreateTaskScreen` so the presensi form reuses them — the duplication debt this file already
    carries was not worth doubling.
* **The recap is built from the volunteer list, not from the presensi rows.** Grouping the rows
    would drop every volunteer who visited nobody — precisely the entry the foundation opens this
    screen to find. There is a test that fails if anyone "simplifies" it back.
* **History rows are text-only, on purpose.** Nothing in this app writes `foto_thumb_path`, so a
    thumbnail list would download every full-size proof photo to render — the opposite of the
    data-frugality rule. A chip says a photo exists; the photo is not re-fetched.
* **Verified:** `assembleDebug` green; **115 unit tests pass, 25 new**. Three rules were confirmed
    by reverting the code and watching the tests fail: the inclusive date range (1 failure), the
    recap being built from the volunteer list (7 failures), and `canSubmit` requiring a photo and
    blocking a second tap (2 failures).
* **Not yet verified — nothing runtime.** Migration `0005` has **not** been applied to the live
    project yet, so no insert has ever been attempted. No screen has been seen on a device: the
    form layout, the fourth bottom-nav tab, the compression actually landing under 200 KB, and the
    duplicate-day message are all untested outside the compiler.
* **Known limitation carried over:** if the row insert fails after the photo upload succeeds, the
    orphaned object stays in the bucket. `CreateTaskViewModel` has always behaved this way; it is
    recorded here rather than quietly inherited.

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
