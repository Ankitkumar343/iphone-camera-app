package com.example.iphone17camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var provider: ProcessCameraProvider? = null
    private var selectedLook = "Natural"

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

    private fun buildUi() {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101114.toInt())
            setPadding(18, 24, 18, 16)
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
        base.addView(status, LinearLayout.LayoutParams(-1, 48))
        base.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))
        val lookRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val look = Spinner(this)
        look.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Natural iPhone Look", "Vivid", "Soft & Bright", "Night Mode (preview)"))
        look.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedLook = listOf("Natural", "Vivid", "Soft", "Night")[position]
                status.text = "$selectedLook look selected. Camera sensor features depend on your phone."
            }
        }
        lookRow.addView(look, LinearLayout.LayoutParams(-1, 54))
        base.addView(lookRow)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(makeButton("🔄 Flip") { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; startCamera() })
        row.addView(makeButton("⚪ Capture") { takePhoto() })
        row.addView(makeButton("🔓 Permission") { showPermissionHelp() })
        base.addView(row)
        val note = TextView(this).apply {
            text = "Portrait blur / AI face enhancement / true 4K are not enabled in this starter build; availability depends on device hardware and additional ML processing."
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
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            val selector = CameraSelector.Builder().requireLensFacing(lens).build()
            try {
                provider?.unbindAll()
                provider?.bindToLifecycle(this, selector, preview, imageCapture)
                status.text = "Camera ready • $selectedLook"
            } catch (e: Exception) {
                status.text = "Camera start failed: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run { status.text = "Camera अभी तैयार नहीं है"; return }
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
    }
}
