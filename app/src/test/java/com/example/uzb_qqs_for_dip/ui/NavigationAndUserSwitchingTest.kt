package com.example.uzb_qqs_for_dip.ui

import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.repository.ReceiptRepository
import com.example.uzb_qqs_for_dip.data.repository.UserRepository
import com.example.uzb_qqs_for_dip.data.session.SessionManager
import com.example.uzb_qqs_for_dip.ui.navigation.MainTab
import com.example.uzb_qqs_for_dip.ui.navigation.TopRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit-тесты для валидации структуры меню (MainTab), логики навигации,
 * переключения ролей и сквозного переключения пользователей в AppViewModel.
 *
 * Также тестируются граничные случаи:
 * 1. Пользователь не авторизован / список пользователей пуст (currentUser == null).
 * 2. Редактирование профиля с пустыми полями (валидация ошибки editError).
 * 3. Удаление текущего активного пользователя (logout и переход) vs удаление неактивного.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationAndUserSwitchingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockApp: QqsApp
    private lateinit var mockContainer: AppContainer
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockReceiptRepository: ReceiptRepository
    private lateinit var mockSessionManager: SessionManager

    private val userFlow = MutableStateFlow<List<User>>(emptyList())
    private val currentUserIdFlow = MutableStateFlow<Long?>(null)

    private val employeeUser = User(
        id = 1L,
        fullName = "Иванов Иван Иванович",
        position = "Бухгалтер",
        initialsSurname = "И.И. Иванов",
        organization = "ООО Вектор",
        role = UserRole.EMPLOYEE
    )

    private val auditorUser = User(
        id = 2L,
        fullName = "Петров Петр Петрович",
        position = "Главный аудитор",
        initialsSurname = "П.П. Петров",
        organization = "АудитКонсалт",
        role = UserRole.AUDITOR
    )

    @Before
    fun setup() {
        mockApp = mockk(relaxed = true)
        mockContainer = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)
        mockReceiptRepository = mockk(relaxed = true)
        mockSessionManager = mockk(relaxed = true)

        every { mockApp.container } returns mockContainer
        every { mockApp.applicationContext } returns mockApp

        every { mockContainer.userRepository } returns mockUserRepository
        every { mockContainer.receiptRepository } returns mockReceiptRepository
        every { mockContainer.sessionManager } returns mockSessionManager

        every { mockUserRepository.users } returns userFlow
        every { mockSessionManager.currentUserId } returns currentUserIdFlow

        every { mockSessionManager.login(any()) } answers {
            val id = firstArg<Long>()
            currentUserIdFlow.value = id
        }
        every { mockSessionManager.logout() } answers {
            currentUserIdFlow.value = null
        }

        coEvery { mockUserRepository.update(any()) } coAnswers {
            val updated = firstArg<User>()
            userFlow.value = userFlow.value.map { if (it.id == updated.id) updated else it }
            Result.success(Unit)
        }

        coEvery { mockUserRepository.delete(any()) } coAnswers {
            val id = firstArg<Long>()
            userFlow.value = userFlow.value.filter { it.id != id }
            true
        }

        coEvery { mockUserRepository.create(any()) } coAnswers {
            val user = firstArg<User>()
            val newId = (userFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
            userFlow.value = userFlow.value + user.copy(id = newId)
            Result.success(newId)
        }

        coEvery { mockReceiptRepository.refresh() } returns Unit
    }

    private fun createViewModel(): AppViewModel {
        return AppViewModel(mockApp)
    }

    // =========================================================================
    // 1. Тесты структуры вкладок и маршрутов навигации
    // =========================================================================

    @Test
    fun testEmployeeTabsComposition() {
        // Проверка: MainTab.employee содержит ровно 2 вкладки [Scan, Report]
        val employeeTabs = MainTab.employee
        assertEquals("Сотрудник должен видеть ровно 2 вкладки", 2, employeeTabs.size)
        assertEquals("Первая вкладка сотрудника — Scan (Добавить)", MainTab.Scan, employeeTabs[0])
        assertEquals("Вторая вкладка сотрудника — Report (Чеки и отчёт)", MainTab.Report, employeeTabs[1])
        assertFalse("Сотрудник не должен иметь вкладку Audit", employeeTabs.contains(MainTab.Audit))
    }

    @Test
    fun testAuditorTabsComposition() {
        // Проверка: MainTab.auditor содержит ровно 2 вкладки [Audit, Report]
        val auditorTabs = MainTab.auditor
        assertEquals("Аудитор должен видеть ровно 2 вкладки", 2, auditorTabs.size)
        assertEquals("Первая вкладка аудитора — Audit (Аудит)", MainTab.Audit, auditorTabs[0])
        assertEquals("Вторая вкладка аудитора — Report (Чеки и отчёт)", MainTab.Report, auditorTabs[1])
        assertFalse("Аудитор не должен иметь вкладку Scan", auditorTabs.contains(MainTab.Scan))
    }

    @Test
    fun testTabProperties() {
        // Проверка корректности маршрутов и заголовков вкладок
        assertEquals("main/scan", MainTab.Scan.route)
        assertEquals("Добавить", MainTab.Scan.title)

        assertEquals("main/report", MainTab.Report.route)
        assertEquals("Чеки и отчёт", MainTab.Report.title)

        assertEquals("main/audit", MainTab.Audit.route)
        assertEquals("Аудит", MainTab.Audit.title)
    }

    @Test
    fun testTopRouteDefinitions() {
        // Проверка маршрутов верхнего уровня
        assertEquals("auth", TopRoute.Auth.route)
        assertEquals("register", TopRoute.Register.route)
        assertEquals("main", TopRoute.Main.route)
    }

    @Test
    fun testNavigationDecisionLogic() {
        // Вспомогательная логика выбора стартового экрана и вкладок (как в AppNavHost)
        fun resolveStartDestination(currentUser: User?): String =
            if (currentUser == null) TopRoute.Auth.route else TopRoute.Main.route

        fun resolveTabs(currentUser: User?): List<MainTab> =
            if (currentUser?.role == UserRole.AUDITOR) MainTab.auditor else MainTab.employee

        fun resolveStartTab(currentUser: User?): String =
            if (currentUser?.role == UserRole.AUDITOR) MainTab.Audit.route else MainTab.Scan.route

        // 1. Неавторизован
        assertEquals(TopRoute.Auth.route, resolveStartDestination(null))

        // 2. Авторизован как сотрудник
        assertEquals(TopRoute.Main.route, resolveStartDestination(employeeUser))
        assertEquals(MainTab.employee, resolveTabs(employeeUser))
        assertEquals(MainTab.Scan.route, resolveStartTab(employeeUser))

        // 3. Авторизован как аудитор
        assertEquals(TopRoute.Main.route, resolveStartDestination(auditorUser))
        assertEquals(MainTab.auditor, resolveTabs(auditorUser))
        assertEquals(MainTab.Audit.route, resolveStartTab(auditorUser))
    }

    // =========================================================================
    // 2. Сквозное переключение пользователей и сессий
    // =========================================================================

    @Test
    fun testUserSwitchingFlow() = runTest {
        userFlow.value = listOf(employeeUser, auditorUser)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // До входа пользователь отсутствует
        assertNull(viewModel.currentUser.value)

        // Вход первым пользователем (EMPLOYEE)
        viewModel.login(1L)
        advanceUntilIdle()
        assertNotNull(viewModel.currentUser.value)
        assertEquals(1L, viewModel.currentUser.value?.id)
        assertEquals(UserRole.EMPLOYEE, viewModel.currentUser.value?.role)

        // Переключение на второго пользователя (AUDITOR)
        viewModel.login(2L)
        advanceUntilIdle()
        assertNotNull(viewModel.currentUser.value)
        assertEquals(2L, viewModel.currentUser.value?.id)
        assertEquals(UserRole.AUDITOR, viewModel.currentUser.value?.role)

        // Выход из системы
        viewModel.logout()
        advanceUntilIdle()
        assertNull(viewModel.currentUser.value)
    }

    // =========================================================================
    // 3. Переключение ролей (switchCurrentUserRole)
    // =========================================================================

    @Test
    fun testSwitchCurrentUserRoleFromEmployeeToAuditor() = runTest {
        userFlow.value = listOf(employeeUser)
        currentUserIdFlow.value = 1L
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(UserRole.EMPLOYEE, viewModel.currentUser.value?.role)

        // Переключаем роль на AUDITOR
        viewModel.switchCurrentUserRole()
        advanceUntilIdle()

        coVerify {
            mockUserRepository.update(match { it.id == 1L && it.role == UserRole.AUDITOR })
        }
        assertEquals(UserRole.AUDITOR, viewModel.currentUser.value?.role)
    }

    @Test
    fun testSwitchCurrentUserRoleFromAuditorToEmployee() = runTest {
        userFlow.value = listOf(auditorUser)
        currentUserIdFlow.value = 2L
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(UserRole.AUDITOR, viewModel.currentUser.value?.role)

        // Переключаем роль обратно на EMPLOYEE
        viewModel.switchCurrentUserRole()
        advanceUntilIdle()

        coVerify {
            mockUserRepository.update(match { it.id == 2L && it.role == UserRole.EMPLOYEE })
        }
        assertEquals(UserRole.EMPLOYEE, viewModel.currentUser.value?.role)
    }

    @Test
    fun testReactiveRoleSwitchingAndTabListChange() = runTest {
        // Логика резолвинга вкладок и стартового экрана из AppNavHost
        fun resolveTabs(user: User?): List<MainTab> =
            if (user?.role == UserRole.AUDITOR) MainTab.auditor else MainTab.employee

        fun resolveStartTab(user: User?): String =
            if (user?.role == UserRole.AUDITOR) MainTab.Audit.route else MainTab.Scan.route

        userFlow.value = listOf(employeeUser)
        currentUserIdFlow.value = 1L
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. Исходно: активный пользователь EMPLOYEE -> вкладки [Scan, Report]
        assertEquals(UserRole.EMPLOYEE, viewModel.currentUser.value?.role)
        assertEquals(listOf(MainTab.Scan, MainTab.Report), resolveTabs(viewModel.currentUser.value))
        assertEquals(MainTab.Scan.route, resolveStartTab(viewModel.currentUser.value))

        // 2. Реактивное переключение роли: EMPLOYEE -> AUDITOR -> вкладки динамически меняются на [Audit, Report]
        viewModel.switchCurrentUserRole()
        advanceUntilIdle()

        assertEquals(UserRole.AUDITOR, viewModel.currentUser.value?.role)
        assertEquals(listOf(MainTab.Audit, MainTab.Report), resolveTabs(viewModel.currentUser.value))
        assertEquals(MainTab.Audit.route, resolveStartTab(viewModel.currentUser.value))

        // 3. Реактивное обратное переключение роли: AUDITOR -> EMPLOYEE -> вкладки возвращаются к [Scan, Report]
        viewModel.switchCurrentUserRole()
        advanceUntilIdle()

        assertEquals(UserRole.EMPLOYEE, viewModel.currentUser.value?.role)
        assertEquals(listOf(MainTab.Scan, MainTab.Report), resolveTabs(viewModel.currentUser.value))
        assertEquals(MainTab.Scan.route, resolveStartTab(viewModel.currentUser.value))
    }

    @Test
    fun testSwitchRoleWhenNoUserLoggedInDoesNothing() = runTest {
        userFlow.value = listOf(employeeUser)
        currentUserIdFlow.value = null
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.currentUser.value)

        // Вызов не должен приводить к падению или вызову update
        viewModel.switchCurrentUserRole()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockUserRepository.update(any()) }
    }

    // =========================================================================
    // 4. Граничные случаи: Не авторизован / список пользователей пуст
    // =========================================================================

    @Test
    fun testBoundaryUserNotAuthorizedOrEmptyList() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Сценарий 1: userFlow пуст, id = null
        assertNull(viewModel.currentUser.value)

        // Сценарий 2: id задан, но в списке пользователя нет
        currentUserIdFlow.value = 999L
        advanceUntilIdle()
        assertNull(viewModel.currentUser.value)

        // Сценарий 3: пользователи появились, но id = 999L не существует
        userFlow.value = listOf(employeeUser)
        advanceUntilIdle()
        assertNull(viewModel.currentUser.value)

        // Сценарий 4: id совпал — пользователь становится активным
        currentUserIdFlow.value = 1L
        advanceUntilIdle()
        assertEquals(employeeUser, viewModel.currentUser.value)

        // Сценарий 5: список пользователей снова очистился (например, удаление БД)
        userFlow.value = emptyList()
        advanceUntilIdle()
        assertNull(viewModel.currentUser.value)
    }

    // =========================================================================
    // 5. Граничные случаи: Редактирование и регистрация с пустыми полями
    // =========================================================================

    @Test
    fun testProfileEditValidationErrors() = runTest {
        userFlow.value = listOf(employeeUser)
        currentUserIdFlow.value = 1L
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. Пустое имя
        var doneCalled = false
        viewModel.updateProfile(1L, fullName = "", position = "Бухгалтер") { doneCalled = true }
        assertEquals("Заполните все поля профиля", viewModel.editError.value)
        assertFalse(doneCalled)
        coVerify(exactly = 0) { mockUserRepository.update(any()) }

        // 2. Имя из одних пробелов
        viewModel.updateProfile(1L, fullName = "    ", position = "Бухгалтер")
        assertEquals("Заполните все поля профиля", viewModel.editError.value)
        coVerify(exactly = 0) { mockUserRepository.update(any()) }

        // 3. Пустая должность
        viewModel.updateProfile(1L, fullName = "Иванов Иван", position = "")
        assertEquals("Заполните все поля профиля", viewModel.editError.value)
        coVerify(exactly = 0) { mockUserRepository.update(any()) }

        // 4. Должность из одних пробелов
        viewModel.updateProfile(1L, fullName = "Иванов Иван", position = "   ")
        assertEquals("Заполните все поля профиля", viewModel.editError.value)
        coVerify(exactly = 0) { mockUserRepository.update(any()) }

        // 5. Сброс ошибки редактирования
        viewModel.clearEditError()
        assertNull(viewModel.editError.value)

        // 6. Успешное обновление валидными полями
        var updateSuccessCalled = false
        viewModel.updateProfile(
            userId = 1L,
            fullName = "Иванов Иван Обновленный",
            position = "Старший бухгалтер",
            organization = "Новая фирма"
        ) {
            updateSuccessCalled = true
        }
        advanceUntilIdle()

        assertNull(viewModel.editError.value)
        assertTrue(updateSuccessCalled)
        coVerify(exactly = 1) { mockUserRepository.update(any()) }
    }

    @Test
    fun testRegisterValidationErrors() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. Пустое имя при регистрации
        viewModel.register(fullName = "", position = "Бухгалтер")
        assertEquals("Заполните все поля профиля", viewModel.registerError.value)
        coVerify(exactly = 0) { mockUserRepository.create(any()) }

        // 2. Пустая должность при регистрации
        viewModel.register(fullName = "Иванов", position = "   ")
        assertEquals("Заполните все поля профиля", viewModel.registerError.value)
        coVerify(exactly = 0) { mockUserRepository.create(any()) }

        // 3. Сброс ошибки регистрации
        viewModel.clearRegisterError()
        assertNull(viewModel.registerError.value)

        // 4. Успешная регистрация
        viewModel.register(fullName = "Сидоров Сидор", position = "Ассистент")
        advanceUntilIdle()
        assertNull(viewModel.registerError.value)
        coVerify(exactly = 1) { mockUserRepository.create(any()) }
    }

    // =========================================================================
    // 6. Граничные случаи: Удаление пользователя (активного vs неактивного)
    // =========================================================================

    @Test
    fun testDeleteActiveUserTriggersLogoutAndRefresh() = runTest {
        userFlow.value = listOf(employeeUser, auditorUser)
        currentUserIdFlow.value = 1L
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1L, viewModel.currentUser.value?.id)

        var onDoneInvoked = false
        // Удаляем активного пользователя (1L)
        viewModel.deleteProfile(1L) {
            onDoneInvoked = true
        }
        advanceUntilIdle()

        // Проверяем:
        // 1. Вызвано удаление в репозитории
        coVerify { mockUserRepository.delete(1L) }
        // 2. Вызвано обновление кэша чеков
        coVerify { mockReceiptRepository.refresh() }
        // 3. Вызван logout из SessionManager
        verify { mockSessionManager.logout() }
        // 4. Колбэк завершения выполнен
        assertTrue("Колбэк onDone должен быть вызван", onDoneInvoked)
        // 5. Текущий пользователь стал null
        assertNull(viewModel.currentUser.value)
    }

    @Test
    fun testDeleteInactiveUserDoesNotTriggerLogout() = runTest {
        userFlow.value = listOf(employeeUser, auditorUser)
        currentUserIdFlow.value = 1L // активен пользователь 1
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1L, viewModel.currentUser.value?.id)

        var onDoneInvoked = false
        // Удаляем НЕактивного пользователя (2L)
        viewModel.deleteProfile(2L) {
            onDoneInvoked = true
        }
        advanceUntilIdle()

        // Проверяем:
        // 1. Репозиторий удалил пользователя 2L
        coVerify { mockUserRepository.delete(2L) }
        // 2. Кэш чеков обновлен
        coVerify { mockReceiptRepository.refresh() }
        // 3. Logout НЕ вызывался, так как удален другой профиль
        verify(exactly = 0) { mockSessionManager.logout() }
        // 4. Колбэк выполнен
        assertTrue(onDoneInvoked)
        // 5. Текущий активный пользователь остался пользователем 1
        assertEquals(1L, viewModel.currentUser.value?.id)
    }
}
