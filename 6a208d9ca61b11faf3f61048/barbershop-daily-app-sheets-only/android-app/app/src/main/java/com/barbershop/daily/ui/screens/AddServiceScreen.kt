package com.barbershop.daily.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TransactionEntry
import com.barbershop.daily.data.UserProfile
import kotlinx.coroutines.launch

private val payMethods = listOf("Cash", "M-pesa", "Withdraw")
private val statuses = listOf("Collected", "Pending")
private val serviceTypes = listOf("Shave", "Dye", "Blow-dry", "Manicure", "Pedicure", "Other")
private val mpesaToOptions = listOf("", "Ng'ash", "Jay", "Esther", "Cate")
private val servedByOptions = listOf("Ng'ash", "Jay", "Esther", "Cate")
private val groomedByOptions = listOf("None", "Esther", "Cate", "Ng'ash", "Jay")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    profile: UserProfile,
    session: Session,
    editingTx: TransactionEntry? = null,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var clientName by remember { mutableStateOf(editingTx?.clientName ?: "") }
    var amountStr by remember { mutableStateOf(editingTx?.amount?.toInt()?.toString() ?: "") }
    var serviceType by remember { mutableStateOf(
        if (editingTx == null || serviceTypes.contains(editingTx.serviceType)) 
            editingTx?.serviceType ?: serviceTypes.first()
        else 
            "Other"
    ) }
    var customServiceType by remember { mutableStateOf(
        if (editingTx != null && !serviceTypes.contains(editingTx.serviceType))
            editingTx.serviceType
        else ""
    ) }
    var servedBy by remember { mutableStateOf(editingTx?.servedBy ?: profile.displayName) }
    var groomedBy by remember { mutableStateOf(if (editingTx?.groomedBy.isNullOrBlank()) "None" else editingTx!!.groomedBy) }
    var payMethod by remember { mutableStateOf(editingTx?.payMethod ?: payMethods.first()) }
    var status by remember { mutableStateOf(editingTx?.status ?: statuses.first()) }
    var mpesaTo by remember { mutableStateOf(editingTx?.mpesaTo ?: "") }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (editingTx == null) "Add Service" else "Edit Service",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

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
                label = { Text("Amount (KES)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(16.dp))

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
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "Served by",
                options = servedByOptions,
                value = servedBy,
                onChange = { servedBy = it }
            )
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "Groomed by",
                options = groomedByOptions,
                value = groomedBy,
                onChange = { groomedBy = it }
            )
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "Pay method",
                options = payMethods,
                value = payMethod,
                onChange = { payMethod = it }
            )
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "Status",
                options = statuses,
                value = status,
                onChange = { status = it }
            )
            Spacer(Modifier.height(12.dp))

            if (payMethod == "M-pesa") {
                DropdownField(
                    label = "Mpesa to",
                    options = mpesaToOptions,
                    value = mpesaTo,
                    onChange = { mpesaTo = it }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !loading && clientName.isNotBlank() && amountStr.isNotBlank(),
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            val amount = amountStr.toDoubleOrNull() ?: 0.0
                            val finalServiceType = if (serviceType == "Other") customServiceType.trim() else serviceType
                            if (editingTx == null) {
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
                            } else {
                                SheetsRepository.updateTx(
                                    token = session.token,
                                    txId = editingTx.txId,
                                    clientName = clientName.trim(),
                                    amount = amount,
                                    serviceType = finalServiceType,
                                    servedBy = servedBy,
                                    groomedBy = if (groomedBy == "None") "" else groomedBy,
                                    payMethod = payMethod,
                                    status = status,
                                    mpesaTo = mpesaTo
                                )
                            }
                            onDone()
                        } catch (t: Throwable) {
                            snackbar.showSnackbar(t.message ?: "Save failed")
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (editingTx == null) "Record Service" else "Update Service", style = MaterialTheme.typography.titleMedium)
                }
            }
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
            value = if (value.isBlank()) "(Select)" else value,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt.isBlank()) "None" else opt) },
                    onClick = { onChange(opt); expanded = false }
                )
            }
        }
    }
}
