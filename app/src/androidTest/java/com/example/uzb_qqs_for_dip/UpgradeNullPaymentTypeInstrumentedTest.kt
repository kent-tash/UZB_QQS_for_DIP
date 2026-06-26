package com.example.uzb_qqs_for_dip

import android.content.ContentValues
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uzb_qqs_for_dip.data.AppContainer
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.model.PaymentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Имитирует обновление с версии без колонки payment_type (v1.1):
 * после миграции старые чеки имеют NULL в payment_type — приложение не должно падать.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeNullPaymentTypeInstrumentedTest {

    @Test
    fun appStartsAfterUpgradeWithNullPaymentType() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = context.getDatabasePath(DbHelper.DB_NAME)
        if (dbFile.exists()) dbFile.delete()
        dbFile.parentFile?.mkdirs()

        // Схема v1.1 (DB_VERSION = 1), без payment_type.
        val dbHelper = DbHelper(context)
        val db = dbHelper.writableDatabase
        db.execSQL(
            """
            INSERT INTO users (full_name, position, initials_surname, created_at)
            VALUES ('Тестов Тест Тестович', 'Сотрудник', 'Т.Т. Тестов', ${System.currentTimeMillis()})
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO receipts (
                user_id, purchased_at, seller_name, total_amount_tiyin, vat_amount_tiyin,
                qr_url, raw_text, created_at
            ) VALUES (
                1, 1782056400000, '"DARVOZA SAVDO" MCHJ', 70000000, 7499998,
                'https://ofd.soliq.uz/check?t=343420031936&r=372442&c=20260621160000&s=560350952343',
                'test receipt', ${System.currentTimeMillis()}
            )
            """.trimIndent()
        )
        db.close()

        // Принудительно поднимаем версию схемы (как при установке нового APK).
        context.openOrCreateDatabase(DbHelper.DB_NAME, 0, null).use {
            it.version = 1
        }
        DbHelper(context).writableDatabase.use { /* onUpgrade 1→8 */ }

        // Старт приложения: AppContainer создаёт ReceiptRepository и читает чеки.
        val container = AppContainer(context)
        val receipts = container.receiptRepository.list()

        assertEquals(1, receipts.size)
        assertTrue(receipts.first().receipt.sellerName.contains("DARVOZA SAVDO"))
        assertEquals(PaymentType.CARD, receipts.first().receipt.paymentType)
    }
}
