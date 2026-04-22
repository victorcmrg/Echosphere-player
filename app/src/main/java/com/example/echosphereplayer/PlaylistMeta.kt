package com.example.echosphereplayer

import android.content.Context
import android.graphics.Color

/**
 * Centraliza tudo relacionado à identidade visual e descrição de playlists.
 *
 * Os metadados (cor e descrição) são salvos localmente no SharedPreferences
 * usando o ID da playlist como chave, porque a API Subsonic não tem campo
 * de comentário/cor acessível de forma padronizada.
 */
object PlaylistMeta {

    private const val PREFS = "playlist_meta"
    private val PALETTE = listOf("#9B5EF9", "#9099FE", "#71BEF6", "#F97C5E", "#5EF9A0", "#F9D05E")

    // ── Cor ──────────────────────────────────────────────────────────────────

    fun getColorHex(name: String): String {
        val index = Math.abs(name.hashCode()) % PALETTE.size
        return PALETTE[index]
    }

    fun getColor(name: String): Int = Color.parseColor(getColorHex(name))

    // ── Iniciais ─────────────────────────────────────────────────────────────

    fun getInitials(name: String): String {
        val words = name.trim().split("\\s+".toRegex())
        return when {
            words.size >= 2  -> "${words[0].first()}${words[1].first()}".uppercase()
            words[0].isNotEmpty() -> words[0].take(2).uppercase()
            else -> "PL"
        }
    }

    // ── Persistência local ────────────────────────────────────────────────────

    /**
     * Salva cor (hex) e descrição associadas ao ID (ou nome) da playlist.
     */
    fun saveMeta(context: Context, playlistKey: String, colorHex: String, description: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("color_$playlistKey", colorHex)
            .putString("desc_$playlistKey",  description)
            .apply()
    }

    /**
     * Retorna a cor salva, ou calcula pela chave como fallback.
     */
    fun getSavedColorHex(context: Context, playlistKey: String, fallbackName: String): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("color_$playlistKey", null)
            ?: getColorHex(fallbackName)
    }

    fun getSavedColor(context: Context, playlistKey: String, fallbackName: String): Int =
        Color.parseColor(getSavedColorHex(context, playlistKey, fallbackName))

    /**
     * Retorna a descrição salva (vazia se não houver).
     */
    fun getDescription(context: Context, playlistKey: String): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("desc_$playlistKey", "") ?: ""
    }
}