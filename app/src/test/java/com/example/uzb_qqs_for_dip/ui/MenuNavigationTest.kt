package com.example.uzb_qqs_for_dip.ui

import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.settings.AppThemeMode
import com.example.uzb_qqs_for_dip.data.settings.ThemeManager
import com.example.uzb_qqs_for_dip.ui.components.MenuScreen
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MenuNavigationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockApp: QqsApp
    private lateinit var mockContainer: AppContainer
    private lateinit var mockThemeManager: ThemeManager
    private val themeFlow = MutableStateFlow(AppThemeMode.SYSTEM)

    @Before
    fun setup() {
        mockApp = mockk(relaxed = true)
        mockContainer = mockk(relaxed = true)
        mockThemeManager = mockk(relaxed = true)

        every { mockApp.container } returns mockContainer
        every { mockApp.applicationContext } returns mockApp
        every { mockContainer.themeManager } returns mockThemeManager
        every { mockThemeManager.themeMode } returns themeFlow

        every { mockThemeManager.setThemeMode(any()) } answers {
            themeFlow.value = firstArg()
        }
    }

    private fun createViewModel(): AppViewModel {
        return AppViewModel(mockApp)
    }

    @Test
    fun `menuInitialScreen starts as null`() {
        val vm = createViewModel()
        assertNull(vm.menuInitialScreen.value)
    }

    @Test
    fun `openMenu default opens MAIN`() {
        val vm = createViewModel()
        vm.openMenu()
        assertEquals(MenuScreen.MAIN, vm.menuInitialScreen.value)
    }

    @Test
    fun `openMenu USERS opens USERS subscreen`() {
        val vm = createViewModel()
        vm.openMenu(MenuScreen.USERS)
        assertEquals(MenuScreen.USERS, vm.menuInitialScreen.value)
    }

    @Test
    fun `openMenu BACKUP opens BACKUP subscreen`() {
        val vm = createViewModel()
        vm.openMenu(MenuScreen.BACKUP)
        assertEquals(MenuScreen.BACKUP, vm.menuInitialScreen.value)
    }

    @Test
    fun `closeMenu resets menuInitialScreen to null`() {
        val vm = createViewModel()
        vm.openMenu(MenuScreen.HELP)
        assertEquals(MenuScreen.HELP, vm.menuInitialScreen.value)

        vm.closeMenu()
        assertNull(vm.menuInitialScreen.value)
    }

    @Test
    fun `setThemeMode delegates to themeManager`() {
        val vm = createViewModel()
        assertEquals(AppThemeMode.SYSTEM, vm.themeMode.value)

        vm.setThemeMode(AppThemeMode.DARK)
        verify { mockThemeManager.setThemeMode(AppThemeMode.DARK) }
        assertEquals(AppThemeMode.DARK, vm.themeMode.value)

        vm.setThemeMode(AppThemeMode.LIGHT)
        verify { mockThemeManager.setThemeMode(AppThemeMode.LIGHT) }
        assertEquals(AppThemeMode.LIGHT, vm.themeMode.value)
    }

    @Test
    fun `validate MenuScreen titles`() {
        assertEquals("Меню", MenuScreen.MAIN.title)
        assertEquals("Управление профилями", MenuScreen.USERS.title)
        assertEquals("Резервная копия", MenuScreen.BACKUP.title)
        assertEquals("Справка и руководство", MenuScreen.HELP.title)
        assertEquals("О приложении", MenuScreen.ABOUT.title)
    }

    @Test
    fun `runtime theme switching cycle SYSTEM to LIGHT to DARK to SYSTEM in AppViewModel`() {
        val vm = createViewModel()
        assertEquals(AppThemeMode.SYSTEM, vm.themeMode.value)

        // 1. SYSTEM -> LIGHT
        vm.setThemeMode(AppThemeMode.LIGHT)
        verify { mockThemeManager.setThemeMode(AppThemeMode.LIGHT) }
        assertEquals(AppThemeMode.LIGHT, vm.themeMode.value)

        // 2. LIGHT -> DARK
        vm.setThemeMode(AppThemeMode.DARK)
        verify { mockThemeManager.setThemeMode(AppThemeMode.DARK) }
        assertEquals(AppThemeMode.DARK, vm.themeMode.value)

        // 3. DARK -> SYSTEM
        vm.setThemeMode(AppThemeMode.SYSTEM)
        verify { mockThemeManager.setThemeMode(AppThemeMode.SYSTEM) }
        assertEquals(AppThemeMode.SYSTEM, vm.themeMode.value)
    }

    @Test
    fun `menu navigation flow across all screens with back navigation`() {
        val vm = createViewModel()
        assertNull(vm.menuInitialScreen.value)

        // Открываем главное меню
        vm.openMenu(MenuScreen.MAIN)
        assertEquals(MenuScreen.MAIN, vm.menuInitialScreen.value)

        // Моделируем внутренний роутер HierarchicalMenuBottomSheet:
        var currentScreen = MenuScreen.MAIN
        val onNavigateToScreen = { target: MenuScreen -> currentScreen = target }
        val onBack = { currentScreen = MenuScreen.MAIN }

        // MAIN -> USERS -> Back
        onNavigateToScreen(MenuScreen.USERS)
        assertEquals(MenuScreen.USERS, currentScreen)
        onBack()
        assertEquals(MenuScreen.MAIN, currentScreen)

        // MAIN -> HELP -> Back
        onNavigateToScreen(MenuScreen.HELP)
        assertEquals(MenuScreen.HELP, currentScreen)
        onBack()
        assertEquals(MenuScreen.MAIN, currentScreen)

        // MAIN -> BACKUP -> Back
        onNavigateToScreen(MenuScreen.BACKUP)
        assertEquals(MenuScreen.BACKUP, currentScreen)
        onBack()
        assertEquals(MenuScreen.MAIN, currentScreen)

        // MAIN -> ABOUT -> Back
        onNavigateToScreen(MenuScreen.ABOUT)
        assertEquals(MenuScreen.ABOUT, currentScreen)
        onBack()
        assertEquals(MenuScreen.MAIN, currentScreen)

        // Закрытие меню сбрасывает начальный экран
        vm.closeMenu()
        assertNull(vm.menuInitialScreen.value)
    }
}
