package com.example.uzb_qqs_for_dip.export

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExporter {
    fun createZip(files: List<File>, outputFile: File): File {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            for (file in files) {
                if (!file.exists()) continue
                FileInputStream(file).use { fis ->
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        return outputFile
    }
}
