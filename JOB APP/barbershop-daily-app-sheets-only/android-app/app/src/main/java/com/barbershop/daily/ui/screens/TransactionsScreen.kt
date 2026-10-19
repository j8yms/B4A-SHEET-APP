package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.util.Log
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TodayData
import com.barbershop.daily.data.TransactionEntry
import com.barbershop.daily.data.UserProfile
import kotlinx.coroutines.launch

private val payMethods = listOf("Cash", "M-pesa", "Withdraw")
private val statuses = listOf("Collected", "Pending")
private val mpesaToOptions = listOf("", "Nash", "JAY")
private val servedByOptions = listOf("Ng'ash", "Jay", "Esther", "Cate")
private val groomedByOptions = listOf("None", "Esther", "Cate", "Ng'ash", "Jay")
private val serviceTypes = listOf("Shave", "Dye", "Blow-dry", "Manicure", "Pedicure", "Other")

@Composable
fun TransactionsScreen(profile: UserProfile, session: Session) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var today by remember { mutableStateOf(TodayData()) }
    val txs = today.transactions
    val total = remember(txs) { txs.sumOf { it.amount } }

    LaunchedEffect(session.token) {
        SheetsRepository.pollEvery(
            intervalMs = 5000,
            fetch = { SheetsRepository.listToday(session.token) },
            onValue = { today = it },
            onError = { e -> Log.w("TransactionsScreen", "Poll error: ${e.message}") }
        )
    }

    var clientName by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }

    var serviceType by remember { mutableStateOf(serviceTypes.first()) }
    var customServiceType by remember { mutableStateOf("") }
    var servedBy by remember { mutableStateOf(profile.displayName) }
    var groomedBy by remember { mutableStateOf(groomedByOptions.first()) }
    var payMethod by remember { mutableStateOf(payMethods.first()) }
    var status by remember { mutableStateOf(statuses.first()) }
    var mpesaTo by remember { mutableStateOf("") }

    var editingTxId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Money", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Total today: ${total.toInt()}", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(if (editingTxId == null) "Add transaction" else "Edit transaction", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Client name") }
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "Service Type",
                    options = serviceTypes,
                    value = serviceType,
                    onChange = { serviceType = it }
                )
                if (serviceType == "Other") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = customServiceType,
                        onValueChange = { customServiceType = it },
                        label = { Text("Specify Service") }
                    )
                }
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "Served by",
                    options = servedByOptions,
                    value = servedBy,
                    onChange = { servedBy = it }
                )
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "Groomed by",
                    options = groomedByOptions,
                    value = groomedBy,
                    onChange = { groomedBy = it }
                )
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "Pay method",
                    options = payMethods,
                    value = payMethod,
                    onChange = { payMethod = it }
                )
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "Status",
                    options = statuses,
                    value = status,
                    onChange = { status = it }
                )
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "Mpesa to",
                    options = mpesaToOptions,
                    value = mpesaTo,
                    onChange = { mpesaTo = it }
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && clientName.isNotBlank() && amountStr.isNotBlank(),
                    onClick = {
                        loading = true
                        scope.launch {
                            try {
                                val amount = amountStr.toDoubleOrNull() ?: 0.0
                                val txId = editingTxId
                                val finalServiceType = if (serviceType == "Other") customServiceType.trim() else serviceType
                                if (txId == null) {
                                    SheetsRepository.createTx(
                                        token = session.token,
                                        clientName = clientName.trim(),
                                        amount = amount,
                                        serviceType = finalServiceType,
                                        servedBy = servedBy,
                                        groomedBy = if (groomedBy == "None") "" else groomedBy,
                                        payMethod = payMethod,
                                        status = status,
                                        mpesaTo = mpesaTo
                                    )
                                    snackbar.showSnackbar("Saved")
                                } else {
                                    SheetsRepository.updateTx(
                                        token = session.token,
                                        txId = txId,
                                        clientName = clientName.trim(),
                                        amount = amount,
                                        serviceType = finalServiceType,
                                        servedBy = servedBy,
                                        groomedBy = if (groomedBy == "None") "" else groomedBy,
                                        payMethod = payMethod,
                                        status = status,
                                        mpesaTo = mpesaTo
                                    )
                                    snackbar.showSnackbar("Updated")
                                }
                                editingTxId = null
                                clientName = ""
                                amountStr = ""
                                customServiceType = ""
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Save failed")
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (editingTxId == null) "Save" else "Update")
                }

                if (editingTxId != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            editingTxId = null
                            clientName = ""
                            amountStr = ""
                            serviceType = serviceTypes.first()
                            customServiceType = ""
                            servedBy = profile.displayName
                            groomedBy = groomedByOptions.first()
                            payMethod = payMethods.first()
                            status = statuses.first()
                            mpesaTo = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Transactions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(txs.reversed()) { tx ->
                TransactionRow(
                    tx = tx,
                    canEdit = tx.createdBy == session.name,
                    onEdit = {
                        editingTxId = tx.txId
                        clientName = tx.clientName
                        amountStr = tx.amount.toInt().toString()
                        if (serviceTypes.contains(tx.serviceType)) {
                            serviceType = tx.serviceType
                            customServiceType = ""
                        } else {
                            serviceType = "Other"
                            customServiceType = tx.serviceType
                        }
                        servedBy = tx.servedBy.ifBlank { profile.displayName }
                        groomedBy = if (tx.groomedBy.isBlank()) "None" else tx.groomedBy
                        payMethod = if (payMethods.contains(tx.payMethod)) tx.payMethod else payMethods.first()
                        status = if (statuses.contains(tx.status)) tx.status else statuses.first()
                        mpesaTo = tx.mpesaTo
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                SheetsRepository.deleteTx(session.token, tx.txId)
                                snackbar.showSnackbar("Deleted")
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Delete failed")
                            }
                        }
                    }
                )
            }
        }

        SnackbarHost(hostState = snackbar)
    }
}

@Composable
private fun TransactionRow(
    tx: TransactionEntry,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${tx.clientName} — ${tx.amount.toInt()} (${tx.serviceType})", style = MaterialTheme.typography.titleMedium)
            Text("Served by: ${tx.servedBy}${if (tx.groomedBy.isNotBlank()) " | Groomed by: ${tx.groomedBy}" else ""}")
            Text("Pay: ${tx.payMethod} | Status: ${tx.status} | Mpesa to: ${tx.mpesaTo}")
            if (tx.createdBy.isNotBlank()) Text("By: ${tx.createdBy}", style = MaterialTheme.typography.bodySmall)
            if (tx.lastEditor.isNotBlank()) Text(tx.lastEditor, style = MaterialTheme.typography.bodySmall)
            if (tx.createdAt.isNotBlank()) {
                Text("Created: ${formatTimestamp(tx.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            if (canEdit) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text("Edit") }
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    value: String,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = value,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt.isBlank()) "(Blank)" else opt) },
                    onClick = { onChange(opt); expanded = false }
                )
            }
        }
    }
}
