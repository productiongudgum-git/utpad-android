package com.example.gudgum_prod_flow.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gudgum_prod_flow.ui.theme.GudGumProdFlowTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object BarcodeScannerExtras {
    const val PromptText = "barcode_scanner_prompt"
    const val ResultBarcodeValue = "barcode_scanner_result"
    const val ResultQuantity = "barcode_scanner_quantity"
}

class BarcodeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prompt = "Scan QR Code"

        setContent {
            GudGumProdFlowTheme {
                BarcodeScannerRoute(
                    prompt = prompt,
                    onClose = ::closeCancelled,
                    onBarcodeConfirmed = ::closeWithBarcode,
                )
            }
        }
    }

    private fun closeCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun closeWithBarcode(value: String, quantity: Int) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(BarcodeScannerExtras.ResultBarcodeValue, value)
                .putExtra(BarcodeScannerExtras.ResultQuantity, quantity),
        )
        finish()
    }
}

@Composable
private fun BarcodeScannerRoute(
    prompt: String,
    onClose: () -> Unit,
    onBarcodeConfirmed: (String, Int) -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var scanEnabled by remember { mutableStateOf(true) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            MlKitBarcodeScannerPreview(
                modifier = Modifier.fillMaxSize(),
                scanEnabled = scanEnabled && scannedBarcode == null,
                onBarcodeDetected = { value ->
                    if (scannedBarcode == null) {
                        scannedBarcode = value
                        // Immediately populate and return
                        onBarcodeConfirmed(value, 1)
                    }
                },
            )
            // Removed ScannerFrameOverlay (green overlay)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
            ) {
                CameraPermissionRequired(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 22.dp),
                    onGrantPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onClose = onClose,
                )
            }
        }

        ScannerTopControls(
            label = prompt,
            scanEnabled = scanEnabled,
            onScanEnabledChange = { enabled ->
                scanEnabled = enabled
                if (enabled) {
                    scannedBarcode = null
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 18.dp, start = 16.dp, end = 16.dp),
        )

        // Center bottom back button
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6BFF)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = "Back",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun ScannerTopControls(
    label: String,
    scanEnabled: Boolean,
    onScanEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Removed volume button

        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(18.dp),
            color = Color(0x991F1F1F),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 190.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
                Switch(
                    checked = scanEnabled,
                    onCheckedChange = onScanEnabledChange,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5E567C),
                        checkedTrackColor = Color(0xFFE8E7F2),
                        uncheckedThumbColor = Color(0xFF5E567C),
                        uncheckedTrackColor = Color(0xFFDCD9E8),
                    ),
                )
            }
        }
    }
}

// Removed ScanSuccessCard and QuantityCircleButton

@Composable
private fun CameraPermissionRequired(
    modifier: Modifier = Modifier,
    onGrantPermission: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Camera permission is required to scan barcodes.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onGrantPermission) {
                Text("Grant camera permission")
            }
            TextButton(onClick = onClose) {
                Text("Go back")
            }
        }
    }
}

@Composable
private fun MlKitBarcodeScannerPreview(
    modifier: Modifier = Modifier,
    scanEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val detectionEnabled = remember { AtomicBoolean(scanEnabled) }

    LaunchedEffect(scanEnabled) {
        detectionEnabled.set(scanEnabled)
    }

    DisposableEffect(lifecycleOwner) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        var imageAnalysis: ImageAnalysis? = null
        var cameraProvider: ProcessCameraProvider? = null
        var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
        var analyzerAlive = true
        var lastCandidate: String? = null
        var stableCount = 0

        val listener = Runnable {
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val previewBuilder = Preview.Builder()
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            Camera2Interop.Extender(analysisBuilder)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            val analysisUseCase = analysisBuilder.build()
            imageAnalysis = analysisUseCase

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysisUseCase,
                )

                val maxZoomRatio = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

                val optionsBuilder = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .enableAllPotentialBarcodes()

                if (maxZoomRatio > 1f) {
                    val zoomOptions = ZoomSuggestionOptions.Builder { zoomRatio ->
                        camera.cameraControl.setZoomRatio(zoomRatio)
                        true
                    }
                        .setMaxSupportedZoomRatio(maxZoomRatio)
                        .build()
                    optionsBuilder.setZoomSuggestionOptions(zoomOptions)
                }

                scanner = BarcodeScanning.getClient(optionsBuilder.build())

                analysisUseCase.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    val activeScanner = scanner
                    if (!analyzerAlive || mediaImage == null || activeScanner == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    if (!detectionEnabled.get()) {
                        lastCandidate = null
                        stableCount = 0
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )

                    activeScanner
                        .process(image)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes
                                .asSequence()
                                .mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                                .firstOrNull()

                            if (value == null) {
                                lastCandidate = null
                                stableCount = 0
                            } else if (value == lastCandidate) {
                                stableCount += 1
                            } else {
                                lastCandidate = value
                                stableCount = 1
                            }

                            if (value != null && stableCount >= 2 && detectionEnabled.get()) {
                                detectionEnabled.set(false)
                                onBarcodeDetected(value)
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            } catch (_: Exception) {
                analyzerAlive = false
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            analyzerAlive = false
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            scanner?.close()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

// Removed ScannerFrameOverlay and itemNameForBarcode
