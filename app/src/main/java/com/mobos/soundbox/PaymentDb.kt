package com.mobos.soundbox

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * On-phone SQLite database of payments. Nothing leaves the phone (the app has no internet permission).
 * Add new columns later with a version bump + ALTER TABLE in onUpgrade, so old data is kept.
 */
class PaymentDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "payments.db", null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                amount REAL NOT NULL,
                source TEXT NOT NULL,
                payer TEXT,
                channel TEXT NOT NULL,
                ref TEXT
            )"""
        )
        db.execSQL("CREATE INDEX idx_payments_time ON payments(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema. Future versions: add ALTER TABLE steps here, never drop data.
    }

    fun insert(p: Payment, channel: String, ref: String?, time: Long = System.currentTimeMillis()): Long =
        writableDatabase.insert("payments", null, ContentValues().apply {
            put("time", time)
            put("amount", p.amount)
            put("source", p.source)
            put("payer", p.payer)
            put("channel", channel)
            put("ref", ref)
        })

    /** Newest first. */
    fun since(fromTime: Long, limit: Int = 500): List<PaymentRecord> {
        val out = ArrayList<PaymentRecord>()
        readableDatabase.query(
            "payments", null, "time >= ?", arrayOf(fromTime.toString()),
            null, null, "time DESC", limit.toString()
        ).use { c ->
            val iId = c.getColumnIndexOrThrow("id")
            val iTime = c.getColumnIndexOrThrow("time")
            val iAmt = c.getColumnIndexOrThrow("amount")
            val iSrc = c.getColumnIndexOrThrow("source")
            val iPayer = c.getColumnIndexOrThrow("payer")
            val iCh = c.getColumnIndexOrThrow("channel")
            val iRef = c.getColumnIndexOrThrow("ref")
            while (c.moveToNext()) {
                out.add(
                    PaymentRecord(
                        c.getLong(iId), c.getLong(iTime), c.getDouble(iAmt), c.getString(iSrc),
                        if (c.isNull(iPayer)) null else c.getString(iPayer),
                        c.getString(iCh),
                        if (c.isNull(iRef)) null else c.getString(iRef),
                    )
                )
            }
        }
        return out
    }

    fun deleteTestPayments(): Int = writableDatabase.delete("payments", "channel = ?", arrayOf("test"))

    companion object {
        private const val VERSION = 1
        @Volatile private var instance: PaymentDb? = null
        fun get(ctx: Context): PaymentDb =
            instance ?: synchronized(this) { instance ?: PaymentDb(ctx).also { instance = it } }
    }
}
