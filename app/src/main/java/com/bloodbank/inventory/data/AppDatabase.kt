package com.bloodbank.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bloodbank.inventory.domain.Category

@Database(
    entities = [
        ClassificationEntity::class,
        ShiftEntity::class,
        OpeningBalanceEntity::class,
        MovementEntity::class,
        ClosingBalanceEntity::class,
        AuditLogEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): InventoryDao

    companion object {
        const val NAME = "blood_bank.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // التصنيفات الافتراضية لكل صنف؛ يمكن تعديلها من الإعدادات
                        Category.entries.forEach { cat ->
                            cat.defaultClassifications.forEachIndexed { i, name ->
                                db.execSQL(
                                    "INSERT INTO classifications(category, name, sortOrder, active) VALUES(?, ?, ?, 1)",
                                    arrayOf<Any>(cat.name, name, i)
                                )
                            }
                        }
                        db.execSQL(
                            "INSERT INTO settings(`key`, value) VALUES(?, ?)",
                            arrayOf<Any>(InventoryRepository.KEY_HOSPITAL, "مصرف الدم")
                        )
                    }
                })
                .build()
    }
}
