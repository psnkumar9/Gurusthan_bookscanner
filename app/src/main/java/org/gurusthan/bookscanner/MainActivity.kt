package org.gurusthan.bookscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.setPadding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var captureButton: Button
    private lateinit var store: BookStore
    private var capture: ImageCapture? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var bookId = ""
    private var busy = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else message("Camera permission is required")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BookStore(this)
        val prefs = getSharedPreferences("scanner", MODE_PRIVATE)
        bookId = prefs.getString("active_book", null) ?: store.newBook().also {
            prefs.edit().putString("active_book", it).apply()
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18) }
        status = TextView(this).apply { textSize = 16f }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        captureButton = Button(this).apply {
            text = "CAPTURE TWO PAGES"
            setOnClickListener { takePicture() }
        }
        val newBook = Button(this).apply {
            text = "NEW BOOK"
            setOnClickListener {
                if (!busy) {
                    bookId = store.newBook()
                    prefs.edit().putString("active_book", bookId).apply()
                    updateCount()
                }
            }
        }
        val export = Button(this).apply {
            text = "EXPORT MASTER TXT"
            setOnClickListener { exportText() }
        }
        layout.addView(status)
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(captureButton)
        layout.addView(newBook)
        layout.addView(export)
        setContentView(layout)
        updateCount()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun message(s: String) { runOnUiThread { status.text = s } }
    private fun updateCount() = message("Book: $bookId | Saved spreads: ${store.pageCount(bookId)}")
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val p = androidx.camera.core.Preview.Builder().build()
                p.surfaceProvider = preview.surfaceProvider
                capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, capture)
            } catch (e: Exception) { message("Camera error: ${e.message}") }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun takePicture() {
        if (busy) return
        val camera = capture ?: return
        busy = true
        captureButton.isEnabled = false
        val currentBook = bookId
        val original = File(store.bookDir(currentBook), "capture_${System.currentTimeMillis()}.jpg")
        camera.takePicture(ImageCapture.OutputFileOptions.Builder(original).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    message("Capture failed: ${exc.message}")
                    finishCapture()
                }
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    message("Processing both pages...")
                    worker.execute {
                        try {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(original.path, opts)
                            val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 2400)
                            val decoded = BitmapFactory.decodeFile(original.path,
                                BitmapFactory.Options().apply { inSampleSize = sample })
                                ?: error("Could not decode photo")
                            val exif = androidx.exifinterface.media.ExifInterface(original.path)
                            val angle = when (exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                            val upright = if (angle == 0f) decoded else Bitmap.createBitmap(decoded,
                                0, 0, decoded.width, decoded.height,
                                Matrix().apply { postRotate(angle) }, true)
                            require(upright.width > upright.height) {
                                "Hold the phone in landscape so both pages appear side by side"
                            }
                            val (left, right) = PageProcessor.split(upright)
                            val stamp = System.currentTimeMillis()
                            store.saveImage(currentBook, "processed_${stamp}_left.jpg", left)
                            store.saveImage(currentBook, "processed_${stamp}_right.jpg", right)
                            runOnUiThread {
                                recognizer.process(InputImage.fromBitmap(left, 0))
                                    .addOnSuccessListener { leftResult ->
                                        recognizer.process(InputImage.fromBitmap(right, 0))
                                            .addOnSuccessListener { rightResult ->
                                                worker.execute {
                                                    try {
                                                        store.appendPair(currentBook, leftResult.text, rightResult.text)
                                                        updateCount()
                                                    } catch (e: Exception) { message("Save error: ${e.message}") }
                                                    finally { finishCapture() }
                                                }
                                            }.addOnFailureListener {
                                                message("Right-page OCR error: ${it.message}"); finishCapture()
                                            }
                                    }.addOnFailureListener {
                                        message("Left-page OCR error: ${it.message}"); finishCapture()
                                    }
                            }
                        } catch (e: Exception) {
                            message("Processing error: ${e.message}")
                            finishCapture()
                        }
                    }
                }
            })
    }
    private fun finishCapture() {
        runOnUiThread { busy = false; captureButton.isEnabled = true }
    }
    private fun exportText() {
        try {
            val file = store.rebuildMaster(bookId)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export scanned book"))
        } catch (e: Exception) { message("Export failed: ${e.message}") }
    }
    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
        worker.shutdown()
    }
}
