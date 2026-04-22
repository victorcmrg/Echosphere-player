package com.example.echosphereplayer

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    val songList   = MutableLiveData<List<SubsonicSong>>()
    val isLoading  = MutableLiveData<Boolean>()
    val errorMsg   = MutableLiveData<String?>()

    /**
     * Busca as músicas recentes/aleatórias do Navidrome.
     * Usa search3 com query vazia para pegar as últimas adicionadas.
     */
    fun fetchLibrary(context: Context) {
        isLoading.value = true
        errorMsg.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serverUrl = SessionManager.getServerUrl(context)
                val username  = SessionManager.getUsername(context)
                val (token, salt) = SessionManager.getAuthTokens(context)

                val api = RetrofitClient.getSubsonicApi(serverUrl)
                val response = api.search(
                    query     = "",          // string vazia retorna músicas recentes
                    user      = username,
                    token     = token,
                    salt      = salt,
                    songCount = 50
                )

                val root = response.`subsonic-response`
                if (root.status == "ok") {
                    val songs = root.searchResult3?.song ?: emptyList()
                    // Favoritas aparecem primeiro
                    val sorted = songs.sortedByDescending { it.starred != null }
                    withContext(Dispatchers.Main) {
                        songList.value = sorted
                        isLoading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMsg.value = root.error?.message ?: "Erro na API"
                        isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg.value = "Falha de rede: ${e.message}"
                    isLoading.value = false
                }
            }
        }
    }

    /** Busca com query do usuário (barra de busca) */
    fun search(context: Context, query: String) {
        if (query.isBlank()) { fetchLibrary(context); return }
        isLoading.value = true
        errorMsg.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serverUrl = SessionManager.getServerUrl(context)
                val username  = SessionManager.getUsername(context)
                val (token, salt) = SessionManager.getAuthTokens(context)

                val api = RetrofitClient.getSubsonicApi(serverUrl)
                val response = api.search(
                    query     = query,
                    user      = username,
                    token     = token,
                    salt      = salt,
                    songCount = 30
                )

                val songs = response.`subsonic-response`.searchResult3?.song ?: emptyList()
                withContext(Dispatchers.Main) {
                    songList.value = songs
                    isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg.value = "Erro na busca: ${e.message}"
                    isLoading.value = false
                }
            }
        }
    }
}