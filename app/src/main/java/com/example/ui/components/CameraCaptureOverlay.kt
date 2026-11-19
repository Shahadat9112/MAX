package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.DarkBlueCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SubtleBlueBorder
import com.example.ui.theme.SuccessMint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraCaptureOverlay(
    autoCapture: Boolean,
    useFrontCamera: Boolean = false,
    lastCapturedUri: Uri?,
    onPhotoCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onClose)

    var lensFacing by remember(useFrontCamera) {
        mutableIntStateOf(
            if (useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureFeedback by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }

    DisposableEffect(lensFacing, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
            }.onFailure { err ->
                Log.e("CameraOverlay", "Camera bind failed", err)
                onError("Unable to open camera: ${err.localizedMessage}")
            }
        }, executor)

        onDispose {
            boundProvider?.unbindAll()
        }
    }

    fun triggerCapture() {
        val capture = imageCapture ?: return
        if (isCapturing) return
        isCapturing = true
        capturePhotoToMediaStore(
            context = context,
            imageCapture = capture,
            onSaved = { uri ->
                isCapturing = false
                captureFeedback = "Saved to Pictures/MAX AI"
                onPhotoCaptured(uri)
            },
            onFailed = { err ->
                isCapturing = false
                onError(err)
            }
        )
    }

    LaunchedEffect(autoCapture, imageCapture) {
        if (autoCapture && imageCapture != null) {
            delay(700L)
            triggerCapture()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("camera_capture_overlay")
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top controls
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = ObsidianBlack.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, SubtleBlueBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (autoCapture) "MAX Camera • Auto Capturing…" else "MAX Camera",
                        style = MaterialTheme.typography.labelLarge,
                        color = IceBlueText
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(ObsidianBlack.copy(alpha = 0.72f), CircleShape)
                    .testTag("close_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close_camera),
                    tint = IceBlueText
                )
            }
        }

        // Bottom shutter dock
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            if (captureFeedback != null || lastCapturedUri != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkBlueCard.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, SuccessMint.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = SuccessMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = captureFeedback ?: "Last photo saved to Gallery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceBlueText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.size(48.dp))

                FloatingActionButton(
                    onClick = { triggerCapture() },
                    containerColor = ElectricBlue,
                    contentColor = ObsidianBlack,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("camera_shutter_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(R.string.capture_photo),
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(DarkBlueCard.copy(alpha = 0.8f), CircleShape)
                        .testTag("switch_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = stringResource(R.string.switch_camera),
                        tint = IceBlueText
                    )
                }
            }
        }
    }
}

private fun capturePhotoToMediaStore(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (Uri) -> Unit,
    onFailed: (String) -> Unit
) {
    val fileName = "MAX_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MAX AI")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri
                if (uri != null) {
                    onSaved(uri)
                } else {
                    onFailed("Photo captured, but URI was unavailable.")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onFailed("Failed to save photo: ${exception.localizedMessage}")
            }
        }
    )
}
