package com.example.echosphereplayer

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.example.echosphereplayer.databinding.FragmentPlayerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val mediaController get() = (requireActivity() as MainActivity).mediaController
    private var progressJob: Job? = null
    private var currentSpeed = 1f

    private var currentPlayingSongId: String? = null
    private var isCurrentSongStarred = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_binding != null) updateUI(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_binding != null) updatePlayPauseButton(isPlaying)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (_binding != null) {
                (activity as? MainActivity)?.let { updateRunnerIcon(it.currentPlaybackMode) }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (_binding != null) {
                (activity as? MainActivity)?.let { updateRunnerIcon(it.currentPlaybackMode) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCollapse.setOnClickListener { findNavController().popBackStack() }

        binding.topGlowEffect.background = ThemeManager.getDynamicGlow(requireContext())

        binding.btnPlayerMenu.setOnClickListener {
            val currentItem = mediaController?.currentMediaItem
            val playlistId = currentItem?.mediaMetadata?.extras?.getString("playlistId")
            val songIndex = currentItem?.mediaMetadata?.extras?.getInt("songIndex", -1) ?: -1

            val sheet = BottomSheetDialog(requireContext())
            val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_song_menu, null)
            sheet.setContentView(sheetView)

            val btnRemove = sheetView.findViewById<View>(R.id.btn_remove_from_playlist)

            if (playlistId.isNullOrBlank() || playlistId == "FAVORITES" || songIndex == -1) {
                btnRemove.alpha = 0.4f
                btnRemove.setOnClickListener {
                    (requireActivity() as MainActivity).showTopNotification("Esta música não pertence a uma playlist editável.")
                }
            } else {
                btnRemove.setOnClickListener {
                    sheet.dismiss()
                    val serverUrl = SessionManager.getServerUrl(requireContext())
                    val username = SessionManager.getUsername(requireContext())
                    val (token, salt) = SessionManager.getAuthTokens(requireContext())

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            RetrofitClient.getSubsonicApi(serverUrl).removeFromPlaylist(
                                playlistId = playlistId, songIndex = songIndex,
                                user = username, token = token, salt = salt
                            )
                            withContext(Dispatchers.Main) {
                                (requireActivity() as MainActivity).showTopNotification("Música removida da playlist!")

                                parentFragmentManager.setFragmentResult("song_removed", Bundle().apply {
                                    putString("songId", currentPlayingSongId)
                                })

                                // Grita para a tela principal de playlists que uma música foi apagada
                                parentFragmentManager.setFragmentResult("playlist_updated", Bundle())

                                mediaController?.let { mc ->
                                    val indexToRemove = mc.currentMediaItemIndex
                                    if (mc.hasNextMediaItem()) {
                                        mc.seekToNextMediaItem()
                                    }
                                    mc.removeMediaItem(indexToRemove)
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                (requireActivity() as MainActivity).showTopNotification("Erro ao remover música.")
                            }
                        }
                    }
                }
            }
            sheet.show()
        }

        binding.btnPlayerFavorite.setOnClickListener {
            val songId = currentPlayingSongId ?: return@setOnClickListener
            val serverUrl = SessionManager.getServerUrl(requireContext())
            val username = SessionManager.getUsername(requireContext())
            val (token, salt) = SessionManager.getAuthTokens(requireContext())

            val newState = !isCurrentSongStarred
            isCurrentSongStarred = newState
            updateFavoriteIcon(isCurrentSongStarred)

            (activity as? MainActivity)?.updateFavoriteState(songId, newState)

            parentFragmentManager.setFragmentResult("star_update", Bundle().apply {
                putString("songId", songId)
                putBoolean("isStarred", newState)
            })

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val api = RetrofitClient.getSubsonicApi(serverUrl)
                    if (newState) {
                        api.starSong(songId, username, token, salt)
                    } else {
                        api.unstarSong(songId, username, token, salt)
                    }
                    withContext(Dispatchers.Main) {
                        (activity as? MainActivity)?.showTopNotification(if (newState) "Favoritada!" else "Removida dos Favoritos")
                        // Grita para atualizar a contagem nas Playlists imediatamente
                        parentFragmentManager.setFragmentResult("playlist_updated", Bundle())
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isCurrentSongStarred = !newState
                        updateFavoriteIcon(isCurrentSongStarred)
                        (activity as? MainActivity)?.updateFavoriteState(songId, !newState)
                        (activity as? MainActivity)?.showTopNotification("Erro ao favoritar")
                    }
                }
            }
        }

        binding.btnPlayPause.setOnClickListener {
            mediaController?.let {
                if (it.playbackState == Player.STATE_ENDED) {
                    it.seekToDefaultPosition()
                    it.play()
                } else if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        }

        binding.btnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }

        binding.btnPrev.setOnClickListener {
            mediaController?.let {
                if (it.currentPosition > 2000) {
                    it.seekTo(0)
                } else {
                    it.seekToPreviousMediaItem()
                }
            }
        }

        binding.btnRewind.setOnClickListener {
            mediaController?.let {
                val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                it.seekTo(newPos)
            }
        }

        binding.btnForward.setOnClickListener {
            mediaController?.let {
                val duration = it.duration.takeIf { d -> d > 0 } ?: 0L
                val newPos = (it.currentPosition + 10000).coerceAtMost(duration)
                it.seekTo(newPos)
            }
        }

        binding.btnAddToPlaylist.setOnClickListener {
            val currentMediaItem = mediaController?.currentMediaItem
            currentMediaItem?.mediaId?.let { songId ->
                showPlaylistPopup(songId)
            }
        }

        binding.btnSpeed.setOnClickListener {
            currentSpeed = when (currentSpeed) {
                1f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2f
                else -> 1f
            }
            binding.btnSpeed.text = "${currentSpeed}x"
            mediaController?.playbackParameters = PlaybackParameters(currentSpeed)
        }

        val mainActivity = activity as? MainActivity
        mainActivity?.let { updateRunnerIcon(it.currentPlaybackMode) }

        binding.btnRunnerType.setOnClickListener {
            if (mainActivity == null) return@setOnClickListener
            val nextMode = when (mainActivity.currentPlaybackMode) {
                PlaybackMode.NEXT -> PlaybackMode.RANDOM
                PlaybackMode.RANDOM -> PlaybackMode.LOOP_PERMANENT
                PlaybackMode.LOOP_PERMANENT -> PlaybackMode.LOOP_ONCE
                PlaybackMode.LOOP_ONCE -> PlaybackMode.NEXT
            }
            mainActivity.setPlaybackMode(nextMode)
        }

        binding.playerSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                progressJob?.cancel()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { mediaController?.seekTo(it.progress.toLong()) }
                startProgressUpdate()
            }
        })

        updateUI(mediaController?.currentMediaItem)
        updatePlayPauseButton(mediaController?.isPlaying == true)
        mediaController?.addListener(playerListener)
    }

    private fun updateRunnerIcon(mode: PlaybackMode) {
        val iconRes = when (mode) {
            PlaybackMode.NEXT -> R.drawable.ic_mode_next
            PlaybackMode.RANDOM -> R.drawable.random_icon
            PlaybackMode.LOOP_PERMANENT -> R.drawable.ic_mode_loop
            PlaybackMode.LOOP_ONCE -> R.drawable.ic_mode_loop_once
        }
        binding.btnRunnerType.setImageResource(iconRes)
    }

    private fun updateFavoriteIcon(isStarred: Boolean) {
        if (isStarred) {
            binding.btnPlayerFavorite.setImageResource(R.drawable.mini_player_star_full)
            binding.btnPlayerFavorite.clearColorFilter()
        } else {
            binding.btnPlayerFavorite.setImageResource(R.drawable.mini_player_star_empty)
            binding.btnPlayerFavorite.setColorFilter(Color.parseColor("#4E4E4E"))
        }
    }

    private fun showPlaylistPopup(songId: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_playlist_picker, null)
        val rvPicker = dialogView.findViewById<RecyclerView>(R.id.rv_playlist_picker)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .create()

        val serverUrl = SessionManager.getServerUrl(requireContext())
        val username = SessionManager.getUsername(requireContext())
        val (token, salt) = SessionManager.getAuthTokens(requireContext())

        val adapter = PlaylistAdapter(serverUrl, username, token, salt) { playlist ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val api = RetrofitClient.getSubsonicApi(serverUrl)

                    if (playlist.id == "FAVORITES") {
                        api.starSong(songId, username, token, salt)
                    } else {
                        api.updatePlaylist(
                            playlistId = playlist.id,
                            songIdToAdd = songId,
                            user = username, token = token, salt = salt
                        )
                    }

                    withContext(Dispatchers.Main) {
                        (requireActivity() as MainActivity).showTopNotification("Música adicionada a ${playlist.name}!")
                        // Grita para o ecrã de playlists para se atualizar sozinho
                        parentFragmentManager.setFragmentResult("playlist_updated", Bundle())
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        (requireActivity() as MainActivity).showTopNotification("Erro ao adicionar.")
                    }
                }
            }
        }

        rvPicker.layoutManager = LinearLayoutManager(requireContext())
        rvPicker.adapter = adapter

        // --- Filtro Antibug de Favoritos Duplicados ---
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.getSubsonicApi(serverUrl)
                val response = api.getPlaylists(user = username, token = token, salt = salt)
                val list = response.`subsonic-response`.playlists?.playlist ?: emptyList()

                // Puxa as músicas favoritas para sabermos o número exato!
                val starredResp = api.getStarred(user = username, token = token, salt = salt)
                val starredCount = starredResp.`subsonic-response`.starred?.song?.size ?: 0
                val favPlaylist = SubsonicPlaylist(id = "FAVORITES", name = "Favoritas", songCount = starredCount)

                // Removemos qualquer playlist falsa criada pelo servidor com o nome "Favoritas" ou "Favorites"
                val filteredList = list.filter {
                    !it.name.equals("Favoritas", ignoreCase = true) && !it.name.equals("Favorites", ignoreCase = true)
                }

                val fullList = listOf(favPlaylist) + filteredList

                withContext(Dispatchers.Main) {
                    adapter.submitList(fullList)
                    dialog.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    (requireActivity() as MainActivity).showTopNotification("Erro ao carregar playlists.")
                }
            }
        }
    }

    private fun updateUI(mediaItem: MediaItem?) {
        mediaItem?.let { item ->
            currentPlayingSongId = item.mediaId

            val baseStarred = item.mediaMetadata.extras?.getBoolean("isStarred") ?: false
            isCurrentSongStarred = (activity as? MainActivity)?.getFavoriteState(item.mediaId, baseStarred) ?: baseStarred
            updateFavoriteIcon(isCurrentSongStarred)

            binding.playerTitle.text = item.mediaMetadata.title
            binding.playerArtist.text = item.mediaMetadata.artist

            item.mediaMetadata.artworkUri?.let { uri ->
                val request = ImageRequest.Builder(requireContext())
                    .data(uri)
                    .allowHardware(false)
                    .target(
                        onSuccess = { result ->
                            if (currentPlayingSongId != item.mediaId) return@target
                            binding.playerCover.setImageDrawable(result)

                            val bitmap = (result as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                Palette.from(bitmap).generate { palette ->
                                    if (currentPlayingSongId != item.mediaId) return@generate

                                    val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                                    val glowColor = swatch?.rgb ?: Color.WHITE

                                    binding.playerCoverGlow.setColorFilter(glowColor)
                                    binding.playerCoverGlow.animate()
                                        .alpha(0.8f)
                                        .setDuration(600)
                                        .start()
                                }
                            }
                        },
                        onError = {
                            if (currentPlayingSongId == item.mediaId) {
                                binding.playerCover.setImageDrawable(null)
                                binding.playerCoverGlow.alpha = 0f
                            }
                        }
                    )
                    .build()
                requireContext().imageLoader.enqueue(request)
            } ?: run {
                binding.playerCover.setImageDrawable(null)
                binding.playerCoverGlow.alpha = 0f
            }

            viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                val duration = mediaController?.duration?.takeIf { d -> d > 0 } ?: 0L
                binding.playerSeekbar.max = duration.toInt()
                binding.tvTotalTime.text = formatTime(duration)
                startProgressUpdate()
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.white_stop_music else R.drawable.white_play_music
        binding.btnPlayPause.setImageResource(icon)
        if (isPlaying) startProgressUpdate() else progressJob?.cancel()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (mediaController?.isPlaying == true) {
                val position = mediaController?.currentPosition ?: 0L
                binding.playerSeekbar.progress = position.toInt()
                binding.tvCurrentTime.text = formatTime(position)
                delay(500)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController?.removeListener(playerListener)
        progressJob?.cancel()
        binding.playerCoverGlow.animate().cancel()
        _binding = null
    }
}