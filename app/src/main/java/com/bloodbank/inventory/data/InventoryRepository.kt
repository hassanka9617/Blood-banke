package com.bloodbank.inventory.data

import androidx.room.withTransaction
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.BalanceRow
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.InventoryException
import com.bloodbank.inventory.domain.MovementType
import com.bloodbank.inventory.domain.ShiftStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** بيانات تحضير شاشة استلام الخفارة. */
data class ReceivingDraft(
    val classifications: List<ClassificationEntity>,
    /** الرصيد المتوقع لكل تصنيف من الرصيد الختامي للخفارة السابقة؛ null إذا لا توجد خفارة سابقة. */
    val expected: Map<Long, Int>?,
    val previousShift: ShiftEntity?,
    val suggestedShiftNumber: String
)

/** كل ما يلزم لإنشاء التقرير النهائي لخفارة مغلقة. */
data class ReportData(
    val hospitalName: String,
    val shift: ShiftEntity,
    val rows: List<BalanceRow>,
    val openings: List<OpeningBalanceEntity>,
    val movements: List<MovementEntity>
)

class InventoryRepository(private val db: AppDatabase) {

    private val dao = db.dao()

    companion object {
        const val KEY_HOSPITAL = "hospital_name"
    }

    // ================= قراءة =================

    fun observeOpenShift(): Flow<ShiftEntity?> = dao.observeOpenShift()
    fun observeClassifications(): Flow<List<ClassificationEntity>> = dao.observeClassifications()
    fun observeMovements(shiftId: Long): Flow<List<MovementEntity>> = dao.observeMovements(shiftId)
    fun observeClosedShifts(): Flow<List<ShiftEntity>> = dao.observeClosedShifts()
    fun observeAudit(): Flow<List<AuditLogEntity>> = dao.observeAudit()
    fun observeHospitalName(): Flow<String?> = dao.observeSetting(KEY_HOSPITAL)

    /** الأرصدة الحالية لخفارة مفتوحة؛ تتحدث تلقائياً بعد كل إضافة أو صرف. */
    fun observeBalances(shiftId: Long): Flow<List<BalanceRow>> =
        combine(
            dao.observeClassifications(),
            dao.observeOpenings(shiftId),
            dao.observeMovements(shiftId)
        ) { classes, openings, movements -> computeRows(classes, openings, movements) }

    /**
     * التصنيفات المعروضة = المفعّلة + أي تصنيف له رصيد افتتاحي أو حركة في هذه الخفارة
     * (حتى لو أُوقف لاحقاً، كي لا يختفي رصيد موجود).
     */
    private fun computeRows(
        classes: List<ClassificationEntity>,
        openings: List<OpeningBalanceEntity>,
        movements: List<MovementEntity>
    ): List<BalanceRow> {
        val used = openings.map { it.classificationId }.toSet() + movements.map { it.classificationId }
        val shown = classes.filter { it.active || it.id in used }.map { it.toInfo() }
        return BalanceCalculator.computeRows(
            shown,
            openings.associate { it.classificationId to it.actual },
            movements.map { it.toLite() }
        )
    }

    private suspend fun currentRows(shiftId: Long): List<BalanceRow> =
        computeRows(dao.getClassifications(), dao.getOpenings(shiftId), dao.getMovements(shiftId))

    // ================= استلام الخفارة =================

    suspend fun prepareReceiving(): ReceivingDraft {
        val previous = dao.getLastClosedShift()
        val classes = dao.getClassifications()
        val expected: Map<Long, Int>? = previous?.let { prev ->
            val closings = dao.getClosings(prev.id).associate { it.classificationId to it.closing }
            val ids = classes.filter { it.active || (closings[it.id] ?: 0) != 0 }.map { it.id }
            BalanceCalculator.expectedOpenings(closings, ids)
        }
        val shown = classes.filter { it.active || (expected?.get(it.id) ?: 0) != 0 }
        val nextNumber = previous?.shiftNumber?.trim()?.toLongOrNull()?.plus(1)?.toString() ?: ""
        return ReceivingDraft(shown, expected, previous, nextNumber)
    }

