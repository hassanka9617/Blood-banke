package com.bloodbank.inventory.domain

/**
 * كل الحسابات في مكان واحد، بدون أي اعتماد على أندرويد، حتى يمكن اختبارها بسهولة.
 * المعادلة لكل تصنيف وبشكل مستقل:
 * الرصيد النهائي = الرصيد الافتتاحي + مجموع الإضافات - مجموع المصروفات + التسويات المعتمدة
 */
object BalanceCalculator {

    /**
     * يحسب سطر رصيد لكل تصنيف.
     * @param classifications التصنيفات المطلوب عرضها
     * @param openings الرصيد الافتتاحي لكل تصنيف (المفتاح = رقم التصنيف)
     * @param movements حركات الخفارة الحالية فقط
     */
    fun computeRows(
        classifications: List<ClassInfo>,
        openings: Map<Long, Int>,
        movements: List<MovementLite>
    ): List<BalanceRow> {
        val active = movements.filter { !it.voided }
        val byClass = active.groupBy { it.classificationId }
        return classifications
            .sortedWith(compareBy<ClassInfo>({ it.category.ordinal }, { it.sortOrder }, { it.id }))
            .map { c ->
                val list = byClass[c.id].orEmpty()
                BalanceRow(
                    classificationId = c.id,
                    category = c.category,
                    name = c.name,
                    sortOrder = c.sortOrder,
                    opening = openings[c.id] ?: 0,
                    added = list.filter { it.type == MovementType.ADD }.sumOf { it.quantity },
                    dispensed = list.filter { it.type == MovementType.DISPENSE }.sumOf { it.quantity },
                    adjustment = list.filter { it.type == MovementType.ADJUSTMENT }.sumOf { it.quantity }
                )
            }
    }

    /** إجمالي كل صنف على حدة. */
    fun categoryTotals(rows: List<BalanceRow>): Map<Category, Totals> =
        Category.entries.associateWith { cat -> sum(cat, rows.filter { it.category == cat }) }

    /** الملخص الشامل للمخزون كله (للعرض فقط؛ لا يُستخدم لأي قرار صرف). */
    fun grandTotal(rows: List<BalanceRow>): Totals = sum(null, rows)

    private fun sum(category: Category?, rows: List<BalanceRow>) = Totals(
        category = category,
        opening = rows.sumOf { it.opening },
        added = rows.sumOf { it.added },
        dispensed = rows.sumOf { it.dispensed },
        adjustment = rows.sumOf { it.adjustment }
    )

    /**
     * يتحقق من صحة حركة جديدة قبل حفظها. يعيد رسالة خطأ بالعربية أو null إذا كانت صحيحة.
     * @param available الرصيد الحالي لنفس التصنيف قبل الحركة
     */
    fun validateMovement(type: MovementType, quantity: Int, available: Int, note: String = ""): String? {
        return when (type) {
            MovementType.ADD ->
                if (quantity <= 0) "يجب أن تكون الكمية المضافة أكبر من صفر" else null
            MovementType.DISPENSE -> when {
                quantity <= 0 -> "يجب أن تكون الكمية المصروفة أكبر من صفر"
                quantity > available -> "لا يمكن صرف $quantity وحدة؛ الرصيد المتاح $available فقط"
                else -> null
            }
            MovementType.ADJUSTMENT -> when {
                quantity == 0 -> "قيمة التسوية لا يمكن أن تكون صفراً"
                note.isBlank() -> "يجب كتابة سبب التسوية"
                available + quantity < 0 -> "التسوية تجعل الرصيد سالباً (${available + quantity})؛ غير مسموح"
                else -> null
            }
        }
    }

    /** الرصيد بعد تطبيق الحركة (للعرض قبل الحفظ). */
    fun balanceAfter(type: MovementType, quantity: Int, available: Int): Int = when (type) {
        MovementType.ADD -> available + quantity
        MovementType.DISPENSE -> available - quantity
        MovementType.ADJUSTMENT -> available + quantity
    }

    /** الأثر الذي تُحدثه الحركة على الرصيد (موجب أو سالب). */
    fun effect(type: MovementType, quantity: Int): Int = balanceAfter(type, quantity, 0)

    /**
     * هل يمكن إلغاء حركة دون أن يصبح الرصيد سالباً؟
     * إلغاء إضافة 10 وحدات يتطلب أن يكون الرصيد الحالي 10 على الأقل.
     */
    fun validateVoid(movement: MovementLite, available: Int): String? {
        if (movement.voided) return "هذه الحركة ملغاة مسبقاً"
        val after = available - effect(movement.type, movement.quantity)
        return if (after < 0) "لا يمكن إلغاء الحركة لأن الرصيد سيصبح سالباً ($after)" else null
    }

    /**
     * الرصيد المتوقع لبداية الخفارة الجديدة = الرصيد الختامي للخفارة السابقة.
     * التصنيفات غير الموجودة في الخفارة السابقة تبدأ بصفر.
     */
    fun expectedOpenings(previousClosings: Map<Long, Int>, classificationIds: Collection<Long>): Map<Long, Int> =
        classificationIds.associateWith { previousClosings[it] ?: 0 }

    /** الفروقات بين العدد الفعلي والمتوقع (الفعلي - المتوقع)، فقط للتصنيفات التي فيها فرق. */
    fun differences(expected: Map<Long, Int>, actual: Map<Long, Int>): Map<Long, Int> =
        (expected.keys + actual.keys)
            .associateWith { (actual[it] ?: 0) - (expected[it] ?: 0) }
            .filterValues { it != 0 }

    /** يتأكد أنه لا يوجد أي رصيد سالب قبل اعتماد نهاية الخفارة. */
    fun negativeRows(rows: List<BalanceRow>): List<BalanceRow> = rows.filter { it.closing < 0 }
}
