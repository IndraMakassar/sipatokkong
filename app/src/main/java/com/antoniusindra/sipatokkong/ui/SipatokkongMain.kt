package com.antoniusindra.sipatokkong.ui

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
import com.antoniusindra.sipatokkong.ui.auth.login.LoginScreen
import kotlinx.coroutines.launch

@Composable
fun SipatokkongMain() {
    val navController = rememberNavController()
    val sessionManager = ServiceLocator.sessionManager
    val sessionState by sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    when (val state = sessionState) {
        is SessionManager.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is SessionManager.State.SignedOut -> {
            NavHost(navController = navController, startDestination = "login") {
                composable("login") {
                    LoginScreen(
                        onLoginSuccess = {
                            // SessionManager will automatically update state to SignedIn
                        }
                    )
                }
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
        is SessionManager.State.SignedIn -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Welcome, ${state.profile.fullName} (${state.profile.role})")
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
