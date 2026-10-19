package com.barbershop.daily

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.barbershop.daily.data.BiometricHelper
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.ui.screens.HomeScreen
import com.barbershop.daily.ui.screens.LoginScreen
import com.barbershop.daily.ui.theme.BarbershopTheme
import com.barbershop.daily.worker.RefreshWorker
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Schedule daily refreshes on app startup
        RefreshWorker.scheduleRefreshes(this)
        setContent {
            BarbershopTheme(darkTheme = false) {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val nav = rememberNavController()

    // Check for existing session on startup
    val session by remember { mutableStateOf<com.barbershop.daily.data.Session?>(SessionStore.load(context)) }
    var signedIn by remember { mutableStateOf(session != null) }
    var loadingSession by remember { mutableStateOf(session == null) } // loading if no cached session yet

    // If no cached session, check if we should auto-restore (not needed since we load synchronously)
    // But we can validate the session with a quick server call
    LaunchedEffect(Unit) {
        if (session != null) {
            loadingSession = true
            try {
                // Validate session with a lightweight call
                SheetsRepository.listToday(session!!.token)
                signedIn = true
            } catch (e: Throwable) {
                // Session invalid/expired/revoked - clear and require login
                SessionStore.clearSession(context)
                signedIn = false
            } finally {
                loadingSession = false
            }
        } else {
            loadingSession = false
        }
    }

    // Simple auth gating
    LaunchedEffect(signedIn, loadingSession) {
        if (!loadingSession) {
            if (signedIn) nav.navigate("home") { popUpTo("login") { inclusive = true } }
            else nav.navigate("login") { popUpTo(0) }
        }
    }

    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onSignedIn = {
                    signedIn = true
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                }
            )
        }
        composable("home") {
            HomeScreen(onSignOut = {
                val currentSession = SessionStore.load(context)
                if (currentSession != null) {
                    // Revoke session on server - fire and forget
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            SheetsRepository.logout(currentSession.token)
                        } catch (_: Throwable) {
                            // Ignore logout errors
                        }
                    }
                }
                SessionStore.clearSession(context)
                signedIn = false
            })
        }
    }
}
