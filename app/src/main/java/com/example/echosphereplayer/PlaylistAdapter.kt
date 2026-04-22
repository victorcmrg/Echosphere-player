package com.example.echosphereplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.echosphereplayer.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val serverUrl: String,
    private val username: String,
    private val token: String,
    private val salt: String,
    private val onClick: (SubsonicPlaylist) -> Unit
) : ListAdapter<SubsonicPlaylist, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(playlist: SubsonicPlaylist) {
            binding.tvPlaylistName.text = playlist.name
            binding.tvPlaylistCount.text = "${playlist.songCount} músicas"

            val prefs = itemView.context.getSharedPreferences("PlaylistCustomCovers", Context.MODE_PRIVATE)
            val settings = itemView.context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)

            val customCover = prefs.getString(playlist.id, null)
            val useCollage = settings.getBoolean("use_collage", false)

            val finalCoverArt = customCover ?: if (useCollage) playlist.coverArt else null

            if (!finalCoverArt.isNullOrBlank()) {
                binding.imgPlaylistCover.load(
                    RetrofitClient.buildCoverArtUrl(serverUrl, finalCoverArt, username, token, salt)
                ) { crossfade(true) }
                binding.tvPlaylistInitials.visibility = View.GONE
            } else {
                binding.imgPlaylistCover.setImageDrawable(null)
                binding.imgPlaylistCover.setBackgroundColor(
                    PlaylistMeta.getSavedColor(itemView.context, playlist.id, playlist.name)
                )
                binding.tvPlaylistInitials.text = PlaylistMeta.getInitials(playlist.name)
                binding.tvPlaylistInitials.visibility = View.VISIBLE
            }
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<SubsonicPlaylist>() {
        override fun areItemsTheSame(old: SubsonicPlaylist, new: SubsonicPlaylist) = old.id == new.id
        override fun areContentsTheSame(old: SubsonicPlaylist, new: SubsonicPlaylist) = old == new
    }
}