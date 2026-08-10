package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.FirebaseRepository
import com.barbershop.daily.data.UserProfile

private enum class Tab(val label: String) {
    Attendance("Attendance"),
    Money("Money"),
    Admin("Admin")
}

@Composable
fun HomeScreen(onSignOut: () -> Unit) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            profile = FirebaseRepository.loadMyProfile()
        } catch (t: Throwable) {
            error = t.message ?: "Failed to load profile"
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (error != null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Error", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSignOut) { Text("Sign out") }
        }
        return
    }

    val isAdmin = profile?.role == "admin"
    val tabs = remember(isAdmin) {
        if (isAdmin) listOf(Tab.Attendance, Tab.Money, Tab.Admin) else listOf(Tab.Attendance, Tab.Money)
    }

    var selectedTab by remember { mutableStateOf(Tab.Attendance) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hi, ${profile?.displayName ?: ""}") },
                actions = {
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        icon = {}
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                Tab.Attendance -> AttendanceScreen(profile!!)
                Tab.Money -> TransactionsScreen(profile!!)
                Tab.Admin -> if (isAdmin) AdminScreen() else Unit
            }
        }
    }
}
