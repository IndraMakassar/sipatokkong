package com.antoniusindra.sipatokkong.ui.volunteer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import com.antoniusindra.sipatokkong.data.model.TeachingAid
import com.antoniusindra.sipatokkong.data.model.TaskInsert
import com.antoniusindra.sipatokkong.data.model.TaskTemplateInsert
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class CreateTaskUiState(
    val children: List<ChildOption> = emptyList(),
    val isLoadingChildren: Boolean = true,
    val selectedAnakId: String? = null,
    val judul: String = "",
    val instruksi: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val deadline: LocalDate? = null,
    val mediaUri: String? = null,
    val mediaName: String? = null,
    val mediaKind: MediaKind? = null,
    /**
     * Demo media inherited from a template: an object in the `tugas-template` bucket rather than a
     * local file. Mutually exclusive with [mediaUri] — picking a file clears this, and applying a
     * template clears that.
     */
    val templateMediaPath: String? = null,
    /** Name of the template the inherited media came from, for the picker's caption. */
    val templateMediaLabel: String? = null,
    /** Signed URL for previewing [templateMediaPath]; null while it is still being fetched. */
    val templateMediaPreviewUrl: String? = null,
    /** True while a template is being fetched and applied to the form. */
    val isApplyingTemplate: Boolean = false,
    /** Stored values, not enums: a volunteer-typed aid has no [TeachingAid] to represent it. */
    val alatBantu: Set<String> = emptySet(),
    /** What the volunteer is typing into the aid box — both a filter and a new-aid draft. */
    val aidQuery: String = "",
    /** Aids used on earlier tasks, offered alongside the built-ins. */
    val aidSuggestions: List<String> = emptyList(),
    // ---- recurrence ----
    val recurrence: RecurrenceType = RecurrenceType.ONCE,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: String = "",
    val intervalDays: String = "",
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
    // ---- save as template ----
    val saveAsTemplate: Boolean = false,
    val templateName: String = "",
    /**
     * `judul` of templates that already exist, so the form does not offer to make a duplicate.
     *
     * Only the titles are kept: the whole template rows are not needed here, and this list is
     * consulted on every keystroke in the judul field.
     */
    val existingTemplateJuduls: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    /**
     * Set when the program saved but the template did not.
     *
     * Separate from [error] on purpose: by the time this can happen the task exists and its parent
     * has already been notified by the `on_tugas_insert` trigger, so reporting it as a failure
     * would be a lie that invites the volunteer to submit the same program twice.
     */
    val templateWarning: String? = null,
) {
    val selectedChildName: String?
        get() = children.firstOrNull { it.id == selectedAnakId }?.nama

    val isRecurring: Boolean get() = recurrence != RecurrenceType.ONCE

    /** True when demo media is present, from either source. */
    val hasMedia: Boolean get() = mediaUri != null || templateMediaPath != null

    /**
     * The name a template would be saved under.
     *
     * Never blank, because `judul` is already required to submit at all. That is what lets the
     * name field be genuinely optional: ticking the box is one tap and nothing else, and a blank
     * name can no longer block the whole save the way it used to.
     */
    val effectiveTemplateName: String get() = templateName.trim().ifBlank { judul.trim() }

    /**
     * True when this volunteer already has a template for this exercise.
     *
     * Guarded on a non-blank judul: without it a brand-new empty form would match nothing and the
     * check would be meaningless, but worse, an empty [existingTemplateJuduls] entry could make it
     * fire and hide the option before anything has been typed.
     */
    val hasTemplateForThisJudul: Boolean
        get() = judul.isNotBlank() &&
            existingTemplateJuduls.any { it.trim().equals(judul.trim(), ignoreCase = true) }

    // ---- aid menu ----
    // Derived here rather than in the composable so the menu's contents can be unit-tested.

    /** Everything the menu can offer: the built-ins, plus past and session-added custom aids. */
    val aidOptions: List<String>
        get() {
            val custom = (aidSuggestions + alatBantu.filter { TeachingAid.matchBuiltIn(it) == null })
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
            return TeachingAid.entries.map { it.dbValue } + custom
        }

    /** Picked aids, pinned above the results so a filter never hides what is already chosen. */
    val aidSelected: List<String> get() = TeachingAid.sortedForStorage(alatBantu)

    /** Unpicked options matching the query. While a query is present, the rest stay hidden. */
    val aidMatches: List<String>
        get() {
            val q = aidQuery.trim()
            return aidOptions.filter { it !in alatBantu }
                .filter { q.isEmpty() || TeachingAid.labelOf(it).contains(q, ignoreCase = true) }
        }

    /** The typed text, when it names something the menu does not already offer. */
    val addableAid: String?
        get() {
            val clean = TeachingAid.normalizeCustom(aidQuery) ?: return null
            val known = aidOptions.any {
                it.equals(clean, ignoreCase = true) ||
                    TeachingAid.labelOf(it).equals(clean, ignoreCase = true)
            }
            return clean.takeUnless { known }
        }
}

