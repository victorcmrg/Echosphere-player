package com.example.echosphereplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.UUID

/**
 * Gerencia as credenciais do Navidrome de forma segura.
 * Usa EncryptedSharedPreferences para guardar servidor, usuário e senha.
 */
object SessionManager {

    private const val PREFS_FILE = "echosphereplayer_session"
    private const val KEY_SERVER  = "server_url"
    private const val KEY_USER    = "username"
    private const val KEY_PASS    = "password"   // senha armazenada para gerar token na hora

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(context: Context, serverUrl: String, username: String, password: String) {
        val clean = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
        getPrefs(context).edit()
            .putString(KEY_SERVER, clean)
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        val prefs = getPrefs(context)
        return !prefs.getString(KEY_SERVER, null).isNullOrBlank() &&
                !prefs.getString(KEY_USER,   null).isNullOrBlank() &&
                !prefs.getString(KEY_PASS,   null).isNullOrBlank()
    }

    fun getServerUrl(context: Context): String =
        getPrefs(context).getString(KEY_SERVER, "") ?: ""

    fun getUsername(context: Context): String =
        getPrefs(context).getString(KEY_USER, "") ?: ""

    /**
     * Gera o par (token, salt) para a autenticação Subsonic.
     * token = MD5(senha + salt)
     */
    fun getAuthTokens(context: Context): Pair<String, String> {
        val password = getPrefs(context).getString(KEY_PASS, "") ?: ""
        val salt = UUID.randomUUID().toString().replace("-", "").take(10)
        val token = md5(password + salt)
        return Pair(token, salt)
    }

    fun logout(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

