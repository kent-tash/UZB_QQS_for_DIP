package com.example.uzb_qqs_for_dip.export

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

object ExportPaths {
    fun exportsDir(context: Context): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun shareUriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
