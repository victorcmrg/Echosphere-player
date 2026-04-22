package com.example.echosphereplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.echosphereplayer.databinding.ItemPlaylistHorizontalBinding

class PlaylistHorizontalAdapter(
    private val serverUrl: String,
    private val username: String,
    private val token: String,
    private val salt: String,
    private val onClick: (SubsonicPlaylist) -> Unit
) : ListAdapter<SubsonicPlaylist, PlaylistHorizontalAdapter.ViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // PATCH: Certifique-se de que o ItemPlaylistHorizontalBinding tenha o tv_playlist_initials no XML
        val binding = ItemPlaylistHorizontalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemPlaylistHorizontalBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) onClick(getItem(bindingAdapterPosition))
            }
        }

        fun bind(playlist: SubsonicPlaylist) {
            binding.tvPlaylistName.text = playlist.name

            val prefs = itemView.context.getSharedPreferences("PlaylistCustomCovers", Context.MODE_PRIVATE)
            val settings = itemView.context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)

            val customCover = prefs.getString(playlist.id, null)
            val useCollage = settings.getBoolean("use_collage", false)

            // Lógica idêntica à página de playlists
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