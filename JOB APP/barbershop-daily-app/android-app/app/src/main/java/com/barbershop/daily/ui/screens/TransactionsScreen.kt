package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.FirebaseRepository
import com.barbershop.daily.data.TransactionEntry
import com.barbershop.daily.data.UserProfile
import kotlinx.coroutines.launch

private val payMethods = listOf("Cash", "M-pesa", "Withdraw")
private val statuses = listOf("Collected", "Pending")
private val mpesaToOptions = listOf("", "Nash", "JAY")
private val servedByOptions = listOf("Ng'ash", "Jay", "Esther", "Cate")

@Composable
fun TransactionsScreen(profile: UserProfile) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val txs by FirebaseRepository.watchTodayTransactions().collectAsState(initial = emptyList())
    val total = remember(txs) { txs.sumOf { it.amount } }

    var clientName by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }

    var servedBy by remember { mutableStateOf(profile.displayName) }
    var payMethod by remember { mutableStateOf(payMethods.first()) }
    var status by remember { mutableStateOf(statuses.first()) }
    var mpesaTo by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Money", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Total today: ${total.toInt()}", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Add transaction", style = MaterialTheme.typography.titleMedium)
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
                    label = "Served by",
                    options = servedByOptions,
                    value = servedBy,
                    onChange = { servedBy = it }
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
                                FirebaseRepository.createTransaction(
                                    clientName = clientName.trim(),
                                    amount = amount,
                                    servedBy = servedBy,
                                    payMethod = payMethod,
                                    status = status,
                                    mpesaTo = mpesaTo
                                )
                                clientName = ""
                                amountStr = ""
                                snackbar.showSnackbar("Saved")
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
                    Text("Save")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Transactions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(txs.reversed()) { tx ->
                TransactionRow(tx)
            }
        }

        SnackbarHost(hostState = snackbar)
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntry) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${tx.clientName} — ${tx.amount.toInt()}", style = MaterialTheme.typography.titleMedium)
            Text("Served by: ${tx.servedBy}")
            Text("Pay: ${tx.payMethod} | Status: ${tx.status} | Mpesa to: ${tx.mpesaTo}")
            if (tx.lastEditor.isNotBlank()) Text(tx.lastEditor, style = MaterialTheme.typography.bodySmall)
        }
    }
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

