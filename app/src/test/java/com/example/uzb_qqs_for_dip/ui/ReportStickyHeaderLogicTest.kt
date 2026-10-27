package com.example.uzb_qqs_for_dip.ui

import androidx.compose.ui.state.ToggleableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тестирование логики вычисления состояния залипания (Sticky Header),
 * визуальных параметров панели сортировки, пакетных действий и граничных случаев.
 */
class ReportStickyHeaderLogicTest {

    object StickyHeaderCalculator {
        // Пороговый индекс элемента списка, при котором шапка отчета (0),
        // селектор режима (1) и кнопки экспорта (2) скрылись за верхним краем
        const val PINNED_THRESHOLD_INDEX = 3

        fun calculateIsPinned(firstVisibleItemIndex: Int): Boolean {
            return firstVisibleItemIndex >= PINNED_THRESHOLD_INDEX
        }

        fun getShadowElevationDp(isPinned: Boolean): Float = if (isPinned) 4f else 0f
        fun getTonalElevationDp(isPinned: Boolean): Float = if (isPinned) 2f else 0f
        fun shouldShowDivider(isPinned: Boolean): Boolean = isPinned

        fun resolveToggleState(totalCount: Int, selectedCount: Int): ToggleableState = when {
            totalCount == 0 || selectedCount == 0 -> ToggleableState.Off
            selectedCount == totalCount -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }

        fun isBatchActionVisible(selectedCount: Int): Boolean = selectedCount > 0

        fun isSelectAllInteractive(totalCount: Int): Boolean = totalCount > 0
    }

    @Test
    fun testIsPinnedCalculation() {
        // До порога (элементы 0, 1, 2 на экране)
        assertFalse("Индекс 0 не должен вызывать залипание", StickyHeaderCalculator.calculateIsPinned(0))
        assertFalse("Индекс 1 не должен вызывать залипание", StickyHeaderCalculator.calculateIsPinned(1))
        assertFalse("Индекс 2 не должен вызывать залипание", StickyHeaderCalculator.calculateIsPinned(2))

        // Начиная с порога (шапка скрылась)
        assertTrue("Индекс 3 должен активировать залипание", StickyHeaderCalculator.calculateIsPinned(3))
        assertTrue("Индекс 4 должен активировать залипание", StickyHeaderCalculator.calculateIsPinned(4))
        assertTrue("Индекс 10 должен активировать залипание", StickyHeaderCalculator.calculateIsPinned(10))
    }

    @Test
    fun testElevationAndDividerValues() {
        // В незалипшем состоянии
        assertEquals(0f, StickyHeaderCalculator.getShadowElevationDp(isPinned = false), 0.001f)
        assertEquals(0f, StickyHeaderCalculator.getTonalElevationDp(isPinned = false), 0.001f)
        assertFalse(StickyHeaderCalculator.shouldShowDivider(isPinned = false))

        // В залипшем состоянии
        assertEquals(4f, StickyHeaderCalculator.getShadowElevationDp(isPinned = true), 0.001f)
        assertEquals(2f, StickyHeaderCalculator.getTonalElevationDp(isPinned = true), 0.001f)
        assertTrue(StickyHeaderCalculator.shouldShowDivider(isPinned = true))
    }

    @Test
    fun testEmptyListEdgeCase() {
        // 1. Граничный случай: список чеков пуст (totalCount = 0)
        val totalCount = 0
        val selectedCount = 0

        // Чекбокс и кнопка "Выбрать все" неактивны при пустом списке
        assertFalse("Интерактивность выбора должна быть отключена при totalCount = 0",
            StickyHeaderCalculator.isSelectAllInteractive(totalCount))

        // Toggle-состояние должно быть Off
        assertEquals(
            "При пустом списке toggle-состояние должно быть Off",
            ToggleableState.Off,
            StickyHeaderCalculator.resolveToggleState(totalCount, selectedCount)
        )

        // Панель действий скрыта
        assertFalse(
            "Панель действий не должна показываться при пустом списке",
            StickyHeaderCalculator.isBatchActionVisible(selectedCount)
        )

        // Начальная позиция скролла при пустом списке
        assertFalse(
            "При пустом списке в начале скролла залипание неактивно",
            StickyHeaderCalculator.calculateIsPinned(0)
        )
    }

