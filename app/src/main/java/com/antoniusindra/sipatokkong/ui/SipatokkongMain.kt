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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.admin.CreateAccountScreen
import com.antoniusindra.sipatokkong.ui.auth.login.LoginScreen
import com.antoniusindra.sipatokkong.ui.home.AdminHomeScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHomeScreen
import kotlinx.coroutines.launch

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
                        ParentHomeScreen(
                            profile = signedInState.profile,
                            onLogout = {
                                scope.launch {
                                    sessionManager.signOut()
                                }
                            }
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
                composable("user_home") {
                    val signedInState = sessionState as? SessionManager.State.SignedIn
                    if (signedInState != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Welcome, ${signedInState.profile.fullName} (${signedInState.profile.role})")
                                Spacer(modifier = Modifier.height(16.dp))
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
            }
        }
    }
}
