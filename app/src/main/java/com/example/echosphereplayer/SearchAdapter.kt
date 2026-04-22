package com.example.echosphereplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.echosphereplayer.databinding.ItemSearchResultBinding

class SearchAdapter(
    private val onAddClick: (PythonSearchResult) -> Unit,
    private val onPreviewClick: (PythonSearchResult) -> Unit // PATCH: Clique do Preview adicionado
) : ListAdapter<PythonSearchResult, SearchAdapter.SearchViewHolder>(SearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnSearchAdd.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onAddClick(getItem(adapterPosition))
                }
            }

            // PATCH: Listener para o novo botão de Play (Preview)
            binding.btnPreviewPlay.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onPreviewClick(getItem(adapterPosition))
                }
            }
        }

        fun bind(result: PythonSearchResult) {
            binding.tvSearchTitle.text = result.title
            binding.tvSearchArtist.text = "${result.channel} • ${result.duration}"

            if (!result.thumbnail.isNullOrEmpty()) {
                binding.imgSearchCover.load(result.thumbnail) { crossfade(true) }
            } else {
                binding.imgSearchCover.setImageDrawable(null)
            }
        }
    }

    class SearchDiffCallback : DiffUtil.ItemCallback<PythonSearchResult>() {
        override fun areItemsTheSame(oldItem: PythonSearchResult, newItem: PythonSearchResult) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: PythonSearchResult, newItem: PythonSearchResult) = oldItem == newItem
    }
}