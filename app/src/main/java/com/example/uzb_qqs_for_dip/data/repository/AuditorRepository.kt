package com.example.uzb_qqs_for_dip.data.repository

import android.content.ContentValues
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.model.AuditDeclaration
import com.example.uzb_qqs_for_dip.data.model.AuditStatus
import com.example.uzb_qqs_for_dip.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сводные данные по сотруднику за выбранный квартал.
 *
 * @property userId         id сотрудника.
 * @property fullName       ФИО из таблицы users.
 * @property position       Должность.
 * @property totalTiyin     Сумма по всем чекам за период.
 * @property vatTiyin       НДС по всем чекам за период.
 * @property receiptCount   Количество чеков в БД.
 * @property verifiedCount  Количество чеков, подтверждённых QR-сканом аудитора.
 * @property declaration    Введённые вручную итоги из PDF (null = аудитор ещё не внёс).
 */
data class EmployeeSummary(
    val userId: Long,
    val fullName: String,
    val initialsSurname: String,
    val position: String,
    val organization: String,
    val totalTiyin: Long,
    val vatTiyin: Long,
    val receiptCount: Int,
    val verifiedCount: Int,
    val declaration: AuditDeclaration?
)

/** Как именно совпали два чека при поиске пересечений. */
enum class ConflictMatchKind {
    /** Одинаковый фискальный признак (+ терминал / номер при наличии). */
    FISCAL,
    /** Продавец + точное время покупки + сумма + НДС. */
    SELLER_TIME_AMOUNT
}

/** Пара сотрудников, у которых обнаружен один и тот же чек. */
data class ReceiptConflict(
    val qrUrl: String,
    val sellerName: String,
    val purchasedAt: Long,
    val totalAmountTiyin: Long,
    val user1Id: Long,
    val user1FullName: String,
    val user2Id: Long,
    val user2FullName: String,
    val matchKind: ConflictMatchKind,
    val fiscalSign: String? = null
)

sealed class DiscrepancyReason {
    data class TotalMismatch(val declared: Long, val actual: Long, val delta: Long) : DiscrepancyReason()
    data class VatMismatch(val declared: Long, val actual: Long, val delta: Long) : DiscrepancyReason()
    data class CountMismatch(val declared: Int, val actual: Int) : DiscrepancyReason()
    data class IncompleteVerification(val verified: Int, val total: Int) : DiscrepancyReason()
    data class DuplicateReceipt(val conflict: ReceiptConflict) : DiscrepancyReason()
    data class ManualNote(val note: String) : DiscrepancyReason()
    data class StatusInfo(val status: AuditStatus) : DiscrepancyReason()
}

data class DiscrepancyDetail(
    val summary: EmployeeSummary,
    val reasons: List<DiscrepancyReason>,
    val conflicts: List<ReceiptConflict>
)

class AuditorRepository(private val dbHelper: DbHelper) {

    /**
     * Возвращает сводные итоги по всем EMPLOYEE-пользователям за указанный период.
     * Аудиторские профили исключаются.
     */
    suspend fun getEmployeeSummaries(
        fromMs: Long,
        toMs: Long,
        year: Int,
        quarter: String
    ): List<EmployeeSummary> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase

        // Агрегация из БД: суммы, количество чеков и количество подтверждённых.
        // Показываем всех пользователей (в т.ч. тех, кто переключился в режим аудитора)
        // — их чеки тоже должны попадать в сводную таблицу квартала.
        val sql = """
            SELECT u.id, u.full_name, u.initials_surname, u.position, u.organization,
                   COALESCE(SUM(r.total_amount_tiyin), 0),
                   COALESCE(SUM(r.vat_amount_tiyin), 0),
                   COUNT(r.id),
                   COUNT(CASE WHEN r.verified_at IS NOT NULL THEN 1 END)
            FROM users u
            LEFT JOIN receipts r ON r.user_id = u.id
                AND r.purchased_at >= ? AND r.purchased_at <= ?
            GROUP BY u.id, u.full_name, u.initials_surname, u.position, u.organization
            ORDER BY u.full_name COLLATE NOCASE
        """.trimIndent()

