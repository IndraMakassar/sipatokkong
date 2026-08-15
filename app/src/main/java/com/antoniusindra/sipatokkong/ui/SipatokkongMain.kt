package com.antoniusindra.sipatokkong.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.admin.CreateAccountScreen
import com.antoniusindra.sipatokkong.ui.admin.CreateChildScreen
import com.antoniusindra.sipatokkong.ui.admin.EditAccountScreen
import com.antoniusindra.sipatokkong.ui.admin.EditChildScreen
import com.antoniusindra.sipatokkong.ui.admin.ManageAccountsScreen
import com.antoniusindra.sipatokkong.ui.admin.ManageChildrenScreen
import com.antoniusindra.sipatokkong.ui.auth.login.LoginScreen
import com.antoniusindra.sipatokkong.ui.feedback.CreateFeedbackScreen
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackListScreen
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackThreadScreen
import com.antoniusindra.sipatokkong.ui.home.AdminHomeScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHistoryScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHomeScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHomeViewModel
import com.antoniusindra.sipatokkong.ui.notifications.ParentNotificationScreen
import com.antoniusindra.sipatokkong.ui.task.TaskDetailScreen
import com.antoniusindra.sipatokkong.ui.volunteer.CreateTaskScreen
import com.antoniusindra.sipatokkong.ui.volunteer.ProgramListScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SipatokkongMain() {
    val navController = rememberNavController()
    val sessionManager = ServiceLocator.sessionManager
    val sessionState by sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Monitor session state changes to trigger root navigation
    LaunchedEffect(sessionState) {
        when (val state = sessionState) {
            is SessionManager.State.SignedOut -> {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
            is SessionManager.State.SignedIn -> {
                val destination = when (state.profile.role) {
                    UserRole.ADMIN -> "admin_home"
                    UserRole.ORANGTUA -> "orangtua_home"
                    else -> "user_home"
                }
                navController.navigate(destination) {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    when (val state = sessionState) {
        is SessionManager.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is SessionManager.State.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { sessionManager.retry() }) {
                        Text("Retry")
                    }
                }
            }
        }
        else -> {
            NavHost(
                navController = navController,
                startDestination = "login",
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    )
                }
            ) {
                composable("login") {
                    LoginScreen(onLoginSuccess = {})
                }
                composable("admin_home") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        AdminHomeScreen(
                            profile = signedInState.profile,
                            onCreateOrangtua = { navController.navigate("create_orangtua") },
                            onCreateRelawan = { navController.navigate("create_relawan") },
                            onCreateAnak = { navController.navigate("create_anak") },
                            onManageOrangtua = { navController.navigate("manage_orangtua") },
                            onManageRelawan = { navController.navigate("manage_relawan") },
                            onManageAnak = { navController.navigate("manage_anak") },
                            onLogout = {
                                scope.launch {
                                    sessionManager.signOut()
                                }
                            }
                        )
                    }
                }
                composable("orangtua_home") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        val parentVm: ParentHomeViewModel = viewModel(factory = ParentHomeViewModel.Factory)
                        val parentState by parentVm.uiState.collectAsState()
                        ParentHomeScreen(
                            profile = signedInState.profile,
                            onLogout = {
                                scope.launch {
                                    sessionManager.signOut()
                                }
                            },
                            onOpenNotifications = { navController.navigate("parent_notifications") },
                            onOpenTask = { id, date ->
                                navController.navigate("tugas_detail/$id/$date")
                            },
                            onOpenFeedbackThread = { id -> navController.navigate("feedback_thread/$id") },
                            onComposeFeedback = { navController.navigate("create_feedback") },
                            onSeeAllHistory = { navController.navigate("parent_history") },
                            occurrencesByDate = parentState.upcomingByDate,
                            weekDone = parentState.weekDone,
                            weekTotal = parentState.weekTotal,
                            today = parentState.today,
                            isLoadingTasks = parentState.isLoading,
                            childName = parentState.childName ?: "anak Anda",
                        )
                    }
                }
                composable("parent_notifications") {
                    ParentNotificationScreen(onBack = { navController.popBackStack() })
                }
                composable("parent_history") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        val parentVm: ParentHomeViewModel = viewModel(factory = ParentHomeViewModel.Factory)
                        val parentState by parentVm.uiState.collectAsState()
                        ParentHistoryScreen(
                            history = parentState.history,
                            today = parentState.today,
                            onBack = { navController.popBackStack() },
                            onOpenTask = { id, date ->
                                navController.navigate("tugas_detail/$id/$date")
                            }
                        )
                    }
                }
                composable("tugas_detail/{tugasId}/{occurrenceDate}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("tugasId")
                    val date = backStackEntry.arguments?.getString("occurrenceDate")
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (id != null && date != null) {
                        TaskDetailScreen(
                            tugasId = id,
                            occurrenceDate = date,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable("create_orangtua") {
                    CreateAccountScreen(
                        role = UserRole.ORANGTUA,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("create_relawan") {
                    CreateAccountScreen(
                        role = UserRole.RELAWAN,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("create_anak") {
                    CreateChildScreen(onBack = { navController.popBackStack() })
                }
                composable("manage_orangtua") {
                    ManageAccountsScreen(
                        role = UserRole.ORANGTUA,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate("edit_akun/$id") },
                    )
                }
                composable("manage_relawan") {
                    ManageAccountsScreen(
                        role = UserRole.RELAWAN,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate("edit_akun/$id") },
                    )
                }
                composable("manage_anak") {
                    ManageChildrenScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate("edit_anak/$id") },
                    )
                }
                composable("edit_akun/{profileId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("profileId")
                    if (id != null) {
                        EditAccountScreen(profileId = id, onBack = { navController.popBackStack() })
                    }
                }
                composable("edit_anak/{anakId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("anakId")
                    if (id != null) {
                        EditChildScreen(anakId = id, onBack = { navController.popBackStack() })
                    }
                }
                composable("parent_feedback_list") {
                    FeedbackListScreen(
                        title = "Catatan & Feedback",
                        onOpenThread = { id -> navController.navigate("feedback_thread/$id") },
                        onCompose = { navController.navigate("create_feedback") },
                    )
                }
                composable("volunteer_feedback_list") {
                    FeedbackListScreen(
                        title = "Feedback Orang Tua",
                        onOpenThread = { id -> navController.navigate("feedback_thread/$id") },
                    )
                }
                composable("create_feedback") {
                    CreateFeedbackScreen(onBack = { navController.popBackStack() })
                }
                composable("feedback_thread/{feedbackId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("feedbackId")
                    if (id != null) {
                        FeedbackThreadScreen(feedbackId = id, onBack = { navController.popBackStack() })
                    }
                }
                composable("user_home") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Welcome, ${signedInState.profile.fullName} (${signedInState.profile.role})")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { navController.navigate("create_program_ajar") }) {
                                    Text("Buat Program Ajar")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { navController.navigate("volunteer_programs") }) {
                                    Text("Program Ajar Saya")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { navController.navigate("volunteer_feedback_list") }) {
                                    Text("Feedback Orang Tua")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        sessionManager.signOut()
                                    }
                                }) {
                                    Text("Logout")
                                }
                            }
                        }
                    }
                }
                composable("volunteer_programs") {
                    ProgramListScreen(onBack = { navController.popBackStack() })
                }
                composable("create_program_ajar") {
                    CreateTaskScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
