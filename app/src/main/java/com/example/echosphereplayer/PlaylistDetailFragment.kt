package com.example.echosphereplayer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.echosphereplayer.databinding.FragmentPlaylistDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaylistLoadData(
    val name: String,
    val dateLabel: String,
    val description: String,
    val songs: List<SubsonicSong>
)

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter
    private var currentPlaylistName = ""
    private var currentPlaylistDesc = ""
    private var currentSongs = listOf<SubsonicSong>()

    private var progressJob: Job? = null

    private var baseVinylTranslationY = 0f
    private var currentVinylTargetY = 0f
    private var isVinylBaseInitialized = false
    private var vinylHiddenByScroll = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_binding != null) {
                songAdapter.currentPlayingId = mediaItem?.mediaId
                updateVinylPosition(hasMiniPlayer = mediaItem != null, animate = true)
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_binding != null) {
                binding.vinylTrackSelector.setPlaying(isPlaying)
                if (isPlaying) startProgressUpdate() else progressJob?.cancel()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentFragmentManager.setFragmentResultListener("star_update", viewLifecycleOwner) { _, bundle ->
            val songId = bundle.getString("songId") ?: return@setFragmentResultListener
            val isStarred = bundle.getBoolean("isStarred")
            onSongStarChanged(songId, isStarred)
        }

        binding.topGlowEffect.background = ThemeManager.getDynamicGlow(requireContext())

        parentFragmentManager.setFragmentResultListener("song_removed", viewLifecycleOwner) { _, bundle ->
            val songId = bundle.getString("songId") ?: return@setFragmentResultListener
            currentSongs = currentSongs.filter { it.id != songId }
            songAdapter.submitList(currentSongs)
            binding.tvPlaylistMeta.text = "${currentSongs.size} músicas • Automática"
            binding.vinylTrackSelector.setSongs(currentSongs)
        }

        val playlistId    = arguments?.getString("playlistId") ?: return
        val serverUrl     = SessionManager.getServerUrl(requireContext())
        val username      = SessionManager.getUsername(requireContext())
        val (token, salt) = SessionManager.getAuthTokens(requireContext())

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        if (playlistId == "FAVORITES") {
            binding.btnPlaylistMenu.visibility = View.GONE
        } else {
            binding.btnPlaylistMenu.setOnClickListener {
                showPlaylistMenu(playlistId, serverUrl, username, token, salt)
            }
        }

        songAdapter = SongAdapter(
            serverUrl   = serverUrl,
            username    = username,
            token       = token,
            salt        = salt,
            onClick     = { song -> playSong(song, playlistId, serverUrl, username, token, salt) },
            onMenuClick = { song ->
                if (playlistId == "FAVORITES") {
                    (requireActivity() as MainActivity).showTopNotification("Desmarque a estrela no player para remover.")
                } else {
                    val index = currentSongs.indexOf(song)
                    if (index >= 0) showSongMenu(playlistId, song, index, serverUrl, username, token, salt)
                }
            }
        )

        binding.rvPlaylistSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylistSongs.adapter = songAdapter

        setupVinylScrollBehavior()

        binding.vinylTrackSelector.setVinylImage(binding.imgVinylDisc)
        binding.vinylTrackSelector.onSongSelected = { selectedSong ->
            playSong(selectedSong, playlistId, serverUrl, username, token, salt)
            binding.vinylTrackSelector.currentPlayingId = selectedSong.id
            binding.vinylTrackSelector.invalidate()
        }

        val mediaController = (requireActivity() as MainActivity).mediaController
        mediaController?.addListener(playerListener)
        binding.vinylTrackSelector.setPlaying(mediaController?.isPlaying == true)

        songAdapter.currentPlayingId = mediaController?.currentMediaItem?.mediaId
        if (mediaController?.isPlaying == true) {
            startProgressUpdate()
        }

        binding.btnPlayAll.setOnClickListener {
            if (currentSongs.isNotEmpty()) {
                val mainActivity = activity as? MainActivity ?: return@setOnClickListener
                mainActivity.setPlaybackMode(PlaybackMode.NEXT)
                val items = currentSongs.mapIndexed { i, s -> buildMediaItem(s, i, playlistId, serverUrl, username, token, salt) }
                mainActivity.mediaController?.apply {
                    setMediaItems(items)
                    seekToDefaultPosition(0)
                    prepare()
                    play()
                }
            }
        }

        binding.btnShuffleAll.setOnClickListener {
            if (currentSongs.isNotEmpty()) {
                val mainActivity = activity as? MainActivity ?: return@setOnClickListener
                mainActivity.setPlaybackMode(PlaybackMode.RANDOM)
                val items = currentSongs.mapIndexed { i, s -> buildMediaItem(s, i, playlistId, serverUrl, username, token, salt) }
                mainActivity.mediaController?.apply {
                    setMediaItems(items)
                    val randomIndex = (0 until items.size).random()
                    seekToDefaultPosition(randomIndex)
                    prepare()
                    play()
                }
            }
        }

        loadPlaylistDetails(playlistId, serverUrl, username, token, salt)
    }

    override fun onResume() {
        super.onResume()
        val hasMiniPlayer = (requireActivity() as MainActivity).mediaController?.currentMediaItem != null
        updateVinylPosition(hasMiniPlayer, animate = true)
    }

    private fun buildMediaItem(
        song: SubsonicSong, index: Int, playlistId: String, serverUrl: String,
        username: String, token: String, salt: String
    ): MediaItem {
        val streamUrl = RetrofitClient.buildStreamUrl(serverUrl, song.id, username, token, salt)
        val coverUrl  = RetrofitClient.buildCoverArtUrl(serverUrl, song.coverArt ?: "", username, token, salt)

        val extras = Bundle().apply {
            putBoolean("isStarred", song.starred != null)
            putString("playlistId", playlistId)
            putInt("songIndex", index)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist ?: "Artista Desconhecido")
            .setArtworkUri(Uri.parse(coverUrl))
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun playSong(
        song: SubsonicSong, playlistId: String, serverUrl: String,
        username: String, token: String, salt: String
    ) {
        val mainActivity = requireActivity() as MainActivity
        val items = currentSongs.mapIndexed { i, s -> buildMediaItem(s, i, playlistId, serverUrl, username, token, salt) }
        val index = currentSongs.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0

        mainActivity.mediaController?.apply {
            setMediaItems(items)
            seekToDefaultPosition(index)
            prepare()
            play()
        }

        binding.vinylTrackSelector.currentPlayingId = song.id
        binding.vinylTrackSelector.invalidate()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val controller = (requireActivity() as MainActivity).mediaController
                if (controller?.isPlaying == true) {
                    val duration = controller.duration.takeIf { it > 0 } ?: 1L
                    val position = controller.currentPosition
                    val progress = position.toFloat() / duration.toFloat()

                    controller.currentMediaItem?.mediaId?.let { songId ->
                        songAdapter.updateProgress(songId, progress)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun setupVinylScrollBehavior() {
        val vinylContainer = binding.vinylContainer
        vinylContainer.post {
            baseVinylTranslationY = vinylContainer.translationY
            isVinylBaseInitialized = true
            val controller = (requireActivity() as MainActivity).mediaController
            updateVinylPosition(hasMiniPlayer = controller?.currentMediaItem != null, animate = false)
        }
        val hideOffsetPx = 400f

        binding.rvPlaylistSongs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private val returnRunnable = Runnable {
                if (vinylHiddenByScroll) {
                    vinylHiddenByScroll = false
                    vinylContainer.animate().translationY(currentVinylTargetY)
                        .setDuration(350).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
            }
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        recyclerView.removeCallbacks(returnRunnable)
                        if (!vinylHiddenByScroll) {
                            vinylHiddenByScroll = true
                            vinylContainer.animate().translationY(currentVinylTargetY + hideOffsetPx)
                                .setDuration(450).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
                        }
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> recyclerView.postDelayed(returnRunnable, 1000)
                    RecyclerView.SCROLL_STATE_SETTLING -> recyclerView.removeCallbacks(returnRunnable)
                }
            }
        })
    }

    private fun updateVinylPosition(hasMiniPlayer: Boolean, animate: Boolean = true) {
        if (!isVinylBaseInitialized) return
        val offsetDp = -75f
        val offsetPx = offsetDp * resources.displayMetrics.density
        currentVinylTargetY = baseVinylTranslationY + (if (hasMiniPlayer) offsetPx else 0f)

        if (!vinylHiddenByScroll) {
            if (animate) {
                binding.vinylContainer.animate().translationY(currentVinylTargetY)
                    .setDuration(300).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            } else {
                binding.vinylContainer.translationY = currentVinylTargetY
            }
        }
    }

    // --- NOVA FUNÇÃO ADICIONADA: Comunicação direta com a MainActivity ---
    fun refreshVinylPosition(hasMiniPlayer: Boolean) {
        if (_binding != null) {
            updateVinylPosition(hasMiniPlayer, animate = true)
        }
    }

    private fun showPlaylistMenu(
        playlistId: String, serverUrl: String,
        username: String, token: String, salt: String
    ) {
        if (_binding == null) return
        val sheet     = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_playlist_menu, null)
        sheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btn_delete_playlist).setOnClickListener {
            sheet.dismiss()
            deletePlaylist(playlistId, serverUrl, username, token, salt)
        }

        sheetView.findViewById<View>(R.id.btn_edit_playlist)?.setOnClickListener {
            sheet.dismiss()
            showEditPlaylistSheet(playlistId, currentPlaylistName, currentPlaylistDesc, serverUrl, username, token, salt)
        }

        sheet.show()
    }

    private fun showEditPlaylistSheet(
        playlistId: String, currentName: String, currentDesc: String,
        serverUrl: String, username: String, token: String, salt: String
    ) {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_edit_playlist, null)
        sheet.setContentView(view)

        val inputName = view.findViewById<EditText>(R.id.input_playlist_name)
        val inputDesc = view.findViewById<EditText>(R.id.input_playlist_desc)
        val btnSave = view.findViewById<Button>(R.id.btn_save_playlist)
        val coverContainer = view.findViewById<FrameLayout>(R.id.preview_cover_container)
        val tvInitials = view.findViewById<TextView>(R.id.preview_cover_initials)
        val imgPreview = view.findViewById<ImageView>(R.id.preview_cover_image)

        inputName.setText(currentName)
        inputDesc.setText(currentDesc)

        val prefs = requireContext().getSharedPreferences("PlaylistCustomCovers", Context.MODE_PRIVATE)
        var selectedCoverArtId: String? = null

        fun updateCoverPreview() {
            val displayCover = selectedCoverArtId ?: prefs.getString(playlistId, null) ?: currentSongs.firstOrNull()?.coverArt

            if (!displayCover.isNullOrBlank()) {
                imgPreview.load(RetrofitClient.buildCoverArtUrl(serverUrl, displayCover, username, token, salt))
                tvInitials.visibility = View.GONE
            } else {
                imgPreview.setImageDrawable(null)
                coverContainer.setBackgroundColor(PlaylistMeta.getSavedColor(requireContext(), playlistId, currentName))
                tvInitials.text = PlaylistMeta.getInitials(currentName)
                tvInitials.visibility = View.VISIBLE
            }
        }
        updateCoverPreview()

        coverContainer.setOnClickListener {
            if (currentSongs.isEmpty()) {
                (requireActivity() as MainActivity).showTopNotification("A playlist está vazia. Adicione músicas primeiro!")
                return@setOnClickListener
            }

            val adapter = object : ArrayAdapter<SubsonicSong>(requireContext(), R.layout.item_cover_choice, currentSongs) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val rowView = convertView ?: layoutInflater.inflate(R.layout.item_cover_choice, parent, false)
                    val song = getItem(position)
                    val img = rowView.findViewById<ImageView>(R.id.img_choice_cover)
                    val txt = rowView.findViewById<TextView>(R.id.tv_choice_title)

                    txt.text = song?.title
                    img.load(RetrofitClient.buildCoverArtUrl(serverUrl, song?.coverArt ?: "", username, token, salt)) {
                        crossfade(true)
                    }
                    return rowView
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Escolher Capa")
                .setAdapter(adapter) { _, which ->
                    selectedCoverArtId = currentSongs[which].coverArt
                    updateCoverPreview()
                }
                .show()
        }

        btnSave.setOnClickListener {
            val newName = inputName.text.toString().trim()
            val newDesc = inputDesc.text.toString().trim()
            if (newName.isBlank()) return@setOnClickListener

            if (selectedCoverArtId != null) {
                prefs.edit().putString(playlistId, selectedCoverArtId).apply()
            }

            sheet.dismiss()
            currentPlaylistName = newName
            currentPlaylistDesc = newDesc
            binding.detailName.text = newName

            if (newDesc.isNotBlank()) {
                binding.detailDescription.text = newDesc
                binding.detailDescription.visibility = View.VISIBLE
            } else {
                binding.detailDescription.visibility = View.GONE
            }

            setupCoverUI(currentSongs, playlistId, newName, serverUrl, username, token, salt)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    RetrofitClient.getSubsonicApi(serverUrl).updatePlaylist(
                        playlistId = playlistId,
                        name = newName,
                        comment = newDesc,
                        user = username, token = token, salt = salt
                    )
                    withContext(Dispatchers.Main) { AppCache.reload(requireContext()) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { AppCache.reload(requireContext()) }
                }
            }
        }
        sheet.show()
    }

    private fun deletePlaylist(
        playlistId: String, serverUrl: String,
        username: String, token: String, salt: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                RetrofitClient.getSubsonicApi(serverUrl)
                    .deletePlaylist(playlistId = playlistId, user = username, token = token, salt = salt)
                AppCache.removePlaylistLocally(playlistId)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    (requireActivity() as MainActivity).showTopNotification("Playlist deletada.")
                    findNavController().popBackStack()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    (requireActivity() as MainActivity).showTopNotification("Erro ao deletar playlist.")
                }
            }
        }
    }

    private fun showSongMenu(
        playlistId: String, song: SubsonicSong, songIndex: Int,
        serverUrl: String, username: String, token: String, salt: String
    ) {
        if (_binding == null) return
        val sheet     = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_song_menu, null)
        sheet.setContentView(sheetView)
        sheetView.findViewById<View>(R.id.btn_remove_from_playlist).setOnClickListener {
            sheet.dismiss()
            removeSongFromPlaylist(playlistId, song, songIndex, serverUrl, username, token, salt)
        }
        sheet.show()
    }

    private fun removeSongFromPlaylist(
        playlistId: String, song: SubsonicSong, songIndex: Int,
        serverUrl: String, username: String, token: String, salt: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                RetrofitClient.getSubsonicApi(serverUrl).removeFromPlaylist(
                    playlistId = playlistId, songIndex = songIndex,
                    user = username, token = token, salt = salt
                )
                currentSongs.toMutableList().also { it.removeAt(songIndex) }
            }.onSuccess { updated ->
                currentSongs = updated
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    songAdapter.submitList(updated.toList())
                    binding.vinylTrackSelector.setSongs(currentSongs)
                    (requireActivity() as MainActivity).showTopNotification("\"${song.title}\" removida.")
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    (requireActivity() as MainActivity).showTopNotification("Erro ao remover música.")
                }
            }
        }
    }

    fun onSongStarChanged(songId: String, isStarred: Boolean) {
        if (_binding == null) return
        val playlistId = arguments?.getString("playlistId")

        if (playlistId == "FAVORITES" && !isStarred) {
            currentSongs = currentSongs.filter { it.id != songId }
            songAdapter.submitList(currentSongs)
            binding.tvPlaylistMeta.text = "${currentSongs.size} músicas • Automática"
            binding.vinylTrackSelector.setSongs(currentSongs)
        } else {
            currentSongs = currentSongs.map { s ->
                if (s.id == songId) s.copy(starred = if (isStarred) "yes" else null) else s
            }
            songAdapter.submitList(currentSongs)
        }
    }

    private fun loadPlaylistDetails(
        playlistId: String, serverUrl: String,
        username: String, token: String, salt: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val api = RetrofitClient.getSubsonicApi(serverUrl)
                if (playlistId == "FAVORITES") {
                    val resp = api.getStarred(user = username, token = token, salt = salt)
                    val songs = resp.`subsonic-response`.starred?.song ?: emptyList()
                    PlaylistLoadData("Favoritas", "Automática", "", songs)
                } else {
                    val resp = api.getPlaylist(playlistId = playlistId, user = username, token = token, salt = salt)
                    val pl   = resp.`subsonic-response`.playlist ?: error("Playlist não encontrada")
                    val dateStr = pl.created?.substringBefore("T") ?: "Desconhecida"
                    val desc = pl.comment ?: ""
                    PlaylistLoadData(pl.name, dateStr, desc, pl.entry ?: emptyList())
                }
            }.onSuccess { data ->
                currentPlaylistName = data.name
                currentPlaylistDesc = data.description
                currentSongs = data.songs

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext

                    binding.detailName.text = data.name
                    val totalSec = data.songs.sumOf { it.duration ?: 0 }
                    val timeStr  = if (totalSec > 3600)
                        String.format("%dh %02dmin", totalSec / 3600, (totalSec % 3600) / 60)
                    else
                        String.format("%d min", totalSec / 60)
                    binding.tvPlaylistMeta.text = "${data.songs.size} músicas • ${data.dateLabel}"
                    binding.btnPlayAll.text = "Iniciar • $timeStr"

                    if (data.description.isNotBlank()) {
                        binding.detailDescription.text = data.description
                        binding.detailDescription.visibility = View.VISIBLE
                    } else {
                        binding.detailDescription.visibility = View.GONE
                    }

                    setupCoverUI(data.songs, playlistId, data.name, serverUrl, username, token, salt)
                    songAdapter.submitList(data.songs)
                    binding.vinylTrackSelector.setSongs(data.songs)
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    (requireActivity() as MainActivity).showTopNotification("Erro ao carregar detalhes")
                }
            }
        }
    }

    private fun setupCoverUI(
        songs: List<SubsonicSong>, id: String, name: String,
        serverUrl: String, username: String, token: String, salt: String
    ) {
        val prefs = requireContext().getSharedPreferences("PlaylistCustomCovers", Context.MODE_PRIVATE)
        val finalCoverArt = prefs.getString(id, null) ?: songs.firstOrNull()?.coverArt

        if (!finalCoverArt.isNullOrBlank()) {
            binding.detailCover.load(
                RetrofitClient.buildCoverArtUrl(serverUrl, finalCoverArt, username, token, salt)
            ) { crossfade(true) }
            binding.tvDetailInitials.visibility = View.GONE
        } else {
            binding.detailCover.setImageDrawable(null)
            binding.detailCover.setBackgroundColor(PlaylistMeta.getSavedColor(requireContext(), id, name))
            binding.tvDetailInitials.visibility = View.VISIBLE
            binding.tvDetailInitials.text = if (id == "FAVORITES") "FV" else PlaylistMeta.getInitials(name)
        }
    }

    override fun onDestroyView() {
        (requireActivity() as MainActivity).mediaController?.removeListener(playerListener)
        progressJob?.cancel()
        binding.vinylContainer.animate().cancel()
        binding.rvPlaylistSongs.clearOnScrollListeners()
        super.onDestroyView()
        _binding = null
    }
}