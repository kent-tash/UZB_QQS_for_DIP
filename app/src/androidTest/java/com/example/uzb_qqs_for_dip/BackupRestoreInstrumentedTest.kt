package com.example.uzb_qqs_for_dip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uzb_qqs_for_dip.data.AppContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет на реальном устройстве/эмуляторе, что бэкап СТАРОЙ версии формата
 * (formatVersion = 1) успешно восстанавливается текущей версией приложения:
 * профили и чеки попадают в базу.
 *
 * JSON лежит в assets тестового APK: app/src/androidTest/assets/backup_v1.json
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {

    @Test
    fun restores_old_version_backup_v1() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext

        val json = instrumentation.context.assets.open("backup_v1.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        // Реальные зависимости приложения (та же БД, что и в проде).
        val container = AppContainer(targetContext)

        val result = container.appBackup.importJsonString(json, previousSessionUserId = null)
        assertTrue("import failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val users = container.userRepository.list()
        assertEquals("должен восстановиться один профиль", 1, users.size)
        assertEquals("Нестерук Денис Николаевич", users.first().fullName)
        assertEquals("Сотрудник Посольства", users.first().position)

        val receipts = container.receiptRepository.list()
        assertEquals("должно восстановиться 100 чеков", 100, receipts.size)

        // Чеки должны быть привязаны к восстановленному профилю и иметь корректные данные.
        val userId = users.first().id
        assertTrue(receipts.all { it.receipt.userId == userId })
        assertTrue(
            "ожидался чек GRAND PHARM TRADE",
            receipts.any { it.receipt.sellerName.contains("GRAND PHARM TRADE") }
        )
        assertTrue(receipts.all { it.receipt.totalAmountTiyin > 0 })
    }

    @Test
    fun merge_adds_another_users_backup_without_deleting() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val container = AppContainer(targetContext)

        // База-основа: восстанавливаем v1 (1 профиль, 100 чеков).
        val baseJson = instrumentation.context.assets.open("backup_v1.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        container.appBackup.importJsonString(baseJson, previousSessionUserId = null).getOrThrow()
        assertEquals(1, container.userRepository.list().size)
        assertEquals(100, container.receiptRepository.list().size)

        // Бэкап «другого пользователя» с одним новым чеком.
        val otherJson = """
            {"formatVersion":2,"exportedAt":0,
             "reportSettings":{"userId":null,"quarter":"Q2","year":2026,"from":0,"to":0,"sortField":"DATE","sortOrder":"ASC"},
             "users":[{"id":99,"fullName":"Иванов Иван Иванович","position":"Тест","initialsSurname":"И.И. Иванов","createdAt":1}],
             "receipts":[{"userId":99,"purchasedAt":1779613860000,"sellerName":"TEST SHOP","totalAmountTiyin":12345,"vatAmountTiyin":1000,"qrUrl":"https://ofd.soliq.uz/check?t=TEST000000000001&r=1&c=1&s=1","rawText":null,"createdAt":1}]}
        """.trimIndent()

        val outcome = container.appBackup.mergeJsonString(otherJson).getOrThrow()
        assertEquals("должен добавиться 1 новый профиль", 1, outcome.addedUsers)
        assertEquals("должен добавиться 1 новый чек", 1, outcome.addedReceipts)

        // Старые данные на месте + добавились новые.
        assertEquals(2, container.userRepository.list().size)
        val afterMerge = container.receiptRepository.list()
        assertEquals(101, afterMerge.size)
        assertTrue(afterMerge.any { it.receipt.sellerName.contains("GRAND PHARM TRADE") })
        assertTrue(afterMerge.any { it.receipt.sellerName == "TEST SHOP" })

        // Повторное слияние того же файла — ничего не добавляет (дедуп) и ничего не удаляет.
        val outcome2 = container.appBackup.mergeJsonString(otherJson).getOrThrow()
        assertEquals(0, outcome2.addedUsers)
        assertEquals(0, outcome2.addedReceipts)
        assertEquals(1, outcome2.skippedReceipts)
        assertEquals(2, container.userRepository.list().size)
        assertEquals(101, container.receiptRepository.list().size)
    }
}
