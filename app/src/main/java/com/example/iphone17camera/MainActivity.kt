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

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var provider: ProcessCameraProvider? = null

    // 0 = Standard (max resolution), 1 = Portrait Blur, 2 = Face Enhance, 3 = Both
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
                "Standard (Max resolution)",
                "Portrait Blur",
                "Face Enhance",
                "Portrait Blur + Face Enhance"
            )
        )
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mode = position
                status.text = when (position) {
                    0 -> "Standard mode • phone ka maximum photo resolution"
                    1 -> "Portrait Blur • background blur hoga"
                    2 -> "Face Enhance • chehre par smoothing aur brightness"
                    else -> "Portrait Blur + Face Enhance"
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
            text = "Portrait Blur aur Face Enhance software se hote hain (ML Kit), depth sensor se nahi. " +
                "In modes mein photo max 2560px tak save hoti hai. Standard mode phone ka maximum resolution use karta hai."
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

        if (mode == 0) {
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
        val doPortrait = mode == 1 || mode == 3
        val doFace = mode == 2 || mode == 3
        capture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    val raw = try { image.toBitmap() } finally { image.close() }
                    var bmp = scaleDown(raw, 2560)
                    if (rotation != 0) {
                        val m = Matrix().apply { postRotate(rotation.toFloat()) }
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    var note = ""
                    if (doFace) {
                        val r = enhanceFaces(bmp)
                        if (r == null) note = " (chehra nahi mila)" else bmp = r
                    }
                    if (doPortrait) bmp = portraitBlur(bmp)
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
