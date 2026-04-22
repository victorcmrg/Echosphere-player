package com.example.echosphereplayer

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.echosphereplayer.databinding.FragmentAllSongsBinding

class AllSongsFragment : Fragment() {

    private var _binding: FragmentAllSongsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topGlowEffect.background = ThemeManager.getDynamicGlow(requireContext())

        if (!SessionManager.isLoggedIn(requireContext())) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        // PATCH: Escuta mudanças de favoritos
        parentFragmentManager.setFragmentResultListener("star_update", viewLifecycleOwner) { _, bundle ->
            val songId = bundle.getString("songId") ?: return@setFragmentResultListener
            val isStarred = bundle.getBoolean("isStarred")

            val currentList = viewModel.songList.value ?: return@setFragmentResultListener
            val updatedList = currentList.map { song ->
                if (song.id == songId) song.copy(starred = if (isStarred) "yes" else null) else song
            }
            viewModel.songList.value = updatedList
        }

        binding.btnBackLibrary.setOnClickListener {
            findNavController().popBackStack()
        }

        if (viewModel.songList.value.isNullOrEmpty()) {
            viewModel.fetchLibrary(requireContext())
        }

        val serverUrl = SessionManager.getServerUrl(requireContext())
        val username = SessionManager.getUsername(requireContext())
        val (token, salt) = SessionManager.getAuthTokens(requireContext())

        songAdapter = SongAdapter(
            serverUrl = serverUrl,
            username = username,
            token = token,
            salt = salt,
            onClick = { clickedSong ->
                val streamUrl = RetrofitClient.buildStreamUrl(serverUrl, clickedSong.id, username, token, salt)
                val coverUrl = RetrofitClient.buildCoverArtUrl(serverUrl, clickedSong.coverArt ?: "", username, token, salt)

                val metadata = MediaMetadata.Builder()
                    .setTitle(clickedSong.title)
                    .setArtist(clickedSong.artist ?: "Artista Desconhecido")
                    .setArtworkUri(Uri.parse(coverUrl))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(clickedSong.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(metadata)
                    .build()

                (requireActivity() as MainActivity).playMediaItem(mediaItem)
            },
            onMenuClick = { clickedSong ->
                val bottomSheet = AddToPlaylistBottomSheet(clickedSong.id)
                bottomSheet.show(childFragmentManager, "AddToPlaylistBottomSheet")
            }
        )

        binding.rvSongs.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.swipeRefreshHome.setColorSchemeColors(android.graphics.Color.parseColor("#D4AF37"))
        binding.swipeRefreshHome.setOnRefreshListener {
            viewModel.fetchLibrary(requireContext())
        }

        viewModel.songList.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
            binding.swipeRefreshHome.isRefreshing = false
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMsg.observe(viewLifecycleOwner) { error ->
            binding.swipeRefreshHome.isRefreshing = false
            error?.let {
                if (it.contains("Wrong username") || it.contains("credentials")) {
                    SessionManager.logout(requireContext())
                    findNavController().navigate(R.id.loginFragment)
                }
            }
        }

        viewModel.fetchLibrary(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}