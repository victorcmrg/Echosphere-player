package com.example.echosphereplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Se já está logado, vai direto para a Home
        if (SessionManager.isLoggedIn(requireContext())) {
            findNavController().navigate(R.id.homeFragment)
            return
        }

        val inputUrl  = view.findViewById<EditText>(R.id.input_server_url)
        val inputUser = view.findViewById<EditText>(R.id.input_user)
        val inputPass = view.findViewById<EditText>(R.id.input_password)
        val btnLogin  = view.findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val serverUrl = inputUrl.text.toString().trim()
            val username  = inputUser.text.toString().trim()
            val password  = inputPass.text.toString()

            if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Conectando..."

            lifecycleScope.launch {
                try {
                    // Gera o token MD5 para o Subsonic (mais seguro que enviar senha pura)
                    val salt  = java.util.UUID.randomUUID().toString().replace("-", "").take(10)
                    val token = java.security.MessageDigest.getInstance("MD5")
                        .digest((password + salt).toByteArray())
                        .joinToString("") { "%02x".format(it) }

                    val api = RetrofitClient.getSubsonicApi(serverUrl)
                    val response = withContext(Dispatchers.IO) {
                        api.ping(user = username, token = token, salt = salt)
                    }

                    val root = response.`subsonic-response`
                    if (root.status == "ok") {
                        // Salva as credenciais de forma criptografada
                        SessionManager.saveCredentials(requireContext(), serverUrl, username, password)
                        findNavController().navigate(R.id.homeFragment)
                    } else {
                        val msg = root.error?.message ?: "Erro desconhecido"
                        Toast.makeText(context, "Falha: $msg", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        btnLogin.text = "Entrar"
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Não foi possível conectar: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btnLogin.isEnabled = true
                    btnLogin.text = "Entrar"
                }
            }
        }
    }
}