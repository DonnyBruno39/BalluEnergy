package ru.balluenergy.app.hommyn

import android.content.Context
import android.os.Build
import android.util.Base64
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Hommyn challenge/response authentication client. */
object HommynAuth {
    private const val HOST = "https://auth-iot.api.rusklimat.ru"
    private const val AUTH_PATH = "/auth"
    private const val HOMMYN_ANDROID_VERSION = "1.19.0"
    private val jsonMediaType = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val random = SecureRandom()

    data class Challenge(val session: String, val challenge: String, val raw: JSONObject)
    data class AuthResult(val accessToken: String, val raw: JSONObject)

    fun init(context: Context, phone: String): Challenge {
        val value = phone.trim()
        require(value.isNotBlank()) { "phone is empty" }

        val primary = buildPayload(context, value, useLegacyMarker = false)
        return try {
            parseChallenge(request(AUTH_PATH, gson.toJson(primary)))
        } catch (e: HommynApiException) {
            if (e.code == 500 && e.response.contains("SMS_SEND_FAILED", ignoreCase = true)) {
                val fallback = buildPayload(context, value, useLegacyMarker = true)
                parseChallenge(request(AUTH_PATH, gson.toJson(fallback)))
            } else {
                throw e
            }
        }
    }

    fun authorize(session: String, challenge: String, response: String): AuthResult {
        val normalized = when (challenge.trim().uppercase()) {
            "SMS", "SMS_CODE", "SMSCODE" -> "SMS_CODE"
            "EMAIL", "EMAIL_CODE", "EMAILCODE" -> "EMAIL_CODE"
            "PASSWORD", "PASSWORD_VERIFIER", "PASSWORDVERIFIER" -> "PASSWORD_VERIFIER"
            else -> challenge.trim().uppercase()
        }
        require(session.isNotBlank()) { "session is empty" }
        require(response.trim().isNotBlank()) { "verification code is empty" }
        val body = linkedMapOf(
            "session" to session,
            "challenge" to normalized,
            "response" to response.trim()
        )
        val json = request("$AUTH_PATH/$normalized", gson.toJson(body))
        val token = firstNonBlank(json, "access_token", "accessToken", "token")
        if (token.isBlank()) throw HommynApiException(200, json.toString())
        return AuthResult(token, json)
    }

    private fun buildPayload(context: Context, value: String, useLegacyMarker: Boolean): Map<String, Any> {
        val marker = if (useLegacyMarker) legacyMarker() else mapOf("a" to "com.hommyn.app")
        val locales = ArrayList<String>()
        val localeList = context.resources.configuration.locales
        for (i in 0 until localeList.size()) locales.add(localeList[i].toString())
        if (locales.isEmpty()) locales.add(java.util.Locale.getDefault().toString())

        return linkedMapOf(
            (if (Regex("^\\+?\\d+$").matches(value)) "phone" else "email") to value,
            "platform" to "android",
            "osVersion" to (Build.VERSION.RELEASE ?: "unknown"),
            "vendor" to (Build.BRAND ?: Build.MANUFACTURER ?: "unknown"),
            "model" to (Build.MODEL ?: "unknown"),
            "name" to (Build.USER ?: "unknown"),
            "deviceInfo" to marker,
            "locales" to locales,
            "bundle" to "com.hommyn.app",
            "version" to HOMMYN_ANDROID_VERSION,
            "client" to marker
        )
    }

    private fun legacyMarker(): Map<String, String> {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val zbd = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return mapOf("zbd" to zbd)
    }

    private fun parseChallenge(json: JSONObject): Challenge {
        val session = firstNonBlank(json, "session", "sessionId", "session_id")
        val challenge = firstNonBlank(json, "challenge", "challengeType", "challenge_type")
        if (session.isBlank() || challenge.isBlank()) throw HommynApiException(200, json.toString())
        return Challenge(session, challenge, json)
    }

    private fun firstNonBlank(json: JSONObject, vararg names: String): String {
        for (name in names) {
            val value = json.opt(name)
            if (value != null && value != JSONObject.NULL) {
                val text = value.toString().trim()
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    private fun request(path: String, body: String): JSONObject {
        val request = Request.Builder()
            .url(HOST + path)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Hommyn/$HOMMYN_ANDROID_VERSION (Android)")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HommynApiException(response.code, text)
            return try { JSONObject(text) } catch (_: Exception) {
                throw HommynApiException(response.code, "Invalid JSON response: $text")
            }
        }
    }
}
