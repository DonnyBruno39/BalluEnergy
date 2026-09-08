package ru.balluenergy.app.hommyn

import android.os.Build
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Hommyn challenge/response authentication client. */
object HommynAuth {
    private const val HOST = "https://auth-iot.api.rusklimat.ru"
    private const val AUTH_PATH = "/auth"
    private val jsonMediaType = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class Challenge(val session: String, val challenge: String, val raw: JSONObject)
    data class AuthResult(val accessToken: String, val raw: JSONObject)

    fun init(phone: String): Challenge {
        val value = phone.trim()
        require(value.isNotBlank()) { "phone is empty" }
        val body = linkedMapOf<String, Any>(
            if (Regex("^\\+?\\d+$").matches(value)) "phone" else "email" to value,
            "platform" to "android",
            "osVersion" to (Build.VERSION.RELEASE ?: "unknown"),
            "vendor" to (Build.MANUFACTURER ?: "unknown"),
            "model" to (Build.MODEL ?: "unknown"),
            "name" to "Ballu Energy",
            "deviceInfo" to mapOf("id" to UUID.randomUUID().toString(), "platform" to "android"),
            "locales" to listOf(java.util.Locale.getDefault().toString()),
            "bundle" to "com.hommyn.app",
            "version" to "1.18.3",
            "client" to "android"
        )
        val json = request(AUTH_PATH, gson.toJson(body))
        val session = json.optString("session")
        val challenge = json.optString("challenge")
        if (session.isBlank() || challenge.isBlank()) throw HommynApiException(200, json.toString())
        return Challenge(session, challenge, json)
    }

    fun authorize(session: String, challenge: String, response: String): AuthResult {
        // Exact enum names used by the original Hommyn AuthChallenge enum.
        val normalized = when (challenge.trim().uppercase()) {
            "SMS", "SMS_CODE", "SMSCODE" -> "SMS_CODE"
            "EMAIL", "EMAIL_CODE", "EMAILCODE" -> "EMAIL_CODE"
            "PASSWORD", "PASSWORD_VERIFIER", "PASSWORDVERIFIER" -> "PASSWORD_VERIFIER"
            else -> challenge.trim().uppercase()
        }
        val path = "$AUTH_PATH/$normalized"
        val body = linkedMapOf(
            "session" to session,
            "challenge" to normalized,
            "response" to response.trim()
        )
        val json = request(path, gson.toJson(body))
        val token = json.optString("access_token").ifBlank { json.optString("accessToken") }
        if (token.isBlank()) throw HommynApiException(200, json.toString())
        return AuthResult(token, json)
    }

    private fun request(path: String, body: String): JSONObject {
        val request = Request.Builder()
            .url(HOST + path)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HommynApiException(response.code, text)
            return JSONObject(text)
        }
    }
}
