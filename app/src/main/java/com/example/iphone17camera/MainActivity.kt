package com.example.iphone17camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.pow

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var provider: ProcessCameraProvider? = null

    // 0 = iPhone Look, 1 = iPhone Look + Portrait Blur, 2 = Original (no edit, max resolution)
    private var mode = 0
    private val worker = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                status.text = "Camera permission blocked. Settings > Apps > iPhone 17 Pro Camera > Permissions > Camera > Allow."
                showPermissionHelp()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    private fun buildUi() {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101114.toInt())
            setPadding(18, 24, 18, 16)
        }
        ViewCompat.setOnApplyWindowInsetsListener(base) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(18, bars.top + 12, 18, bars.bottom + 12)
            insets
        }
        val title = TextView(this).apply {
            text = "📸  iPhone 17 Pro Camera"
            textSize = 22f; setTextColor(-1); gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Camera permission आवश्यक है"
            textSize = 13f; setTextColor(0xFFFFD76A.toInt()); gravity = Gravity.CENTER
            setPadding(4, 12, 4, 12)
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        base.addView(title, LinearLayout.LayoutParams(-1, 54))
        base.addView(status, LinearLayout.LayoutParams(-1, 96))
        base.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))

        val modeSpinner = Spinner(this)
        modeSpinner.setBackgroundColor(0xFFDDDDDD.toInt())
        modeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf(
                "iPhone Look (recommended)",
                "iPhone Look + Portrait Blur",
                "Original (no edit, max resolution)"
            )
        )
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mode = position
                status.text = when (position) {
                    0 -> "iPhone Look • bright, warm, vivid rang + chehre par halki smoothing"
                    1 -> "iPhone Look + Portrait Blur • background blur"
                    else -> "Original • bina edit, phone ka maximum resolution"
                }
            }
        }
        val lookRow = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 12, 0, 8) }
        lookRow.addView(modeSpinner, LinearLayout.LayoutParams(-1, 110))
        base.addView(lookRow)

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(makeButton("🔄 Flip") {
            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        })
        row.addView(makeButton("⚪ Capture") { takePhoto() })
        row.addView(makeButton("🔓 Permission") { showPermissionHelp() })
        base.addView(row)

        val note = TextView(this).apply {
            text = "iPhone Look aur Portrait Blur software se hote hain (ML Kit), depth sensor ya iPhone ke HDR se nahi. " +
                "In modes mein photo max 4096px tak save hoti hai. Original mode phone ka maximum resolution use karta hai."
            textSize = 11f; setTextColor(0xFFB9BBC3.toInt()); gravity = Gravity.CENTER
            setPadding(4, 12, 4, 4)
        }
        base.addView(note)
        setContentView(base)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label; isAllCaps = false; textSize = 12f
            setOnClickListener { action() }
        }

    private fun showPermissionHelp() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Camera permission unblock")
            .setMessage("1. Tap App Settings below.\n2. Open Permissions > Camera.\n3. Choose Allow.\n4. Return to the app and tap Retry Camera.\n\nIf Android shows a permission prompt, choose Allow.")
            .setPositiveButton("App Settings") { _, _ ->
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Retry Camera") { _, _ ->
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }.show()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            provider = future.get()

            val previewSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val captureSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(previewSelector)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(captureSelector)
                .build()
            val selector = CameraSelector.Builder().requireLensFacing(lens).build()
            try {
                provider?.unbindAll()
                provider?.bindToLifecycle(this, selector, preview, imageCapture)
                val res = imageCapture?.resolutionInfo?.resolution
                status.text = if (res != null) "Camera ready • ${res.width}x${res.height}" else "Camera ready"
            } catch (e: Exception) {
                status.text = "Camera start failed: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run { status.text = "Camera अभी तैयार नहीं है"; return }

        if (mode == 2) {
            val name = "IMG_iPhoneStyle_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/iPhoneStyleCamera")
            }
            val output = ImageCapture.OutputFileOptions.Builder(
                contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ).build()
            capture.takePicture(output, ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        status.text = "Photo saved in Gallery > Pictures/iPhoneStyleCamera"
                        Toast.makeText(this@MainActivity, "Photo saved ✓", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        status.text = "Photo save failed: ${exception.localizedMessage}"
                    }
                })
            return
        }

        status.text = "Processing... thoda ruko"
        val doPortrait = mode == 1
        val doFace = true
        capture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    val raw = try { image.toBitmap() } finally { image.close() }
                    var note = ""
                    val bmp = try {
                        processPhoto(raw, rotation, 4096, doFace, doPortrait) { note = it }
                    } catch (oom: OutOfMemoryError) {
                        processPhoto(raw, rotation, 2560, doFace, doPortrait) { note = it }
                    }
                    val ok = saveBitmap(bmp)
                    runOnUiThread {
                        if (ok) {
                            status.text = "Photo saved in Gallery > Pictures/iPhoneStyleCamera$note"
                            Toast.makeText(this@MainActivity, "Photo saved ✓", Toast.LENGTH_SHORT).show()
                        } else status.text = "Photo save failed"
                    }
                } catch (e: Throwable) {
                    runOnUiThread { status.text = "Processing failed: ${e.localizedMessage}" }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { status.text = "Photo capture failed: ${exception.localizedMessage}" }
            }
        })
    }

    // ---------- Image processing ----------

    private fun processPhoto(
        raw: Bitmap, rotation: Int, maxDim: Int,
        doFace: Boolean, doPortrait: Boolean, setNote: (String) -> Unit
    ): Bitmap {
        var bmp = scaleDown(raw, maxDim)
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        if (doFace) {
            val r = enhanceFaces(bmp)
            if (r == null) setNote(" (chehra nahi mila)") else bmp = r
        }
        if (doPortrait) bmp = portraitBlur(bmp)
        bmp = iphoneLook(bmp)
        return bmp
    }

    private fun scaleDown(b: Bitmap, maxDim: Int): Bitmap {
        val longSide = maxOf(b.width, b.height)
        if (longSide <= maxDim) return b
        val s = maxDim.toFloat() / longSide
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
        val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
        val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun blurred(bmp: Bitmap, factor1: Int, factor2: Int): Bitmap {
        val w = bmp.width; val h = bmp.height
        val s1 = Bitmap.createScaledBitmap(bmp, maxOf(1, w / factor1), maxOf(1, h / factor1), true)
        val s2 = Bitmap.createScaledBitmap(s1, maxOf(1, w / factor2), maxOf(1, h / factor2), true)
        val u1 = Bitmap.createScaledBitmap(s2, maxOf(1, w / factor1), maxOf(1, h / factor1), true)
        return Bitmap.createScaledBitmap(u1, w, h, true)
    }

    /** Background blur using ML Kit selfie segmentation (software portrait mode). */
    private fun portraitBlur(bmp: Bitmap): Bitmap {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val mask = try {
            Tasks.await(segmenter.process(InputImage.fromBitmap(bmp, 0)))
        } finally {
            segmenter.close()
        }
        val mw = mask.width
        val mh = mask.height
        val conf = FloatArray(mw * mh)
        val buf = mask.buffer
        buf.rewind()
        for (i in conf.indices) conf[i] = buf.float

        val w = bmp.width
        val h = bmp.height
        val sharp = IntArray(w * h)
        bmp.getPixels(sharp, 0, w, 0, 0, w, h)
        val blur = IntArray(w * h)
        blurred(bmp, 8, 24).getPixels(blur, 0, w, 0, 0, w, h)

        val out = IntArray(w * h)
        for (y in 0 until h) {
            val my = minOf(mh - 1, y * mh / h)
            for (x in 0 until w) {
                val mx = minOf(mw - 1, x * mw / w)
                val c = conf[my * mw + mx]
                val t = ((c - 0.35f) / 0.4f).coerceIn(0f, 1f)
                val s = t * t * (3f - 2f * t)
                val i = y * w + x
                out[i] = mix(blur[i], sharp[i], s)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** Light skin smoothing + brightness on detected faces. Returns null if no face found. */
    private fun enhanceFaces(bmp: Bitmap): Bitmap? {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        val faces = try {
            Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
        } finally {
            detector.close()
        }
        if (faces.isEmpty()) return null

        val w = bmp.width
        val h = bmp.height
        val orig = IntArray(w * h)
        bmp.getPixels(orig, 0, w, 0, 0, w, h)
        val smooth = IntArray(w * h)
        blurred(bmp, 3, 6).getPixels(smooth, 0, w, 0, 0, w, h)
        val out = orig.copyOf()

        for (f in faces) {
            val bb = f.boundingBox
            val cx = bb.exactCenterX()
            val cy = bb.exactCenterY()
            val rx = bb.width() / 2f * 1.05f
            val ry = bb.height() / 2f * 1.15f
            val x0 = maxOf(0, (cx - rx).toInt())
            val x1 = minOf(w - 1, (cx + rx).toInt())
            val y0 = maxOf(0, (cy - ry).toInt())
            val y1 = minOf(h - 1, (cy + ry).toInt())
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val dx = (x - cx) / rx
                    val dy = (y - cy) / ry
                    val d = dx * dx + dy * dy
                    if (d >= 1f) continue
                    val weight = minOf(1f, (1f - d) * 3f) * 0.6f
                    val i = y * w + x
                    var p = mix(out[i], smooth[i], weight)
                    p = mix(p, -1, 0.06f * weight / 0.6f)
                    out[i] = p
                }
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** iPhone-style look: auto brightness, soft contrast, warm tone, vibrance and light sharpening. */
    private fun iphoneLook(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val bl = IntArray(w * h)
        blurred(bmp, 2, 3).getPixels(bl, 0, w, 0, 0, w, h)

        var sum = 0.0
        var n = 0
        var j = 0
        while (j < px.size) {
            val p = px[j]
            sum += (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)) / 255.0
            n++
            j += 16
        }
        val mean = (sum / maxOf(1, n)).coerceIn(0.05, 0.95)
        val gamma = (ln(0.50) / ln(mean)).coerceIn(0.65, 1.0).toFloat()

        for (k in px.indices) {
            val p = px[k]
            val q = bl[k]
            var r = ((p shr 16) and 255) / 255f
            var g = ((p shr 8) and 255) / 255f
            var b = (p and 255) / 255f
            val br = ((q shr 16) and 255) / 255f
            val bg = ((q shr 8) and 255) / 255f
            val bb = (q and 255) / 255f

            // light sharpening (unsharp mask)
            r = (r + 0.6f * (r - br)).coerceIn(0f, 1f)
            g = (g + 0.6f * (g - bg)).coerceIn(0f, 1f)
            b = (b + 0.6f * (b - bb)).coerceIn(0f, 1f)

            // brighten dark photos
            r = r.pow(gamma); g = g.pow(gamma); b = b.pow(gamma)

            // contrast + slightly lifted blacks
            r = (((r - 0.5f) * 1.10f + 0.5f).coerceIn(0f, 1f)) * 0.97f + 0.03f
            g = (((g - 0.5f) * 1.10f + 0.5f).coerceIn(0f, 1f)) * 0.97f + 0.03f
            b = (((b - 0.5f) * 1.10f + 0.5f).coerceIn(0f, 1f)) * 0.97f + 0.03f

            // warm tone
            r = (r * 1.04f).coerceIn(0f, 1f)
            b = (b * 0.96f).coerceIn(0f, 1f)

            // vibrance: boost dull colours more than already-strong ones
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            val sat = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
            val boost = 1f + 0.35f * (1f - sat)
            r = (l + (r - l) * boost).coerceIn(0f, 1f)
            g = (l + (g - l) * boost).coerceIn(0f, 1f)
            b = (l + (b - l) * boost).coerceIn(0f, 1f)

            px[k] = (0xFF shl 24) or ((r * 255f + 0.5f).toInt() shl 16) or
                ((g * 255f + 0.5f).toInt() shl 8) or (b * 255f + 0.5f).toInt()
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(px, 0, w, 0, 0, w, h)
        return result
    }

    private fun saveBitmap(bmp: Bitmap): Boolean {
        val name = "IMG_iPhoneStyle_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/iPhoneStyleCamera")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: false
    }
}
