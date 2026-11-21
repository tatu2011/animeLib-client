package com.example.animelibclient

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receives AnimeLib's registered HTTPS OAuth callback and exchanges its code using PKCE. */
class AnimeLibOAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Завершаю вход в AnimeLib…"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(14, 14, 20))
            gravity = Gravity.CENTER
        })

        val callback = intent?.data
        if (!isExpectedCallback(callback)) {
            returnToApp(error = "Не удалось проверить адрес возврата AnimeLib. Начни вход заново.")
            return
        }
        val callbackUri = requireNotNull(callback)
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val returnedState = callbackUri.getQueryParameter("state")
        val expectedState = preferences.getString(AnimeLibOAuth.CALLBACK_STATE, null)
        val verifier = preferences.getString(AnimeLibOAuth.CALLBACK_VERIFIER, null)

        if (returnedState.isNullOrBlank() || returnedState != expectedState || verifier.isNullOrBlank()) {
            returnToApp(error = "Не удалось проверить возврат AnimeLib. Начни вход заново.")
            return
        }

        val code = callbackUri.getQueryParameter("code")
        val oauthError = callbackUri.getQueryParameter("error_description")
            ?: callbackUri.getQueryParameter("error")
        preferences.edit()
            .remove(AnimeLibOAuth.CALLBACK_STATE)
            .remove(AnimeLibOAuth.CALLBACK_VERIFIER)
            .apply()

        if (code.isNullOrBlank()) {
            returnToApp(error = oauthError ?: "AnimeLib не вернул код авторизации.")
            return
        }

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AnimeLibOAuth.exchangeCodeForToken(code, verifier)
                }
            }
            val token = result.getOrNull()
            returnToApp(
                token = token,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    private fun returnToApp(token: String? = null, error: String? = null) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (!token.isNullOrBlank()) putExtra(MainActivity.EXTRA_AUTH_TOKEN, token)
            if (!error.isNullOrBlank()) putExtra(MainActivity.EXTRA_AUTH_ERROR, error)
        }
        startActivity(appIntent)
        finish()
    }

    private fun isExpectedCallback(uri: Uri?): Boolean =
        uri?.scheme == "https" &&
            uri.host == "animelib.org" &&
            uri.path == CALLBACK_PATH

    companion object {
        private const val PREFS_NAME = "anime_lib_auth"
        private const val CALLBACK_PATH = "/ru/front/auth/oauth/callback"
    }
}
