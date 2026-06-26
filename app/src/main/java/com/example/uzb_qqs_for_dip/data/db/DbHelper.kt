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
                organization TEXT NOT NULL DEFAULT '',
                role TEXT NOT NULL DEFAULT 'EMPLOYEE',
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
                payment_type TEXT,
                fiscal_sign TEXT,
                address TEXT,
                tin TEXT,
                terminal_id TEXT,
                receipt_number TEXT,
                nkm_name TEXT,
                sn TEXT,
                raw_text TEXT,
                verified_at INTEGER,
                verified_by INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_receipts_user ON receipts(user_id)")
        db.execSQL("CREATE INDEX idx_receipts_date ON receipts(purchased_at)")
        db.execSQL("CREATE UNIQUE INDEX idx_receipts_qr ON receipts(qr_url)")

        db.execSQL(
            """
            CREATE TABLE audit_declarations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                year INTEGER NOT NULL,
                quarter TEXT NOT NULL,
                declared_total_tiyin INTEGER NOT NULL DEFAULT 0,
                declared_vat_tiyin INTEGER NOT NULL DEFAULT 0,
                declared_count INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'PENDING',
                note TEXT,
                checked_at INTEGER,
                UNIQUE(user_id, year, quarter),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureReceiptColumns(db)
        ensureUserColumns(db)
        ensureAuditDeclarationsTable(db)
    }

    private fun ensureReceiptColumns(db: SQLiteDatabase) {
        val existing = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(receipts)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) existing.add(c.getString(nameIdx))
        }
        if (existing.isEmpty()) {
            onCreate(db)
            return
        }
        val optionalColumns = linkedMapOf(
            "payment_type" to "TEXT",
            "fiscal_sign" to "TEXT",
            "address" to "TEXT",
            "tin" to "TEXT",
            "terminal_id" to "TEXT",
            "receipt_number" to "TEXT",
            "nkm_name" to "TEXT",
            "sn" to "TEXT",
            "raw_text" to "TEXT",
            "verified_at" to "INTEGER",
            "verified_by" to "INTEGER"
        )
        for ((column, type) in optionalColumns) {
            if (column !in existing) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN $column $type")
            }
        }
        db.execSQL(
            "UPDATE receipts SET payment_type = 'CARD' WHERE payment_type IS NULL OR payment_type = ''"
        )
    }

    private fun ensureUserColumns(db: SQLiteDatabase) {
        val existing = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(users)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) existing.add(c.getString(nameIdx))
        }
        if ("role" !in existing) {
            db.execSQL("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'EMPLOYEE'")
        }
        if ("organization" !in existing) {
            db.execSQL("ALTER TABLE users ADD COLUMN organization TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun ensureAuditDeclarationsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audit_declarations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                year INTEGER NOT NULL,
                quarter TEXT NOT NULL,
                declared_total_tiyin INTEGER NOT NULL DEFAULT 0,
                declared_vat_tiyin INTEGER NOT NULL DEFAULT 0,
                declared_count INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'PENDING',
                note TEXT,
                checked_at INTEGER,
                UNIQUE(user_id, year, quarter),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DB_NAME = "uzb_qqs.db"
        const val DB_VERSION = 8
    }
}
