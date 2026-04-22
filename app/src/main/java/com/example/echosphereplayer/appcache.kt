package com.example.echosphereplayer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object AppCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _playlists = MutableStateFlow<List<SubsonicPlaylist>>(emptyList())
    val playlists: StateFlow<List<SubsonicPlaylist>> = _playlists

    private val _recentSongs = MutableStateFlow<List<SubsonicSong>>(emptyList())
    val recentSongs: StateFlow<List<SubsonicSong>> = _recentSongs

    // StateFlow dedicado para o spinner — nunca fica preso em true
    private val _playlistsRefreshing = MutableStateFlow(false)
    val playlistsRefreshing: StateFlow<Boolean> = _playlistsRefreshing

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        load(context.applicationContext)
    }

    fun reload(context: Context) = load(context.applicationContext)

    private fun load(context: Context) {
        // Cancela qualquer load anterior e inicia novo — não empilha
        scope.launch {
            _playlistsRefreshing.value = true
            try {
                val serverUrl = SessionManager.getServerUrl(context)
                val username  = SessionManager.getUsername(context)
                val (token, salt) = SessionManager.getAuthTokens(context)

                if (serverUrl.isBlank() || username.isBlank()) return@launch

                val api = RetrofitClient.getSubsonicApi(serverUrl)

                val playlistsJob = launch {
                    runCatching {
                        val resp = api.getPlaylists(user = username, token = token, salt = salt)
                        val fromApi = resp.`subsonic-response`.playlists?.playlist ?: emptyList()
                        val favorites = SubsonicPlaylist(id = "FAVORITES", name = "Favoritas", songCount = 0)
                        _playlists.value = listOf(favorites) + fromApi
                    }
                }

                val songsJob = launch {
                    runCatching {
                        val resp = api.search(
                            query = "", user = username, token = token, salt = salt, songCount = 50
                        )
                        _recentSongs.value = resp.`subsonic-response`.searchResult3?.song ?: emptyList()
                    }
                }

                playlistsJob.join()
                songsJob.join()

            } finally {
                // SEMPRE para o spinner, mesmo em erro ou exceção
                _playlistsRefreshing.value = false
            }
        }
    }

    fun removePlaylistLocally(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
    }

    fun setPlaylists(list: List<SubsonicPlaylist>) {
        _playlists.value = list
    }
}