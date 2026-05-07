package com.example.uzb_qqs_for_dip.data.repository

import android.content.ContentValues
import com.example.uzb_qqs_for_dip.data.db.DbHelper
import com.example.uzb_qqs_for_dip.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class UserRepository(private val dbHelper: DbHelper) {

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: Flow<List<User>> = _users.asStateFlow()

    init {
        refreshSync()
    }

    private fun refreshSync() {
        _users.value = readAllSync()
    }

    private fun readAllSync(): List<User> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<User>()
        db.rawQuery(
            "SELECT id, full_name, position, initials_surname, created_at FROM users ORDER BY full_name COLLATE NOCASE",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    User(
                        id = c.getLong(0),
                        fullName = c.getString(1),
                        position = c.getString(2),
                        initialsSurname = c.getString(3),
                        createdAt = c.getLong(4)
                    )
                )
            }
        }
        return list
    }

    suspend fun list(): List<User> = withContext(Dispatchers.IO) { readAllSync() }

    suspend fun getById(id: Long): User? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT id, full_name, position, initials_surname, created_at FROM users WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                User(
                    id = c.getLong(0),
                    fullName = c.getString(1),
                    position = c.getString(2),
                    initialsSurname = c.getString(3),
                    createdAt = c.getLong(4)
                )
            } else null
        }
    }

    suspend fun create(user: User): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("full_name", user.fullName.trim())
                put("position", user.position.trim())
                put("initials_surname", user.initialsSurname.trim())
                put("created_at", user.createdAt)
            }
            val id = db.insertOrThrow("users", null, cv)
            refreshSync()
            id
        }
    }

    /**
     * Обновляет имя/должность/ФИО в подписи существующего профиля.
     * UNIQUE-конфликт по полю full_name возвращается через [Result.failure].
     */
    suspend fun update(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("full_name", user.fullName.trim())
                put("position", user.position.trim())
                put("initials_surname", user.initialsSurname.trim())
            }
            val n = db.update("users", cv, "id = ?", arrayOf(user.id.toString()))
            if (n <= 0) error("Профиль не найден")
            refreshSync()
        }
    }

    /** Перечитать пользователей из БД (после прямых SQL-операций вне репозитория). */
    suspend fun refresh() = withContext(Dispatchers.IO) { refreshSync() }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // ON DELETE CASCADE на таблице receipts уберёт связанные чеки автоматически.
        val n = db.delete("users", "id = ?", arrayOf(id.toString()))
        if (n > 0) refreshSync()
        n > 0
    }
}
