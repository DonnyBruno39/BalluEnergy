package ru.balluenergy.app.hommyn

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Hommyn challenge/response authentication client. */
object HommynAuth {
    private const val HOST = "https://auth-iot.api.rusklimat.ru"
    private const val AUTH_PATH = "/auth"

    data class Challenge(
        val session: String,
        val challenge: String,
        val raw: JSONObject
    )

    data class AuthResult(val accessToken: String, val raw: JSONObject)

    fun init(phone: String): Challenge {
        val body = JSONObject().apply {
            put("login", phone)
            put("phone", phone)
            put("platform", "android")
            put("osVersion", android.os.Build.VERSION.RELEASE ?: "unknown")
            put("vendor", android.os.Build.MANUFACTURER ?: "unknown")
            put("model", android.os.Build.MODEL ?: "unknown")
            put("name", "Ballu Energy")
            put("deviceInfo", JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("platform", "android")
            })
            put("locales", java.util.Locale.getDefault().toLanguageTag())
            put("bundle", "com.hommyn.app")
            put("version", "1.18.3")
            put("client", "android")
        }
        val json = request(AUTH_PATH, "POST", body.toString())
        val session = json.optString("session")
        val challenge = json.optString("challenge")
        if (session.isBlank() || challenge.isBlank()) throw HommynApiException(200, json.toString())
        return Challenge(session, challenge, json)
    }

    fun authorize(session: String, challenge: String, response: String): AuthResult {
        // Hommyn uses the challenge type as the second path segment, e.g. /auth/SMS_CODE.
        val path = "$AUTH_PATH/${challenge.trim()}"
        val body = JSONObject().apply {
            put("session", session)
            put("challenge", challenge)
            put("response", response)
        }
        val json = request(path, "POST", body.toString())
        val token = json.optString("access_token").ifBlank { json.optString("accessToken") }
        if (token.isBlank()) throw HommynApiException(200, json.toString())
        return AuthResult(token, json)
    }

    private fun request(path: String, method: String, body: String): JSONObject {
        val c = (URL(HOST + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HommynApiException(code, text)
            return JSONObject(text)
        } finally { c.disconnect() }
    }
}
