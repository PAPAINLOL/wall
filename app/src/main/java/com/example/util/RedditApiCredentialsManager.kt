package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RedditApiCredentialsManager {

    private const val PREFS_NAME = "reddit_api_config"
    private const val KEY_CLIENT_ID = "reddit_client_id"
    private const val KEY_CLIENT_SECRET = "reddit_client_secret"
    private const val PLACEHOLDER_ID = "YOUR_REDDIT_CLIENT_ID"
    private const val PLACEHOLDER_SECRET = "YOUR_REDDIT_CLIENT_SECRET"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getClientId(context: Context): String {
        val saved = getPrefs(context).getString(KEY_CLIENT_ID, "")?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        val buildCfg = BuildConfig.REDDIT_CLIENT_ID.trim()
        return if (buildCfg.isNotBlank() && buildCfg != PLACEHOLDER_ID) buildCfg else ""
    }

    fun getClientSecret(context: Context): String {
        val saved = getPrefs(context).getString(KEY_CLIENT_SECRET, "")?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        val buildCfg = BuildConfig.REDDIT_CLIENT_SECRET.trim()
        return if (buildCfg.isNotBlank() && buildCfg != PLACEHOLDER_SECRET) buildCfg else ""
    }

    fun saveCredentials(context: Context, clientId: String, clientSecret: String) {
        getPrefs(context).edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
        AppLogger.i("RedditAuth", "Credenciales de API de Reddit guardadas persistentemente")
    }

    fun clearCredentials(context: Context) {
        getPrefs(context).edit().clear().apply()
        AppLogger.i("RedditAuth", "Credenciales guardadas eliminadas")
    }

    fun hasCredentials(context: Context): Boolean {
        return getClientId(context).isNotBlank()
    }

    /**
     * Tests Reddit OAuth credentials live by requesting an access token from Reddit.
     * Supports both 'personal use script' (client_credentials) and 'installed app' (installed_client).
     */
    suspend fun testCredentials(clientId: String, clientSecret: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()

        if (cleanId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El Client ID no puede estar vacío"))
        }

        try {
            val credentials = Base64.encodeToString("$cleanId:$cleanSecret".toByteArray(), Base64.NO_WRAP)
            val formBuilder = FormBody.Builder()
            if (cleanSecret.isNotBlank()) {
                formBuilder.add("grant_type", "client_credentials")
            } else {
                formBuilder.add("grant_type", "https://oauth.reddit.com/grants/installed_client")
                formBuilder.add("device_id", "DO_NOT_TRACK_THIS_DEVICE")
            }

            val request = Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .header("Authorization", "Basic $credentials")
                .header("User-Agent", "android:com.aistudio.redditwallpapers:v1.2.0 (by /u/tv_curator)")
                .post(formBuilder.build())
                .build()

            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val token = json.optString("access_token")
                    if (token.isNotBlank()) {
                        AppLogger.s("RedditAuth", "Token OAuth de Reddit verificado con éxito (código 200)")
                        return@withContext Result.success(token)
                    } else {
                        val errorDesc = json.optString("error", "Respuesta inesperada sin token")
                        return@withContext Result.failure(Exception("Error de Reddit: $errorDesc"))
                    }
                } else {
                    val errorMsg = when (code) {
                        401 -> "HTTP 401 Unauthorized: El Client ID o el Client Secret son incorrectos"
                        403 -> "HTTP 403 Forbidden: Reddit denegó la solicitud"
                        429 -> "HTTP 429 Too Many Requests: Límite de tasa excedido, espera unos segundos"
                        else -> "HTTP $code: $body"
                    }
                    AppLogger.e("RedditAuth", "Fallo al verificar token: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("RedditAuth", "Error de red al conectar con Reddit API", e)
            Result.failure(Exception("Error de conexión: ${e.message ?: "Tiempo de espera agotado"}"))
        }
    }
}
