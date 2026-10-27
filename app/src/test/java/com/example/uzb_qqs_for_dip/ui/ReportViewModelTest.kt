package com.example.uzb_qqs_for_dip.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportViewModelTest {

    @Test
    fun testExportModeDefaultsToAll() {
        val defaultMode = ExportMode.ALL
        assertEquals("По умолчанию режим экспорта должен быть 'Всё'", ExportMode.ALL, defaultMode)
        assertEquals("В режиме REPORT название должно быть 'Отчёт'", "Отчёт", ExportMode.REPORT.title)
        assertEquals("В режиме ALL название должно быть 'Всё'", "Всё", ExportMode.ALL.title)
        assertEquals("В режиме RECEIPTS название должно быть 'Чеки'", "Чеки", ExportMode.RECEIPTS.title)
    }
}
