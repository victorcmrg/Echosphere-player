package com.example.echosphereplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.echosphereplayer.databinding.BottomSheetCreatePlaylistBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreatePlaylistBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreatePlaylistBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreatePlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Atualiza preview em tempo real conforme digita o nome
        binding.inputPlaylistName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val name = s?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    binding.previewCoverInitials.text = PlaylistMeta.getInitials(name)
                    binding.previewCoverContainer.setBackgroundColor(PlaylistMeta.getColor(name))
                } else {
                    binding.previewCoverInitials.text = "?"
                    binding.previewCoverContainer.setBackgroundColor(android.graphics.Color.parseColor("#333333"))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnSavePlaylist.setOnClickListener {
            val playlistName = binding.inputPlaylistName.text.toString().trim()
            val description  = binding.inputPlaylistDesc.text?.toString()?.trim() ?: ""

            if (playlistName.isEmpty()) {
                (requireActivity() as MainActivity).showTopNotification("Dê um nome para a playlist!")
                return@setOnClickListener
            }

            val serverUrl = SessionManager.getServerUrl(requireContext())
            val username  = SessionManager.getUsername(requireContext())
            val (token, salt) = SessionManager.getAuthTokens(requireContext())

            // Encoda cor e descrição no comentário da playlist (padrão Subsonic comment)
            // Formato: nome real da playlist (não muda)
            // A cor e descrição ficam no SharedPreferences local, keyed pelo nome
            val colorHex = PlaylistMeta.getColorHex(playlistName)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.getSubsonicApi(serverUrl).createPlaylist(
                        name = playlistName, user = username, token = token, salt = salt
                    )
                    // Pega o ID da playlist recém criada para salvar os metadados
                    val newPlaylistId = response.`subsonic-response`.playlist?.id

                    withContext(Dispatchers.Main) {
                        // Salva metadados locais (cor e descrição) pelo ID se disponível, senão pelo nome
                        val key = newPlaylistId ?: playlistName
                        PlaylistMeta.saveMeta(requireContext(), key, colorHex, description)

                        (requireActivity() as MainActivity).showTopNotification("Playlist criada: $playlistName")
                        requireActivity().supportFragmentManager.setFragmentResult("RELOAD_PLAYLISTS", Bundle())
                        dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        (requireActivity() as MainActivity).showTopNotification("Erro ao criar playlist")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}