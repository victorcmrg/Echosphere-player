package com.example.echosphereplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.atan2

class VinylTrackSelector @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var songs: List<SubsonicSong> = emptyList()
    var onSongSelected: ((SubsonicSong) -> Unit)? = null
    var currentPlayingId: String? = null

    private var vinylImage: View? = null
    private var coverImage: View? = null

    private var currentScroll = 0f
    private var lastPlayedIndex = 0
    private val angleGap = 22.5f
    private val centerTargetAngle = 135f

    private var animator: ValueAnimator? = null

    // --- LÓGICA DE EXPANSÃO DOS NOMES ---
    private var currentRadius = 0f
    private var radiusAnimator: ValueAnimator? = null
    private val hideDelay = 300L // 0.3 segundos
    private val hideRunnable = Runnable { animateRadius(0f) }
    // ------------------------------------

    private var baseRotation = 0f
    private var isTouching = false
    private val idleSpinSpeed = 0.4f

    // --- ESTADO DE REPRODUÇÃO (Diz se o vinil deve rodar sozinho) ---
    private var isPlaying = false

    private var soundPool: SoundPool
    private var clickSoundId: Int = 0

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attributes)
            .build()

        clickSoundId = soundPool.load(context, R.raw.click, 1)

        post(object : Runnable {
            override fun run() {
                // O vinil só gira sozinho se a música estiver a tocar,
                // se o utilizador não estiver a tocar na tela e se não estiver a animar o scroll.
                if (isPlaying && !isTouching && animator?.isRunning != true) {
                    baseRotation += idleSpinSpeed
                    baseRotation %= 360f
                    updateVinylRotation()
                }
                postOnAnimation(this)
            }
        })
    }

    // Função para ligar/desligar a rotação vinda do Player
    fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    fun setVinylImage(view: View) { vinylImage = view }
    fun setCoverImage(view: View) { coverImage = view }

    private fun updateVinylRotation() {
        val rotation = baseRotation + (currentScroll * angleGap)
        vinylImage?.rotation = rotation
        coverImage?.rotation = rotation
    }

    private fun checkAndPlayClick() {
        val currentIndex = Math.round(currentScroll)
        if (currentIndex != lastPlayedIndex) {
            lastPlayedIndex = currentIndex
            soundPool.play(clickSoundId, 0.7f, 0.7f, 1, 0, 1f)
        }
    }

    private fun animateRadius(target: Float) {
        radiusAnimator?.cancel()
        val targetRadiusValue = if (target > 0) width * 0.28f else 0f

        radiusAnimator = ValueAnimator.ofFloat(currentRadius, targetRadiusValue).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentRadius = it.animatedValue as Float
                invalidate()
            }
        }
        radiusAnimator?.start()
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null) return false
            animator?.cancel()
            removeCallbacks(hideRunnable)

            if (currentRadius == 0f && radiusAnimator?.isRunning != true) {
                animateRadius(1f)
            }

            val centerX = width / 2f
            val centerY = height / 2f
            val prevAngle = Math.toDegrees(atan2((e2.y + distanceY - centerY).toDouble(), (e2.x + distanceX - centerX).toDouble())).toFloat()
            val currentAngle = Math.toDegrees(atan2((e2.y - centerY).toDouble(), (e2.x - centerX).toDouble())).toFloat()

            var delta = currentAngle - prevAngle
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360

            currentScroll += delta / angleGap
            checkAndPlayClick()
            updateVinylRotation()
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val centerX = width / 2f
            val centerY = height / 2f
            val rx = e2.x - centerX
            val ry = e2.y - centerY
            val crossProduct = (rx * velocityY) - (ry * velocityX)
            val velocity = Math.hypot(velocityX.toDouble(), velocityY.toDouble()).toFloat()
            val direction = if (crossProduct > 0) 1 else -1

            val scrollAmount = (velocity / 1200f) * direction
            val targetScroll = Math.round(currentScroll + scrollAmount).toFloat()

            animateToScroll(targetScroll, 1200L)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (songs.isEmpty()) return false

        // ─── MÁGICA DA DETEÇÃO CIRCULAR ──────────────────────────────────────────
        if (event.action == MotionEvent.ACTION_DOWN) {
            val centerX = width / 2f
            val centerY = height / 2f

            val distanceFromCenter = Math.hypot(
                (event.x - centerX).toDouble(),
                (event.y - centerY).toDouble()
            ).toFloat()

            // O raio clicável é exatamente a metade da largura (círculo perfeito)
            val maxRadius = width / 2f

            // Se tocar nos cantos transparentes do quadrado, o toque passa reto
            if (distanceFromCenter > maxRadius) {
                return false
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                animator?.cancel()
                removeCallbacks(hideRunnable)
                animateRadius(1f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                if (animator?.isRunning != true) {
                    animateToScroll(Math.round(currentScroll).toFloat(), 350L)
                }
            }
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun animateToScroll(target: Float, durationMs: Long) {
        animator?.cancel()
        var isCanceled = false

        animator = ValueAnimator.ofFloat(currentScroll, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                currentScroll = it.animatedValue as Float
                checkAndPlayClick()
                updateVinylRotation()
                invalidate()
            }
        }

        animator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) { isCanceled = true }

            override fun onAnimationEnd(animation: Animator) {
                if (isCanceled || songs.isEmpty()) return

                postDelayed(hideRunnable, hideDelay)

                val size = songs.size
                val rawIndex = Math.round(target)
                val actualIndex = ((rawIndex % size) + size) % size
                val selectedSong = songs[actualIndex]

                if (selectedSong.id != currentPlayingId) {
                    currentPlayingId = selectedSong.id
                    onSongSelected?.invoke(selectedSong)
                }
            }
        })
        animator?.start()
    }

    fun setSongs(newSongs: List<SubsonicSong>) {
        songs = newSongs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (songs.isEmpty() || currentRadius == 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        textPaint.textSize = width * 0.042f

        val baseIndex = Math.floor(currentScroll.toDouble()).toInt()

        for (i in (baseIndex - 4)..(baseIndex + 4)) {
            val size = songs.size
            val actualIndex = ((i % size) + size) % size
            val song = songs[actualIndex]

            val angle = centerTargetAngle + (i - currentScroll) * angleGap

            if (angle < 60f || angle > 210f) continue

            val distanceToCenter = Math.abs(centerTargetAngle - angle)
            val alphaRatio = Math.max(0f, 1f - (distanceToCenter / 70f))
            val expansionOpacity = currentRadius / (width * 0.28f)

            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * alphaRatio * expansionOpacity).toInt()

            val displayTitle = if (song.title.length > 8) song.title.take(8) + "..." else song.title
            val textToDraw = if (song.id == currentPlayingId) "• $displayTitle" else displayTitle

            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.rotate(-angle)

            canvas.translate(currentRadius, 0f)

            canvas.rotate(180f)

            val scaleRatio = Math.max(0.5f, 1f - (distanceToCenter / 100f))
            canvas.scale(scaleRatio, scaleRatio)

            canvas.drawText(textToDraw, 0f, textPaint.textSize / 3f, textPaint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(hideRunnable)
        soundPool.release()
    }
}