package com.example.uzb_qqs_for_dip.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.uzb_qqs_for_dip.R
import com.example.uzb_qqs_for_dip.ui.CameraScanResult
import com.example.uzb_qqs_for_dip.ui.CameraScanSession
import com.example.uzb_qqs_for_dip.ui.CameraScanStatus
import com.example.uzb_qqs_for_dip.ui.theme.ScannerDimensions
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class ValidationStatus {
    IDLE,
    VALIDATING,
    SUCCESS,
    WARNING,
    DUPLICATE,
    ERROR
}

@Composable
fun MultiQrCameraScannerDialog(
    onDismiss: () -> Unit,
    isAuditor: Boolean = false,
    employeeName: String? = null,
    onQrDetected: suspend (String) -> CameraScanResult
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_permission),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScannerDimensions.touchTarget)
                        ) {
                            Text(stringResource(R.string.scanner_restart))
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScannerDimensions.touchTarget)
                        ) {
                            Text(stringResource(R.string.scanner_settings))
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScannerDimensions.touchTarget)
                        ) {
                            Text(stringResource(R.string.scanner_finish))
                        }
                    }
                }
                else -> {
                    MultiQrCameraContent(
                        isAuditor = isAuditor,
                        employeeName = employeeName,
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
    isAuditor: Boolean,
    employeeName: String?,
    onDismiss: () -> Unit,
    onQrDetected: suspend (String) -> CameraScanResult
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val session = remember { CameraScanSession() }

    var currentStatus by remember { mutableStateOf(ValidationStatus.IDLE) }
    var currentResult by remember { mutableStateOf<CameraScanResult?>(null) }
    var activeCode by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf(false) }
    var restartTrigger by remember { mutableStateOf(0) }

    val currentOnQrDetected by rememberUpdatedState(onQrDetected)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzing = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    var dismissJob by remember { mutableStateOf<Job?>(null) }

    fun showResult(code: String, result: CameraScanResult) {
        session.finish(code, result)
        currentResult = result
        activeCode = code
        currentStatus = when (result.status) {
            CameraScanStatus.SUCCESS -> ValidationStatus.SUCCESS
            CameraScanStatus.WARNING -> ValidationStatus.WARNING
            CameraScanStatus.DUPLICATE -> ValidationStatus.DUPLICATE
            CameraScanStatus.ERROR -> ValidationStatus.ERROR
        }
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(2500)
            currentStatus = ValidationStatus.IDLE
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
                    try {
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
                            var closed = false
                            fun closeImage() {
                                if (!closed) {
                                    closed = true
                                    analyzing.set(false)
                                    imageProxy.close()
                                }
                            }

                            if (currentStatus == ValidationStatus.VALIDATING) {
                                closeImage()
                                return@setAnalyzer
                            }

                            if (!analyzing.compareAndSet(false, true)) {
                                closeImage()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                closeImage()
                                return@setAnalyzer
                            }

                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            try {
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val urls = barcodes.mapNotNull { it.rawValue?.trim() }
                                            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }

                                        if (urls.isEmpty()) {
                                            return@addOnSuccessListener
                                        }

                                        val nowMs = System.currentTimeMillis()
                                        val nextCode = session.next(urls, nowMs) ?: return@addOnSuccessListener

                                        if (session.wasCompleted(nextCode)) {
                                            val dupMsg = if (isAuditor) R.string.scanner_verified_duplicate else R.string.scanner_duplicate
                                            showResult(nextCode, CameraScanResult(CameraScanStatus.DUPLICATE, dupMsg))
                                            return@addOnSuccessListener
                                        }

                                        currentStatus = ValidationStatus.VALIDATING
                                        currentResult = null
                                        activeCode = nextCode
                                        dismissJob?.cancel()

                                        scope.launch {
                                            val result = currentOnQrDetected(nextCode)
                                            showResult(nextCode, result)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        closeImage()
                                    }
                            } catch (_: Exception) {
                                closeImage()
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        cameraError = false
                    } catch (_: Exception) {
                        cameraError = true
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            update = { view ->
                // restartTrigger referenced to trigger recomposition when user retries
                if (restartTrigger > 0 && cameraError) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(view.context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            cameraProvider.unbindAll()
                            cameraError = false
                        } catch (_: Exception) {
                            cameraError = true
                        }
                    }, ContextCompat.getMainExecutor(view.context))
                }
            }
        )

        // Top panel
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.75f))
                .statusBarsPadding()
                .padding(horizontal = ScannerDimensions.padding, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.scanner_title),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(ScannerDimensions.touchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.scanner_close),
                        tint = Color.White
                    )
                }
            }

            val countText = if (isAuditor) {
                stringResource(R.string.scanner_verified_count, session.count)
            } else {
                stringResource(R.string.scanner_added_count, session.count)
            }
            Text(
                text = countText,
                color = Color(0xFF81C784),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )

            if (isAuditor && employeeName != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.scanner_employee, employeeName),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(4.dp))
            val hintText = if (isAuditor) {
                stringResource(R.string.scanner_auditor_hint)
            } else {
                stringResource(R.string.scanner_hint)
            }
            Text(
                text = hintText,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Camera error overlay
        if (cameraError) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(ScannerDimensions.padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.scanner_camera_error),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { restartTrigger++ },
                            modifier = Modifier.height(ScannerDimensions.touchTarget)
                        ) {
                            Text(stringResource(R.string.scanner_restart))
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(ScannerDimensions.touchTarget)
                        ) {
                            Text(stringResource(R.string.scanner_finish))
                        }
                    }
                }
            }
        }

        // Bottom panel
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(ScannerDimensions.padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ScannerDimensions.gap)
        ) {
            if (currentStatus != ValidationStatus.IDLE) {
                val bgColor = when (currentStatus) {
                    ValidationStatus.VALIDATING -> Color(0xFF37474F)
                    ValidationStatus.SUCCESS -> Color(0xFF2E7D32)
                    ValidationStatus.WARNING -> Color(0xFFE65100)
                    ValidationStatus.DUPLICATE -> Color(0xFFEF6C00)
                    ValidationStatus.ERROR -> Color(0xFFC62828)
                    ValidationStatus.IDLE -> Color.Transparent
                }
                val icon = when (currentStatus) {
                    ValidationStatus.SUCCESS -> Icons.Default.Check
                    ValidationStatus.WARNING -> Icons.Default.Warning
                    ValidationStatus.DUPLICATE -> Icons.Default.Info
                    ValidationStatus.ERROR -> Icons.Default.Error
                    else -> null
                }
                val messageText = if (currentStatus == ValidationStatus.VALIDATING) {
                    stringResource(R.string.scanner_checking)
                } else {
                    currentResult?.let { res ->
                        if (res.args.isNotEmpty()) {
                            stringResource(res.message, *res.args.toTypedArray())
                        } else {
                            stringResource(res.message)
                        }
                    }.orEmpty()
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .padding(ScannerDimensions.padding)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (currentStatus == ValidationStatus.VALIDATING) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(ScannerDimensions.indicator),
                                strokeWidth = 2.dp
                            )
                        } else if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(ScannerDimensions.indicator)
                            )
                        }
                        Text(
                            text = messageText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentResult?.retryable == true && activeCode != null) {
                            IconButton(
                                onClick = {
                                    val code = activeCode ?: return@IconButton
                                    session.retry(code)
                                    currentStatus = ValidationStatus.VALIDATING
                                    dismissJob?.cancel()
                                    scope.launch {
                                        val res = currentOnQrDetected(code)
                                        showResult(code, res)
                                    }
                                },
                                modifier = Modifier.size(ScannerDimensions.touchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.scanner_retry),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScannerDimensions.touchTarget),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.scanner_finish))
            }
        }
    }
}
