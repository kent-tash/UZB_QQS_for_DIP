package com.example.uzb_qqs_for_dip.data.backup

import android.content.ContentValues
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.repository.ReceiptRepository
import com.example.uzb_qqs_for_dip.data.repository.UserRepository
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.data.settings.ReportSettings
import com.example.uzb_qqs_for_dip.data.settings.ReportSettingsHolder
import com.example.uzb_qqs_for_dip.data.settings.SortField
import com.example.uzb_qqs_for_dip.data.settings.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Один JSON-файл: все профили, чеки и настройки отчёта (вкладка «Отчёт» / «Чеки»).
 *
 * Импорт **полностью заменяет** текущие данные в БД и подставляет настройки из файла.
 * Идентификаторы пользователей и чеков пересоздаются; в настройках отчёта `userId`
 * и сессия переназначаются по карте старый id → новый id.
 */
class AppBackup(
    private val dbHelper: DbHelper,
    private val reportSettingsHolder: ReportSettingsHolder,
    private val userRepository: UserRepository,
    private val receiptRepository: ReceiptRepository,
) {

    data class RestoreOutcome(
        /** Новый id для пользователя, который был залогинен до импорта, или null (выйти на экран входа). */
        val newSessionUserId: Long?,
    )

    suspend fun exportJsonString(): String = withContext(Dispatchers.IO) {
        val users = userRepository.list()
        val receipts = receiptRepository.list()
        val settings = reportSettingsHolder.settings.value

        val root = JSONObject()
        root.put(KEY_FORMAT, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_REPORT_SETTINGS, reportSettingsToJson(settings))

        val usersArr = JSONArray()
        for (u in users) {
            usersArr.put(
                JSONObject().apply {
                    put(KEY_ID, u.id)
                    put(KEY_FULL_NAME, u.fullName)
                    put(KEY_POSITION, u.position)
                    put(KEY_INITIALS_SURNAME, u.initialsSurname)
                    put(KEY_CREATED_AT, u.createdAt)
                }
            )
        }
        root.put(KEY_USERS, usersArr)

        val receiptsArr = JSONArray()
        for (rw in receipts) {
            val r = rw.receipt
            receiptsArr.put(
                JSONObject().apply {
                    put(KEY_USER_ID, r.userId)
                    put(KEY_PURCHASED_AT, r.purchasedAt)
                    put(KEY_SELLER_NAME, r.sellerName)
                    put(KEY_TOTAL_TIYIN, r.totalAmountTiyin)
                    put(KEY_VAT_TIYIN, r.vatAmountTiyin)
                    put(KEY_QR_URL, r.qrUrl)
                    put(KEY_RAW_TEXT, r.rawText ?: JSONObject.NULL)
                    put(KEY_CREATED_AT, r.createdAt)
                }
            )
        }
        root.put(KEY_RECEIPTS, receiptsArr)

        root.toString(2)
    }

    /**
     * Заменяет данные содержимым бэкапа.
     * @return новый user id для сессии, если до импорта был залогинен пользователь из бэкапа.
     */
    suspend fun importJsonString(json: String, previousSessionUserId: Long?): Result<RestoreOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(json)
                val ver = root.getInt(KEY_FORMAT)
                if (ver != FORMAT_VERSION) {
                    error("Версия бэкапа не поддерживается: $ver")
                }

                val idMap = LinkedHashMap<Long, Long>()
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    db.delete("receipts", null, null)
                    db.delete("users", null, null)

                    val usersArr = root.getJSONArray(KEY_USERS)
                    for (i in 0 until usersArr.length()) {
                        val o = usersArr.getJSONObject(i)
                        val oldId = o.getLong(KEY_ID)
                        val cv = ContentValues().apply {
                            put("full_name", o.getString(KEY_FULL_NAME).trim())
                            put("position", o.getString(KEY_POSITION).trim())
                            put("initials_surname", o.getString(KEY_INITIALS_SURNAME).trim())
                            put("created_at", o.optLong(KEY_CREATED_AT, System.currentTimeMillis()))
                        }
                        val newId = db.insertOrThrow("users", null, cv)
                        idMap[oldId] = newId
                    }

                    val receiptsArr = root.getJSONArray(KEY_RECEIPTS)
                    for (i in 0 until receiptsArr.length()) {
                        val o = receiptsArr.getJSONObject(i)
                        val legacyUserId = o.getLong(KEY_USER_ID)
                        val newUserId =
                            idMap[legacyUserId] ?: error("Чек ссылается на неизвестный профиль id=$legacyUserId")
                        val cv = ContentValues().apply {
                            put("user_id", newUserId)
                            put("purchased_at", o.getLong(KEY_PURCHASED_AT))
                            put("seller_name", o.getString(KEY_SELLER_NAME))
                            put("total_amount_tiyin", o.getLong(KEY_TOTAL_TIYIN))
                            put("vat_amount_tiyin", o.getLong(KEY_VAT_TIYIN))
                            put("qr_url", o.getString(KEY_QR_URL))
                            if (o.isNull(KEY_RAW_TEXT)) putNull("raw_text")
                            else put("raw_text", o.getString(KEY_RAW_TEXT))
                            put("created_at", o.optLong(KEY_CREATED_AT, System.currentTimeMillis()))
                        }
                        db.insertOrThrow("receipts", null, cv)
                    }

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                userRepository.refresh()
                receiptRepository.refresh()

                val fileSettings =
                    reportSettingsFromJson(root.getJSONObject(KEY_REPORT_SETTINGS))
                val newFilterUserId = fileSettings.userId?.let { old ->
                    idMap[old]
                }
                reportSettingsHolder.replace(
                    fileSettings.copy(userId = newFilterUserId)
                )

                val newSession = previousSessionUserId?.let { idMap[it] }
                RestoreOutcome(newSessionUserId = newSession)
            }
        }

    companion object {
        const val FORMAT_VERSION = 1
        const val MIME_TYPE = "application/json"
        /** Рекомендуемое имя при сохранении — один файл UTF-8 JSON. */
        const val FILE_BASE_NAME = "qqs_backup"

        private const val KEY_FORMAT = "formatVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_REPORT_SETTINGS = "reportSettings"
        private const val KEY_USERS = "users"
        private const val KEY_RECEIPTS = "receipts"

        private const val KEY_ID = "id"
        private const val KEY_FULL_NAME = "fullName"
        private const val KEY_POSITION = "position"
        private const val KEY_INITIALS_SURNAME = "initialsSurname"
        private const val KEY_CREATED_AT = "createdAt"

        private const val KEY_USER_ID = "userId"
        private const val KEY_PURCHASED_AT = "purchasedAt"
        private const val KEY_SELLER_NAME = "sellerName"
        private const val KEY_TOTAL_TIYIN = "totalAmountTiyin"
        private const val KEY_VAT_TIYIN = "vatAmountTiyin"
        private const val KEY_QR_URL = "qrUrl"
        private const val KEY_RAW_TEXT = "rawText"

        private fun reportSettingsToJson(s: ReportSettings): JSONObject =
            JSONObject().apply {
                put("userId", s.userId ?: JSONObject.NULL)
                put("quarter", s.quarter.name)
                put("year", s.year)
                put("from", s.from)
                put("to", s.to)
                put("sortField", s.sortField.name)
                put("sortOrder", s.sortOrder.name)
            }

        private fun reportSettingsFromJson(o: JSONObject): ReportSettings {
            val userId = if (o.isNull("userId") || o.get("userId") == JSONObject.NULL) {
                null
            } else {
                o.getLong("userId")
            }
            return ReportSettings(
                userId = userId,
                quarter = Quarter.valueOf(o.getString("quarter")),
                year = o.getInt("year"),
                from = o.getLong("from"),
                to = o.getLong("to"),
                sortField = SortField.valueOf(o.getString("sortField")),
                sortOrder = SortOrder.valueOf(o.getString("sortOrder")),
            )
        }
    }
}
