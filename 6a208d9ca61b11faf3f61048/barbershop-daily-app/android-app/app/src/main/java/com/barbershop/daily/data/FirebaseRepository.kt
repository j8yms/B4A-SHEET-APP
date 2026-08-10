package com.barbershop.daily.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object FirebaseRepository {
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private val functions: FirebaseFunctions = Firebase.functions

    private val tz = ZoneId.of("Africa/Nairobi")
    private val dayKeyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun currentEmail(): String? = auth.currentUser?.email

    fun authState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser != null) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, pin: String) {
        auth.signInWithEmailAndPassword(email, pin).await()
    }

    fun signOut() = auth.signOut()

    suspend fun loadMyProfile(): UserProfile {
        val email = currentEmail() ?: throw IllegalStateException("Not signed in")
        val snap = db.document("userProfiles/$email").get().await()
        return snap.toObject(UserProfile::class.java)?.copy(email = email)
            ?: throw IllegalStateException("Profile not found for $email")
    }

    private fun todayDayKey(): String {
        val today = LocalDate.now(tz)
        return today.format(dayKeyFmt)
    }

    fun watchTodayAttendance(): Flow<List<AttendanceEntry>> = callbackFlow {
        val dayKey = todayDayKey()
        val reg = db.collection("days/$dayKey/attendance")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val rows = snap?.documents?.mapNotNull { it.toObject(AttendanceEntry::class.java) } ?: emptyList()
                trySend(rows.sortedBy { it.displayName })
            }
        awaitClose { reg.remove() }
    }

    fun watchTodayTransactions(): Flow<List<TransactionEntry>> = callbackFlow {
        val dayKey = todayDayKey()
        val reg = db.collection("days/$dayKey/transactions")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val rows = snap?.documents?.mapNotNull { it.toObject(TransactionEntry::class.java) } ?: emptyList()
                trySend(rows)
            }
        awaitClose { reg.remove() }
    }

    suspend fun clockIn(): Map<String, Any?> {
        val res = functions.getHttpsCallable("clockIn").call().await()
        @Suppress("UNCHECKED_CAST")
        return res.data as Map<String, Any?>
    }

    suspend fun createTransaction(
        clientName: String,
        amount: Double,
        servedBy: String,
        payMethod: String,
        status: String,
        mpesaTo: String
    ) {
        val data = hashMapOf(
            "clientName" to clientName,
            "amount" to amount,
            "servedBy" to servedBy,
            "payMethod" to payMethod,
            "status" to status,
            "mpesaTo" to mpesaTo
        )
        functions.getHttpsCallable("createTransaction").call(data).await()
    }
}

