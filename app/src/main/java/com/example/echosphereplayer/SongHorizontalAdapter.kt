package com.example.echosphereplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.echosphereplayer.databinding.ItemSongHorizontalBinding

class SongHorizontalAdapter(
    private val serverUrl: String,
    private val username: String,
    private val token: String,
    private val salt: String,
    private val onClick: (SubsonicSong) -> Unit
) : ListAdapter<SubsonicSong, SongHorizontalAdapter.ViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongHorizontalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemSongHorizontalBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) onClick(getItem(bindingAdapterPosition))
            }
        }
        fun bind(song: SubsonicSong) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist ?: "Artista Desconhecido"

            // PATCH: Pergunta à tela principal qual é o status exato agora.
            // Impede estrelas falsas em reciclagem.
            val mainActivity = itemView.context as? MainActivity
            val isStarred = mainActivity?.getFavoriteState(song.id, song.starred != null) ?: (song.starred != null)

            binding.imgStar.visibility = if (isStarred) View.VISIBLE else View.GONE

            if (song.coverArt != null) {
                binding.imgCover.load(RetrofitClient.buildCoverArtUrl(serverUrl, song.coverArt, username, token, salt)) { crossfade(true) }
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