package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import android.util.Log
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TodayData
import com.barbershop.daily.data.TransactionEntry
import com.barbershop.daily.data.UserProfile
import kotlinx.coroutines.launch

import androidx.compose.ui.text.style.TextOverflow

val staffColors = mapOf(
    "Ng'ash" to Color(0xFFE91E63), // Pink
    "Jay" to Color(0xFF39FF14),     // Neon Green
    "Esther" to Color(0xFF6200EA), // Deep Purple
    "Cate" to Color(0xFFFF5722),   // Orange
)

fun getStaffColor(name: String): Color {
    return staffColors.entries.find { it.key.equals(name, ignoreCase = true) }?.value ?: Color.Unspecified
}

@Composable
fun HistoryScreen(
    profile: UserProfile,
    session: Session,
    onEdit: (TransactionEntry) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var today by remember { mutableStateOf(TodayData()) }
    val txs = today.transactions

    var txToDelete by remember { mutableStateOf<TransactionEntry?>(null) }

    // Poll today's records so deletes/edits appear immediately.
    LaunchedEffect(session.token) {
        SheetsRepository.pollEvery(
            intervalMs = 5000,
            fetch = { SheetsRepository.listToday(session.token) },
            onValue = { today = it },
            onError = { e -> Log.w("HistoryScreen", "Poll error: ${e.message}") }
        )
    }

    if (txToDelete != null) {
        AlertDialog(
            onDismissRequest = { txToDelete = null },
            title = { Text("Delete Record") },
            text = { Text("Are you sure you want to delete the record for ${txToDelete?.clientName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tx = txToDelete!!
                        txToDelete = null
                        scope.launch {
                            try {
                                SheetsRepository.deleteTx(
                                    token = session.token,
                                    txId = tx.txId,
                                    clientName = tx.clientName,
                                    amount = tx.amount,
                                    createdBy = tx.createdBy
                                )
                                snackbar.showSnackbar("Deleted record")
                                today = SheetsRepository.listToday(session.token)
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Failed to delete")
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { txToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Service History", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text("Records for ${today.tabName}", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        if (txs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No services recorded yet today.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Client", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Barber", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Pay", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Amt", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Groomer", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(56.dp)) // space for actions
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(Modifier.fillMaxSize()) {
                items(txs.reversed()) { tx ->
                    TransactionItem(
                        tx = tx,
                        canManage = tx.createdBy == session.name || session.role == "admin",
                        onEdit = { onEdit(tx) },
                        onDelete = { txToDelete = tx }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
        SnackbarHost(snackbar)
    }
}

@Composable
fun TransactionItem(
    tx: TransactionEntry,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tx.clientName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (tx.txType == "sale") tx.serviceType else tx.servedBy,
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (tx.txType == "sale") MaterialTheme.colorScheme.secondary else getStaffColor(tx.servedBy),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when(tx.payMethod) {
                    "Cash" -> "Csh"
                    "M-pesa" -> "Mps"
                    "Withdraw" -> "Wd"
                    else -> tx.payMethod.take(3)
                },
                modifier = Modifier.width(42.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = tx.amount.toInt().toString(),
                modifier = Modifier.width(42.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = tx.groomedBy,
                modifier = Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodySmall,
                color = getStaffColor(tx.groomedBy),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(52.dp))
            }
        }

        // Timestamp row
        if (tx.createdAt.isNotBlank() || tx.lastEditor.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tx.createdAt.isNotBlank()) {
                    Text(
                        text = "Created: ${formatTimestamp(tx.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (tx.lastEditor.isNotBlank()) {
                    Text(
                        text = tx.lastEditor,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(iso: String): String {
    // Input format: "yyyy-MM-dd HH:mm:ss" in Africa/Nairobi
    // Display format: "19 Oct 2026, 08:42"
    try {
        val parts = iso.split(" ")
        if (parts.size >= 2) {
            val datePart = parts[0]
            val timePart = parts[1].substring(0, 5) // HH:mm
            val dateParts = datePart.split("-")
            if (dateParts.size == 3) {
                val year = dateParts[0]
                val month = dateParts[1].toIntOrNull() ?: 1
                val day = dateParts[2].toIntOrNull() ?: 1
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                return "${day} ${monthNames[month - 1]} $year, $timePart"
            }
        }
    } catch (_: Throwable) {}
    return iso // fallback
}
