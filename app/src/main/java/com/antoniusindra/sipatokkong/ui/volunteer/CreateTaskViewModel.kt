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
import com.antoniusindra.sipatokkong.data.model.TaskInsert
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
    // ---- recurrence ----
    val recurrence: RecurrenceType = RecurrenceType.ONCE,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: String = "",
    val intervalDays: String = "",
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
) {
    val selectedChildName: String?
        get() = children.firstOrNull { it.id == selectedAnakId }?.nama

    val isRecurring: Boolean get() = recurrence != RecurrenceType.ONCE
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

    init {
        loadChildren()
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

    fun onAnakSelected(id: String) = _uiState.update { it.copy(selectedAnakId = id) }
    fun onJudulChange(v: String) = _uiState.update { it.copy(judul = v, error = null) }
    fun onInstruksiChange(v: String) = _uiState.update { it.copy(instruksi = v) }
    fun onPriorityChange(p: TaskPriority) = _uiState.update { it.copy(priority = p) }
    fun onDeadlineChange(d: LocalDate?) = _uiState.update { it.copy(deadline = d) }

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
            s.mediaUri == null || s.mediaKind == null || mediaExt == null ->
                return fail("Foto/video demonstrasi wajib diunggah.")
            relawanId == null -> return fail("Sesi tidak valid. Silakan login ulang.")
        }
        validateRecurrence(s)?.let { return fail(it) }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ServiceLocator.appContext.contentResolver
                        .openInputStream(Uri.parse(s.mediaUri))
                        ?.use { it.readBytes() }
                } ?: throw IllegalStateException("Gagal membaca file media.")

                val path = taskRepository.uploadDemo(s.selectedAnakId!!, bytes, mediaExt!!)

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
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
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
