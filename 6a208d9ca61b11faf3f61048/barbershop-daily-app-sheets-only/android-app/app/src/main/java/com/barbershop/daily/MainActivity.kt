package com.barbershop.daily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.ui.screens.HomeScreen
import com.barbershop.daily.ui.screens.LoginScreen
import com.barbershop.daily.ui.theme.BarbershopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var signedIn by remember { mutableStateOf(SessionStore.load(context) != null) }

    // Simple auth gating
    LaunchedEffect(signedIn) {
        if (signedIn) nav.navigate("home") { popUpTo("login") { inclusive = true } }
        else nav.navigate("login") { popUpTo(0) }
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
                SessionStore.clear(context)
                signedIn = false
            })
        }
    }
    // No extra loading overlay required for Sheets-only auth.
}
