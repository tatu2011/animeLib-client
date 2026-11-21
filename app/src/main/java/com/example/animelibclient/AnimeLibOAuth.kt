package com.example.animelibclient

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import java.net.URL

/** Starts AnimeLib's public OAuth/PKCE flow in the user's default Android browser. */
internal object AnimeLibOAuth {
    const val REDIRECT_URI = "https://animelib.org/ru/front/auth/oauth/callback"
    const val CALLBACK_STATE = "animelib_oauth"
    const val CALLBACK_VERIFIER = "animelib_oauth_verifier"

    private const val AUTHORIZATION_ENDPOINT = "https://auth.hentaicdn.org/auth/oauth/authorize"
    private const val TOKEN_ENDPOINT = "https://hapi.hentaicdn.org/api/auth/oauth/token"
    private const val CLIENT_ID = "1"
    private const val PREFS_NAME = "anime_lib_auth"

    fun createBrowserIntent(context: Context): Intent {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(32)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(CALLBACK_STATE, state)
            .putString(CALLBACK_VERIFIER, verifier)
            .commit()
        if (!saved) throw IllegalStateException("Не удалось подготовить безопасный вход AnimeLib")

        val authorizationUri = Uri.parse(AUTHORIZATION_ENDPOINT).buildUpon()
            .appendQueryParameter("scope", "")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("iframe", "false")
            .build()

        return Intent(Intent.ACTION_VIEW, authorizationUri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
    }

    fun exchangeCodeForToken(code: String, verifier: String): String {
        val connection = URL(TOKEN_ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Site-Id", "5")
            connection.setRequestProperty("Origin", "https://animelib.org")
            connection.setRequestProperty("Referer", "https://animelib.org/")
            connection.setRequestProperty("Client-Time-Zone", TimeZone.getDefault().id)
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36"
            )

            val requestBody = JSONObject()
                .put("grant_type", "authorization_code")
                .put("client_id", CLIENT_ID)
                .put("redirect_uri", REDIRECT_URI)
                .put("code_verifier", verifier)
                .put("code", code)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(requestBody) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val serverMessage = runCatching {
                    val error = JSONObject(body)
                    error.optString("message").ifBlank { error.optString("error_description") }
                }.getOrDefault("")
                val suffix = serverMessage.takeIf { it.isNotBlank() && it != "null" }
                    ?.let { ": ${it.take(180)}" }.orEmpty()
                throw IllegalStateException("AnimeLib OAuth вернул HTTP $status$suffix")
            }

            val root = JSONObject(body)
            val token = root.optString("access_token")
                .ifBlank { root.optJSONObject("data")?.optString("access_token").orEmpty() }
            if (token.isBlank() || token == "null") {
                throw IllegalStateException("AnimeLib не вернул access token")
            }
            return token.removePrefix("Bearer ").trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}
