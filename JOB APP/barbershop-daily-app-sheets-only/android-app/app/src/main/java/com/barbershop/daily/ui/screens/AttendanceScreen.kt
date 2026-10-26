package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.AttendanceEntry
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TodayData
import com.barbershop.daily.data.UserProfile
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

private val fallbackStaff = listOf("Ng'ash", "Jay", "Esther")

@Composable
fun AttendanceScreen(profile: UserProfile, session: Session) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var today by remember { mutableStateOf(TodayData()) }

    LaunchedEffect(session.token) {
        SheetsRepository.pollEvery(
            intervalMs = 5000,
            fetch = { SheetsRepository.listToday(session.token) },
            onValue = { today = it }
        )
    }

    val byName = remember(today.attendance) { today.attendance.associateBy { it.name } }

    val isAfterClose = remember {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        hour > 21 || (hour == 21 && minute >= 0)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    try {
                        val res = SheetsRepository.clockIn(session.token)
                        snackbar.showSnackbar("Clocked in: ${res.optString("arrival")} at ${res.optString("time")}")
                    } catch (t: Throwable) {
                        snackbar.showSnackbar(t.message ?: "Clock in failed")
                    }
                }
            }
        ) {
            Text("Clock In")
        }

        Spacer(Modifier.height(16.dp))
        Text("Staff status", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val staffList = remember(today.attendance) {
            val names = today.attendance.map { it.name.trim() }.filter { com.barbershop.daily.data.isRealStaffName(it) }
            if (names.isNotEmpty()) names else fallbackStaff
        }

        LazyColumn(Modifier.weight(1f)) {
            items(staffList) { name ->
                val entry: AttendanceEntry? = byName[name]
                val status = when {
                    entry != null -> entry.arrival.ifBlank { "IN" }
                    isAfterClose -> "ABSENT"
                    else -> "Not yet clocked in"
                }
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            if (entry?.time?.isNotBlank() == true) {
                                Text("Time: ${entry.time}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(
                            status,
                            color = when {
                                status.startsWith("LATE") -> MaterialTheme.colorScheme.error
                                status == "ABSENT" -> MaterialTheme.colorScheme.error
                                status == "ON TIME" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar)
    }
}
