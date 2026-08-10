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

    fun createTx(token: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String): JSONObject =
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
        )

    fun updateTx(token: String, txId: String, clientName: String, amount: Double, serviceType: String, servedBy: String, groomedBy: String, payMethod: String, status: String, mpesaTo: String): JSONObject =
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
        )

    fun deleteTx(token: String, txId: String): JSONObject =
        post("deleteTx", JSONObject().put("token", token).put("txId", txId))

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
}

