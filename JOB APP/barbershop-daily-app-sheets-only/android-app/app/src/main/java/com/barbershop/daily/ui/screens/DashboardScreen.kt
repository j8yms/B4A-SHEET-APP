package com.barbershop.daily.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TodayData
import com.barbershop.daily.data.UserProfile
import com.barbershop.daily.worker.RefreshWorker
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(profile: UserProfile, session: Session, today: TodayData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val myAttendance = today.attendance.find { it.name == session.name }
    val isClockedIn = myAttendance?.`in` ?: false

    val isJay = session.name.equals("Jay", ignoreCase = true)
    val isNgash = session.name.equals("Ng'ash", ignoreCase = true)
    val isEstherOrCate = session.name.equals("Esther", ignoreCase = true) || session.name.equals("Cate", ignoreCase = true)
    val isBarber = isJay || isNgash

    val clientCount = if (isJay || isNgash || isEstherOrCate) {
        today.transactions.count { 
            if (isEstherOrCate) it.groomedBy.equals(session.name, ignoreCase = true)
            else it.servedBy.equals(session.name, ignoreCase = true)
        }
    } else {
        today.transactions.size
    }

    val myCommission = today.transactions
        .filter { it.servedBy.equals(session.name, ignoreCase = true) }
        .sumOf { tx ->
            when {
                tx.serviceType.equals("Dye", ignoreCase = true) -> 150.0
                tx.serviceType.equals("Shave", ignoreCase = true) -> {
                    if (isBarber) tx.amount * 0.4
                    else if (isEstherOrCate) tx.amount * 0.25
                    else tx.amount * 0.4
                }
                else -> tx.amount * 0.4
            }
        } + if (isEstherOrCate) {
            val groomingCount = today.transactions.count { 
                it.groomedBy.equals(session.name, ignoreCase = true) && !it.servedBy.equals(session.name, ignoreCase = true) 
            }
            if (isClockedIn) (if (groomingCount < 10) 500.0 else groomingCount * 60.0) else 0.0
        } else 0.0

    val myMpesaReceived = today.transactions
        .filter { it.mpesaTo.equals(session.name, ignoreCase = true) }
        .sumOf { it.amount }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Last refresh indicator
        val lastRefreshTime = remember { mutableStateOf(RefreshWorker.getLastRefreshTime(context)) }
        val lastRefreshText = remember(lastRefreshTime.value) {
            if (lastRefreshTime.value > 0) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                "Last auto-refresh: ${sdf.format(lastRefreshTime.value)}"
            } else {
                "No auto-refresh yet"
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lastRefreshText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    // Trigger manual refresh
                    val currentSession = SessionStore.load(context)
                    if (currentSession != null) {
                        scope.launch {
                            try {
                                SheetsRepository.fetchOnce(
                                    fetch = { SheetsRepository.listToday(currentSession.token) },
                                    onSuccess = { 
                                        lastRefreshTime.value = System.currentTimeMillis()
                                        RefreshWorker.storeLastRefreshTime(context, System.currentTimeMillis())
                                    },
                                    onError = { e -> Log.w("DashboardScreen", "Manual refresh failed: ${e.message}") }
                                )
                            } catch (e: Throwable) {
                                Log.w("DashboardScreen", "Manual refresh error: ${e.message}")
                            }
                        }
                    }
                }) {
                    Text("Refresh Now")
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "Hi, ${profile.displayName}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Please record all entries as soon as possible to keep our records accurate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        if (isJay || isNgash || isEstherOrCate) {
            Spacer(Modifier.height(24.dp))
            Text("Personal Earnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    label = if (isJay || isNgash) "Your Share" else "Today's Earnings",
                    value = if (isClockedIn) "KES ${myCommission.toInt()}" else "_",
                    icon = Icons.Default.Payments,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "M-pesa to You",
                    value = if (isClockedIn) "KES ${myMpesaReceived.toInt()}" else "_",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Row of Stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                label = if (isJay) "My Clients Today" else if (isEstherOrCate) "My Clients" else "Total Shop Clients",
                value = if (isClockedIn || (!isJay && !isNgash && !isEstherOrCate)) clientCount.toString() else "_",
                icon = Icons.Default.People,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isEstherOrCate) {
            val cateCount = today.transactions.count { it.groomedBy.equals("Cate", ignoreCase = true) }
            val estherCount = today.transactions.count { it.groomedBy.equals("Esther", ignoreCase = true) }
            
            Spacer(Modifier.height(24.dp))
            Text("Competition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    label = "Cate's Count",
                    value = cateCount.toString(),
                    icon = Icons.Default.Person,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Esther's Count",
                    value = estherCount.toString(),
                    icon = Icons.Default.Person,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Shift Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    val displayTime = remember(myAttendance?.time) {
                        val t = myAttendance?.time ?: ""
                        if (t.contains("1899")) {
                            // Try to extract time if present, else just "IN"
                            val timePart = t.split(" ").firstOrNull { it.contains(":") }
                            if (timePart != null) timePart else "IN"
                        } else t
                    }
                    Text("My Shift", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isClockedIn) displayTime else "OFF",
                        color = if (isClockedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val res = SheetsRepository.clockIn(session.token)
                                    snackbar.showSnackbar("Clocked In: ${res.optString("time")}")
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(t.message ?: "Failed")
                                }
                            }
                        },
                        enabled = !isClockedIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Clock In", color = Color.Black)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val res = SheetsRepository.clockIn(session.token)
                                    snackbar.showSnackbar("Clocked Out: ${res.optString("time")}")
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(t.message ?: "Failed")
                                }
                            }
                        },
                        enabled = isClockedIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clock Out")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Staff Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        today.attendance.filter { it.name.isNotBlank() && !it.name.equals("Admin", ignoreCase = true) }.forEach { entry ->
            val isEditing = today.transactions.any { it.lastEditor == entry.name }
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(entry.name, fontWeight = FontWeight.Medium)
                        if (isEditing) {
                            Text("Currently editing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Text(
                        if (entry.`in`) "IN" else "OFF",
                        color = if (entry.`in`) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbar)
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.surfaceVariant, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
