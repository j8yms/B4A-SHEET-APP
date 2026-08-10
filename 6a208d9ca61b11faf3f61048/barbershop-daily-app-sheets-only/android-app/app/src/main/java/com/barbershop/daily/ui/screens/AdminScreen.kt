package com.barbershop.daily.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.AttendanceEntry
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.SheetsRepository
import com.barbershop.daily.data.TodayData
import com.barbershop.daily.data.TransactionEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(session: Session, today: TodayData) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Records, 1: Wages, 2: History, 3: Staff

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Admin Console", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Records") },
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Wages") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Staff") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }

            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> TransactionViewer(session)
                    1 -> WagesViewer(session)
                    2 -> HistoricalRevenueViewer(session)
                    3 -> StaffManagement(session, today)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionViewer(session: Session) {
    val scope = rememberCoroutineScope()
    var tabName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<TodayData?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("EEE dd MMM yyyy", Locale.ENGLISH)
                        tabName = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                value = tabName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Date") },
                placeholder = { Text("Click to choose a date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
            )
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && tabName.isNotBlank(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            data = SheetsRepository.adminListDay(session.token, tabName.trim())
                        } catch (t: Throwable) {
                            error = t.message ?: "Failed"
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Load Records")
                }
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (data != null) {
            val txs = data!!.transactions
            val totalRevenue = txs.sumOf { it.amount }
            val cashAtHand = txs
                .filter { it.payMethod == "Cash" || it.payMethod == "Withdraw" }
                .sumOf { it.amount }

            Column(Modifier.fillMaxSize()) {
                // Summary Card
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Revenue", style = MaterialTheme.typography.labelMedium)
                                Text("KES ${totalRevenue.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Clients", style = MaterialTheme.typography.labelMedium)
                                Text("${txs.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Cash at Hand", style = MaterialTheme.typography.labelMedium)
                                Text("KES ${cashAtHand.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Table Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Client", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Service", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Barber", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Groomer", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Pay", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Amt", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(Modifier.fillMaxSize()) {
                    items(txs) { tx ->
                        AdminTransactionRow(tx)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTransactionRow(tx: TransactionEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tx.clientName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(tx.createdBy, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            text = tx.serviceType,
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = tx.servedBy,
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = getStaffColor(tx.servedBy),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = tx.groomedBy,
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = getStaffColor(tx.groomedBy),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = when(tx.payMethod) {
                "Cash" -> "Csh"
                "M-pesa" -> "Mps"
                "Withdraw" -> "Wd"
                else -> tx.payMethod.take(3)
            },
            modifier = Modifier.width(36.dp),
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WagesViewer(session: Session) {
    val scope = rememberCoroutineScope()
    var tabName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<TodayData?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("EEE dd MMM yyyy", Locale.ENGLISH)
                        tabName = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                value = tabName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !loading && tabName.isNotBlank(),
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            data = SheetsRepository.adminListDay(session.token, tabName.trim())
                        } catch (_: Throwable) {} finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Calculate")
            }
        }

        if (data != null) {
            val txs = data!!.transactions
            val staffNames = listOf("Ng'ash", "Jay", "Esther", "Cate")
            
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(staffNames) { name ->
                    val isBarber = name.equals("Jay", ignoreCase = true) || name.equals("Ng'ash", ignoreCase = true)
                    val isGroomer = name.equals("Cate", ignoreCase = true) || name.equals("Esther", ignoreCase = true)
                    
                    // Money sent to them (M-Pesa recipient)
                    val moneySent = txs.filter { it.mpesaTo.equals(name, ignoreCase = true) }.sumOf { it.amount }
                    
                    // Service Share (Dye: 150, Shave: 40% Jay/Ng'ash, 25% Cate/Esther, Others: 40%)
                    val servicesServed = txs.filter { it.servedBy.equals(name, ignoreCase = true) }
                    val commission = servicesServed.sumOf { tx ->
                        when {
                            tx.serviceType.equals("Dye", ignoreCase = true) -> 150.0
                            tx.serviceType.equals("Shave", ignoreCase = true) -> {
                                if (isBarber) tx.amount * 0.4
                                else if (isGroomer) tx.amount * 0.25
                                else tx.amount * 0.4 // Default for anyone else
                            }
                            else -> tx.amount * 0.4
                        }
                    }
                    
                    // Total groomed for display
                    val totalGroomed = if (isGroomer) {
                        txs.count { it.groomedBy.equals(name, ignoreCase = true) }
                    } else 0
                    
                    // Grooming Count for Wage: people they groomed but didn't serve
                    val groomingCountForWage = if (isGroomer) {
                        txs.count { 
                            it.groomedBy.equals(name, ignoreCase = true) && !it.servedBy.equals(name, ignoreCase = true) 
                        }
                    } else 0

                    val groomingWage = if (isGroomer) {
                        if (groomingCountForWage < 10) 500.0 else groomingCountForWage * 60.0
                    } else 0.0
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            
                            if (isGroomer) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Client Count:", style = MaterialTheme.typography.bodySmall)
                                    Text("$totalGroomed clients", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Base/Grooming Wage:", style = MaterialTheme.typography.bodySmall)
                                    Text("KES ${groomingWage.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Service Share:", style = MaterialTheme.typography.bodySmall)
                                Text("KES ${commission.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }

                            HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Money Sent (M-Pesa):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                Text("KES ${moneySent.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            
                            val totalWage = groomingWage + commission
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Estimated Wage:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("KES ${totalWage.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoricalRevenueViewer(session: Session) {
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        scope.launch {
            val sdf = SimpleDateFormat("EEE dd MMM yyyy", Locale.ENGLISH)
            val calendar = Calendar.getInstance()
            
            val dates = (0 until 7).map {
                val d = sdf.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                d
            }

            val deferredResults = dates.map { dateStr ->
                async {
                    try {
                        val data = SheetsRepository.adminListDay(session.token, dateStr)
                        dateStr to data.transactions.sumOf { it.amount }
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            
            history = deferredResults.awaitAll().filterNotNull()
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Last 7 Days Revenue", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        if (loading && history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history) { (date, revenue) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(date, style = MaterialTheme.typography.bodyMedium)
                            Text("KES ${revenue.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffManagement(session: Session, today: TodayData) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loadingName by remember { mutableStateOf<String?>(null) }

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
            Text("Staff Management", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Reset PINs or toggle account status", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(Modifier.height(24.dp))

            today.attendance.filter { it.name.isNotBlank() }.forEach { staff ->
                StaffAdminCard(
                    staff = staff,
                    isLoading = loadingName == staff.name,
                    onAction = { action ->
                        loadingName = staff.name
                        scope.launch {
                            try {
                                SheetsRepository.manageStaff(session.token, staff.name, action)
                                snackbar.showSnackbar("Success for ${staff.name}")
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(t.message ?: "Failed")
                            } finally {
                                loadingName = null
                            }
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StaffAdminCard(staff: AttendanceEntry, isLoading: Boolean, onAction: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(staff.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAction("reset") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("Reset PIN", style = MaterialTheme.typography.labelSmall)
                }
                
                Button(
                    onClick = { onAction(if (staff.isActive) "suspend" else "recall") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (staff.isActive) MaterialTheme.colorScheme.error else Color(0xFF39FF14) // Red for suspend, Green for recall
                    )
                ) {
                    Text(
                        if (staff.isActive) "Suspend" else "Recall", 
                        style = MaterialTheme.typography.labelSmall,
                        color = if (staff.isActive) Color.White else Color.Black
                    )
                }
            }
        }
    }
}
