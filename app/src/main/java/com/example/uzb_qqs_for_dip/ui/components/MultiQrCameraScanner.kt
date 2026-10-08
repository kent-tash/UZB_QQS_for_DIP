package com.example.uzb_qqs_for_dip.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class ValidationStatus {
    IDLE,
    VALIDATING,
    SUCCESS,
    ERROR
}

@Composable
fun MultiQrCameraScannerDialog(
    onDismiss: () -> Unit,
    onQrDetected: suspend (String) -> Pair<Boolean, String>
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when {
                !hasPermission -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Нужен доступ к камере, чтобы сканировать QR",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Разрешить камеру")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onDismiss) { Text("Отмена") }
                    }
                }
                else -> {
                    MultiQrCameraContent(
                        onDismiss = onDismiss,
                        onQrDetected = onQrDetected
                    )
                }
            }
        }
    }
}

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun MultiQrCameraContent(
    onDismiss: () -> Unit,
    onQrDetected: suspend (String) -> Pair<Boolean, String>
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(ValidationStatus.IDLE) }
    var messageJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var message by remember { mutableStateOf("") }
    val alreadyScanned = remember { mutableSetOf<String>() }
    var successCount by remember { mutableStateOf(0) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzing = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    val scanner = BarcodeScanning.getClient(options)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (status == ValidationStatus.VALIDATING) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        if (!analyzing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            analyzing.set(false)
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val raw = barcode.rawValue?.trim().orEmpty()
                                    if (raw.isEmpty()) continue
                                    if (!raw.startsWith("http://") && !raw.startsWith("https://")) continue
                                    
                                    if (alreadyScanned.contains(raw)) continue

                                    status = ValidationStatus.VALIDATING
                                    alreadyScanned.add(raw)

                                    messageJob?.cancel()
                                    messageJob = scope.launch {
                                        val (success, msg) = onQrDetected(raw)
                                        message = msg
                                        status = if (success) ValidationStatus.SUCCESS else ValidationStatus.ERROR
                                        if (success) successCount++
                                        
                                        kotlinx.coroutines.delay(2500)
                                        status = ValidationStatus.IDLE
                                        message = ""
                                    }
                                    break
                                }
                            }
                            .addOnCompleteListener {
                                analyzing.set(false)
                                imageProxy.close()
                            }
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Скан чеков",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Закрыть", tint = Color.White)
                }
            }
            Text(
                "Наведите на QR код. Сканер автоматически проверит и сохранит его.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (status != ValidationStatus.IDLE) {
            val bgColor = when {
                status == ValidationStatus.VALIDATING -> Color.DarkGray
                message.contains("нулевым") -> Color(0xFFFF9800) // Orange
                message.contains("уже отсканирован") -> Color(0xFFFF9800) // Orange
                status == ValidationStatus.SUCCESS -> Color(0xFF4CAF50) // Green
                else -> Color(0xFFF44336) // Red
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 70.dp)
                    .align(Alignment.TopCenter)
                    .background(bgColor, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == ValidationStatus.VALIDATING) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.padding(horizontal = 8.dp))
                    }
                    Text(
                        text = if (status == ValidationStatus.VALIDATING) "Проверка..." else message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Завершить сканирование")
            }
        }
    }
}
