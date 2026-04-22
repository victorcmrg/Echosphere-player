package com.example.echosphereplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.echosphereplayer.databinding.FragmentPlaylistsBinding // Reaproveitando o layout de lista
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddToPlaylistBottomSheet(private val currentSongId: String) : BottomSheetDialogFragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitlePlaylists.text = "Adicionar à Playlist..."
        binding.fabNewPlaylist.visibility = View.GONE // Esconde o FAB dourado aqui
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())

        val serverUrl = SessionManager.getServerUrl(requireContext())
        val username = SessionManager.getUsername(requireContext())
        val (token, salt) = SessionManager.getAuthTokens(requireContext())

        val adapter = PlaylistAdapter(serverUrl, username, token, salt) { clickedPlaylist ->
            addSongToPlaylist(clickedPlaylist.id, serverUrl, username, token, salt)
        }
        binding.rvPlaylists.adapter = adapter

        // Busca as playlists
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.getSubsonicApi(serverUrl)
                    .getPlaylists(user = username, token = token, salt = salt)
                val playlists = response.`subsonic-response`.playlists?.playlist ?: emptyList()
                withContext(Dispatchers.Main) {
                    adapter.submitList(playlists)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { dismiss() }
            }
        }
    }

    private fun addSongToPlaylist(playlistId: String, serverUrl: String, user: String, token: String, salt: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.getSubsonicApi(serverUrl).updatePlaylist(
                    playlistId = playlistId, songIdToAdd = currentSongId,
                    user = user, token = token, salt = salt
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Música adicionada!", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro ao adicionar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}