package com.barbershop.daily.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SheetsApi {
    /**
     * Paste your Apps Script Web App URL here after you deploy it.
     * Example: https://script.google.com/macros/s/XXXX/exec
     */
    const val APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbztlhqgyHIz_FCKFiqifJi6BFUetrLaNQAtXR9Q9RhuJg6jq65xjPcpvtSMXmqL-jLF/exec"

    private fun post(action: String, payload: JSONObject): JSONObject {
        val url = URL(APPS_SCRIPT_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = 15000
            readTimeout = 20000
        }

        val body = JSONObject()
            .put("action", action)
            .put("payload", payload)
            .toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        val json = JSONObject(text)

        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "Request failed"))
        }

        return json.getJSONObject("data")
    }

    fun bootstrap(): JSONObject = post("bootstrap", JSONObject())

    fun login(name: String, pin: String, deviceId: String): JSONObject =
        post("login", JSONObject().put("name", name).put("pin", pin).put("deviceId", deviceId))

    fun clockIn(token: String): JSONObject =
        post("clockIn", JSONObject().put("token", token))

    fun listToday(token: String): JSONObject =
        post("listToday", JSONObject().put("token", token))

    fun createTx(token: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String, txType: String = "service", itemName: String = ""): JSONObject =
        post(
            "createTx",
            JSONObject()
                .put("token", token)
                .put("clientName", clientName)
                .put("amount", amount)
                .put("serviceType", serviceType)
                .put("servedBy", servedBy)
                .put("groomedBy", groomedBy)
                .put("payMethod", payMethod)
                .put("status", status)
                .put("mpesaTo", mpesaTo)
                .put("txType", txType)
                .put("itemName", itemName)
        )

    fun updateTx(token: String, txId: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String, txType: String = "service", itemName: String = ""): JSONObject =
        post(
            "updateTx",
            JSONObject()
                .put("token", token)
                .put("txId", txId)
                .put("clientName", clientName)
                .put("amount", amount)
                .put("serviceType", serviceType)
                .put("servedBy", servedBy)
                .put("groomedBy", groomedBy)
                .put("payMethod", payMethod)
                .put("status", status)
                .put("mpesaTo", mpesaTo)
                .put("txType", txType)
                .put("itemName", itemName)
        )

    fun deleteTx(token: String, txId: String, clientName: String = "", amount: Double = 0.0, createdBy: String = ""): JSONObject =
        post(
            "deleteTx",
            JSONObject()
                .put("token", token)
                .put("txId", txId)
                .put("clientName", clientName)
                .put("amount", amount)
                .put("createdBy", createdBy)
        )

    fun adminListDay(token: String, tabName: String): JSONObject =
        post("adminListDay", JSONObject().put("token", token).put("tabName", tabName))

    fun manageStaff(token: String, staffName: String, action: String): JSONObject =
        post(
            "manageStaff",
            JSONObject()
                .put("token", token)
                .put("staffName", staffName)
                .put("staffAction", action) // "reset", "suspend", "recall"
        )

    fun listUsers(token: String): JSONObject =
        post("listUsers", JSONObject().put("token", token))

    fun renameUser(token: String, oldName: String, newName: String): JSONObject =
        post(
            "renameUser",
            JSONObject()
                .put("token", token)
                .put("oldName", oldName)
                .put("newName", newName)
        )

    fun listQueue(token: String): JSONObject =
        post("listQueue", JSONObject().put("token", token))

    fun addToQueue(token: String, clientName: String, service: String, phone: String, note: String): JSONObject =
        post(
            "addToQueue",
            JSONObject()
                .put("token", token)
                .put("clientName", clientName)
                .put("service", service)
                .put("phone", phone)
                .put("note", note)
        )

    fun startQueueService(token: String, qId: String, barber: String): JSONObject =
        post(
            "startQueueService",
            JSONObject()
                .put("token", token)
                .put("qId", qId)
                .put("barber", barber)
        )

    fun completeQueue(token: String, qId: String): JSONObject =
        post("completeQueue", JSONObject().put("token", token).put("qId", qId))

    fun removeFromQueue(token: String, qId: String): JSONObject =
        post("removeFromQueue", JSONObject().put("token", token).put("qId", qId))

    fun setBarberFree(token: String, barber: String, free: Boolean): JSONObject =
        post(
            "setBarberFree",
            JSONObject()
                .put("token", token)
                .put("barber", barber)
                .put("free", free)
        )
}

