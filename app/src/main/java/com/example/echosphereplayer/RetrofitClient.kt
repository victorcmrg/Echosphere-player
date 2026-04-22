package com.example.echosphereplayer

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── API Python (Busca e Download no Ubuntu) ────────────────────────────
    fun getPythonApi(serverUrl: String): PythonApi {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PythonApi::class.java)
    }

    // ── API Subsonic / Navidrome ───────────────────────────────────────────
    private var _subsonicApi: SubsonicApi? = null
    private var _lastServerUrl: String = ""

    fun getSubsonicApi(serverUrl: String): SubsonicApi {
        // Recria apenas se a URL mudou (troca de servidor)
        if (serverUrl != _lastServerUrl || _subsonicApi == null) {
            val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            _subsonicApi = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SubsonicApi::class.java)
            _lastServerUrl = serverUrl
        }
        return _subsonicApi!!
    }

    /** URL para stream de uma música — use direto no ExoPlayer */
    fun buildStreamUrl(serverUrl: String, songId: String, user: String, token: String, salt: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return "${base}rest/stream?id=$songId&u=$user&t=$token&s=$salt&v=1.16.1&c=EchoSphere&f=json"
    }

    /** URL para capa do álbum — use com Coil: coil: imageView.load(url) */
    fun buildCoverArtUrl(serverUrl: String, coverArtId: String, user: String, token: String, salt: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return "${base}rest/getCoverArt?id=$coverArtId&u=$user&t=$token&s=$salt&v=1.16.1&c=EchoSphere"
    }
}