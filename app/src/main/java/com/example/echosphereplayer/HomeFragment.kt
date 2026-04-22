package com.example.echosphereplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.echosphereplayer.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()

    private lateinit var playlistAdapter: PlaylistHorizontalAdapter
    private lateinit var songAdapter: SongHorizontalAdapter

    private var vinylAnimator: ObjectAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!SessionManager.isLoggedIn(requireContext())) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        val serverUrl = SessionManager.getServerUrl(requireContext())
        val username = SessionManager.getUsername(requireContext())
        val (token, salt) = SessionManager.getAuthTokens(requireContext())

        vinylAnimator = ObjectAnimator.ofFloat(binding.imgStatusVinyl, View.ROTATION, 0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        // Aplica as cores e imagens assim que a tela é criada
        applyTheme()

        // --- AQUI ESTÁ O CÓDIGO DO CLIQUE QUE FALTAVA! ---
        binding.btnSettings.bringToFront() // Força o botão para a frente de tudo
        binding.btnSettings.setOnClickListener {
            val popup = android.widget.PopupMenu(requireContext(), binding.btnSettings)
            popup.menu.add("Editar Interface")
            popup.setOnMenuItemClickListener {
                findNavController().navigate(R.id.editInterfaceFragment)
                true
            }
            popup.show()
        }

        startNowPlayingRadar(serverUrl, username, token, salt)

        parentFragmentManager.setFragmentResultListener("star_update", viewLifecycleOwner) { _, bundle ->
            val songId = bundle.getString("songId") ?: return@setFragmentResultListener
            val isStarred = bundle.getBoolean("isStarred")

            val currentList = viewModel.songList.value ?: return@setFragmentResultListener
            val updatedList = currentList.map { song ->
                if (song.id == songId) song.copy(starred = if (isStarred) "yes" else null) else song
            }
            viewModel.songList.value = updatedList
        }

        binding.headerPlaylists.setOnClickListener {
            (requireActivity() as MainActivity).findViewById<FooterNavigationView>(R.id.custom_footer).performClickTab(2)
        }

        binding.headerAllSongs.setOnClickListener {
            findNavController().navigate(R.id.allSongsFragment)
        }

        binding.swipeRefreshHome.setColorSchemeColors(Color.parseColor("#D4AF37"))
        binding.swipeRefreshHome.setOnRefreshListener {
            viewModel.fetchLibrary(requireContext())
            AppCache.reload(requireContext())
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (!loading) binding.swipeRefreshHome.isRefreshing = false
        }

        playlistAdapter = PlaylistHorizontalAdapter(serverUrl, username, token, salt) { playlist ->
            val bundle = Bundle().apply { putString("playlistId", playlist.id) }
            findNavController().navigate(R.id.playlistDetailFragment, bundle)
        }
        binding.rvHomePlaylists.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHomePlaylists.adapter = playlistAdapter

        if (AppCache.playlists.value.isEmpty()) AppCache.init(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            AppCache.playlists.collectLatest { list ->
                val filteredList = list.filter {
                    !it.name.equals("Favoritas", ignoreCase = true) && !it.name.equals("Favorites", ignoreCase = true)
                }
                playlistAdapter.submitList(filteredList)
            }
        }

        songAdapter = SongHorizontalAdapter(serverUrl, username, token, salt) { clickedSong ->
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
        }

        binding.rvHomeSongs.layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.HORIZONTAL, false)
        binding.rvHomeSongs.adapter = songAdapter

        viewModel.songList.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
        }

        if (viewModel.songList.value.isNullOrEmpty()) {
            viewModel.fetchLibrary(requireContext())
        }
    }

    // Garante que o tema atualiza sempre que você volta da tela de Edição
    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            applyTheme()
        }
    }

    private fun applyTheme() {
        binding.imgTopBanner.setImageDrawable(ThemeManager.getDynamicBackground(requireContext()))
        binding.topGlowEffect.background = ThemeManager.getDynamicGlow(requireContext())

        binding.customImagesContainer.removeAllViews()
        val savedImages = ThemeManager.getCustomImages(requireContext())
        for (config in savedImages) {
            val imageView = android.widget.ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(400, 400)
                setImageURI(android.net.Uri.parse(config.uri))
                x = config.x
                y = config.y
                scaleX = config.scale
                scaleY = config.scale
                rotation = config.rotation
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            binding.customImagesContainer.addView(imageView)
        }
    }

    private fun startNowPlayingRadar(serverUrl: String, username: String, token: String, salt: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val api = RetrofitClient.getSubsonicApi(serverUrl)
                    val response = api.getNowPlaying(user = username, token = token, salt = salt)
                    val entries = response.`subsonic-response`.nowPlaying?.entry
                    val friendPlaying = entries?.firstOrNull { it.username != username }

                    withContext(Dispatchers.Main) {
                        if (friendPlaying != null) {
                            binding.groupOffline.visibility = View.GONE
                            binding.groupPlaying.visibility = View.VISIBLE

                            if (vinylAnimator?.isRunning == false) {
                                vinylAnimator?.start()
                            }

                            val artist = friendPlaying.artist ?: "Desconhecido"
                            binding.tvStatusNowPlaying.text = "${friendPlaying.title} • $artist"
                        } else {
                            binding.groupPlaying.visibility = View.GONE
                            binding.groupOffline.visibility = View.VISIBLE
                            vinylAnimator?.cancel()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.groupPlaying.visibility = View.GONE
                        binding.groupOffline.visibility = View.VISIBLE
                        vinylAnimator?.cancel()
                    }
                }
                delay(10000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vinylAnimator?.cancel()
        vinylAnimator = null
        _binding = null
    }
}