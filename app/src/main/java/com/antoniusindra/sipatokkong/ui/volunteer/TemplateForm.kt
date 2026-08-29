package com.antoniusindra.sipatokkong.ui.volunteer

import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import com.antoniusindra.sipatokkong.data.model.TaskTemplate
import java.time.LocalDate

/**
 * Prefills the create-program form from a template.
 *
 * This is the other half of the split enforced by `TaskTemplateInsert.from`, and the reason it is a
 * plain function rather than logic inside the ViewModel: what it **must not** touch —
 * [CreateTaskUiState.selectedAnakId], [CreateTaskUiState.deadline], [CreateTaskUiState.endsOn],
 * [CreateTaskUiState.children] — is the design, and a test can hold it to that.
 *
 * Nothing here locks anything down. Every field it writes is an ordinary form field the volunteer
 * can still change before saving; a template is a starting point, not a contract.
 *
 * @param today used to default a recurring program's start date, exactly as
 *   [CreateTaskViewModel.onRecurrenceChange] does when the volunteer picks a repeating pattern by
 *   hand. Passed in rather than read from the clock so the rule can be unit-tested.
 */
fun CreateTaskUiState.applyTemplate(
    template: TaskTemplate,
    today: LocalDate = LocalDate.now(),
): CreateTaskUiState {
    val type = template.recurrenceType
    return copy(
        judul = template.judul,
        instruksi = template.instruksi.orEmpty(),
        priority = TaskPriority.entries
            .firstOrNull { it.dbValue == template.priorityLevel }
            ?: TaskPriority.MEDIUM,
        alatBantu = template.alatBantu.orEmpty().toSet(),
        // The query is a spent filter, not a draft — starting a new form with it filled would hide
        // most of the aid menu behind a search nobody typed.
        aidQuery = "",

        // ---- media comes from the template bucket, so there is no local Uri to hold ----
        mediaUri = null,
        mediaName = null,
        mediaKind = MediaKind.entries.firstOrNull { it.dbValue == template.mediaKind },
        templateMediaPath = template.mediaPath,
        templateMediaLabel = template.nama,

        // ---- the recurrence PATTERN travels; the dates do not ----
        recurrence = type,
        // Each sub-field is cleared unless its own type is active. Without this, applying a "once"
        // template over a form already set to weekly would leave the day chips selected underneath,
        // and they would reappear the moment the volunteer switched back to weekly.
        daysOfWeek = if (type == RecurrenceType.WEEKLY) template.daysOfWeek else emptySet(),
        dayOfMonth = if (type == RecurrenceType.MONTHLY) {
            template.recurrenceDayOfMonth?.toString().orEmpty()
        } else "",
        intervalDays = if (type == RecurrenceType.INTERVAL) {
            template.recurrenceInterval?.toString().orEmpty()
        } else "",
        // Same rule as picking a repeating pattern by hand: start today unless something is already
        // chosen. `endsOn` is deliberately left alone — it is required for a recurring program, so
        // guessing it would put a date in front of the volunteer that nobody decided on.
        startsOn = if (type != RecurrenceType.ONCE) startsOn ?: today else startsOn,

        error = null,
    )
}
