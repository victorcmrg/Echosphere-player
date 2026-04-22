package com.example.echosphereplayer

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

object ThemeManager {
    private const val PREFS_NAME = "ThemePrefs"
    private const val KEY_GLOW_COLOR = "glow_color"
    private const val KEY_BG_START = "bg_start"
    private const val KEY_BG_END = "bg_end"
    private const val KEY_CUSTOM_IMAGES = "custom_images_data"

    data class CustomImageConfig(
        val uri: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotation: Float
    )

    // CORES DO GLOW DISPONÍVEIS
    val glowColors = listOf(
        "#9B5EF9", "#FF2226", "#FF22D7", "#3122FF",
        "#22C4FF", "#22FF6C", "#FFF822", "#FF7E22"
    )

    // PARES DE FUNDO DISPONÍVEIS (Start, End)
    val bgPairs = listOf(
        Pair("#9B5EF9", "#71BEF6"), // Original (Roxo/Azul)
        Pair("#22FF6C", "#22C4FF"), // Verde/Ciano
        Pair("#FF2226", "#FF7E22"), // Fogo (Vermelho/Laranja)
        Pair("#3122FF", "#FF22D7"), // Neon (Azul/Rosa)
        Pair("#FFF822", "#FF7E22")  // Sol (Amarelo/Laranja)
    )

    fun saveGlowColor(context: Context, color: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GLOW_COLOR, color).apply()
    }

    fun saveBgPair(context: Context, start: String, end: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BG_START, start).putString(KEY_BG_END, end).apply()
    }

    // GERA O GENERIC GLOW EFFECT DINAMICAMENTE!
    fun getDynamicGlow(context: Context): LayerDrawable {
        val colorHex = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_GLOW_COLOR, "#9B5EF9") ?: "#9B5EF9"

        // Prevenção de crash caso a cor seja inválida
        val baseColor = try { Color.parseColor(colorHex) } catch (e: Exception) { Color.parseColor("#9B5EF9") }

        val alpha30 = Color.argb(48, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val alpha05 = Color.argb(5, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()

        val layer1 = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.0f, 0.0f)
            gradientRadius = screenWidth * 1.0f
            colors = intArrayOf(alpha30, Color.TRANSPARENT)
            shape = GradientDrawable.RECTANGLE
        }


        return LayerDrawable(arrayOf(layer1))
    }

    // GERA O BANNER DO TOPO DINAMICAMENTE!
    fun getDynamicBackground(context: Context): LayerDrawable {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startHex = prefs.getString(KEY_BG_START, "#9B5EF9") ?: "#9B5EF9"
        val endHex = prefs.getString(KEY_BG_END, "#71BEF6") ?: "#71BEF6"

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()

        val gradient = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.0f, 0.0f)
            gradientRadius = screenWidth * 2.5f
            colors = try {
                intArrayOf(Color.parseColor(startHex), Color.parseColor(endHex))
            } catch (e: Exception) {
                intArrayOf(Color.parseColor("#9B5EF9"), Color.parseColor("#71BEF6"))
            }
            shape = GradientDrawable.RECTANGLE
        }
        return LayerDrawable(arrayOf(gradient))
    }

    // --- SISTEMA DE SALVAMENTO DE IMAGENS ---
    fun saveCustomImages(context: Context, images: List<CustomImageConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val builder = StringBuilder()
        for (img in images) {
            // Guarda no formato: uri|x|y|scale|rotation;
            builder.append("${img.uri}|${img.x}|${img.y}|${img.scale}|${img.rotation};")
        }
        prefs.edit().putString(KEY_CUSTOM_IMAGES, builder.toString()).apply()
    }

    fun getCustomImages(context: Context): List<CustomImageConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = prefs.getString(KEY_CUSTOM_IMAGES, "") ?: ""
        if (data.isEmpty()) return emptyList()

        val list = mutableListOf<CustomImageConfig>()
        val items = data.split(";").filter { it.isNotEmpty() }
        for (item in items) {
            val parts = item.split("|")
            if (parts.size == 5) {
                list.add(
                    CustomImageConfig(
                        parts[0],
                        parts[1].toFloat(),
                        parts[2].toFloat(),
                        parts[3].toFloat(),
                        parts[4].toFloat()
                    )
                )
            }
        }
        return list
    }
}