class CreateTaskViewModel(
    private val childRepository: ChildRepository,
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTaskUiState())
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    // Store extension separately so the upload names the file correctly.
    private var mediaExt: String? = null

    /** One-shot guard for [applyTemplate]; see its doc for why. */
    private var templateApplied = false

    /** One-shot guard for [preselectChild], for the same reason. */
    private var childPreselected = false

    init {
        loadChildren()
        loadAidSuggestions()
        loadExistingTemplates()
    }

    /**
     * Titles of templates that already exist, so the form can stop offering to duplicate one.
     *
     * Failure is swallowed exactly like [loadAidSuggestions]: this is a convenience. If the list
     * never arrives the worst case is the volunteer being offered a template they already have --
     * far better than blocking a form they can still fill in.
     */
    private fun loadExistingTemplates() {
        viewModelScope.launch {
            runCatching { taskRepository.templates() }
                .onSuccess { list ->
                    _uiState.update { it.copy(existingTemplateJuduls = list.map { t -> t.judul }) }
                }
        }
    }

    /**
     * Fills the aid menu with what this volunteer used before.
     *
     * Failure is swallowed on purpose: suggestions are a convenience, not a requirement. Surfacing
     * an error here would block a form the volunteer can still fill in with the twelve built-ins.
     */
    private fun loadAidSuggestions() {
        viewModelScope.launch {
            runCatching { taskRepository.aidsUsedByCurrentVolunteer() }
                .onSuccess { used ->
                    _uiState.update { it.copy(aidSuggestions = TeachingAid.suggestionsFrom(used)) }
                }
        }
    }

    private fun loadChildren() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChildren = true, error = null) }
            runCatching { childRepository.childrenForCurrentVolunteer() }
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            children = list,
                            isLoadingChildren = false,
                            selectedAnakId = it.selectedAnakId ?: list.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingChildren = false, error = e.message ?: "Gagal memuat daftar anak")
                    }
                }
        }
    }

    fun retryLoadChildren() = loadChildren()

    /**
     * Loads a template and prefills the form with it.
     *
     * The id arrives from the screen rather than a `SavedStateHandle`, matching
     * `FeedbackThreadViewModel.load`. The [templateApplied] guard makes this idempotent: the
     * screen calls it from a `LaunchedEffect`, which re-runs on configuration change, and a second
     * apply would silently undo edits the volunteer had already made.
     */
    fun applyTemplate(id: String) {
        if (templateApplied) return
        templateApplied = true
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingTemplate = true, error = null) }
            runCatching { taskRepository.getTemplate(id) ?: error("Template tidak ditemukan.") }
                .onSuccess { template ->
                    mediaExt = null // media now comes from the bucket, not a picked file
                    _uiState.update { it.applyTemplate(template).copy(isApplyingTemplate = false) }
                    loadTemplatePreview(template.mediaPath)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isApplyingTemplate = false, error = e.message ?: "Gagal memuat template")
                    }
                }
        }
    }

    /**
     * Signed URL for the inherited media's thumbnail.
     *
     * Failure is swallowed like [loadAidSuggestions]: the media is still attached and will still be
     * copied on submit, so a missing preview must not block a form that is otherwise complete.
     */
    private fun loadTemplatePreview(path: String) {
        viewModelScope.launch {
            runCatching { taskRepository.signedTemplateUrl(path) }
                .onSuccess { url -> _uiState.update { it.copy(templateMediaPreviewUrl = url) } }
        }
    }

    /**
     * Ticking the box fills the name in from the judul.
     *
     * Prefilled rather than left to [CreateTaskUiState.effectiveTemplateName] alone, so the text is
     * actually visible in the field: for a low-literacy reader, what is on screen has to be what
     * gets saved. The derived fallback still covers ticking the box before typing a judul.
     */
    fun onSaveAsTemplateChange(v: Boolean) = _uiState.update {
        it.copy(
            saveAsTemplate = v,
            templateName = if (v) it.templateName.ifBlank { it.judul.trim() } else it.templateName,
            error = null,
        )
    }

    fun onTemplateNameChange(v: String) = _uiState.update {
        it.copy(templateName = v.trimStart(), error = null)
    }

    fun onAnakSelected(id: String) = _uiState.update { it.copy(selectedAnakId = id) }

    /**
     * Opens the form with one child already chosen, for the "Anak Binaan Saya" cards.
     *
     * Order-independent by construction: [loadChildren] sets `selectedAnakId ?: list.first()`, so
     * whichever of the two finishes first, an explicit choice is never overwritten by the default.
     * The guard keeps a `LaunchedEffect` re-run after a configuration change from silently
     * reverting a child the volunteer changed by hand.
     */
    fun preselectChild(id: String) {
        if (childPreselected) return
        childPreselected = true
        _uiState.update { it.copy(selectedAnakId = id) }
    }
    fun onJudulChange(v: String) = _uiState.update { it.copy(judul = v, error = null) }
    fun onInstruksiChange(v: String) = _uiState.update { it.copy(instruksi = v) }
    fun onPriorityChange(p: TaskPriority) = _uiState.update { it.copy(priority = p) }
    fun onDeadlineChange(d: LocalDate?) = _uiState.update { it.copy(deadline = d) }

    /**
     * Adds or removes one aid.
     *
     * The query deliberately survives: it is a filter, not a draft. Clearing it here would snap the
     * menu from a few rows back to the full list mid-tap — and would defeat the pinned selection,
     * which exists precisely so a filter can stay applied while several aids are picked.
     */
    fun onAlatBantuToggle(value: String) = _uiState.update {
        val next = if (value in it.alatBantu) it.alatBantu - value else it.alatBantu + value
        it.copy(alatBantu = next)
    }

    fun onAidQueryChange(v: String) = _uiState.update {
        it.copy(aidQuery = v.trimStart().take(TeachingAid.MAX_CUSTOM_LENGTH))
    }

    /**
     * Commits the typed text as an aid. [TeachingAid.resolve] folds a retyped built-in back onto
     * its stored value, so typing "Cermin" selects the existing option instead of adding a twin.
     */
    fun onAddTypedAid() = _uiState.update {
        val value = TeachingAid.resolve(it.aidQuery) ?: return@update it.copy(aidQuery = "")
        it.copy(alatBantu = it.alatBantu + value, aidQuery = "")
    }

    fun onRecurrenceChange(type: RecurrenceType) = _uiState.update {
        // Default the start date to today the moment a repeating pattern is chosen.
        it.copy(
            recurrence = type,
            startsOn = if (type != RecurrenceType.ONCE) it.startsOn ?: LocalDate.now() else it.startsOn,
            error = null,
        )
    }

    fun onDayOfWeekToggle(day: DayOfWeek) = _uiState.update {
        val next = if (day in it.daysOfWeek) it.daysOfWeek - day else it.daysOfWeek + day
        it.copy(daysOfWeek = next, error = null)
    }

    fun onDayOfMonthChange(v: String) = _uiState.update {
        it.copy(dayOfMonth = v.filter(Char::isDigit).take(2), error = null)
    }

    fun onIntervalChange(v: String) = _uiState.update {
        it.copy(intervalDays = v.filter(Char::isDigit).take(3), error = null)
    }

    fun onStartsOnChange(d: LocalDate?) = _uiState.update { it.copy(startsOn = d, error = null) }
    fun onEndsOnChange(d: LocalDate?) = _uiState.update { it.copy(endsOn = d, error = null) }

    /** Resolve MIME of the picked media and map to a supported kind/extension. */
    fun onMediaPicked(uri: Uri) {
        val resolver = ServiceLocator.appContext.contentResolver
        val mime = resolver.getType(uri)
        val (kind, ext) = when (mime) {
            "image/jpeg" -> MediaKind.IMAGE to "jpg"
            "image/webp" -> MediaKind.IMAGE to "webp"
            "video/mp4" -> MediaKind.VIDEO to "mp4"
            else -> {
                _uiState.update { it.copy(error = "Format tidak didukung. Gunakan JPG, WebP, atau MP4.") }
                return
            }
        }
        mediaExt = ext
        _uiState.update {
            it.copy(
                mediaUri = uri.toString(),
                mediaName = "Media terpilih (.$ext)",
                mediaKind = kind,
                // Replacing inherited media with a picked file: the two sources are exclusive.
                templateMediaPath = null,
                templateMediaLabel = null,
                templateMediaPreviewUrl = null,
                error = null,
            )
        }
    }

    fun submit() {
        val s = _uiState.value
        val relawanId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        when {
            s.selectedAnakId == null -> return fail("Pilih anak terlebih dahulu.")
            s.judul.isBlank() -> return fail("Judul program ajar wajib diisi.")
            !s.hasMedia || s.mediaKind == null ->
                return fail("Foto/video demonstrasi wajib diunggah.")
            s.mediaUri != null && mediaExt == null ->
                return fail("Foto/video demonstrasi wajib diunggah.")
            relawanId == null -> return fail("Sesi tidak valid. Silakan login ulang.")
        }
        validateRecurrence(s)?.let { return fail(it) }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, templateWarning = null) }
            try {
                // Two sources, one destination. Inherited media is copied server-side, so reusing
                // a template costs no upload at all -- and the copy is what puts the object under
                // this child's id, which is the only reason their parent can open it.
                val path = if (s.mediaUri != null) {
                    val bytes = withContext(Dispatchers.IO) {
                        ServiceLocator.appContext.contentResolver
                            .openInputStream(Uri.parse(s.mediaUri))
                            ?.use { it.readBytes() }
                    } ?: throw IllegalStateException("Gagal membaca file media.")
                    taskRepository.uploadDemo(s.selectedAnakId!!, bytes, mediaExt!!)
                } else {
                    taskRepository.copyTemplateToDemo(s.templateMediaPath!!, s.selectedAnakId!!)
                }

                taskRepository.create(
                    TaskInsert(
                        anakId = s.selectedAnakId,
                        relawanId = relawanId!!,
                        judul = s.judul.trim(),
                        instruksi = s.instruksi.ifBlank { null },
                        mediaKind = s.mediaKind!!.dbValue,
                        mediaPath = path,
                        priorityLevel = s.priority.dbValue,
                        // A one-shot task keeps using `deadline`; a program uses starts_on/ends_on.
                        deadline = if (s.isRecurring) null
                        else s.deadline?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        weekOf = LocalDate.now()
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                        recurrence = s.recurrence.dbValue,
                        recurrenceDays = if (s.recurrence == RecurrenceType.WEEKLY) {
                            s.daysOfWeek.map { it.value }.sorted()
                        } else null,
                        recurrenceDayOfMonth = if (s.recurrence == RecurrenceType.MONTHLY) {
                            s.dayOfMonth.toIntOrNull()
                        } else null,
                        recurrenceInterval = if (s.recurrence == RecurrenceType.INTERVAL) {
                            s.intervalDays.toIntOrNull()
                        } else null,
                        startsOn = if (s.isRecurring) {
                            s.startsOn?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        } else null,
                        endsOn = if (s.isRecurring) {
                            s.endsOn?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        } else null,
                        // Declaration order, not tap order, so the parent always sees a stable list.
                        // Leftover text in the search box is NOT committed: the box stays filled
                        // while picking, so "bol" left behind after tapping Bola is a spent filter,
                        // not an aid the volunteer meant to add.
                        alatBantu = TeachingAid.sortedForStorage(s.alatBantu).ifEmpty { null },
                    )
                )
                val warning = if (s.saveAsTemplate) {
                    saveTemplate(s, relawanId!!, path)
                } else null

                _uiState.update {
                    it.copy(isSubmitting = false, isSuccess = true, templateWarning = warning)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.message ?: "Gagal menyimpan program ajar") }
            }
        }
    }

    /** Returns an error message when the recurrence settings are incomplete, or null when valid. */
    private fun validateRecurrence(s: CreateTaskUiState): String? {
        if (!s.isRecurring) return null

        val start = s.startsOn ?: return "Tanggal mulai wajib diisi untuk program berulang."
        val end = s.endsOn ?: return "Tanggal selesai wajib diisi untuk program berulang."
        if (end < start) return "Tanggal selesai tidak boleh sebelum tanggal mulai."

        return when (s.recurrence) {
            RecurrenceType.WEEKLY ->
                if (s.daysOfWeek.isEmpty()) "Pilih minimal satu hari dalam seminggu." else null

            RecurrenceType.MONTHLY -> {
                val day = s.dayOfMonth.toIntOrNull()
                if (day == null || day !in 1..31) "Tanggal dalam bulan harus antara 1 sampai 31." else null
            }

            RecurrenceType.INTERVAL -> {
                val n = s.intervalDays.toIntOrNull()
                if (n == null || n < 1) "Jumlah hari interval minimal 1." else null
            }

            else -> null
        }
    }

    /**
     * Saves the just-created program as a template. Returns a warning message, or null on success.
     *
     * Never throws. By the time this runs the `tugas` row exists and the parent has already been
     * notified by the `on_tugas_insert` trigger, so a failure here must not surface as "Gagal
     * menyimpan program ajar" -- that would invite the volunteer to submit the whole thing again
     * and give the family a duplicate task.
     *
     * [demoPath] is the object just written to `tugas-demo`; it is copied on to the shared
     * `tugas-template` bucket so other volunteers can read it. Copying rather than re-uploading
     * means saving a template costs no extra bytes from the phone.
     */
    private suspend fun saveTemplate(
        s: CreateTaskUiState,
        relawanId: String,
        demoPath: String,
    ): String? = runCatching {
        val templatePath = taskRepository.copyDemoToTemplate(demoPath, relawanId)
        taskRepository.createTemplate(
            TaskTemplateInsert(
                relawanId = relawanId,
                nama = s.effectiveTemplateName,
                judul = s.judul.trim(),
                instruksi = s.instruksi.ifBlank { null },
                mediaKind = s.mediaKind!!.dbValue,
                mediaPath = templatePath,
                priorityLevel = s.priority.dbValue,
                alatBantu = TeachingAid.sortedForStorage(s.alatBantu).ifEmpty { null },
                // The pattern travels; anak_id and every date deliberately do not.
                recurrence = s.recurrence.dbValue,
                recurrenceDays = if (s.recurrence == RecurrenceType.WEEKLY) {
                    s.daysOfWeek.map { it.value }.sorted()
                } else null,
                recurrenceDayOfMonth = if (s.recurrence == RecurrenceType.MONTHLY) {
                    s.dayOfMonth.toIntOrNull()
                } else null,
                recurrenceInterval = if (s.recurrence == RecurrenceType.INTERVAL) {
                    s.intervalDays.toIntOrNull()
                } else null,
            )
        )
    }.fold(
        onSuccess = { null },
        onFailure = { e ->
            "Program ajar tersimpan, tetapi templatenya gagal dibuat: ${e.message ?: "kesalahan tidak diketahui"}"
        },
    )

    private fun fail(message: String) = _uiState.update { it.copy(error = message) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateTaskViewModel(
                    childRepository = ServiceLocator.childRepository,
                    taskRepository = ServiceLocator.taskRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
