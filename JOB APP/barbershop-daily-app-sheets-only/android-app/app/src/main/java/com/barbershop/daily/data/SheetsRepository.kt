package com.barbershop.daily.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TodayData(
    val tabName: String = "",
    val role: String = "staff",
    val me: String = "",
    val attendance: List<AttendanceEntry> = emptyList(),
    val transactions: List<TransactionEntry> = emptyList(),
)

object SheetsRepository {
    suspend fun bootstrap(): JSONObject = withContext(Dispatchers.IO) { SheetsApi.bootstrap() }

    suspend fun login(name: String, pin: String, deviceId: String): Session = withContext(Dispatchers.IO) {
        val data = SheetsApi.login(name, pin, deviceId)
        Session(
            token = data.getString("token"),
            role = data.getString("role"),
            name = data.getString("name")
        )
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        SheetsApi.logout(token)
    }

    suspend fun listStaff(token: String): List<StaffUser> = withContext(Dispatchers.IO) {
        val data = SheetsApi.listStaff(token)
        val arr = data.optJSONArray("staff") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StaffUser(
                name = o.optString("name"),
                role = o.optString("role", "staff"),
                active = o.optBoolean("active", true)
            )
        }
    }

    suspend fun clockIn(token: String): JSONObject = withContext(Dispatchers.IO) { SheetsApi.clockIn(token) }

    suspend fun listToday(token: String): TodayData = withContext(Dispatchers.IO) {
        parseTodayData(SheetsApi.listToday(token))
    }

    suspend fun createTx(token: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String, txType: String = "service", itemName: String = ""): String =
        withContext(Dispatchers.IO) {
            SheetsApi.createTx(token, clientName, amount, serviceType, servedBy, groomedBy, payMethod, status, mpesaTo, txType, itemName).getString("txId")
        }

    suspend fun updateTx(token: String, txId: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String, txType: String = "service", itemName: String = "") =
        withContext(Dispatchers.IO) { SheetsApi.updateTx(token, txId, clientName, amount, serviceType, servedBy, groomedBy, payMethod, status, mpesaTo, txType, itemName) }

    suspend fun deleteTx(token: String, txId: String, clientName: String = "", amount: Double = 0.0, createdBy: String = "") =
        withContext(Dispatchers.IO) { SheetsApi.deleteTx(token, txId, clientName, amount, createdBy) }

    suspend fun adminListDay(token: String, tabName: String): TodayData = withContext(Dispatchers.IO) {
        parseTodayData(SheetsApi.adminListDay(token, tabName))
    }

    suspend fun manageStaff(token: String, staffName: String, action: String) = withContext(Dispatchers.IO) {
        SheetsApi.manageStaff(token, staffName, action)
    }

    suspend fun listUsers(token: String): List<StaffUser> = withContext(Dispatchers.IO) {
        val data = SheetsApi.listUsers(token)
        val arr = data.optJSONArray("users") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StaffUser(
                name = o.optString("name"),
                role = o.optString("role", "staff"),
                active = o.optBoolean("active", true)
            )
        }
    }

    suspend fun renameUser(token: String, oldName: String, newName: String) = withContext(Dispatchers.IO) {
        SheetsApi.renameUser(token, oldName, newName)
    }

    suspend fun listQueue(token: String): QueueData = withContext(Dispatchers.IO) {
        parseQueue(SheetsApi.listQueue(token))
    }

    suspend fun addToQueue(token: String, clientName: String, service: String, phone: String, note: String) =
        withContext(Dispatchers.IO) { SheetsApi.addToQueue(token, clientName, service, phone, note) }

    suspend fun startQueueService(token: String, qId: String, barber: String) =
        withContext(Dispatchers.IO) { SheetsApi.startQueueService(token, qId, barber) }

    suspend fun completeQueue(token: String, qId: String) =
        withContext(Dispatchers.IO) { SheetsApi.completeQueue(token, qId) }

    suspend fun removeFromQueue(token: String, qId: String) =
        withContext(Dispatchers.IO) { SheetsApi.removeFromQueue(token, qId) }

    suspend fun setBarberFree(token: String, barber: String, free: Boolean) =
        withContext(Dispatchers.IO) { SheetsApi.setBarberFree(token, barber, free) }

    /**
     * Poll helper with error handling. Calls onError for failures instead of silently swallowing.
     * Call from a coroutine:
     * pollEvery(5000, onError = { log error }) { listToday(token) }
     */
    suspend fun <T> pollEvery(
        intervalMs: Long,
        fetch: suspend () -> T,
        onValue: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        while (true) {
            try {
                onValue(fetch())
            } catch (e: Throwable) {
                onError?.invoke(e)
            }
            delay(intervalMs)
        }
    }

    /**
     * One-time fetch with error handling for manual refresh.
     */
    suspend fun <T> fetchOnce(
        fetch: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            onSuccess(fetch())
        } catch (e: Throwable) {
            onError(e)
        }
    }

    private fun parseTodayData(data: JSONObject): TodayData {
        val attendanceArr = data.optJSONArray("attendance") ?: JSONArray()
        val txArr = data.optJSONArray("transactions") ?: JSONArray()

        val attendance = (0 until attendanceArr.length()).map { i ->
            val o = attendanceArr.getJSONObject(i)
            AttendanceEntry(
                name = o.optString("name"),
                `in` = o.optBoolean("in", false),
                time = o.optString("time"),
                arrival = o.optString("arrival"),
                isActive = o.optBoolean("active", true)
            )
        }

        val tx = (0 until txArr.length()).map { i ->
            val o = txArr.getJSONObject(i)
            TransactionEntry(
                txId = o.optString("txId"),
                clientName = o.optString("clientName"),
                amount = o.optDouble("amount", 0.0),
                serviceType = o.optString("serviceType", "Shave"),
                txType = o.optString("txType", "service"),
                servedBy = o.optString("servedBy"),
                groomedBy = o.optString("groomedBy"),
                payMethod = o.optString("payMethod"),
                status = o.optString("status"),
                mpesaTo = o.optString("mpesaTo"),
                lastEditor = o.optString("lastEditor"),
                createdBy = o.optString("createdBy"),
                deviceId = o.optString("deviceId"),
                createdAt = o.optString("createdAt"),
            )
        }

        return TodayData(
            tabName = data.optString("tabName"),
            role = data.optString("role", "staff"),
            me = data.optString("me"),
            attendance = attendance,
            transactions = tx
        )
    }

    private fun parseQueue(data: JSONObject): QueueData {
        val queueArr = data.optJSONArray("queue") ?: JSONArray()
        val barbersArr = data.optJSONArray("barbers") ?: JSONArray()

        val queue = (0 until queueArr.length()).map { i ->
            val o = queueArr.getJSONObject(i)
            QueueEntry(
                qId = o.optString("qId"),
                clientName = o.optString("clientName"),
                service = o.optString("service"),
                barber = o.optString("barber"),
                status = o.optString("status", "waiting"),
                phone = o.optString("phone"),
                addedAt = o.optString("addedAt"),
                note = o.optString("note")
            )
        }

        val barbers = (0 until barbersArr.length()).map { i ->
            val o = barbersArr.getJSONObject(i)
            BarberStatus(
                name = o.optString("name"),
                free = o.optBoolean("free", true)
            )
        }

        return QueueData(
            connected = data.optBoolean("connected", false),
            queue = queue,
            barbers = barbers
        )
    }
}
