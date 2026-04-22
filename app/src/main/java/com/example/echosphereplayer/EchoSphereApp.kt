package com.example.echosphereplayer

import android.app.Application

/**
 * Application class — dispara o cache assim que o processo inicia,
 * antes de qualquer fragmento ser criado.
 *
 * Adicione no AndroidManifest.xml:
 *   <application android:name=".EchoSphereApp" ...>
 */
class EchoSphereApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Só inicializa se já estiver logado; caso contrário o LoginFragment
        // chama AppCache.init() após salvar as credenciais.
        if (SessionManager.isLoggedIn(this)) {
            AppCache.init(this)
        }
    }
}