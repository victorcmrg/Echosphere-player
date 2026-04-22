package com.example.echosphereplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.echosphereplayer.databinding.ItemSongBinding

class SongAdapter(
    private val serverUrl: String,
    private val username: String,
    private val token: String,
    private val salt: String,
    private val onClick: (SubsonicSong) -> Unit,
    private val onMenuClick: ((SubsonicSong) -> Unit)? = null
) : ListAdapter<SubsonicSong, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    var currentPlayingId: String? = null
        set(value) {
            val oldId = field
            field = value
            val oldIndex = currentList.indexOfFirst { it.id == oldId }
            if (oldIndex != -1) notifyItemChanged(oldIndex)

            val newIndex = currentList.indexOfFirst { it.id == value }
            if (newIndex != -1) notifyItemChanged(newIndex)
        }

    fun updateProgress(songId: String, progress: Float) {
        if (songId != currentPlayingId) return
        val index = currentList.indexOfFirst { it.id == songId }
        if (index != -1) {
            notifyItemChanged(index, progress)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val progress = payloads[0] as? Float
            if (progress != null) {
                holder.updateProgressOnly(progress)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
            binding.btnSongMenu.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMenuClick?.invoke(getItem(pos))
            }
        }

        fun updateProgressOnly(progress: Float) {
            binding.viewSongProgress.visibility = View.VISIBLE
            val params = binding.viewSongProgress.layoutParams as ConstraintLayout.LayoutParams
            params.matchConstraintPercentWidth = progress.coerceIn(0f, 1f)
            binding.viewSongProgress.layoutParams = params
        }

        fun bind(song: SubsonicSong) {
            binding.tvTitle.text = song.title

            val durationSec = song.duration ?: 0
            val min = durationSec / 60
            val sec = durationSec % 60
            val timeStr = String.format("%d:%02d", min, sec)

            val artistName = song.artist ?: "Artista Desconhecido"
            binding.tvArtist.text = "$artistName • $timeStr"

            if (song.id == currentPlayingId) {
                binding.viewSongProgress.visibility = View.VISIBLE
            } else {
                binding.viewSongProgress.visibility = View.GONE
                val params = binding.viewSongProgress.layoutParams as ConstraintLayout.LayoutParams
                params.matchConstraintPercentWidth = 0f
                binding.viewSongProgress.layoutParams = params
            }

            // PATCH: Verificação rígida do estado favorito
            val mainActivity = itemView.context as? MainActivity
            val isStarred = mainActivity?.getFavoriteState(song.id, song.starred != null) ?: (song.starred != null)

            if (isStarred) {
                binding.imgStar.visibility = View.VISIBLE
                binding.imgStar.setImageResource(R.drawable.mini_player_star_full)
                binding.imgStar.clearColorFilter()
            } else {
                binding.imgStar.visibility = View.GONE
            }

            binding.btnSongMenu.visibility = if (onMenuClick != null) View.VISIBLE else View.GONE

            if (song.coverArt != null) {
                binding.imgCover.load(
                    RetrofitClient.buildCoverArtUrl(serverUrl, song.coverArt, username, token, salt)
                ) { crossfade(true) }
            } else {
                binding.imgCover.setImageDrawable(null)
            }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<SubsonicSong>() {
        override fun areItemsTheSame(old: SubsonicSong, new: SubsonicSong) = old.id == new.id
        override fun areContentsTheSame(old: SubsonicSong, new: SubsonicSong) = old == new
    }
}