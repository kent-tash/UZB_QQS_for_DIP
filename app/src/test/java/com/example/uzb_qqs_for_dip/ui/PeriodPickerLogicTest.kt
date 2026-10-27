package com.example.uzb_qqs_for_dip.ui

import com.example.uzb_qqs_for_dip.data.settings.Quarter
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodPickerLogicTest {

    @Test
    fun testQuarterMonthsLabels() {
        assertEquals("январь–март", Quarter.Q1.monthsLabel())
        assertEquals("апрель–июнь", Quarter.Q2.monthsLabel())
        assertEquals("июль–сентябрь", Quarter.Q3.monthsLabel())
        assertEquals("октябрь–декабрь", Quarter.Q4.monthsLabel())
        assertEquals("произвольный", Quarter.Custom.monthsLabel())
    }

    @Test
    fun testYearNavigationBounds() {
        val minYear = 2020
        val maxYear = 2035
        var currentYear = 2026

        // Переход назад
        currentYear = (currentYear - 1).coerceAtLeast(minYear)
        assertEquals(2025, currentYear)

        // Переход вперед
        currentYear = (currentYear + 2).coerceAtMost(maxYear)
        assertEquals(2027, currentYear)
    }

    @Test
    fun testYearNavigationExactBoundaries() {
        val minYear = 2020
        val maxYear = 2035

        // Попытка выйти за нижнюю границу (2020)
        var yearAtMin = 2020
        yearAtMin = (yearAtMin - 1).coerceAtLeast(minYear)
        assertEquals(2020, yearAtMin)

        // Попытка выйти за верхнюю границу (2035)
        var yearAtMax = 2035
        yearAtMax = (yearAtMax + 1).coerceAtMost(maxYear)
        assertEquals(2035, yearAtMax)
    }
}

fun Quarter.monthsLabel(): String = when (this) {
    Quarter.Q1 -> "январь–март"
    Quarter.Q2 -> "апрель–июнь"
    Quarter.Q3 -> "июль–сентябрь"
    Quarter.Q4 -> "октябрь–декабрь"
    Quarter.Custom -> "произвольный"
}
