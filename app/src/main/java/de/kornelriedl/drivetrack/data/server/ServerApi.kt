package de.kornelriedl.drivetrack.data.server

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Einfacher HTTP-Client fürs DriveTrack-Backend – bewusst ohne zusätzliche Bibliothek
 * (nur java.net + org.json, beides bereits Teil von Android), da wir nur eine Handvoll
 * simpler JSON-Endpunkte ansprechen. Alle Funktionen sind blockierend und müssen daher
 * vom Aufrufer in Dispatchers.IO ausgeführt werden.
 */
object ServerApi {
    private const val BASE_URL = "https://drivetrack-api.kornel-riedl.de/api"

    data class ApiResult(
        val success: Boolean,
        val code: Int,
        val json: JSONObject?,
        val error: String?
    )

    private fun request(
        path: String,
        method: String,
        body: JSONObject?,
        token: String?
    ): Pair<Int, String> {
        val url = URL("$BASE_URL$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Content-Type", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

        if (body != null) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    private fun safeRequest(
        path: String,
        method: String,
        body: JSONObject? = null,
        token: String? = null
    ): ApiResult {
        return try {
            val (code, text) = request(path, method, body, token)
            if (code in 200..299) {
                ApiResult(true, code, if (text.isNotBlank()) JSONObject(text) else JSONObject(), null)
            } else {
                val message = try {
                    JSONObject(text).optString("error", "Fehler $code")
                } catch (e: Exception) {
                    "Fehler $code"
                }
                ApiResult(false, code, null, message)
            }
        } catch (e: Exception) {
            ApiResult(false, -1, null, e.message ?: "Netzwerkfehler")
        }
    }

    fun register(
        username: String, email: String, password: String,
        dekWrappedPassword: String, dekWrappedRecovery: String,
        passwordSalt: String, recoverySalt: String, recoveryCode: String
    ): ApiResult {
        val body = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
            put("dekWrappedPassword", dekWrappedPassword)
            put("dekWrappedRecovery", dekWrappedRecovery)
            put("passwordSalt", passwordSalt)
            put("recoverySalt", recoverySalt)
            put("recoveryCode", recoveryCode)
        }
        return safeRequest("/register", "POST", body)
    }

    fun login(username: String, password: String): ApiResult {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        return safeRequest("/login", "POST", body)
    }

    fun requestReset(email: String): ApiResult {
        val body = JSONObject().apply { put("email", email) }
        return safeRequest("/request-reset", "POST", body)
    }

    fun verifyResetCode(email: String, code: String): ApiResult {
        val body = JSONObject().apply {
            put("email", email)
            put("code", code)
        }
        return safeRequest("/verify-reset-code", "POST", body)
    }

    fun confirmReset(
        email: String, code: String, newPassword: String,
        newDekWrappedPassword: String, newPasswordSalt: String
    ): ApiResult {
        val body = JSONObject().apply {
            put("email", email)
            put("code", code)
            put("newPassword", newPassword)
            put("newDekWrappedPassword", newDekWrappedPassword)
            put("newPasswordSalt", newPasswordSalt)
        }
        return safeRequest("/confirm-reset", "POST", body)
    }

    fun uploadBackup(token: String, ciphertext: String, iv: String): ApiResult {
        val body = JSONObject().apply {
            put("ciphertext", ciphertext)
            put("iv", iv)
        }
        return safeRequest("/backup", "POST", body, token)
    }

    fun downloadLatestBackup(token: String): ApiResult {
        return safeRequest("/backup", "GET", null, token)
    }

    fun backupHistory(token: String): ApiResult {
        return safeRequest("/backup/history", "GET", null, token)
    }
}
