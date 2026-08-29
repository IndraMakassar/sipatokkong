# Sipatokkong Project Status

## Technical Overview
Sipatokkong is an Android application using **Jetpack Compose** and **Supabase**. It follows a clean MVVM architecture with manual dependency injection via a `ServiceLocator`. The backend logic is heavily reliant on Supabase's **Row Level Security (RLS)** and database triggers for notifications and data integrity.

## Status Summary

### ✅ Built / Completed
*   **Infrastructure:** Supabase project setup, database schema, RLS policies, and storage bucket
    configurations. Migrations `0001` through `0004` are all applied to the live project;
    `0005` and `0006` are written but **not yet run**.
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
    *   Reusable program-ajar templates, shared across volunteers: save one from the create form
        or from an existing program, then start a new program from it without re-uploading media.
    *   Home screen "Anak Binaan Saya": one card per assigned child with age, parent, condition
        note and running-programme count; tapping one opens the create form with that child set.
    *   Recurring programs (daily / weekly / monthly / every-N-days) with an end date, plus a
        "Program Ajar Saya" screen for stopping a running program.
*   **Communication:** Feedback threads for parent-volunteer interaction, with one-tap quick
    replies for the volunteer and a triaged inbox ("Perlu Ditanggapi" / "Sudah Beres").

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

## Change Log

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