    /**
     * يعتمد استلام الخفارة: ينشئ الخفارة ويحفظ الجرد الفعلي كرصيد افتتاحي مستقل.
     * يرفض إذا كانت هناك خفارة مفتوحة، أو إذا رُحِّلت الخفارة السابقة مسبقاً، أو إذا كان هناك فرق غير موثق.
     */
    suspend fun confirmReceiving(
        shiftNumber: String,
        receiverName: String,
        delivererName: String,
        actual: Map<Long, Int>,
        note: String
    ): Long = db.withTransaction {
        if (dao.getOpenShift() != null) throw InventoryException("توجد خفارة مفتوحة بالفعل؛ يجب إنهاؤها أولاً")
        if (actual.values.any { it < 0 }) throw InventoryException("لا يُسمح بأعداد سالبة")

        val draft = prepareReceiving()
        val previous = draft.previousShift
        if (previous != null && dao.countShiftsCarriedFrom(previous.id) > 0) {
            throw InventoryException("تم ترحيل رصيد الخفارة السابقة مسبقاً")
        }
        val classes = dao.getClassifications().associateBy { it.id }
        val ids = (draft.classifications.map { it.id } + actual.keys).toSet()
        val expected = draft.expected
        val diffs = if (expected != null) BalanceCalculator.differences(expected, actual.filterKeys { it in ids }) else emptyMap()
        if (diffs.isNotEmpty() && note.isBlank()) {
            throw InventoryException("يوجد فرق بين العدد الفعلي والمتوقع؛ يجب كتابة ملاحظة توضح سبب الفرق")
        }

        val now = System.currentTimeMillis()
        val shiftId = dao.insertShift(
            ShiftEntity(
                shiftNumber = shiftNumber.trim(),
                openedAt = now,
                receiverName = receiverName.trim(),
                delivererName = delivererName.trim(),
                receiveNote = note.trim(),
                status = ShiftStatus.OPEN,
                previousShiftId = previous?.id
            )
        )
        dao.insertOpenings(ids.mapNotNull { id ->
            val c = classes[id] ?: return@mapNotNull null
            OpeningBalanceEntity(
                shiftId = shiftId,
                classificationId = id,
                category = c.category,
                name = c.name,
                sortOrder = c.sortOrder,
                expected = expected?.get(id),
                actual = actual[id] ?: 0
            )
        })
        val diffText = if (diffs.isEmpty()) "لا توجد فروقات" else diffs.entries.joinToString("، ") { (id, d) ->
            val c = classes[id]
            "${c?.category?.arabicName} ${c?.name}: ${if (d > 0) "+" else ""}$d"
        }
        audit(shiftId, "استلام خفارة", "رقم الخفارة: ${shiftNumber.ifBlank { "-" }}، المستلم: ${receiverName.ifBlank { "-" }}، المسلِّم: ${delivererName.ifBlank { "-" }}. $diffText. ${note.trim()}")
        shiftId
    }

    // ================= الحركات =================

    /** الرصيد الحالي لتصنيف واحد في الخفارة. */
    suspend fun availableFor(shiftId: Long, classificationId: Long): Int =
        currentRows(shiftId).firstOrNull { it.classificationId == classificationId }?.closing ?: 0

    /**
     * يسجل حركة جديدة داخل معاملة واحدة: يعيد حساب الرصيد من القاعدة ثم يتحقق ثم يحفظ.
     * @return true إذا حُفظت الآن، false إذا كانت محفوظة مسبقاً (ضغط مكرر).
     */
    suspend fun addMovement(
        shiftId: Long,
        classificationId: Long,
        type: MovementType,
        quantity: Int,
        note: String,
        requestId: String
    ): Boolean = db.withTransaction {
        val shift = dao.getShift(shiftId) ?: throw InventoryException("الخفارة غير موجودة")
        if (shift.status != ShiftStatus.OPEN) throw InventoryException("الخفارة مغلقة؛ لا يمكن تسجيل حركات عليها")
        val c = dao.getClassification(classificationId) ?: throw InventoryException("التصنيف غير موجود")
        val available = availableFor(shiftId, classificationId)
        BalanceCalculator.validateMovement(type, quantity, available, note)?.let { throw InventoryException(it) }

        val id = dao.insertMovement(
            MovementEntity(
                shiftId = shiftId,
                classificationId = classificationId,
                category = c.category,
                classificationName = c.name,
                type = type,
                quantity = quantity,
                createdAt = System.currentTimeMillis(),
                note = note.trim(),
                requestId = requestId
            )
        )
        if (id == -1L) return@withTransaction false
        if (type == MovementType.ADJUSTMENT) {
            audit(shiftId, "تسوية", "${c.category.arabicName} ${c.name}: ${if (quantity > 0) "+" else ""}$quantity (الرصيد $available ← ${available + quantity}). السبب: ${note.trim()}")
        }
        true
    }

    /** إلغاء حركة مع توثيق السبب؛ الحركة تبقى محفوظة في السجل ولا تُحذف. */
    suspend fun voidMovement(movementId: Long, reason: String) = db.withTransaction {
        if (reason.isBlank()) throw InventoryException("يجب كتابة سبب الإلغاء")
        val m = dao.getMovement(movementId) ?: throw InventoryException("الحركة غير موجودة")
        val shift = dao.getShift(m.shiftId) ?: throw InventoryException("الخفارة غير موجودة")
        if (shift.status != ShiftStatus.OPEN) throw InventoryException("لا يمكن تعديل حركات خفارة مغلقة؛ استخدم تسوية في الخفارة الحالية")
        val available = availableFor(m.shiftId, m.classificationId)
        BalanceCalculator.validateVoid(m.toLite(), available)?.let { throw InventoryException(it) }
        dao.updateMovement(m.copy(voided = true, voidedAt = System.currentTimeMillis(), voidReason = reason.trim()))
        audit(m.shiftId, "إلغاء حركة", "${m.type.arabicName} ${m.quantity} من ${m.category.arabicName} ${m.classificationName}. السبب: ${reason.trim()}")
    }

