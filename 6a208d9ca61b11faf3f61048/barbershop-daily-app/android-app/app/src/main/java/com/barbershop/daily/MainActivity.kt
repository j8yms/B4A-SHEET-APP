package com.barbershop.daily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.barbershop.daily.data.FirebaseRepository
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
    val nav = rememberNavController()
    val signedIn by FirebaseRepository.authState().collectAsState(initial = false)

    // Simple auth gating
    LaunchedEffect(signedIn) {
        if (signedIn) nav.navigate("home") { popUpTo("login") { inclusive = true } }
        else nav.navigate("login") { popUpTo(0) }
    }

    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onSignedIn = { nav.navigate("home") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("home") {
            HomeScreen(onSignOut = {
                FirebaseRepository.signOut()
            })
        }
    }

    if (signedIn && FirebaseRepository.currentEmail() == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

