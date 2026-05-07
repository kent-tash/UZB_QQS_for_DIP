package com.example.uzb_qqs_for_dip.data.repository

import android.content.ContentValues
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ReceiptRepository(private val dbHelper: DbHelper) {

    private val _receipts = MutableStateFlow<List<ReceiptWithUser>>(emptyList())
    val receipts: Flow<List<ReceiptWithUser>> = _receipts.asStateFlow()

    init {
        refreshSync()
    }

    private fun refreshSync() {
        _receipts.value = readAllSync()
    }

    /** Принудительно перечитать чеки (например, после каскадного удаления пользователя). */
    suspend fun refresh() = withContext(Dispatchers.IO) { refreshSync() }

    private fun readAllSync(): List<ReceiptWithUser> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<ReceiptWithUser>()
        val sql = """
            SELECT r.id, r.user_id, r.purchased_at, r.seller_name, r.total_amount_tiyin,
                   r.vat_amount_tiyin, r.qr_url, r.raw_text, r.created_at,
                   u.full_name, u.position, u.initials_surname
            FROM receipts r
            INNER JOIN users u ON u.id = r.user_id
            ORDER BY r.purchased_at DESC, r.id DESC
        """.trimIndent()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val receipt = Receipt(
                    id = c.getLong(0),
                    userId = c.getLong(1),
                    purchasedAt = c.getLong(2),
                    sellerName = c.getString(3),
                    totalAmountTiyin = c.getLong(4),
                    vatAmountTiyin = c.getLong(5),
                    qrUrl = c.getString(6),
                    rawText = if (c.isNull(7)) null else c.getString(7),
                    createdAt = c.getLong(8)
                )
                list.add(
                    ReceiptWithUser(
                        receipt = receipt,
                        userFullName = c.getString(9),
                        userPosition = c.getString(10),
                        userInitialsSurname = c.getString(11)
                    )
                )
            }
        }
        return list
    }

    suspend fun list(): List<ReceiptWithUser> = withContext(Dispatchers.IO) { readAllSync() }

    suspend fun findByQrUrl(qrUrl: String): Receipt? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """SELECT id, user_id, purchased_at, seller_name, total_amount_tiyin,
                      vat_amount_tiyin, qr_url, raw_text, created_at
               FROM receipts WHERE qr_url = ?""",
            arrayOf(qrUrl)
        ).use { c ->
            if (c.moveToFirst()) {
                Receipt(
                    id = c.getLong(0),
                    userId = c.getLong(1),
                    purchasedAt = c.getLong(2),
                    sellerName = c.getString(3),
                    totalAmountTiyin = c.getLong(4),
                    vatAmountTiyin = c.getLong(5),
                    qrUrl = c.getString(6),
                    rawText = if (c.isNull(7)) null else c.getString(7),
                    createdAt = c.getLong(8)
                )
            } else null
        }
    }

    suspend fun insert(receipt: Receipt): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("user_id", receipt.userId)
                put("purchased_at", receipt.purchasedAt)
                put("seller_name", receipt.sellerName)
                put("total_amount_tiyin", receipt.totalAmountTiyin)
                put("vat_amount_tiyin", receipt.vatAmountTiyin)
                put("qr_url", receipt.qrUrl)
                put("raw_text", receipt.rawText)
                put("created_at", receipt.createdAt)
            }
            val id = db.insertOrThrow("receipts", null, cv)
            refreshSync()
            id
        }
    }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val n = db.delete("receipts", "id = ?", arrayOf(id.toString()))
        if (n > 0) refreshSync()
        n > 0
    }

    /**
     * Удаляет несколько чеков одной транзакцией и публикует обновлённый список
     * один раз — так подписчики не моргают и не пересчитывают порядок на каждое
     * удаление по отдельности.
     *
     * @return количество фактически удалённых записей.
     */
    suspend fun deleteAll(ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        val db = dbHelper.writableDatabase
        var removed = 0
        db.beginTransaction()
        try {
            ids.forEach { id ->
                removed += db.delete("receipts", "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (removed > 0) refreshSync()
        removed
    }
}
