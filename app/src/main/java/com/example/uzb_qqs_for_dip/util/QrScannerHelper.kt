package com.example.uzb_qqs_for_dip.util

import android.app.Activity
import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Запускает системный QR-сканер ML Kit. Используется как на вкладке «Добавить»,
 * так и на экране «Проверка чеков» аудитора.
 */
fun startQrScanner(
    context: Context,
    onScanned: (String?) -> Unit,
    onError: (String) -> Unit
) {
    val activity = context.findActivity()
    if (activity == null) {
        onError("Не удалось открыть сканер: нет активности")
        return
    }
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    val scanner = GmsBarcodeScanning.getClient(activity, options)

    fun launchScan() {
        scanner.startScan()
            .addOnSuccessListener { barcode -> onScanned(barcode.rawValue) }
            .addOnCanceledListener { /* пользователь закрыл */ }
            .addOnFailureListener { e ->
                onError("Сканер недоступен: ${e.localizedMessage ?: e::class.simpleName}")
            }
    }

    val moduleInstall = ModuleInstall.getClient(activity)
    val request = ModuleInstallRequest.newBuilder().addApi(scanner).build()
    moduleInstall.installModules(request)
        .addOnSuccessListener { launchScan() }
        .addOnFailureListener { launchScan() }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
