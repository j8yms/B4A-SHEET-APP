package com.barbershop.daily.data

data class UserProfile(
    val displayName: String = "",
    val role: String = "staff" // "admin" | "staff"
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