        val rows = mutableListOf<EmployeeSummary>()
        db.rawQuery(sql, arrayOf(fromMs.toString(), toMs.toString())).use { c ->
            while (c.moveToNext()) {
                val userId = c.getLong(0)
                val decl = getDeclaration(userId, year, quarter)
                rows.add(
                    EmployeeSummary(
                        userId = userId,
                        fullName = c.getString(1),
                        initialsSurname = c.getString(2),
                        position = c.getString(3),
                        organization = c.getString(4),
                        totalTiyin = c.getLong(5),
                        vatTiyin = c.getLong(6),
                        receiptCount = c.getInt(7),
                        verifiedCount = c.getInt(8),
                        declaration = decl
                    )
                )
            }
        }
        rows
    }

    // ── AuditDeclarations ────────────────────────────────────────────────────

    suspend fun getDeclaration(userId: Long, year: Int, quarter: String): AuditDeclaration? =
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                """SELECT id, user_id, year, quarter, declared_total_tiyin, declared_vat_tiyin,
                          declared_count, status, note, checked_at
                   FROM audit_declarations WHERE user_id = ? AND year = ? AND quarter = ?""",
                arrayOf(userId.toString(), year.toString(), quarter)
            ).use { c ->
                if (c.moveToFirst()) parseDeclaration(c) else null
            }
        }

    suspend fun upsertDeclaration(decl: AuditDeclaration): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val db = dbHelper.writableDatabase
                val cv = ContentValues().apply {
                    put("user_id", decl.userId)
                    put("year", decl.year)
                    put("quarter", decl.quarter)
                    put("declared_total_tiyin", decl.declaredTotalTiyin)
                    put("declared_vat_tiyin", decl.declaredVatTiyin)
                    put("declared_count", decl.declaredCount)
                    put("status", decl.status.name)
                    put("note", decl.note)
                    if (decl.checkedAt != null) put("checked_at", decl.checkedAt) else putNull("checked_at")
                }
                db.insertWithOnConflict(
                    "audit_declarations", null, cv,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                Unit
            }
        }

    private fun parseDeclaration(c: android.database.Cursor): AuditDeclaration = AuditDeclaration(
        id = c.getLong(0),
        userId = c.getLong(1),
        year = c.getInt(2),
        quarter = c.getString(3),
        declaredTotalTiyin = c.getLong(4),
        declaredVatTiyin = c.getLong(5),
        declaredCount = c.getInt(6),
        status = runCatching { AuditStatus.valueOf(c.getString(7)) }.getOrDefault(AuditStatus.PENDING),
        note = if (c.isNull(8)) null else c.getString(8),
        checkedAt = if (c.isNull(9)) null else c.getLong(9)
    )

    // ── Duplicate / conflict detection ──────────────────────────────────────

    /**
     * Находит вероятные дубликаты одного и того же чека у разных пользователей.
     *
     * Совпадение только по магазину + сумме + дате (без времени) давало ложные
     * срабатывания: два разных чека в один день из одного магазина на одну сумму.
     *
     * Считаем конфликтом, если у разных user_id:
     * - одинаковый фискальный признак (и при наличии — терминал / номер чека), или
     * - полное совпадение момента покупки (дата+время) + продавец + сумма + НДС.
     */
    suspend fun findConflicts(fromMs: Long, toMs: Long): List<ReceiptConflict> =
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val args = arrayOf(fromMs.toString(), toMs.toString())
            // Ключ пары чеков: при совпадении обеих веток предпочитаем FISCAL.
            val byPair = linkedMapOf<String, ReceiptConflict>()

            fun pairKey(c: ReceiptConflict): String =
                "${c.user1Id}|${c.user2Id}|${c.purchasedAt}|${c.totalAmountTiyin}|${c.sellerName}"

            fun readRow(
                c: android.database.Cursor,
                matchKind: ConflictMatchKind,
                fiscalSign: String?
            ): ReceiptConflict = ReceiptConflict(
                qrUrl = c.getString(0),
                sellerName = c.getString(1),
                purchasedAt = c.getLong(2),
                totalAmountTiyin = c.getLong(3),
                user1Id = c.getLong(4),
                user1FullName = c.getString(5),
                user2Id = c.getLong(6),
                user2FullName = c.getString(7),
                matchKind = matchKind,
                fiscalSign = fiscalSign
            )

            val fiscalSql = """
                SELECT r1.qr_url, r1.seller_name, r1.purchased_at, r1.total_amount_tiyin,
                       r1.user_id, u1.full_name,
                       r2.user_id, u2.full_name,
                       r1.fiscal_sign
                FROM receipts r1
                INNER JOIN receipts r2
                    ON r1.user_id < r2.user_id
                    AND r1.fiscal_sign IS NOT NULL AND r1.fiscal_sign != ''
                    AND r1.fiscal_sign = r2.fiscal_sign
                    AND IFNULL(r1.terminal_id, '') = IFNULL(r2.terminal_id, '')
                    AND IFNULL(r1.receipt_number, '') = IFNULL(r2.receipt_number, '')
                INNER JOIN users u1 ON u1.id = r1.user_id
                INNER JOIN users u2 ON u2.id = r2.user_id
                WHERE r1.purchased_at >= ? AND r1.purchased_at <= ?
                ORDER BY r1.purchased_at DESC
            """.trimIndent()

            db.rawQuery(fiscalSql, args).use { c ->
                while (c.moveToNext()) {
                    val fiscal = c.getString(8)?.takeIf { it.isNotBlank() }
                    val row = readRow(c, ConflictMatchKind.FISCAL, fiscal)
                    byPair[pairKey(row)] = row
                }
            }

            val sellerSql = """
                SELECT r1.qr_url, r1.seller_name, r1.purchased_at, r1.total_amount_tiyin,
                       r1.user_id, u1.full_name,
                       r2.user_id, u2.full_name,
                       r1.fiscal_sign
                FROM receipts r1
                INNER JOIN receipts r2
                    ON r1.user_id < r2.user_id
                    AND r1.seller_name = r2.seller_name
                    AND r1.purchased_at = r2.purchased_at
                    AND r1.total_amount_tiyin = r2.total_amount_tiyin
                    AND r1.vat_amount_tiyin = r2.vat_amount_tiyin
                INNER JOIN users u1 ON u1.id = r1.user_id
                INNER JOIN users u2 ON u2.id = r2.user_id
                WHERE r1.purchased_at >= ? AND r1.purchased_at <= ?
                ORDER BY r1.purchased_at DESC
            """.trimIndent()

            db.rawQuery(sellerSql, args).use { c ->
                while (c.moveToNext()) {
                    val fiscal = c.getString(8)?.takeIf { it.isNotBlank() }
                    val row = readRow(c, ConflictMatchKind.SELLER_TIME_AMOUNT, fiscal)
                    val key = pairKey(row)
                    // Не перезаписываем уже найденное фискальное совпадение.
                    if (key !in byPair) byPair[key] = row
                }
            }

            byPair.values.sortedByDescending { it.purchasedAt }
        }

    /**
     * Собирает человекочитаемые причины несостыковок по сводке сотрудника
     * и списку его конфликтов чеков.
     */
    fun buildDiscrepancyDetail(
        summary: EmployeeSummary,
        conflictsForUser: List<ReceiptConflict>
    ): DiscrepancyDetail {
        val reasons = mutableListOf<DiscrepancyReason>()
        val decl = summary.declaration

        if (decl != null) {
            if (decl.declaredTotalTiyin != 0L && decl.declaredTotalTiyin != summary.totalTiyin) {
                reasons += DiscrepancyReason.TotalMismatch(
                    declared = decl.declaredTotalTiyin,
                    actual = summary.totalTiyin,
                    delta = summary.totalTiyin - decl.declaredTotalTiyin
                )
            }
            if (decl.declaredVatTiyin != 0L && decl.declaredVatTiyin != summary.vatTiyin) {
                reasons += DiscrepancyReason.VatMismatch(
                    declared = decl.declaredVatTiyin,
                    actual = summary.vatTiyin,
                    delta = summary.vatTiyin - decl.declaredVatTiyin
                )
            }
            if (decl.declaredCount != 0 && decl.declaredCount != summary.receiptCount) {
                reasons += DiscrepancyReason.CountMismatch(
                    declared = decl.declaredCount,
                    actual = summary.receiptCount
                )
            }
            if (!decl.note.isNullOrBlank()) {
                reasons += DiscrepancyReason.ManualNote(decl.note.trim())
            }
            if (decl.status != AuditStatus.PENDING) {
                reasons += DiscrepancyReason.StatusInfo(decl.status)
            }
        }

        if (summary.verifiedCount < summary.receiptCount) {
            reasons += DiscrepancyReason.IncompleteVerification(
                verified = summary.verifiedCount,
                total = summary.receiptCount
            )
        }

        for (conflict in conflictsForUser) {
            reasons += DiscrepancyReason.DuplicateReceipt(conflict)
        }

        return DiscrepancyDetail(
            summary = summary,
            reasons = reasons,
            conflicts = conflictsForUser
        )
    }

    // ── Batch import analysis ────────────────────────────────────────────────

    /**
     * Для набора JSON-бэкапов (строки) анализирует пересечения QR без записи в БД.
     * Возвращает карту qr_url → список имён файлов/профилей, у которых он встречается.
     */
    suspend fun scanBackupsForConflicts(
        jsons: List<Pair<String, String>> // Pair(имя_файла, json_текст)
    ): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val qrToSources = mutableMapOf<String, MutableList<String>>()
        for ((name, json) in jsons) {
            try {
                val root = org.json.JSONObject(json)
                val arr = root.optJSONArray("receipts") ?: continue
                for (i in 0 until arr.length()) {
                    val qr = arr.getJSONObject(i).optString("qrUrl") ?: continue
                    qrToSources.getOrPut(qr) { mutableListOf() }.add(name)
                }
            } catch (_: Exception) {}
        }
        // Возвращаем только те QR, которые встречаются более чем в одном файле.
        qrToSources.filter { it.value.size > 1 }
    }
}
