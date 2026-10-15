package me.weishu.kernelsu.jwsk.alpha

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/** The daemon's app_process callback runs as uid 0; ordinary apps cannot forge logs. */
class AlphaLogProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == 0) { "Only the Root daemon may deliver Alpha events" }
        if (method == "log" || method == "notify") {
            val data = extras ?: return Bundle.EMPTY
            @Suppress("DEPRECATION")
            fun number(key: String) = (data.get(key) as? Number)?.toLong() ?: -1
            val uid = number("from.uid")
            if (uid >= 0) {
                val app = requireNotNull(context)
                if (method == "log") AlphaLogs(app).use { logs ->
                    logs.record(uid, number("to.uid"), number("policy"), data.getString("command", "").take(2048))
                }
                if (method == "notify" || data.getBoolean("notify", true)) {
                    val pm = app.packageManager
                    val label = runCatching {
                        pm.getPackagesForUid(uid.toInt())?.firstOrNull()?.let { pm.getApplicationInfo(it, 0).loadLabel(pm).toString() }
                    }.getOrNull() ?: app.getString(me.weishu.kernelsu.R.string.jwsk_alpha_uninstalled)
                    val message = app.getString(if (number("policy") >= 2)
                        me.weishu.kernelsu.R.string.jwsk_alpha_access_granted else me.weishu.kernelsu.R.string.jwsk_alpha_access_denied, label.take(256))
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return Bundle.EMPTY
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

internal class AlphaLogs(context: Context) : SQLiteOpenHelper(context, "jwsk-alpha-events.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY, time INTEGER, uid INTEGER, target INTEGER, policy INTEGER, command TEXT)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun record(uid: Long, target: Long, policy: Long, command: String) {
        val values = ContentValues().apply {
            put("time", System.currentTimeMillis()); put("uid", uid); put("target", target); put("policy", policy); put("command", command)
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow("events", null, values)
            writableDatabase.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 512)")
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun text(): String = readableDatabase.rawQuery("SELECT time,uid,target,policy,command FROM events ORDER BY id DESC LIMIT 200", null).use { cursor ->
        buildString {
            while(cursor.moveToNext() && length < 49152) {
                append(java.time.Instant.ofEpochMilli(cursor.getLong(0)))
                append(" uid=").append(cursor.getLong(1)).append(" target=").append(cursor.getLong(2))
                append(" policy=").append(cursor.getLong(3)).append('\n').append(cursor.getString(4)).append("\n\n")
            }
        }.take(49152)
    }
    fun clear() { writableDatabase.delete("events", null, null) }
}
