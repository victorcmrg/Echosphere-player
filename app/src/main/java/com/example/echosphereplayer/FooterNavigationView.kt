package com.example.echosphereplayer

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

class FooterNavigationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ANIM_DURATION = 250L
    private val TAB_COUNT = 3

    private lateinit var glowView: ImageView
    private val tabIcons = arrayOfNulls<ImageView>(TAB_COUNT)
    private var selectedIndex = 0
    var onTabSelectedListener: ((Int) -> Unit)? = null

    // Cores exatas que você pediu
    private val colorSelected = Color.parseColor("#FFFFFF")
    private val colorUnselected = Color.parseColor("#484848")

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_custom_footer, this, true)

        // IMPORTANTE: Garanta que no layout_custom_footer.xml o ID seja "footer_glow"
        glowView = findViewById(R.id.footer_glow)
        glowView.alpha = 0f // Começa invisível para não dar o erro do "canto da tela"

        val containerIds = intArrayOf(R.id.tab_home, R.id.tab_search, R.id.tab_playlists)
        val iconIds = intArrayOf(R.id.icon_home, R.id.icon_search, R.id.icon_playlists)

        for (i in 0 until TAB_COUNT) {
            tabIcons[i] = findViewById(iconIds[i])

            // Força a cor inicial: Primeiro Branco, os outros Cinza
            tabIcons[i]?.setColorFilter(if (i == 0) colorSelected else colorUnselected)

            findViewById<FrameLayout>(containerIds[i]).setOnClickListener {
                if (i != selectedIndex) {
                    animateToTab(i)
                    onTabSelectedListener?.invoke(i)
                }
            }
        }
    }

    // Resolve o bug do Glow nascer no lugar errado (espera a tela ser medida)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            post {
                val tabWidth = width.toFloat() / TAB_COUNT
                val targetX = (tabWidth * selectedIndex + tabWidth / 2f) - (glowView.width / 2f)
                glowView.translationX = targetX
                glowView.alpha = 1f // Aparece já no lugar certo da Home
            }
        }
    }

    private fun animateToTab(index: Int) {
        val tabWidth = width.toFloat() / TAB_COUNT
        val targetX = (tabWidth * index + tabWidth / 2f) - (glowView.width / 2f)

        // 1. Efeito de FADE no Glow (Some em um lugar, aparece no outro)
        glowView.animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION / 2)
            .withEndAction {
                glowView.translationX = targetX // Move enquanto está invisível
                glowView.animate()
                    .alpha(1f)
                    .setDuration(ANIM_DURATION / 2)
                    .start()
            }.start()

        // 2. TROCA DE CORES: O pulo do gato está aqui!
        tabIcons[selectedIndex]?.setColorFilter(colorUnselected) // Antigo fica cinza
        tabIcons[index]?.setColorFilter(colorSelected)       // Novo fica branco

        // 3. Animação de PRESS (Diminui e volta, sem subir)
        tabIcons[index]?.animate()
            ?.scaleX(0.8f)?.scaleY(0.8f)
            ?.setDuration(100)
            ?.withEndAction {
                tabIcons[index]?.animate()
                    ?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(100)
                    ?.start()
            }?.start()

        selectedIndex = index
    }

    fun setSelectedTab(index: Int) {
        // Removida a trava "index == selectedIndex", pois o NavController
        // pode estar em um estado diferente do visual do Footer.
        if (index !in 0 until TAB_COUNT) return

        val tabWidth = width.toFloat() / TAB_COUNT
        val targetX = (tabWidth * index + tabWidth / 2f) - (glowView.width / 2f)

        glowView.translationX = targetX
        glowView.alpha = 1f

        // Atualiza as cores sem animação brusca
        for (i in 0 until TAB_COUNT) {
            tabIcons[i]?.setColorFilter(if (i == index) colorSelected else colorUnselected)
        }

        selectedIndex = index
    }

    // Adicione isso ao FooterNavigationView.kt

    fun performClickTab(index: Int) {
        if (index !in 0 until TAB_COUNT) return

        // Mesmo que o selectedIndex seja o mesmo (ex: clicou em playlist na home),
        // nós forçamos a animação e o disparo do listener para garantir a navegação.
        animateToTab(index)
        onTabSelectedListener?.invoke(index)
    }
}