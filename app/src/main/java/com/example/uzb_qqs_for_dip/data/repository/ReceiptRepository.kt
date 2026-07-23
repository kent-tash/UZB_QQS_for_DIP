package com.example.uzb_qqs_for_dip.data.repository

import android.content.ContentValues
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.data.model.Receipt
import com.example.uzb_qqs_for_dip.data.model.ReceiptOwner
import com.example.uzb_qqs_for_dip.data.model.ReceiptSource
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Сводка по чекам сотрудника за период (для экрана проверки аудитора). */
data class UserReceiptStats(
    val totalCount: Int,
    val verifiedCount: Int,
    val totalTiyin: Long,
    val vatTiyin: Long,
    val verifiedTotalTiyin: Long,
    val verifiedVatTiyin: Long,
)

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
                   r.vat_amount_tiyin, r.qr_url, r.payment_type, r.fiscal_sign,
                   r.address, r.tin, r.terminal_id, r.receipt_number, r.nkm_name, r.sn,
                   r.raw_text, r.source, r.created_at,
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
                    paymentType = PaymentType.fromDb(if (c.isNull(7)) null else c.getString(7)),
                    fiscalSign = if (c.isNull(8)) null else c.getString(8),
                    address = if (c.isNull(9)) null else c.getString(9),
                    tin = if (c.isNull(10)) null else c.getString(10),
                    terminalId = if (c.isNull(11)) null else c.getString(11),
                    receiptNumber = if (c.isNull(12)) null else c.getString(12),
                    nkmName = if (c.isNull(13)) null else c.getString(13),
                    sn = if (c.isNull(14)) null else c.getString(14),
                    rawText = if (c.isNull(15)) null else c.getString(15),
                    source = ReceiptSource.fromDb(if (c.isNull(16)) null else c.getString(16)),
                    createdAt = c.getLong(17)
                )
                list.add(
                    ReceiptWithUser(
                        receipt = receipt,
                        userFullName = c.getString(18),
                        userPosition = c.getString(19),
                        userInitialsSurname = c.getString(20)
                    )
                )
            }
        }
        return list
    }

    suspend fun list(): List<ReceiptWithUser> = withContext(Dispatchers.IO) { readAllSync() }

    /**
     * Поиск чеков по тексту (ФИО, продавец, фискальный признак, QR) и/или сумме.
     * [userId]/[fromMs]/[toMs] ограничивают область (для экрана проверки сотрудника).
     */
    suspend fun search(
        query: String,
        userId: Long? = null,
        fromMs: Long? = null,
        toMs: Long? = null,
    ): List<ReceiptWithUser> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty() && userId == null) return@withContext emptyList()

        val all = readAllSync().asSequence()
            .filter { userId == null || it.receipt.userId == userId }
            .filter {
                fromMs == null || toMs == null ||
                    it.receipt.purchasedAt in fromMs..toMs
            }

        if (q.isEmpty()) {
            return@withContext all.toList()
        }

        val qLower = q.lowercase()
        val amountTiyin = parseAmountToTiyin(q)

        all.filter { item ->
            val r = item.receipt
            item.userFullName.lowercase().contains(qLower) ||
                r.sellerName.lowercase().contains(qLower) ||
                (r.fiscalSign?.contains(q, ignoreCase = true) == true) ||
                r.qrUrl.contains(q, ignoreCase = true) ||
                (r.receiptNumber?.contains(q, ignoreCase = true) == true) ||
                (r.terminalId?.contains(q, ignoreCase = true) == true) ||
                (amountTiyin != null && (
                    r.totalAmountTiyin == amountTiyin ||
                        r.vatAmountTiyin == amountTiyin
                    ))
        }.toList()
    }

    private fun parseAmountToTiyin(raw: String): Long? {
        val normalized = raw.trim()
            .replace('\u00A0', ' ')
            .replace(" ", "")
            .replace(',', '.')
            .filter { it.isDigit() || it == '.' }
        if (normalized.isEmpty() || normalized == ".") return null
        val soum = normalized.toDoubleOrNull() ?: return null
        return (soum * 100.0).toLong()
    }

    suspend fun getUserPeriodStats(userId: Long, fromMs: Long, toMs: Long): UserReceiptStats =
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                """
                SELECT COUNT(*),
                       COUNT(CASE WHEN verified_at IS NOT NULL THEN 1 END),
                       COALESCE(SUM(total_amount_tiyin), 0),
                       COALESCE(SUM(vat_amount_tiyin), 0),
                       COALESCE(SUM(CASE WHEN verified_at IS NOT NULL THEN total_amount_tiyin END), 0),
                       COALESCE(SUM(CASE WHEN verified_at IS NOT NULL THEN vat_amount_tiyin END), 0)
                FROM receipts
                WHERE user_id = ? AND purchased_at >= ? AND purchased_at <= ?
                """.trimIndent(),
                arrayOf(userId.toString(), fromMs.toString(), toMs.toString())
            ).use { c ->
                if (c.moveToFirst()) {
                    UserReceiptStats(
                        totalCount = c.getInt(0),
                        verifiedCount = c.getInt(1),
                        totalTiyin = c.getLong(2),
                        vatTiyin = c.getLong(3),
                        verifiedTotalTiyin = c.getLong(4),
                        verifiedVatTiyin = c.getLong(5),
                    )
                } else {
                    UserReceiptStats(0, 0, 0L, 0L, 0L, 0L)
                }
            }
        }

    suspend fun findByQrUrl(qrUrl: String): Receipt? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """SELECT id, user_id, purchased_at, seller_name, total_amount_tiyin,
                      vat_amount_tiyin, qr_url, payment_type, fiscal_sign,
                      address, tin, terminal_id, receipt_number, nkm_name, sn,
                      raw_text, source, created_at
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
                    paymentType = PaymentType.fromDb(if (c.isNull(7)) null else c.getString(7)),
                    fiscalSign = if (c.isNull(8)) null else c.getString(8),
                    address = if (c.isNull(9)) null else c.getString(9),
                    tin = if (c.isNull(10)) null else c.getString(10),
                    terminalId = if (c.isNull(11)) null else c.getString(11),
                    receiptNumber = if (c.isNull(12)) null else c.getString(12),
                    nkmName = if (c.isNull(13)) null else c.getString(13),
                    sn = if (c.isNull(14)) null else c.getString(14),
                    rawText = if (c.isNull(15)) null else c.getString(15),
                    source = ReceiptSource.fromDb(if (c.isNull(16)) null else c.getString(16)),
                    createdAt = c.getLong(17)
                )
            } else null
        }
    }

    /**
     * Возвращает владельца чека по QR-URL или по фискальной идентичности.
     * Совпадение только по магазину/сумме/дате без времени здесь не используется —
     * иначе разные чеки одного дня ложно блокировались бы.
     */
    suspend fun findOwnerByQrUrl(qrUrl: String): ReceiptOwner? =
        findOwner(qrUrl = qrUrl, fiscalSign = null, terminalId = null, receiptNumber = null)

    /**
     * Поиск владельца: сначала точный QR-URL, затем фискальный признак
     * (один и тот же чек может отличаться только схемой/порядком параметров URL).
     */
    suspend fun findOwner(
        qrUrl: String,
        fiscalSign: String?,
        terminalId: String? = null,
        receiptNumber: String? = null,
    ): ReceiptOwner? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """SELECT r.id, r.user_id, u.full_name
               FROM receipts r
               INNER JOIN users u ON u.id = r.user_id
               WHERE r.qr_url = ?""",
            arrayOf(qrUrl)
        ).use { c ->
            if (c.moveToFirst()) {
                return@withContext ReceiptOwner(
                    receiptId = c.getLong(0),
                    userId = c.getLong(1),
                    fullName = c.getString(2)
                )
            }
        }
        val sign = fiscalSign?.trim().orEmpty()
        if (sign.isEmpty()) return@withContext null
        val sql = buildString {
            append(
                """SELECT r.id, r.user_id, u.full_name
                   FROM receipts r
                   INNER JOIN users u ON u.id = r.user_id
                   WHERE r.fiscal_sign = ?"""
            )
            if (!terminalId.isNullOrBlank()) append(" AND IFNULL(r.terminal_id, '') = ?")
            if (!receiptNumber.isNullOrBlank()) append(" AND IFNULL(r.receipt_number, '') = ?")
            append(" LIMIT 1")
        }
        val args = buildList {
            add(sign)
            if (!terminalId.isNullOrBlank()) add(terminalId.trim())
            if (!receiptNumber.isNullOrBlank()) add(receiptNumber.trim())
        }
        db.rawQuery(sql, args.toTypedArray()).use { c ->
            if (c.moveToFirst()) {
                ReceiptOwner(
                    receiptId = c.getLong(0),
                    userId = c.getLong(1),
                    fullName = c.getString(2)
                )
            } else null
        }
    }

    /** Помечает чек как проверенный аудитором. */
    suspend fun markVerified(receiptId: Long, auditorUserId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("verified_at", System.currentTimeMillis())
                put("verified_by", auditorUserId)
            }
            db.update("receipts", cv, "id = ?", arrayOf(receiptId.toString()))
            refreshSync()
        }
    }

    /**
     * Помечает все НЕ проверенные чеки сотрудника за указанный период как проверенные аудитором.
     * @return количество обновлённых записей.
     */
    suspend fun markAllVerifiedForUser(
        userId: Long,
        auditorUserId: Long,
        fromMs: Long,
        toMs: Long
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("verified_at", System.currentTimeMillis())
                put("verified_by", auditorUserId)
            }
            val count = db.update(
                "receipts", cv,
                "user_id = ? AND purchased_at >= ? AND purchased_at <= ? AND verified_at IS NULL",
                arrayOf(userId.toString(), fromMs.toString(), toMs.toString())
            )
            if (count > 0) refreshSync()
            count
        }
    }

    suspend fun insert(receipt: Receipt): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = createContentValues(receipt)
            val id = db.insertOrThrow("receipts", null, cv)
            refreshSync()
            id
        }
    }

    suspend fun update(receipt: Receipt): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = createContentValues(receipt)
            db.update("receipts", cv, "id = ?", arrayOf(receipt.id.toString()))
            refreshSync()
        }
    }

    private fun createContentValues(receipt: Receipt): ContentValues = ContentValues().apply {
        put("user_id", receipt.userId)
        put("purchased_at", receipt.purchasedAt)
        put("seller_name", receipt.sellerName)
        put("total_amount_tiyin", receipt.totalAmountTiyin)
        put("vat_amount_tiyin", receipt.vatAmountTiyin)
        put("qr_url", receipt.qrUrl)
        put("payment_type", receipt.paymentType.name)
        put("fiscal_sign", receipt.fiscalSign)
        put("address", receipt.address)
        put("tin", receipt.tin)
        put("terminal_id", receipt.terminalId)
        put("receipt_number", receipt.receiptNumber)
        put("nkm_name", receipt.nkmName)
        put("sn", receipt.sn)
        put("raw_text", receipt.rawText)
        put("source", receipt.source.name)
        put("created_at", receipt.createdAt)
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