    @Test
    fun testRapidScrollExtremes() {
        // 2. Граничный случай: быстрый скролл вверх/вниз с резкими скачками firstVisibleItemIndex
        val indicesSequence = listOf(0, 50, 100, 250, 500, 150, 2, 0, 3, 1, 999, 0)
        val expectedPinned = listOf(false, true, true, true, true, true, false, false, true, false, true, false)

        for (i in indicesSequence.indices) {
            val index = indicesSequence[i]
            val expected = expectedPinned[i]
            val actual = StickyHeaderCalculator.calculateIsPinned(index)
            assertEquals("Быстрый скролл на индекс $index должен давать pinned=$expected", expected, actual)

            val shadow = StickyHeaderCalculator.getShadowElevationDp(actual)
            val expectedShadow = if (expected) 4f else 0f
            assertEquals("Тень для индекса $index должна быть $expectedShadow dp", expectedShadow, shadow, 0.001f)
        }
    }

    @Test
    fun testSelectionActionBarStatesWithPinnedHeader() {
        // 3. Выбор чеков при закреплённой панели
        val totalCount = 20

        // Случай A: Ничего не выбрано при закреплённой панели
        val isPinned = StickyHeaderCalculator.calculateIsPinned(5)
        assertTrue(isPinned)
        assertEquals(ToggleableState.Off, StickyHeaderCalculator.resolveToggleState(totalCount, 0))
        assertFalse(StickyHeaderCalculator.isBatchActionVisible(0))

        // Случай B: Частичный выбор (выбрано 3 чека) при закреплённой панели
        val partialSelected = 3
        assertEquals(ToggleableState.Indeterminate, StickyHeaderCalculator.resolveToggleState(totalCount, partialSelected))
        assertTrue("SelectionActionBar должен отображаться при partialSelected > 0",
            StickyHeaderCalculator.isBatchActionVisible(partialSelected))

        // Случай C: Полный выбор (выбраны все 20 чеков) при закреплённой панели
        val allSelected = 20
        assertEquals(ToggleableState.On, StickyHeaderCalculator.resolveToggleState(totalCount, allSelected))
        assertTrue("SelectionActionBar должен отображаться при allSelected",
            StickyHeaderCalculator.isBatchActionVisible(allSelected))
    }

    @Test
    fun testClearSelectionAndBatchDeleteTransitions() {
        // 4. Очистка выбора («Снять») и удаление
        var selectedIds = mutableSetOf(101L, 102L, 103L)
        var totalCount = 10

        // До очистки: панель видна, состояние Indeterminate
        assertTrue(StickyHeaderCalculator.isBatchActionVisible(selectedIds.size))
        assertEquals(ToggleableState.Indeterminate, StickyHeaderCalculator.resolveToggleState(totalCount, selectedIds.size))

        // Действие: нажатие кнопки "Снять" (clearSelection)
        selectedIds.clear()
        assertFalse("После очистки SelectionActionBar должен скрыться",
            StickyHeaderCalculator.isBatchActionVisible(selectedIds.size))
        assertEquals("После очистки toggle-состояние должно быть Off",
            ToggleableState.Off,
            StickyHeaderCalculator.resolveToggleState(totalCount, selectedIds.size))

        // Действие: выбор 2 элементов перед удалением
        selectedIds.addAll(listOf(201L, 202L))
        assertTrue(StickyHeaderCalculator.isBatchActionVisible(selectedIds.size))

        // Действие: подтверждение пакетного удаления
        val deletedCount = selectedIds.size
        totalCount -= deletedCount
        selectedIds.clear()

        // После удаления: выбранные ID сброшены, бар скрыт, общее число уменьшилось
        assertEquals(0, selectedIds.size)
        assertEquals(8, totalCount)
        assertFalse(StickyHeaderCalculator.isBatchActionVisible(selectedIds.size))
        assertEquals(ToggleableState.Off, StickyHeaderCalculator.resolveToggleState(totalCount, selectedIds.size))
    }
}
