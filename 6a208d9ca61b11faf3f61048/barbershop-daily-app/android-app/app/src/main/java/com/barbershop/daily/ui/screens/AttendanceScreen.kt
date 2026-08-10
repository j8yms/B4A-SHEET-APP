package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.AttendanceEntry
import com.barbershop.daily.data.FirebaseRepository
import com.barbershop.daily.data.UserProfile
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

private val allStaffNames = listOf("Ng'ash", "Jay", "Esther", "Cate")

@Composable
fun AttendanceScreen(profile: UserProfile) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val attendance by FirebaseRepository.watchTodayAttendance().collectAsState(initial = emptyList())

    val byName = remember(attendance) { attendance.associateBy { it.displayName } }

    val tz = remember { ZoneId.of("Africa/Nairobi") }
    val isAfterClose = java.time.ZonedDateTime.now(tz).toLocalTime().isAfter(LocalTime.of(21, 0))

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    try {
                        val res = FirebaseRepository.clockIn()
                        snackbar.showSnackbar("Clocked in: ${res["arrival"]} at ${res["time"]}")
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

        LazyColumn(Modifier.weight(1f)) {
            items(allStaffNames) { name ->
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
