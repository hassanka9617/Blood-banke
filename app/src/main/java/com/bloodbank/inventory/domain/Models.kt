package com.bloodbank.inventory.domain

/** الأصناف الأربعة الرئيسية. كل صنف له تصنيفات مستقلة تماماً ولا تختلط حساباته بغيره. */
enum class Category(val arabicName: String, val englishName: String, val defaultClassifications: List<String>) {
    RBC("كريات الدم الحمراء", "PRBC", listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")),
    PLATELETS(
        "الصفائح الدموية", "Platelets",
        listOf(
            "A", "B", "O", "AB",
            "تريما A", "تريما B", "تريما O", "تريما AB",
            "ريفيس A", "ريفيس B", "ريفيس O", "ريفيس AB"
        )
    ),
    PLASMA("البلازما", "Plasma", listOf("A", "B", "O", "AB")),
    CRYO("الراسب البردي", "Cryo", listOf("A", "B", "O", "AB"));

    val displayName: String get() = "$arabicName ($englishName)"
}

/** أنواع الحركات أثناء الخفارة. */
enum class MovementType(val arabicName: String) {
    ADD("إضافة"),
    DISPENSE("صرف"),
    /** تسوية معتمدة: الكمية موجبة (زيادة) أو سالبة (نقص) ويجب ذكر السبب. */
    ADJUSTMENT("تسوية")
}

enum class ShiftStatus { OPEN, CLOSED }

/** تصنيف (فصيلة) واحد ضمن صنف معين. */
data class ClassInfo(
    val id: Long,
    val category: Category,
    val name: String,
    val sortOrder: Int
)

/** نسخة مبسطة من الحركة تكفي للحساب. */
data class MovementLite(
    val classificationId: Long,
    val type: MovementType,
    val quantity: Int,
    val voided: Boolean = false
)

/** سطر رصيد لتصنيف واحد داخل صنف واحد. */
data class BalanceRow(
    val classificationId: Long,
    val category: Category,
    val name: String,
    val sortOrder: Int,
    val opening: Int,
    val added: Int,
    val dispensed: Int,
    val adjustment: Int
) {
    /** الرصيد النهائي = الافتتاحي + الإضافات - المصروفات (+ التسويات المعتمدة إن وجدت). */
    val closing: Int get() = opening + added - dispensed + adjustment
}

/** إجماليات صنف كامل (أو المخزون كله عندما يكون category = null). */
data class Totals(
    val category: Category?,
    val opening: Int,
    val added: Int,
    val dispensed: Int,
    val adjustment: Int
) {
    val closing: Int get() = opening + added - dispensed + adjustment
}

class InventoryException(message: String) : Exception(message)
