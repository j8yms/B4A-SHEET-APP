package com.barbershop.daily.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barbershop.daily.data.Session
import com.barbershop.daily.data.TodayData

@Composable
fun ReportsScreen(session: Session, today: TodayData) {
    val txs = today.transactions

    val isEstherOrCate = session.name.equals("Esther", ignoreCase = true) || session.name.equals("Cate", ignoreCase = true)

    val cashAtHand = txs
        .filter { it.payMethod == "Cash" || it.payMethod == "Withdraw" }
        .sumOf { it.amount }
    
    val byStaffClients = remember(txs) {
        val counts = mutableMapOf<String, Double>()
        txs.forEach { tx ->
            if (isEstherOrCate) {
                // If viewed by Esther or Cate, chart represents grooming counts
                val groomer = tx.groomedBy
                if (groomer.isNotBlank() && !groomer.equals("None", ignoreCase = true)) {
                    counts[groomer] = (counts[groomer] ?: 0.0) + 1.0
                }
            } else {
                // Otherwise (Admin/Barbers), chart represents shaving/served counts
                val server = tx.servedBy
                if (server.isNotBlank()) {
                    counts[server] = (counts[server] ?: 0.0) + 1.0
                }
            }
        }
        counts
    }
    val totalPerformance = byStaffClients.values.sum()

    val lates = today.attendance.filter { it.arrival.startsWith("LATE") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Daily Analytics", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(today.tabName, style = MaterialTheme.typography.bodyMedium)
        
        Spacer(Modifier.height(24.dp))

        // Cash at Hand Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Cash at Hand", color = Color.White.copy(alpha = 0.8f))
                Text(
                    "KES ${cashAtHand.toInt()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text("Cash + Withdrawals", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.height(24.dp))

        if (lates.isNotEmpty()) {
            Text("Late Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            lates.forEach { entry ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.arrival, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
            }
            Spacer(Modifier.height(24.dp))
        }

        val chartTitle = if (isEstherOrCate) "Staff Performance (Grooming)" else "Staff Performance (Shaving)"
        Text(chartTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        if (totalPerformance > 0) {
            StaffPerformancePieChart(byStaffClients, totalPerformance)
        } else {
            Text("No transactions to analyze", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun StaffPerformancePieChart(data: Map<String, Double>, total: Double) {
    val sliceColors = listOf(
        Color(0xFFE91E63), // Pink (Ng'ash)
        Color(0xFF39FF14), // Neon Green (Jay)
        Color(0xFF6200EA), // Deep Purple (Esther)
        Color(0xFFFF5722), // Orange (Cate)
        Color(0xFF00E5FF), // Cyan
        Color(0xFF00BCD4)  // Teal
    )

    val staffColorMap = mapOf(
        "Ng'ash" to Color(0xFFE91E63),
        "Jay" to Color(0xFF39FF14),
        "Esther" to Color(0xFF6200EA),
        "Cate" to Color(0xFFFF5722)
    )

    val chartData = data.entries.toList()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.size(180.dp)) {
                var startAngle = -90f
                chartData.forEachIndexed { index, entry ->
                    val color = staffColorMap.entries.find { it.key.equals(entry.key, ignoreCase = true) }?.value 
                        ?: sliceColors[index % sliceColors.size]
                    
                    val sweepAngle = (entry.value / total).toFloat() * 360f
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Legend
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chartData.forEachIndexed { index, entry ->
                val color = staffColorMap.entries.find { it.key.equals(entry.key, ignoreCase = true) }?.value 
                    ?: sliceColors[index % sliceColors.size]
                
                val percentage = (entry.value / total * 100).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${entry.key.ifBlank { "Unknown" }}: $percentage%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
