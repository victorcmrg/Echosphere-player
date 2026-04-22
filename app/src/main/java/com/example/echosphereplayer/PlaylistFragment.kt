package com.example.echosphereplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.echosphereplayer.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── ATUALIZAÇÃO INSTANTÂNEA: Escuta quando o Player adiciona/remove/favorita ──
        parentFragmentManager.setFragmentResultListener("playlist_updated", viewLifecycleOwner) { _, _ ->
            AppCache.reload(requireContext())
        }

        val serverUrl     = SessionManager.getServerUrl(requireContext())
        val username      = SessionManager.getUsername(requireContext())
        val (token, salt) = SessionManager.getAuthTokens(requireContext())

        adapter = PlaylistAdapter(serverUrl, username, token, salt) { playlist ->
            val bundle = Bundle().apply { putString("playlistId", playlist.id) }
            findNavController().navigate(R.id.playlistDetailFragment, bundle)
        }

        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = adapter

        // ── Observa o cache → atualiza instantaneamente ───────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            AppCache.playlists.collectLatest { list ->
                if (_binding == null) return@collectLatest

                // Vai buscar a quantidade real de favoritas silenciosamente em background
                val starredCount = withContext(Dispatchers.IO) {
                    try {
                        val api = RetrofitClient.getSubsonicApi(serverUrl)
                        val resp = api.getStarred(username, token, salt)
                        resp.`subsonic-response`.starred?.song?.size ?: 0
                    } catch (e: Exception) {
                        0
                    }
                }

                val favPlaylist = SubsonicPlaylist(
                    id = "FAVORITES",
                    name = "Favoritas",
                    songCount = starredCount // <-- CONTAGEM REAL!
                )

                // ── FILTRO ANTIBUG: Remove pastas "Favoritas" falsas enviadas pelo servidor ──
                val filteredList = list.filter {
                    !it.name.equals("Favoritas", ignoreCase = true) && !it.name.equals("Favorites", ignoreCase = true)
                }

                // Junta as Favoritas no topo da lista limpa e envia para o adapter
                adapter.submitList(listOf(favPlaylist) + filteredList)
            }
        }

        // ── Spinner: controlado exclusivamente pelo StateFlow dedicado ─────────
        viewLifecycleOwner.lifecycleScope.launch {
            AppCache.playlistsRefreshing.collectLatest { refreshing ->
                if (_binding == null) return@collectLatest
                binding.swipeRefreshPlaylists.isRefreshing = refreshing
            }
        }

        // ── Pull-to-refresh ───────────────────────────────────────────────────
        binding.swipeRefreshPlaylists.setColorSchemeColors(
            android.graphics.Color.parseColor("#D4AF37")
        )
        binding.swipeRefreshPlaylists.setOnRefreshListener {
            AppCache.reload(requireContext())
        }

        // ── FAB: nova playlist ────────────────────────────────────────────────
        binding.fabNewPlaylist.setOnClickListener {
            CreatePlaylistBottomSheet().show(childFragmentManager, "CreatePlaylist")
        }

        // ── Após criar playlist → recarrega cache ─────────────────────────────
        requireActivity().supportFragmentManager
            .setFragmentResultListener("RELOAD_PLAYLISTS", viewLifecycleOwner) { _, _ ->
                AppCache.reload(requireContext())
            }

        // ── Se o cache já tem dados, mostra imediatamente; senão, carrega ──────
        if (AppCache.playlists.value.isEmpty()) {
            AppCache.init(requireContext())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}