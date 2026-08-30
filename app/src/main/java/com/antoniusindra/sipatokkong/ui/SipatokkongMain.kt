package com.antoniusindra.sipatokkong.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.push.PUSH_LOG_TAG
import kotlinx.coroutines.flow.first
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.admin.CreateAccountScreen
import com.antoniusindra.sipatokkong.ui.admin.CreateChildScreen
import com.antoniusindra.sipatokkong.ui.admin.EditAccountScreen
import com.antoniusindra.sipatokkong.ui.admin.EditChildScreen
import com.antoniusindra.sipatokkong.ui.admin.ManageAccountsScreen
import com.antoniusindra.sipatokkong.ui.admin.ManageChildrenScreen
import com.antoniusindra.sipatokkong.ui.admin.RekapAbsensiScreen
import com.antoniusindra.sipatokkong.ui.auth.login.LoginScreen
import com.antoniusindra.sipatokkong.ui.components.NotificationPermissionEffect
import com.antoniusindra.sipatokkong.ui.feedback.CreateFeedbackScreen
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackListScreen
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackThreadScreen
import com.antoniusindra.sipatokkong.ui.home.AdminHomeScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHistoryScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHomeScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHomeViewModel
import com.antoniusindra.sipatokkong.ui.notifications.NotificationScreen
import com.antoniusindra.sipatokkong.ui.task.TaskDetailScreen
import com.antoniusindra.sipatokkong.ui.volunteer.CreatePresensiScreen
import com.antoniusindra.sipatokkong.ui.volunteer.CreateTaskScreen
import com.antoniusindra.sipatokkong.ui.volunteer.ProgramListScreen
import com.antoniusindra.sipatokkong.ui.volunteer.TemplateListScreen
import com.antoniusindra.sipatokkong.ui.volunteer.VolunteerHomeScreen
import com.antoniusindra.sipatokkong.ui.volunteer.VolunteerHomeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SipatokkongMain(
    /** A route a tapped notification asked for, or null. Defaulted so this stays additive. */
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val sessionManager = ServiceLocator.sessionManager
    val sessionState by sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    val resolved = SessionRouting.startRoute(sessionState)

    // Set once and never again. NavHost remembers its graph keyed on startDestination, so letting
    // this change would rebuild the graph and reset the back stack behind Navigation's back. Every
    // later root change goes through navigate() below instead.
    var graphStart by rememberSaveable { mutableStateOf<String?>(null) }

    // The root the graph is currently showing. Seeded from graphStart on the first resolved frame,
    // because the NavHost already starts there — navigating to it again would rebuild the home
    // screen and, with popUpTo(0), discard a back stack the user may be three screens deep in.
    // That is what made backgrounding the app on a task detail come back to the dashboard: the old
    // effect fired on every SignedIn *emission*, not on transitions.
    var routedRoot by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(resolved) {
        val target = resolved ?: return@LaunchedEffect
        if (graphStart == null) {
            graphStart = target
            routedRoot = target
            return@LaunchedEffect
        }
        if (target == routedRoot) return@LaunchedEffect
        routedRoot = target
        // popUpTo(0) stays, and is still right: this now runs only on a genuine root change — a
        // sign-out, or someone else signing in on a shared phone — where the previous user's stack
        // must go.
        navController.navigate(target) { popUpTo(0) { inclusive = true } }
    }

    // Declared AFTER the root effect on purpose. That one navigates with popUpTo(0) { inclusive =
    // true } on every session change, so a push destination applied first would simply be wiped.
    // Running second means the push route lands on top of the role's home screen — which is also
    // the back behaviour we want: Back from the thread returns home, not to a blank stack.
    // Waits for the NavHost to actually own a graph before navigating. That wait is the fix for a
    // crash this shipped with: tapping a notification while the app was closed opened it for two
    // seconds and killed it with "Navigation graph has not been set".
    //
    // The NavHost below composes only once `graphStart` is non-null, and its composition is where
    // setGraph happens. On a cold start the order was: compose with graphStart still null so no
    // NavHost exists, then effects run, then this one calls navigate() on a graphless controller.
    //
    // The obvious repair -- add `graphStart` as a key and return early while it is null -- was
    // tried, shipped to a device, and DID NOT WORK. Two reasons, both worth recording because both
    // are easy to talk yourself into:
    //
    //   1. Reading `graphStart` inside the coroutine reads the LIVE value, not the key the effect
    //      was launched with. LaunchedEffect(resolved) assigns it during the same effect pass, so
    //      by the time this body ran the guard already saw non-null while no NavHost had composed.
    //   2. Even a composition-time snapshot would only be reasoning about effect ORDERING, and
    //      whether NavHost installs its graph during composition or in an effect of its own is an
    //      implementation detail of a library that is free to change it.
    //
    // currentBackStackEntryFlow settles both: it emits only once a graph is set and a destination
    // is current, and emits immediately when that is already true. So this cannot run early no
    // matter how the frames fall, and it costs nothing on the warm path.
    //
    // The warm path never had the bug -- a push tapped while the app is open arrives through
    // onNewIntent, long after the graph exists.
    LaunchedEffect(pendingRoute, sessionState) {
        val route = pendingRoute ?: return@LaunchedEffect
        when (sessionState) {
            is SessionManager.State.SignedIn -> {
                navController.currentBackStackEntryFlow.first()
                // Defensive, and deliberately NOT the fix -- the wait above is. This route is built
                // from server data (`entity_table`/`entity_id`, via NotifDisplay.targetOf), and a
                // destination this build does not contain must not kill the app of someone who did
                // nothing but tap a notification. Logged loudly, because a swallowed navigation is
                // a silent dead tap: that is exactly how the wait above was found to be missing.
                runCatching { navController.navigate(route) }
                    .onFailure { Log.w(PUSH_LOG_TAG, "rute push '$route' gagal dibuka: ${it.message}") }
                onRouteConsumed()
            }
            // Don't hold it: a push tapped while signed out must not ambush whoever logs in next,
            // possibly a different user on a shared phone.
            is SessionManager.State.SignedOut -> onRouteConsumed()
            // Loading: keep the route. This is what makes a cold start work — the effect re-runs
            // when the session resolves.
            else -> {}
        }
    }

    // Only once someone is signed in and looking at their own home — never at launch, where a
    // permission dialog arrives before the user knows what the app is and gets denied by reflex.
    if (sessionState is SessionManager.State.SignedIn) {
        NotificationPermissionEffect()
    }

    val start = graphStart
    if (start == null) {
        // Only reachable BEFORE the session first resolves. After that SessionTransition never
        // returns to Loading from SignedIn, so the NavHost — once composed — is never torn out of
        // composition again. That is the fix for backgrounding the app destroying the back stack.
        //
        // Normally invisible: the system splash is held over this until the session resolves. Still
        // reachable when that hold times out, which is why it keeps a way forward.
        when (val state = sessionState) {
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
            // No `else`: the bug this rewrite fixes was SignedIn falling into one. A fifth state
            // should be a compile error, not a blank screen.
            is SessionManager.State.Loading,
            is SessionManager.State.SignedIn,
            is SessionManager.State.SignedOut -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    } else {
            NavHost(
                navController = navController,
                startDestination = start,
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
                            onRekapAbsensi = { navController.navigate("rekap_absensi") },
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
                        // Re-runs when this destination re-enters composition after popping back
                        // from the task detail screen, so a just-completed task updates at once.
                        LaunchedEffect(Unit) { parentVm.refresh() }
                        ParentHomeScreen(
                            profile = signedInState.profile,
                            onLogout = {
                                scope.launch {
                                    sessionManager.signOut()
                                }
                            },
                            onOpenNotifications = { navController.navigate("parent_notifications") },
                            onOpenTask = { id, date ->
                                navController.navigate("tugas_detail/$id?occurrenceDate=$date")
                            },
                            onOpenFeedbackThread = { id -> navController.navigate("feedback_thread/$id") },
                            onComposeFeedback = { navController.navigate("create_feedback") },
                            onSeeAllHistory = { navController.navigate("parent_history") },
                            occurrencesByDate = parentState.visibleByDate,
                            weekDone = parentState.weekDone,
                            weekTotal = parentState.weekTotal,
                            today = parentState.today,
                            isLoadingTasks = parentState.isLoading,
                            childName = parentState.childName ?: "anak Anda",
                            unreadNotifications = parentState.unreadNotifications,
                        )
                    }
                }
                composable("parent_notifications") {
                    NotificationScreen(
                        onBack = { navController.popBackStack() },
                        onOpenFeedbackThread = { id -> navController.navigate("feedback_thread/$id") },
                        onOpenTask = { id -> navController.navigate("tugas_detail/$id") },
                    )
                }
                composable("parent_history") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        val parentVm: ParentHomeViewModel = viewModel(factory = ParentHomeViewModel.Factory)
                        val parentState by parentVm.uiState.collectAsState()
                        LaunchedEffect(Unit) { parentVm.refresh() }
                        ParentHistoryScreen(
                            history = parentState.history,
                            today = parentState.today,
                            onBack = { navController.popBackStack() },
                            onOpenTask = { id, date ->
                                navController.navigate("tugas_detail/$id?occurrenceDate=$date")
                            }
                        )
                    }
                }
                // `occurrenceDate` is optional, following the same idiom as create_program_ajar
                // below, and for the same kind of reason: one destination has to match two callers
                // that know different amounts. A screen or history row navigates here off a
                // rendered TaskOccurrence and has a date in hand; a notification tap has only the
                // task id, because `notifications` has no date column and a push cannot look one
                // up (see PushPayload.routeFor). Rather than two routes that could drift, the
                // screen accepts null and resolves a date from the task's own recurrence rule.
                composable(
                    route = "tugas_detail/{tugasId}?occurrenceDate={occurrenceDate}",
                    arguments = listOf(
                        navArgument("tugasId") { type = NavType.StringType },
                        navArgument("occurrenceDate") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("tugasId")
                    // Null on the notification path by design, and an unparseable value is treated
                    // the same rather than rendering nothing -- which is what the old two-segment
                    // route did, leaving a blank screen with no explanation.
                    val date = backStackEntry.arguments?.getString("occurrenceDate")
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (id != null) {
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
                composable("create_feedback") {
                    CreateFeedbackScreen(onBack = { navController.popBackStack() })
                }
                composable("feedback_thread/{feedbackId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("feedbackId")
                    if (id != null) {
                        FeedbackThreadScreen(feedbackId = id, onBack = { navController.popBackStack() })
                    }
                }
                composable("relawan_home") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        val volunteerVm: VolunteerHomeViewModel =
                            viewModel(factory = VolunteerHomeViewModel.Factory)
                        val volunteerState by volunteerVm.uiState.collectAsState()
                        // Re-runs on re-entry, so the counts pick up a program just created.
                        LaunchedEffect(Unit) { volunteerVm.refresh() }
                        VolunteerHomeScreen(
                            profile = signedInState.profile,
                            ui = volunteerState,
                            onCreateProgram = { navController.navigate("create_program_ajar") },
                            onOpenPrograms = { navController.navigate("volunteer_programs") },
                            onOpenTemplates = { navController.navigate("volunteer_templates") },
                            onOpenChild = { id ->
                                navController.navigate("create_program_ajar?anakId=$id")
                            },
                            onCatatKunjungan = { navController.navigate("create_presensi") },
                            onOpenNotifications = { navController.navigate("relawan_notifications") },
                            onOpenFeedbackThread = { id -> navController.navigate("feedback_thread/$id") },
                            onRetry = { volunteerVm.refresh() },
                            onLogout = {
                                scope.launch {
                                    sessionManager.signOut()
                                }
                            },
                        )
                    }
                }
                composable("relawan_notifications") {
                    NotificationScreen(
                        onBack = { navController.popBackStack() },
                        onOpenFeedbackThread = { id -> navController.navigate("feedback_thread/$id") },
                        // No onOpenTask, and not an oversight: on_tugas_insert writes the row to
                        // the child's `orangtua_id`, so no volunteer can hold a `tugas`
                        // notification. Wiring one would point a volunteer at a parent-only screen.
                        //
                        // Only feedback_baru ever reaches a relawan, so name that and nothing else.
                        emptyText = "Catatan baru dari orang tua akan muncul di sini.",
                    )
                }
                composable("volunteer_programs") {
                    ProgramListScreen(
                        onBack = { navController.popBackStack() },
                        onCreateProgram = { navController.navigate("create_program_ajar") },
                    )
                }
                composable("volunteer_templates") {
                    TemplateListScreen(
                        onBack = { navController.popBackStack() },
                        onUseTemplate = { id ->
                            navController.navigate("create_program_ajar?templateId=$id")
                        },
                        onCreateProgram = { navController.navigate("create_program_ajar") },
                    )
                }
                // Both arguments optional, so the plain navigate("create_program_ajar") callers
                // still match this route unchanged and open an empty form.
                composable(
                    route = "create_program_ajar?templateId={templateId}&anakId={anakId}",
                    arguments = listOf(
                        navArgument("templateId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("anakId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStackEntry ->
                    CreateTaskScreen(
                        onBack = { navController.popBackStack() },
                        templateId = backStackEntry.arguments?.getString("templateId"),
                        anakId = backStackEntry.arguments?.getString("anakId"),
                    )
                }
                composable("rekap_absensi") {
                    RekapAbsensiScreen(onBack = { navController.popBackStack() })
                }
                composable("create_presensi") {
                    CreatePresensiScreen(onBack = { navController.popBackStack() })
                }
            }
    }
}
