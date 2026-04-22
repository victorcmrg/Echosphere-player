package com.example.echosphereplayer

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.echosphereplayer.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.palette.graphics.Palette
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import android.graphics.Color

enum class PlaybackMode {
    NEXT, LOOP_PERMANENT, RANDOM, LOOP_ONCE
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null

    private var currentPlayingSongId: String? = null
    private var isCurrentSongStarred = false

    var currentPlaybackMode = PlaybackMode.NEXT

    private val starredOverrides = mutableMapOf<String, Boolean>()

    fun updateFavoriteState(songId: String, isStarred: Boolean) {
        starredOverrides[songId] = isStarred
        if (currentPlayingSongId == songId) {
            isCurrentSongStarred = isStarred
            updateMiniPlayerStarUI(isStarred)
        }
    }

    fun getFavoriteState(songId: String, defaultState: Boolean): Boolean {
        return starredOverrides[songId] ?: defaultState
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (SessionManager.isLoggedIn(this)) {
            AppCache.init(this)
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.customFooter.onTabSelectedListener = { index ->
            val destination = when (index) {
                0    -> R.id.homeFragment
                1    -> R.id.searchFragment
                else -> R.id.playlistsFragment
            }
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, inclusive = false, saveState = true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
            navController.navigate(destination, null, navOptions)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.playerFragment -> {
                    binding.customFooter.visibility = View.GONE
                    binding.miniPlayerContainer.root.visibility = View.GONE
                }
                else -> {
                    binding.customFooter.visibility = View.VISIBLE
                    when (destination.id) {
                        R.id.homeFragment -> binding.customFooter.setSelectedTab(0)
                        R.id.searchFragment -> binding.customFooter.setSelectedTab(1)
                        R.id.playlistsFragment -> binding.customFooter.setSelectedTab(2)
                    }
                    if (mediaController?.currentMediaItem != null) {
                        updateMiniPlayerUI(mediaController?.currentMediaItem)
                    }
                }
            }
        }

        binding.miniPlayerContainer.root.setOnClickListener {
            val navOptions = NavOptions.Builder()
                .setEnterAnim(R.anim.slide_up)
                .setExitAnim(android.R.anim.fade_out)
                .setPopEnterAnim(android.R.anim.fade_in)
                .setPopExitAnim(R.anim.slide_down)
                .build()

            navController.navigate(R.id.playerFragment, null, navOptions)
        }

        binding.miniPlayerContainer.miniBtnPlayPause.setOnClickListener {
            mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        // PATCH: Ação do Favorito no Mini Player avisa a tela inicial!
        binding.miniPlayerContainer.btnMiniFavorite.setOnClickListener {
            val songId = currentPlayingSongId ?: return@setOnClickListener
            val serverUrl = SessionManager.getServerUrl(this)
            val username = SessionManager.getUsername(this)
            val (token, salt) = SessionManager.getAuthTokens(this)

            val newState = !isCurrentSongStarred
            updateFavoriteState(songId, newState)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val api = RetrofitClient.getSubsonicApi(serverUrl)
                    if (newState) api.starSong(songId, username, token, salt)
                    else api.unstarSong(songId, username, token, salt)

                    withContext(Dispatchers.Main) {
                        showTopNotification(if (newState) "Favoritada!" else "Removida dos Favoritos")

                        // DISPARA EVENTO GLOBAL DE FAVORITO
                        val bundle = Bundle().apply {
                            putString("songId", songId)
                            putBoolean("isStarred", newState)
                        }
                        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                        navHost?.childFragmentManager?.setFragmentResult("star_update", bundle)

                        (navHost?.childFragmentManager?.primaryNavigationFragment as? PlaylistDetailFragment)
                            ?.onSongStarChanged(songId, newState)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateFavoriteState(songId, !newState)
                        showTopNotification("Erro de conexão")
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }

    fun playMediaItem(mediaItem: MediaItem) {
        mediaController?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateMiniPlayerUI(mediaController?.currentMediaItem)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMiniPlayerUI(mediaItem)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && currentPlaybackMode == PlaybackMode.LOOP_ONCE) {
                    setPlaybackMode(PlaybackMode.NEXT)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val icon = if (isPlaying) R.drawable.mini_player_pause else R.drawable.mini_player_play
                binding.miniPlayerContainer.miniBtnPlayPause.setImageResource(icon)
                if (isPlaying && binding.miniPlayerContainer.root.visibility != View.VISIBLE) {
                    updateMiniPlayerUI(mediaController?.currentMediaItem)
                }
            }
        })

        updateMiniPlayerUI(mediaController?.currentMediaItem)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        currentPlaybackMode = mode
        mediaController?.let { mc ->
            when (mode) {
                PlaybackMode.NEXT -> { mc.repeatMode = Player.REPEAT_MODE_ALL; mc.shuffleModeEnabled = false }
                PlaybackMode.LOOP_PERMANENT -> { mc.repeatMode = Player.REPEAT_MODE_ONE; mc.shuffleModeEnabled = false }
                PlaybackMode.RANDOM -> { mc.repeatMode = Player.REPEAT_MODE_ALL; mc.shuffleModeEnabled = true }
                PlaybackMode.LOOP_ONCE -> { mc.repeatMode = Player.REPEAT_MODE_ONE; mc.shuffleModeEnabled = false }
            }
        }
    }

