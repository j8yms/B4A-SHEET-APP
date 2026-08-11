package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TodayData
import com.barbershop.daily.data.TransactionEntry
import com.barbershop.daily.data.UserProfile

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Add("Add", Icons.Default.Add),
    History("History", Icons.Default.List),
    Queue("Queue", Icons.Default.Queue),
    Reports("Reports", Icons.Default.Info),
    Admin("Admin", Icons.Default.Settings),
    Profile("Profile", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSignOut: () -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var today by remember { mutableStateOf(TodayData()) }

    var selectedTab by remember { mutableStateOf(Tab.Home) }
    var editingTransaction by remember { mutableStateOf<TransactionEntry?>(null) }

    LaunchedEffect(Unit) {
        try {
            val loadedSession = SessionStore.load(context)
            if (loadedSession == null) {
                onSignOut()
                return@LaunchedEffect
            }
            session = loadedSession
            profile = UserProfile(
                displayName = loadedSession.name,
                role = loadedSession.role
            )

            today = SheetsRepository.listToday(loadedSession.token)
            loading = false

            SheetsRepository.pollEvery(
                intervalMs = 10000,
                fetch = { SheetsRepository.listToday(loadedSession.token) },
                onValue = { today = it }
            )
        } catch (t: Throwable) {
            error = t.message ?: "Failed to load"
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
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Error", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSignOut) { Text("Sign out") }
        }
        return
    }

    val isAdmin = profile?.role == "admin"
    val isClockedIn = today.attendance.find { it.name == session?.name }?.`in` ?: false

    // Hide Admin tab for regular staff
    val visibleTabs = Tab.values().filter { it != Tab.Admin || isAdmin }

    Scaffold(
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { tab ->
                    val isLocked = !isClockedIn && !isAdmin && (tab == Tab.Add || tab == Tab.History)
                    
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        enabled = !isLocked,
                        onClick = { 
                            if (tab != Tab.Add) editingTransaction = null
                            selectedTab = tab 
                        },
                        label = { Text(tab.label) },
                        icon = { 
                            BadgedBox(badge = {
                                if (isLocked) {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(10.dp))
                                }
                            }) {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                Tab.Home -> DashboardScreen(profile!!, session!!, today)
                Tab.Add -> {
                    if (isClockedIn || isAdmin) {
                        key(editingTransaction?.txId) {
                            AddServiceScreen(
                                profile = profile!!,
                                session = session!!,
                                today = today,
                                editingTx = editingTransaction,
                                onDone = {
                                    editingTransaction = null
                                    selectedTab = Tab.History
                                }
                            )
                        }
                    } else {
                        selectedTab = Tab.Home
                    }
                }
                Tab.History -> {
                    if (isClockedIn || isAdmin) {
                        HistoryScreen(
                            profile = profile!!,
                            session = session!!,
                            onEdit = { tx ->
                                editingTransaction = tx
                                selectedTab = Tab.Add
                            }
                        )
                    } else {
                        selectedTab = Tab.Home
                    }
                }
                Tab.Queue -> QueueScreen(session!!)
                Tab.Reports -> ReportsScreen(session!!, today)
                Tab.Admin -> AdminScreen(session = session!!, today = today)
                Tab.Profile -> ProfileScreen(profile!!, session!!, isAdmin, onSignOut)
            }
        }
    }
}
