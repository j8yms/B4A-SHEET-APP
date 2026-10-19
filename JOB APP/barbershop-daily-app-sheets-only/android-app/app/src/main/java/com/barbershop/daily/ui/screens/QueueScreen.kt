package com.barbershop.daily.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.QueueData
import com.barbershop.daily.data.QueueEntry
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SheetsRepository
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(session: Session) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var data by remember { mutableStateOf(QueueData()) }

    var clientName by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    var startTarget by remember { mutableStateOf<QueueEntry?>(null) }
    var startBarber by remember { mutableStateOf("") }

    LaunchedEffect(session.token) {
        SheetsRepository.pollEvery(
            intervalMs = 5000,
            fetch = { SheetsRepository.listQueue(session.token) },
            onValue = { data = it }
        )
    }

    if (startTarget != null) {
        val freeBarbers = data.barbers.filter { it.free }
        AlertDialog(
            onDismissRequest = { startTarget = null },
            title = { Text("Start service") },
            text = {
                Column {
                    Text("Assign ${startTarget?.clientName} to a barber.")
                    Spacer(Modifier.height(8.dp))
                    if (data.barbers.isEmpty()) {
                        Text("No barbers available yet.", color = MaterialTheme.colorScheme.error)
                    } else {
                        data.barbers.forEach { b ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = startBarber == b.name,
                                    onClick = { startBarber = b.name }
                                )
                                Text(b.name)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (b.free) "Free" else "Busy",
                                    color = if (b.free) Color(0xFF00B050) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (freeBarbers.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("No barber is currently free.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = startBarber.isNotBlank(),
                    onClick = {
                        val target = startTarget!!
                        val barber = startBarber
                        startTarget = null
                        startBarber = ""
                        scope.launch {
                            try {
                                SheetsRepository.startQueueService(session.token, target.qId, barber)
                                data = SheetsRepository.listQueue(session.token)
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Failed")
                            }
                        }
                    }
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { startTarget = null }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue & Bookings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (data.connected) {
                AssistChip(
                    onClick = {},
                    label = { Text("Website linked") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp)) }
                )
            }
        }
        Text(
            if (data.connected) "Synced with your booking sheet" else "Local queue (connect a sheet in Settings to link the website)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(16.dp))

        // Barber availability
        Text("Barbers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (data.barbers.isEmpty()) {
            Text("No barbers listed yet.", color = MaterialTheme.colorScheme.secondary)
        } else {
            data.barbers.forEach { barber ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (barber.free) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    color = if (barber.free) Color(0xFF00B050) else MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(5.dp)
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(barber.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            if (barber.free) "FREE" else "BUSY",
                            color = if (barber.free) Color(0xFF00B050) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        SheetsRepository.setBarberFree(session.token, barber.name, !barber.free)
                                        data = SheetsRepository.listQueue(session.token)
                                    } catch (t: Throwable) {
                                        snackbar.showSnackbar(t.message ?: "Failed")
                                    }
                                }
                            }
                        ) {
                            Text(if (barber.free) "Mark Busy" else "Mark Free")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Add person to line
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Add to line", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Client name") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = service,
                    onValueChange = { service = it },
                    label = { Text("Service (e.g. Shave)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !adding && clientName.isNotBlank(),
                    onClick = {
                        adding = true
                        scope.launch {
                            try {
                                SheetsRepository.addToQueue(
                                    token = session.token,
                                    clientName = clientName.trim(),
                                    service = service.trim(),
                                    phone = phone.trim(),
                                    note = note.trim()
                                )
                                clientName = ""
                                service = ""
                                phone = ""
                                note = ""
                                data = SheetsRepository.listQueue(session.token)
                                snackbar.showSnackbar("Added to line")
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Failed")
                            } finally {
                                adding = false
                            }
                        }
                    }
                ) {
                    if (adding) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Queue list
        Text("People in line (${data.queue.count { it.status != "done" }})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val activeQueue = data.queue.filter { it.status != "done" }
        if (activeQueue.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("No one in line right now.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(activeQueue) { entry ->
                    QueueItem(
                        entry = entry,
                        onStart = {
                            startBarber = ""
                            startTarget = entry
                        },
                        onDone = {
                            scope.launch {
                                try {
                                    SheetsRepository.completeQueue(session.token, entry.qId)
                                    data = SheetsRepository.listQueue(session.token)
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(t.message ?: "Failed")
                                }
                            }
                        },
                        onRemove = {
                            scope.launch {
                                try {
                                    SheetsRepository.removeFromQueue(session.token, entry.qId)
                                    data = SheetsRepository.listQueue(session.token)
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(t.message ?: "Failed")
                                }
                            }
                        }
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbar)
    }
}

@Composable
private fun QueueItem(
    entry: QueueEntry,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.clientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when (entry.status) {
                    "in-progress" -> AssistChip(
                        onClick = {},
                        label = { Text(if (entry.barber.isNotBlank()) "With ${entry.barber}" else "In progress") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp)) }
                    )
                    else -> Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "WAITING",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (entry.service.isNotBlank() || entry.phone.isNotBlank() || entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(entry.service, entry.phone, entry.note).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.addedAt.isNotBlank()) {
                Text(entry.addedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.status == "waiting") {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                } else {
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done")
                    }
                }
                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}
