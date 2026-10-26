package com.barbershop.daily.data

data class UserProfile(
    val displayName: String = "",
    val role: String = "staff" // "admin" | "staff"
)

data class StaffUser(
    val name: String = "",
    val role: String = "staff",
    val active: Boolean = true
)

data class AttendanceEntry(
    val name: String = "",
    val `in`: Boolean = false,
    val time: String = "",
    val arrival: String = "",
    val isActive: Boolean = true
)

data class TransactionEntry(
    val txId: String = "",
    val clientName: String = "",
    val amount: Double = 0.0,
    val serviceType: String = "Shave",
    val txType: String = "service", // "service" | "sale"
    val servedBy: String = "",
    val groomedBy: String = "",
    val payMethod: String = "",
    val status: String = "",
    val mpesaTo: String = "",
    val lastEditor: String = "",
    val createdBy: String = "",
    val deviceId: String = "",
    val createdAt: String = ""
)

fun TransactionEntry.displayTime(): String {
    if (createdAt.isNotBlank()) {
        val parts = createdAt.trim().split(" ")
        val timePart = if (parts.size >= 2) parts[1] else parts[0]
        if (timePart.contains(":")) {
            return timePart.substring(0, minOf(5, timePart.length))
        }
    }
    if (lastEditor.isNotBlank()) {
        val dashChars = listOf("-", "–", "—")
        for (dash in dashChars) {
            if (lastEditor.contains(dash)) {
                val timePart = lastEditor.split(dash)[0].trim()
                if (timePart.contains(":")) {
                    return timePart.substring(0, minOf(5, timePart.length))
                }
            }
        }
        val firstWord = lastEditor.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
        if (firstWord.contains(":")) {
            return firstWord.substring(0, minOf(5, firstWord.length))
        }
    }
    return ""
}

fun isRealStaffName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return false
    val upper = trimmed.uppercase()
    if (upper == "S/N" || upper == "SN" || upper == "NAME" || upper == "ATTENDANCE" || upper == "ADMIN") return false
    if (trimmed.matches(Regex("^\\d+$"))) return false
    if (trimmed.matches(Regex("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun).*", RegexOption.IGNORE_CASE))) return false
    return true
}

data class QueueEntry(
    val qId: String = "",
    val clientName: String = "",
    val service: String = "",
    val barber: String = "",
    val status: String = "waiting", // "waiting" | "in-progress" | "done"
    val phone: String = "",
    val addedAt: String = "",
    val note: String = ""
)

data class BarberStatus(
    val name: String = "",
    val free: Boolean = true
)

data class QueueData(
    val connected: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val barbers: List<BarberStatus> = emptyList()
)