    // ================= إنهاء الخفارة =================

    suspend fun closeShift(shiftId: Long, handoverToName: String, notes: String) = db.withTransaction {
        val shift = dao.getShift(shiftId) ?: throw InventoryException("الخفارة غير موجودة")
        if (shift.status != ShiftStatus.OPEN) throw InventoryException("الخفارة مغلقة مسبقاً")
        val rows = currentRows(shiftId)
        val negative = BalanceCalculator.negativeRows(rows)
        if (negative.isNotEmpty()) {
            throw InventoryException("يوجد رصيد سالب في: " + negative.joinToString { "${it.category.arabicName} ${it.name}" })
        }
        dao.insertClosings(rows.map {
            ClosingBalanceEntity(
                shiftId = shiftId,
                classificationId = it.classificationId,
                category = it.category,
                name = it.name,
                sortOrder = it.sortOrder,
                opening = it.opening,
                added = it.added,
                dispensed = it.dispensed,
                adjustment = it.adjustment,
                closing = it.closing
            )
        })
        dao.updateShift(
            shift.copy(
                status = ShiftStatus.CLOSED,
                closedAt = System.currentTimeMillis(),
                handoverToName = handoverToName.trim(),
                closingNotes = notes.trim()
            )
        )
        val grand = BalanceCalculator.grandTotal(rows)
        audit(shiftId, "إنهاء خفارة", "تم اعتماد الجرد النهائي. المستلم التالي: ${handoverToName.ifBlank { "-" }}. إجمالي الرصيد النهائي: ${grand.closing}")
    }

    // ================= التقارير =================

    suspend fun getReport(shiftId: Long): ReportData {
        val shift = dao.getShift(shiftId) ?: throw InventoryException("الخفارة غير موجودة")
        val rows = if (shift.status == ShiftStatus.CLOSED) {
            dao.getClosings(shiftId).map {
                BalanceRow(it.classificationId, it.category, it.name, it.sortOrder, it.opening, it.added, it.dispensed, it.adjustment)
            }.sortedWith(compareBy<BalanceRow>({ it.category.ordinal }, { it.sortOrder }, { it.classificationId }))
        } else currentRows(shiftId)
        return ReportData(
            hospitalName = dao.getSetting(KEY_HOSPITAL) ?: "",
            shift = shift,
            rows = rows,
            openings = dao.getOpenings(shiftId),
            movements = dao.getMovements(shiftId)
        )
    }

    // ================= الإعدادات =================

    suspend fun setHospitalName(name: String) {
        dao.putSetting(SettingEntity(KEY_HOSPITAL, name.trim()))
        audit(null, "تعديل الإعدادات", "اسم المستشفى/المصرف: ${name.trim()}")
    }

    suspend fun addClassification(category: Category, name: String) = db.withTransaction {
        val clean = name.trim()
        if (clean.isEmpty()) throw InventoryException("اكتب اسم التصنيف")
        val existing = dao.getClassifications().filter { it.category == category }
        if (existing.any { it.name.equals(clean, ignoreCase = true) }) throw InventoryException("هذا التصنيف موجود مسبقاً في ${category.arabicName}")
        dao.insertClassification(ClassificationEntity(category = category, name = clean, sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1))
        audit(null, "إضافة تصنيف", "${category.arabicName}: $clean")
    }

    suspend fun setClassificationActive(id: Long, active: Boolean) = db.withTransaction {
        val c = dao.getClassification(id) ?: return@withTransaction
        dao.updateClassification(c.copy(active = active))
        audit(null, if (active) "تفعيل تصنيف" else "إيقاف تصنيف", "${c.category.arabicName}: ${c.name}")
    }

    suspend fun renameClassification(id: Long, newName: String) = db.withTransaction {
        val clean = newName.trim()
        if (clean.isEmpty()) throw InventoryException("اكتب اسم التصنيف")
        val c = dao.getClassification(id) ?: return@withTransaction
        if (dao.getClassifications().any { it.category == c.category && it.id != id && it.name.equals(clean, true) }) {
            throw InventoryException("هذا الاسم مستخدم مسبقاً")
        }
        dao.updateClassification(c.copy(name = clean))
        audit(null, "تعديل اسم تصنيف", "${c.category.arabicName}: ${c.name} ← $clean")
    }

    /** نقل التصنيف للأعلى (-1) أو للأسفل (+1) ضمن صنفه. */
    suspend fun moveClassification(id: Long, direction: Int) = db.withTransaction {
        val c = dao.getClassification(id) ?: return@withTransaction
        val list = dao.getClassifications().filter { it.category == c.category }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val index = list.indexOfFirst { it.id == id }
        val target = index + direction
        if (target !in list.indices) return@withTransaction
        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { i, item -> if (item.sortOrder != i) dao.updateClassification(item.copy(sortOrder = i)) }
    }

    suspend fun audit(shiftId: Long?, action: String, details: String) {
        dao.insertAudit(AuditLogEntity(at = System.currentTimeMillis(), shiftId = shiftId, action = action, details = details))
    }
}
