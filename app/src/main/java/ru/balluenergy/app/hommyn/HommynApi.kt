package ru.balluenergy.app.hommyn

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Hommyn cloud client recovered from Hommyn 39 APK.
 *
 * Important: authentication is intentionally kept separate from this class.
 * The Hommyn APK exposes a challenge/response auth flow; until that flow is
 * reproduced exactly, BalluEnergy must not invent credentials or tokens.
 */
object HommynApi {
    private const val DEVICES_HOST = "https://user-iot.api.rusklimat.ru"
    private const val DEVICES_PATH = "/devices/all/short"

    data class Device(
        val id: String?,
        val mac: String?,
        val name: String?,
        val model: String?,
        val token: String?,
        val deviceType: String?,
        val vendor: String?,
        val host: String?,
        val raw: JSONObject
    )

    fun getDevices(accessToken: String): List<Device> {
        require(accessToken.isNotBlank()) { "accessToken is empty" }

        val connection = (URL(DEVICES_HOST + DEVICES_PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw HommynApiException(code, body)
            }
            return parseDevices(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDevices(body: String): List<Device> {
        val root = JSONObject(body)
        val array = when {
            root.opt("devices") is JSONArray -> root.getJSONArray("devices")
            root.opt("data") is JSONArray -> root.getJSONArray("data")
            root.opt("items") is JSONArray -> root.getJSONArray("items")
            else -> JSONArray()
        }

        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    Device(
                        id = first(o, "id", "deviceId", "device_id"),
                        mac = first(o, "mac", "macAddress", "mac_address"),
                        name = first(o, "name", "title"),
                        model = first(o, "model", "deviceModel"),
                        token = first(o, "token", "deviceToken", "device_token"),
                        deviceType = first(o, "device_type", "deviceType", "type"),
                        vendor = first(o, "device_vendor", "deviceVendor", "vendor"),
                        host = first(o, "host", "mqttHost"),
                        raw = o
                    )
                )
            }
        }
    }

    private fun first(o: JSONObject, vararg names: String): String? {
        for (name in names) {
            val value = o.opt(name)
            if (value != null && value != JSONObject.NULL) {
                val text = value.toString()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }
}

class HommynApiException(val code: Int, val response: String) : Exception(
    "Hommyn API HTTP $code: $response"
)
