package com.bloodbank.inventory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // ---------- التصنيفات ----------
    @Query("SELECT * FROM classifications ORDER BY category, sortOrder, id")
    fun observeClassifications(): Flow<List<ClassificationEntity>>

    @Query("SELECT * FROM classifications ORDER BY category, sortOrder, id")
    suspend fun getClassifications(): List<ClassificationEntity>

    @Query("SELECT * FROM classifications WHERE id = :id")
    suspend fun getClassification(id: Long): ClassificationEntity?

    @Insert
    suspend fun insertClassification(c: ClassificationEntity): Long

    @Update
    suspend fun updateClassification(c: ClassificationEntity)

    // ---------- الخفارات ----------
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1")
    fun observeOpenShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1")
    suspend fun getOpenShift(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE status = 'CLOSED' ORDER BY closedAt DESC, id DESC")
    fun observeClosedShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE status = 'CLOSED' ORDER BY closedAt DESC, id DESC LIMIT 1")
    suspend fun getLastClosedShift(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getShift(id: Long): ShiftEntity?

    @Query("SELECT COUNT(*) FROM shifts WHERE previousShiftId = :previousId")
    suspend fun countShiftsCarriedFrom(previousId: Long): Int

    @Insert
    suspend fun insertShift(s: ShiftEntity): Long

    @Update
    suspend fun updateShift(s: ShiftEntity)

    // ---------- الرصيد الافتتاحي ----------
    @Insert
    suspend fun insertOpenings(list: List<OpeningBalanceEntity>)

    @Query("SELECT * FROM opening_balances WHERE shiftId = :shiftId")
    fun observeOpenings(shiftId: Long): Flow<List<OpeningBalanceEntity>>

    @Query("SELECT * FROM opening_balances WHERE shiftId = :shiftId")
    suspend fun getOpenings(shiftId: Long): List<OpeningBalanceEntity>

    // ---------- الحركات ----------
    /** يعيد -1 إذا كانت الحركة محفوظة مسبقاً بنفس requestId. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovement(m: MovementEntity): Long

    @Query("SELECT * FROM movements WHERE shiftId = :shiftId ORDER BY createdAt DESC, id DESC")
    fun observeMovements(shiftId: Long): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements WHERE shiftId = :shiftId ORDER BY createdAt, id")
    suspend fun getMovements(shiftId: Long): List<MovementEntity>

    @Query("SELECT * FROM movements WHERE id = :id")
    suspend fun getMovement(id: Long): MovementEntity?

    @Update
    suspend fun updateMovement(m: MovementEntity)

    // ---------- الرصيد الختامي ----------
    @Insert
    suspend fun insertClosings(list: List<ClosingBalanceEntity>)

    @Query("SELECT * FROM closing_balances WHERE shiftId = :shiftId")
    suspend fun getClosings(shiftId: Long): List<ClosingBalanceEntity>

    // ---------- سجل التدقيق ----------
    @Insert
    suspend fun insertAudit(a: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY at DESC, id DESC LIMIT 1000")
    fun observeAudit(): Flow<List<AuditLogEntity>>

    // ---------- الإعدادات ----------
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observeSetting(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(s: SettingEntity)
}
