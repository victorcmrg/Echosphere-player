package com.example.echosphereplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.echosphereplayer.databinding.FragmentSearchBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: SearchAdapter
    private var debounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchAdapter = SearchAdapter(
            onAddClick = { clickedSong ->
                showPlaylistPopupForDownload(clickedSong)
            },
            onPreviewClick = { clickedSong ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clickedSong.url))
                startActivity(intent)
            }
        )

        binding.topGlowEffect.background = ThemeManager.getDynamicGlow(requireContext())

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                debounceJob?.cancel()
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    binding.rvSearchResults.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    return
                }
                debounceJob = lifecycleScope.launch {
                    delay(500)
                    performSearch(query)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun performSearch(query: String) {
        binding.tvEmptyState.visibility = View.GONE
        binding.rvSearchResults.visibility = View.GONE
        binding.progressSearch.visibility = View.VISIBLE

        val pythonServerUrl = SessionManager.getServerUrl(requireContext()).replace("4533", "5000")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.getPythonApi(pythonServerUrl)
                val response = api.searchMusic(query)
                withContext(Dispatchers.Main) {
                    binding.progressSearch.visibility = View.GONE
                    binding.rvSearchResults.visibility = View.VISIBLE
                    searchAdapter.submitList(response.results)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressSearch.visibility = View.GONE
                    (requireActivity() as MainActivity).showTopNotification("Erro ao pesquisar música.")
                }
            }
        }
    }

    private fun showPlaylistPopupForDownload(song: PythonSearchResult) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_playlist_picker, null)
        val rvPicker   = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_playlist_picker)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .create()

        val navidromeUrl   = SessionManager.getServerUrl(requireContext())
        val pythonServerUrl = navidromeUrl.replace("4533", "5000")
        val username        = SessionManager.getUsername(requireContext())
        val (token, salt)   = SessionManager.getAuthTokens(requireContext())

        val adapter = PlaylistAdapter(navidromeUrl, username, token, salt) { playlist ->
            dialog.dismiss()
            requireActivity().lifecycleScope.launch(Dispatchers.IO) {
                downloadAndAddToPlaylist(
                    song            = song,
                    playlist        = playlist,
                    navidromeUrl    = navidromeUrl,
                    pythonServerUrl = pythonServerUrl,
                    username        = username,
                    token           = token,
                    salt            = salt
                )
            }
        }

        rvPicker.layoutManager = LinearLayoutManager(requireContext())
        rvPicker.adapter = adapter

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.getSubsonicApi(navidromeUrl)
                    .getPlaylists(user = username, token = token, salt = salt)
                withContext(Dispatchers.Main) {
                    adapter.submitList(response.`subsonic-response`.playlists?.playlist ?: emptyList())
                    dialog.show()
                }
            } catch (e: Exception) { }
        }
    }

    private suspend fun downloadAndAddToPlaylist(
        song: PythonSearchResult,
        playlist: SubsonicPlaylist,
        navidromeUrl: String,
        pythonServerUrl: String,
        username: String,
        token: String,
        salt: String
    ) {
        val mainActivity = activity as? MainActivity ?: return

        withContext(Dispatchers.Main) {
            mainActivity.showTopNotification("⬇️ Baixando: ${song.title}...")
        }

        try {
            // 1. Download
            val api     = RetrofitClient.getPythonApi(pythonServerUrl)
            val request = PythonDownloadRequest(url = song.url, title = song.title, thumbnail = song.thumbnail)
            val downloadResp = api.downloadMusic(request)

            if (!downloadResp.isSuccessful) {
                withContext(Dispatchers.Main) { mainActivity.showTopNotification("❌ Falha no download.") }
                return
            }

            withContext(Dispatchers.Main) {
                mainActivity.showTopNotification("✅ Música baixada! Aguardando indexação...")
            }

            // 2. Dispara scan no Navidrome
            val subsonic = RetrofitClient.getSubsonicApi(navidromeUrl)
            try { subsonic.startScan(user = username, token = token, salt = salt) } catch (_: Exception) { }

            // 3. Busca o ID novo
            val cleanQuery = song.title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
            var newSongId: String? = null
            val maxAttempts = 60
            val intervalMs  = 5_000L

            for (attempt in 1..maxAttempts) {
                delay(intervalMs)
                if (attempt % 5 == 0) {
                    try { subsonic.startScan(user = username, token = token, salt = salt) } catch (_: Exception) {}
                }

                try {
                    val searchResp = subsonic.search(query = cleanQuery, user = username, token = token, salt = salt)
                    val foundSongs = searchResp.`subsonic-response`.searchResult3?.song
                    if (!foundSongs.isNullOrEmpty()) {
                        newSongId = foundSongs.minByOrNull { s ->
                            levenshtein(s.title.lowercase(), song.title.lowercase())
                        }?.id ?: foundSongs.first().id
                        break
                    }
                } catch (_: Exception) { }
            }

            // 4. ADICIONA À PLAYLIST COM VERIFICAÇÃO ANTI-DUPLICADOS CORRIGIDA
            if (newSongId != null) {
                try {
                    // CÓDIGO CORRIGIDO AQUI
                    val playlistResponse = subsonic.getPlaylist(playlistId = playlist.id, user = username, token = token, salt = salt)
                    val playlistAtual = playlistResponse.`subsonic-response`.playlist
                    val jaExiste = playlistAtual?.entry?.any { it.id == newSongId } == true

                    if (jaExiste) {
                        withContext(Dispatchers.Main) {
                            mainActivity.showTopNotification("⚠️ Esta música já está na playlist \"${playlist.name}\"!")
                        }
                        return // Retorno normal dentro de suspend fun
                    }

                    // SE PASSOU, ADICIONA
                    subsonic.updatePlaylist(
                        playlistId  = playlist.id,
                        songIdToAdd = newSongId,
                        user        = username,
                        token       = token,
                        salt        = salt
                    )
                    withContext(Dispatchers.Main) {
                        mainActivity.showTopNotification("🎵 Adicionada em \"${playlist.name}\"!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        mainActivity.showTopNotification("⚠️ Baixada, mas falhou ao adicionar à playlist.")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    mainActivity.showTopNotification("📁 Baixada! Servidor demorou a indexar — adicione manualmente.")
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                mainActivity.showTopNotification("❌ Erro: ${e.message?.take(60)}")
            }
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i-1][j-1]
            else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[a.length][b.length]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}