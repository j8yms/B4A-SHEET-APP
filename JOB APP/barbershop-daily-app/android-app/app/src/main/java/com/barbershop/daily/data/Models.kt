package com.barbershop.daily.data

data class UserProfile(
    val email: String = "",
    val displayName: String = "",
    val role: String = "staff" // "admin" | "staff"
)

data class AttendanceEntry(
    val displayName: String = "",
    val `in`: Boolean = false,
    val time: String = "",
    val arrival: String = ""
)

data class TransactionEntry(
    val clientName: String = "",
    val amount: Double = 0.0,
    val servedBy: String = "",
    val payMethod: String = "",
    val status: String = "",
    val mpesaTo: String = "",
    val lastEditor: String = ""
)

