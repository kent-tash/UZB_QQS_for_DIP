package com.example.uzb_qqs_for_dip.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL UNIQUE,
                position TEXT NOT NULL,
                initials_surname TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                purchased_at INTEGER NOT NULL,
                seller_name TEXT NOT NULL,
                total_amount_tiyin INTEGER NOT NULL,
                vat_amount_tiyin INTEGER NOT NULL,
                qr_url TEXT NOT NULL,
                raw_text TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_receipts_user ON receipts(user_id)")
        db.execSQL("CREATE INDEX idx_receipts_date ON receipts(purchased_at)")
        db.execSQL("CREATE UNIQUE INDEX idx_receipts_qr ON receipts(qr_url)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Для дипломного приложения — простая стратегия: пересоздать.
        db.execSQL("DROP TABLE IF EXISTS receipts")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DB_NAME = "uzb_qqs.db"
        const val DB_VERSION = 1
    }
}
