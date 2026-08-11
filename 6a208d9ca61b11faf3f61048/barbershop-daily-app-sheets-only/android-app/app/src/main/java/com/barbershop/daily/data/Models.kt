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
