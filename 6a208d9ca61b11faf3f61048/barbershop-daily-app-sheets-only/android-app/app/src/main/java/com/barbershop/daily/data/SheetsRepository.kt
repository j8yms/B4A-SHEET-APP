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

    suspend fun clockIn(token: String): JSONObject = withContext(Dispatchers.IO) { SheetsApi.clockIn(token) }

    suspend fun listToday(token: String): TodayData = withContext(Dispatchers.IO) {
        parseTodayData(SheetsApi.listToday(token))
    }

    suspend fun createTx(token: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String): String =
        withContext(Dispatchers.IO) {
            SheetsApi.createTx(token, clientName, amount, serviceType, servedBy, groomedBy, payMethod, status, mpesaTo).getString("txId")
        }

    suspend fun updateTx(token: String, txId: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String) =
        withContext(Dispatchers.IO) { SheetsApi.updateTx(token, txId, clientName, amount, serviceType, servedBy, groomedBy, payMethod, status, mpesaTo) }

    suspend fun deleteTx(token: String, txId: String) =
        withContext(Dispatchers.IO) { SheetsApi.deleteTx(token, txId) }

    suspend fun adminListDay(token: String, tabName: String): TodayData = withContext(Dispatchers.IO) {
        parseTodayData(SheetsApi.adminListDay(token, tabName))
    }

    suspend fun manageStaff(token: String, staffName: String, action: String) = withContext(Dispatchers.IO) {
        SheetsApi.manageStaff(token, staffName, action)
    }

    /**
     * Simple poll helper. Call from a coroutine:
     * pollEvery(5000) { listToday(token) }
     */
    suspend fun <T> pollEvery(intervalMs: Long, fetch: suspend () -> T, onValue: (T) -> Unit) {
        while (true) {
            try {
                onValue(fetch())
            } catch (_: Throwable) {
                // ignore transient errors; UI can show last known state
            }
            delay(intervalMs)
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
}
