package com.example.echosphereplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.echosphereplayer.databinding.FragmentEditInterfaceBinding

class EditInterfaceFragment : Fragment() {

    private var _binding: FragmentEditInterfaceBinding? = null
    private val binding get() = _binding!!

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // Pede permissão permanente ao Android para aceder à foto mesmo depois de fechar a app
            requireContext().contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            if (binding.previewContainer.childCount < 3) {
                val newImage = ImageView(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(400, 400)
                    setImageURI(it)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    tag = it.toString() // Guardamos a URI na tag para recuperar ao salvar
                }
                binding.previewContainer.addView(newImage)
                setupTouchListener(newImage)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditInterfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.previewBg.setImageDrawable(ThemeManager.getDynamicBackground(requireContext()))
        binding.previewGlow.background = ThemeManager.getDynamicGlow(requireContext())

        // CARREGA AS IMAGENS JÁ SALVAS ANTERIORMENTE
        val savedImages = ThemeManager.getCustomImages(requireContext())
        for (config in savedImages) {
            val img = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(400, 400)
                setImageURI(Uri.parse(config.uri))
                x = config.x
                y = config.y
                scaleX = config.scale
                scaleY = config.scale
                rotation = config.rotation
                scaleType = ImageView.ScaleType.FIT_CENTER
                tag = config.uri
            }
            binding.previewContainer.addView(img)
            setupTouchListener(img)
        }

        ThemeManager.glowColors.forEach { colorHex ->
            val btn = createColorButton(colorHex)
            btn.setOnClickListener {
                ThemeManager.saveGlowColor(requireContext(), colorHex)
                binding.previewGlow.background = ThemeManager.getDynamicGlow(requireContext())
            }
            binding.layoutGlowColors.addView(btn)
        }

        ThemeManager.bgPairs.forEach { pair ->
            val btn = createColorButton(pair.first)
            btn.setOnClickListener {
                ThemeManager.saveBgPair(requireContext(), pair.first, pair.second)
                binding.previewBg.setImageDrawable(ThemeManager.getDynamicBackground(requireContext()))
            }
            binding.layoutBgColors.addView(btn)
        }

        binding.btnAddImage.setOnClickListener {
            if (binding.previewContainer.childCount >= 3) {
                (requireActivity() as MainActivity).showTopNotification("Máximo 3 imagens!")
            } else {
                pickImage.launch("image/*")
            }
        }

        binding.btnSave.setOnClickListener {
            // SALVA AS POSIÇÕES, ROTAÇÕES E ZOOM DAS IMAGENS
            val imagesToSave = mutableListOf<ThemeManager.CustomImageConfig>()
            for (i in 0 until binding.previewContainer.childCount) {
                val imgView = binding.previewContainer.getChildAt(i)
                val uriStr = imgView.tag as? String ?: continue
                imagesToSave.add(
                    ThemeManager.CustomImageConfig(uriStr, imgView.x, imgView.y, imgView.scaleX, imgView.rotation)
                )
            }
            ThemeManager.saveCustomImages(requireContext(), imagesToSave)

            (requireActivity() as MainActivity).showTopNotification("Interface Salva!")
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun createColorButton(colorHex: String): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(16, 0, 16, 0) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }
        }
    }

    // A MÁGICA DE DRAG, ZOOM E ROTATE!
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(view: View) {
        var dX = 0f
        var dY = 0f
        var scaleFactor = view.scaleX
        var initialRotation = 0f

        val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.2f, 5.0f) // Limites de Zoom
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
                return true
            }
        })

        view.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        initialRotation = v.rotation - getAngle(event)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                        // Arrasta com 1 dedo
                        v.x = event.rawX + dX
                        v.y = event.rawY + dY
                    } else if (event.pointerCount == 2) {
                        // Roda com 2 dedos
                        v.rotation = initialRotation + getAngle(event)
                    }
                }
            }
            true
        }
    }

    // Calcula o ângulo entre 2 dedos
    private fun getAngle(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}