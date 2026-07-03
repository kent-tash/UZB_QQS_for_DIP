package com.example.uzb_qqs_for_dip.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UriFileWriter {

    suspend fun copyFileToUri(context: Context, source: File, uri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Не удалось записать файл")
        }
    }
}
