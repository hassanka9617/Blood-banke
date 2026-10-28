package com.bloodbank.inventory.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.bloodbank.inventory.BloodBankApp
import com.bloodbank.inventory.data.AppDatabase
import com.bloodbank.inventory.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * النسخ الاحتياطي = نسخة كاملة من ملف قاعدة البيانات.
 * الاستعادة تستبدل قاعدة البيانات الحالية (بعد حفظ نسخة أمان تلقائية منها) ثم تعيد تشغيل التطبيق.
 */
object BackupManager {

    fun suggestedFileName() = "blood_bank_backup_${Format.fileStamp()}.db"

    suspend fun exportTo(context: Context, target: Uri) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as BloodBankApp
        // دمج سجل WAL داخل الملف الرئيسي حتى تكون النسخة كاملة
        app.database.openHelper.writableDatabase
            .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        context.contentResolver.openOutputStream(target)?.use { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("تعذر إنشاء ملف النسخة الاحتياطية")
        app.repository.audit(null, "نسخ احتياطي", "تم تصدير نسخة احتياطية كاملة")
    }

    /** يعيد null عند النجاح أو رسالة خطأ. بعد النجاح يجب استدعاء restartApp. */
    suspend fun restoreFrom(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "restore_candidate.db")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext "تعذر قراءة الملف"

            // التحقق أن الملف قاعدة بيانات SQLite صحيحة وتحتوي جداول التطبيق
            val header = temp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
            if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                return@withContext "الملف المختار ليس نسخة احتياطية صحيحة"
            }
            val ok = SQLiteDatabase.openDatabase(temp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('shifts','movements','opening_balances')", null)
                    .use { it.count == 3 }
            }
            if (!ok) return@withContext "الملف لا يحتوي على بيانات هذا التطبيق"

            val app = context.applicationContext as BloodBankApp
            val dbFile = context.getDatabasePath(AppDatabase.NAME)

            // نسخة أمان من البيانات الحالية قبل الاستبدال
            app.database.openHelper.writableDatabase
                .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
            val safety = File(context.filesDir, "backups").apply { mkdirs() }
            dbFile.copyTo(File(safety, "before_restore_${Format.fileStamp()}.db"), overwrite = true)

            app.database.close()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-journal").delete()
            temp.copyTo(dbFile, overwrite = true)
            null
        } catch (e: Exception) {
            "فشلت الاستعادة: ${e.message}"
        } finally {
            temp.delete()
        }
    }

    /** إعادة تشغيل التطبيق لفتح قاعدة البيانات المستعادة. */
    fun restartApp(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
