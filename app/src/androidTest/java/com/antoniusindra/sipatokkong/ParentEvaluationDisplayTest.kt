package com.antoniusindra.sipatokkong

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antoniusindra.sipatokkong.core.media.MediaDownloadState
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.ui.task.DetailBody
import com.antoniusindra.sipatokkong.ui.task.TaskDetailUiState
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParentEvaluationDisplayTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showCompletedTask(feedback: String?) {
        compose.setContent {
            SipatokkongTheme {
                DetailBody(
                    modifier = Modifier,
                    ui = TaskDetailUiState(
                        isLoading = false,
                        tugas = Task(
                            id = "task-1", anakId = "child-1", relawanId = "volunteer-1",
                            judul = "Latihan memegang sendok", mediaKind = "image",
                            mediaPath = "child-1/demo.jpg", priorityLevel = "medium", status = "done",
                        ),
                        occurrenceDate = LocalDate.of(2026, 10, 25),
                        alreadyDone = true,
                        feedbackRelawan = feedback,
                    ),
                    download = MediaDownloadState(),
                    onDownloadDemo = {}, onDownloadProof = {}, onResponse = {},
                    onDuration = {}, onCatatan = {}, onPickProof = {},
                )
            }
        }
    }

    @Test
    fun evaluationIsVisibleEvenWhenTheCompletionHasNoProofMedia() {
        val feedback = "Sudah bagus, lanjutkan latihan secara perlahan."
        showCompletedTask(feedback)
        compose.onNodeWithText(feedback).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Masukan / Feedback Relawan").assertIsDisplayed()
    }

    @Test
    fun completionWithoutEvaluationHasNoEmptyFeedbackCard() {
        showCompletedTask(null)
        compose.onNodeWithText("Tugas ini sudah ditandai selesai.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Masukan / Feedback Relawan").assertDoesNotExist()
    }
}
