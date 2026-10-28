package com.bloodbank.inventory.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.ClassInfo
import com.bloodbank.inventory.domain.MovementLite
import com.bloodbank.inventory.domain.MovementType
import com.bloodbank.inventory.domain.ShiftStatus

/** تصنيف (فصيلة) قابل للتعديل من الإعدادات، مستقل لكل صنف. */
@Entity(
    tableName = "classifications",
    indices = [Index(value = ["category", "name"], unique = true)]
)
data class ClassificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: Category,
    val name: String,
    val sortOrder: Int,
    val active: Boolean = true
) {
    fun toInfo() = ClassInfo(id, category, name, sortOrder)
}

/**
 * الخفارة. previousShiftId عليه فهرس فريد، فلا يمكن أن تُرحَّل خفارة واحدة إلى خفارتين (منع النقل المزدوج).
 */
@Entity(
    tableName = "shifts",
    indices = [Index(value = ["previousShiftId"], unique = true), Index("status")]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftNumber: String,
    val openedAt: Long,
    /** الموظف الذي استلم هذه الخفارة ويعمل فيها (وهو المسلِّم عند نهايتها). */
    val receiverName: String,
    /** الموظف السابق الذي سلّم المخزون عند بداية الخفارة. */
    val delivererName: String,
    /** ملاحظة توثيق فروقات الاستلام. */
    val receiveNote: String,
    val status: ShiftStatus,
    val previousShiftId: Long?,
    val closedAt: Long? = null,
    /** الموظف الذي سيستلم المخزون عند نهاية الخفارة. */
    val handoverToName: String = "",
    val closingNotes: String = ""
)

/** الرصيد الافتتاحي (الجرد الفعلي عند الاستلام). لا يتغير بسبب الحركات اللاحقة. */
@Entity(tableName = "opening_balances", primaryKeys = ["shiftId", "classificationId"])
data class OpeningBalanceEntity(
    val shiftId: Long,
    val classificationId: Long,
    val category: Category,
    val name: String,
    val sortOrder: Int,
    /** الرصيد المتوقع من الخفارة السابقة (null إذا لم توجد خفارة سابقة). */
    val expected: Int?,
    val actual: Int
)

/**
 * حركة واحدة (إضافة/صرف/تسوية). requestId فريد لمنع حفظ نفس الحركة مرتين عند تكرار الضغط.
 * الحركات لا تُحذف أبداً؛ الإلغاء يكون بعلامة voided مع سبب موثق.
 */
@Entity(
    tableName = "movements",
    indices = [Index(value = ["requestId"], unique = true), Index("shiftId")]
)
data class MovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val classificationId: Long,
    val category: Category,
    val classificationName: String,
    val type: MovementType,
    /** للإضافة والصرف: موجبة دائماً. للتسوية: موجبة (زيادة) أو سالبة (نقص). */
    val quantity: Int,
    val createdAt: Long,
    val note: String,
    val requestId: String,
    val voided: Boolean = false,
    val voidedAt: Long? = null,
    val voidReason: String = ""
) {
    fun toLite() = MovementLite(classificationId, type, quantity, voided)
}

/** لقطة ثابتة للأرصدة عند اعتماد نهاية الخفارة؛ منها يُنشأ التقرير ويُرحَّل الرصيد للخفارة التالية. */
@Entity(tableName = "closing_balances", primaryKeys = ["shiftId", "classificationId"])
data class ClosingBalanceEntity(
    val shiftId: Long,
    val classificationId: Long,
    val category: Category,
    val name: String,
    val sortOrder: Int,
    val opening: Int,
    val added: Int,
    val dispensed: Int,
    val adjustment: Int,
    val closing: Int
)

/** سجل تدقيق لكل عملية مهمة: استلام، إلغاء حركة، تسوية، إنهاء خفارة، تعديل إعدادات، استعادة نسخة. */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val shiftId: Long?,
    val action: String,
    val details: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