    private fun updateMiniPlayerUI(mediaItem: MediaItem?) {
        val container = binding.miniPlayerContainer.root

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentDestId = navHost?.navController?.currentDestination?.id
        val isAllowedScreen = currentDestId != R.id.loginFragment && currentDestId != R.id.playerFragment

        if (mediaItem == null || !isAllowedScreen) {
            if (container.visibility == View.VISIBLE) {
                container.animate().translationY(container.height.toFloat() + 100f).setDuration(300)
                    .withEndAction { container.visibility = View.GONE }.start()
            }
            notifyVinylState(false)
            return
        }

        currentPlayingSongId = mediaItem.mediaId
        notifyVinylState(true)

        if (container.visibility != View.VISIBLE) {
            container.visibility = View.VISIBLE
            container.translationY = 0f
            container.setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        binding.miniPlayerContainer.miniTitle.text = mediaItem.mediaMetadata.title
        binding.miniPlayerContainer.miniArtist.text = mediaItem.mediaMetadata.artist

        val baseStarred = mediaItem.mediaMetadata.extras?.getBoolean("isStarred") ?: false
        isCurrentSongStarred = getFavoriteState(mediaItem.mediaId, baseStarred)
        updateMiniPlayerStarUI(isCurrentSongStarred)

        mediaItem.mediaMetadata.artworkUri?.let { uri ->
            val capturedId = mediaItem.mediaId
            val request = ImageRequest.Builder(this).data(uri).allowHardware(false)
                .target(onSuccess = { result ->
                    if (currentPlayingSongId != capturedId) return@target
                    binding.miniPlayerContainer.miniCover.setImageDrawable(result)
                    val bitmap = (result as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            if (currentPlayingSongId != capturedId) return@generate
                            val bgColor = (palette?.dominantSwatch ?: palette?.mutedSwatch)?.rgb ?: Color.parseColor("#1A1A1A")
                            val shape = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f, 0f, 0f, 0f, 0f)
                                setColor(bgColor)
                                alpha = 255
                            }
                            container.background = shape
                            binding.miniPlayerContainer.miniTitle.setTextColor((palette?.dominantSwatch ?: palette?.mutedSwatch)?.titleTextColor ?: Color.WHITE)
                            binding.miniPlayerContainer.miniArtist.setTextColor((palette?.dominantSwatch ?: palette?.mutedSwatch)?.bodyTextColor ?: Color.parseColor("#B3B3B3"))
                        }
                    }
                }, onError = {
                    if (currentPlayingSongId == capturedId) {
                        binding.miniPlayerContainer.miniCover.setImageDrawable(null)
                        container.setBackgroundColor(Color.parseColor("#1A1A1A"))
                    }
                }).build()
            imageLoader.enqueue(request)
        } ?: run {
            binding.miniPlayerContainer.miniCover.setImageDrawable(null)
            container.setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
    }

    private fun notifyVinylState(hasMiniPlayer: Boolean) {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        (navHost?.childFragmentManager?.primaryNavigationFragment as? PlaylistDetailFragment)
            ?.refreshVinylPosition(hasMiniPlayer)
    }

    private fun updateMiniPlayerStarUI(isStarred: Boolean) {
        binding.miniPlayerContainer.btnMiniFavorite.setImageResource(
            if (isStarred) R.drawable.mini_player_star_full else R.drawable.mini_player_star_empty
        )
    }

    fun showTopNotification(message: String) {
        val banner = binding.topNotification.root
        banner.findViewById<android.widget.TextView>(R.id.tv_notification_message)?.text = message
        banner.visibility = View.VISIBLE
        banner.translationY = -300f
        banner.animate().translationY(0f).setDuration(300).withEndAction {
            banner.postDelayed({
                banner.animate().translationY(-300f).setDuration(300).withEndAction { banner.visibility = View.GONE }.start()
            }, 3000)
        }.start()
    }
}