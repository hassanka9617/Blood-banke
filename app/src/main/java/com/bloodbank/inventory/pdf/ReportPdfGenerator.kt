package com.bloodbank.inventory.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.bloodbank.inventory.data.ReportData
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.MovementType
import com.bloodbank.inventory.ui.Format
import java.io.File
import java.io.FileOutputStream

/**
 * ينشئ تقرير الخفارة بصيغة PDF على ورق A4 (595 × 842 نقطة) باتجاه من اليمين إلى اليسار.
 * يستخدم StaticLayout حتى تُرسم الحروف العربية متصلة وبالاتجاه الصحيح.
 */
class ReportPdfGenerator(private val context: Context) {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 36f
    private val contentWidth = pageWidth - 2 * margin
    private val bottomLimit = pageHeight - margin - 24f

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var pageNumber = 0
    private var y = margin

    private val linePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = Color.DKGRAY }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }

    fun generate(data: ReportData): File {
        newPage()
        drawHeader(data)

        Category.entries.forEach { cat ->
            val rows = data.rows.filter { it.category == cat }
            drawCategoryTable(cat, rows)
        }
        drawSummary(data)
        drawDiscrepancies(data)
        drawAdjustments(data)
        drawMovements(data)
        drawNotesAndSignatures(data)
        finishPage()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeNumber = data.shift.shiftNumber.ifBlank { data.shift.id.toString() }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "shift_report_${safeNumber}_${Format.fileStamp(data.shift.closedAt ?: System.currentTimeMillis())}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ------------------------------------------------------------------ الصفحات

    private fun newPage() {
        finishPage()
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val p = doc.startPage(info)
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun finishPage() {
        val p = page ?: return
        val footer = "صفحة $pageNumber  —  تم الإنشاء: ${Format.dateTime(System.currentTimeMillis())}  —  للتوثيق وحساب المخزون فقط ولا يغني عن إجراءات التوافق المعتمدة"
        drawText(footer, margin, pageHeight - margin - 10f, contentWidth, 7f, color = Color.GRAY, align = Layout.Alignment.ALIGN_CENTER)
        doc.finishPage(p)
        page = null
    }

    private fun ensureSpace(height: Float) {
        if (y + height > bottomLimit) newPage()
    }

    // ------------------------------------------------------------------ أدوات الرسم

    private fun paint(size: Float, bold: Boolean, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.color = color
    }

    private fun layout(text: String, width: Float, size: Float, bold: Boolean, color: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint(size, bold, color), width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(false)
            .build()

    /** يرسم نصاً في المربع (left, top, width) ويعيد ارتفاعه. ALIGN_NORMAL مع نص عربي = محاذاة لليمين. */
    private fun drawText(
        text: String, left: Float, top: Float, width: Float, size: Float,
        bold: Boolean = false, color: Int = Color.BLACK, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        val l = layout(text, width, size, bold, color, align)
        canvas.save()
        canvas.translate(left, top)
        l.draw(canvas)
        canvas.restore()
        return l.height.toFloat()
    }

    private fun textHeight(text: String, width: Float, size: Float, bold: Boolean = false) =
        layout(text, width, size, bold, Color.BLACK, Layout.Alignment.ALIGN_NORMAL).height.toFloat()

    /** فقرة كاملة العرض محاذاة لليمين. */
    private fun paragraph(text: String, size: Float = 10f, bold: Boolean = false, color: Int = Color.BLACK, gap: Float = 3f) {
        val h = textHeight(text, contentWidth, size, bold)
        ensureSpace(h)
        drawText(text, margin, y, contentWidth, size, bold, color)
        y += h + gap
    }

    private fun sectionTitle(text: String) {
        ensureSpace(75f)
        y += 6f
        fillPaint.color = Color.rgb(183, 28, 28)
        canvas.drawRect(RectF(margin, y, margin + contentWidth, y + 20f), fillPaint)
        drawText(text, margin + 6f, y + 4f, contentWidth - 12f, 11f, bold = true, color = Color.WHITE)
        y += 22f
    }

    /**
     * جدول من اليمين إلى اليسار: العمود الأول في القائمة يُرسم في أقصى اليمين.
     * @param widths نسب عرض الأعمدة
     */
    private inner class Table(private val headers: List<String>, widths: List<Float>, private val size: Float = 9f) {
        private val colWidths = widths.map { it / widths.sum() * contentWidth }

        fun header() = row(headers, header = true)

        fun row(cells: List<String>, header: Boolean = false, highlight: Boolean = false) {
            val heights = cells.mapIndexed { i, t -> textHeight(t, colWidths[i] - 6f, size, header || highlight) }
            val h = (heights.maxOrNull() ?: 0f) + 8f
            if (y + h > bottomLimit) {
                newPage()
                if (!header) row(headers, header = true)
            }
            var right = margin + contentWidth
            cells.forEachIndexed { i, text ->
                val w = colWidths[i]
                val rect = RectF(right - w, y, right, y + h)
                if (header || highlight) {
                    fillPaint.color = if (header) Color.rgb(238, 238, 238) else Color.rgb(253, 236, 236)
                    canvas.drawRect(rect, fillPaint)
                }
                canvas.drawRect(rect, linePaint)
                drawText(text, rect.left + 3f, y + (h - heights[i]) / 2f, w - 6f, size, header || highlight, align = Layout.Alignment.ALIGN_CENTER)
                right -= w
            }
            y += h
        }
    }

    // ------------------------------------------------------------------ أقسام التقرير

    private fun drawHeader(data: ReportData) {
        val s = data.shift
        drawText(data.hospitalName.ifBlank { "مصرف الدم" }, margin, y, contentWidth, 16f, bold = true, align = Layout.Alignment.ALIGN_CENTER)
        y += 24f
        drawText("تقرير جرد وتسليم الخفارة", margin, y, contentWidth, 13f, bold = true, color = Color.rgb(183, 28, 28), align = Layout.Alignment.ALIGN_CENTER)
        y += 24f

        val t = Table(listOf("البيان", "القيمة", "البيان", "القيمة"), listOf(1.1f, 1.4f, 1.1f, 1.4f))
        t.row(listOf("رقم الخفارة", s.shiftNumber.ifBlank { "-" }, "تاريخ الخفارة", Format.date(s.openedAt)))
        t.row(listOf("وقت الاستلام", Format.dateTime(s.openedAt), "وقت الإنهاء", Format.dateTime(s.closedAt)))
        t.row(listOf("الموظف المسلِّم", s.receiverName.ifBlank { "-" }, "الموظف المستلم", s.handoverToName.ifBlank { "-" }))
        t.row(listOf("استُلمت الخفارة من", s.delivererName.ifBlank { "-" }, "حالة الخفارة", if (s.closedAt != null) "معتمدة ومغلقة" else "مفتوحة (مسودة)"))
        y += 4f
    }

    private fun drawCategoryTable(cat: Category, rows: List<com.bloodbank.inventory.domain.BalanceRow>) {
        sectionTitle(cat.displayName)
        val hasAdjust = rows.any { it.adjustment != 0 }
        val headers = mutableListOf("الفصيلة / التصنيف", "الرصيد المستلم", "الإضافات", "المصروفات")
        if (hasAdjust) headers += "التسوية"
        headers += "الرصيد النهائي"
        val t = Table(headers, List(headers.size) { if (it == 0) 1.3f else 1f })
        t.header()
        if (rows.isEmpty()) {
            t.row(List(headers.size) { if (it == 0) "لا توجد تصنيفات" else "-" })
        }
        rows.forEach { r ->
            val cells = mutableListOf(r.name, r.opening.toString(), r.added.toString(), r.dispensed.toString())
            if (hasAdjust) cells += Format.signed(r.adjustment)
            cells += r.closing.toString()
            t.row(cells)
        }
        val tot = BalanceCalculator.categoryTotals(rows).getValue(cat)
        val totalCells = mutableListOf("إجمالي ${cat.arabicName}", tot.opening.toString(), tot.added.toString(), tot.dispensed.toString())
        if (hasAdjust) totalCells += Format.signed(tot.adjustment)
        totalCells += tot.closing.toString()
        t.row(totalCells, highlight = true)
        y += 4f
    }

    private fun drawSummary(data: ReportData) {
        sectionTitle("الملخص الشامل للمخزون")
        val t = Table(listOf("الصنف", "المستلم", "الإضافات", "المصروفات", "التسوية", "الرصيد النهائي"), listOf(1.6f, 1f, 1f, 1f, 1f, 1f))
        t.header()
        val totals = BalanceCalculator.categoryTotals(data.rows)
        Category.entries.forEach { c ->
            val x = totals.getValue(c)
            t.row(listOf(c.arabicName, x.opening.toString(), x.added.toString(), x.dispensed.toString(), Format.signed(x.adjustment), x.closing.toString()))
        }
        val g = BalanceCalculator.grandTotal(data.rows)
        t.row(listOf("المجموع الكلي", g.opening.toString(), g.added.toString(), g.dispensed.toString(), Format.signed(g.adjustment), g.closing.toString()), highlight = true)
        paragraph("ملاحظة: المجموع الكلي للعرض فقط؛ كل صنف وفصيلة يُحسب ويُصرف بشكل مستقل.", 8f, color = Color.DKGRAY)
    }

    private fun drawDiscrepancies(data: ReportData) {
        val diffs = data.openings.filter { it.expected != null && it.expected != it.actual }
        if (diffs.isEmpty() && data.shift.receiveNote.isBlank()) return
        sectionTitle("فروقات الجرد عند الاستلام")
        if (diffs.isNotEmpty()) {
            val t = Table(listOf("الصنف", "الفصيلة", "المتوقع من الخفارة السابقة", "العدد الفعلي", "الفرق"), listOf(1.5f, 1f, 1.5f, 1f, 0.8f))
            t.header()
            diffs.sortedWith(compareBy({ it.category.ordinal }, { it.sortOrder })).forEach {
                t.row(listOf(it.category.arabicName, it.name, it.expected.toString(), it.actual.toString(), Format.signed(it.actual - (it.expected ?: 0))))
            }
        }
        if (data.shift.receiveNote.isNotBlank()) paragraph("التوثيق: ${data.shift.receiveNote}", 9f)
    }

    private fun drawAdjustments(data: ReportData) {
        val adj = data.movements.filter { it.type == MovementType.ADJUSTMENT && !it.voided }
        if (adj.isEmpty()) return
        sectionTitle("التسويات المعتمدة وأثرها على الرصيد")
        val t = Table(listOf("الوقت", "الصنف", "الفصيلة", "الأثر", "السبب"), listOf(0.9f, 1.3f, 0.8f, 0.6f, 2.4f))
        t.header()
        adj.forEach { t.row(listOf(Format.dateTime(it.createdAt), it.category.arabicName, it.classificationName, Format.signed(it.quantity), it.note)) }
    }

    private fun drawMovements(data: ReportData) {
        sectionTitle("سجل حركات الخفارة (${data.movements.size} حركة)")
        if (data.movements.isEmpty()) {
            paragraph("لم تُسجل أي حركة خلال الخفارة.", 9f)
            return
        }
        val t = Table(listOf("الوقت", "النوع", "الصنف", "الفصيلة", "الكمية", "ملاحظات"), listOf(0.7f, 0.7f, 1.3f, 0.7f, 0.6f, 2.2f), size = 8f)
        t.header()
        data.movements.forEach { m ->
            val note = buildString {
                append(m.note)
                if (m.voided) {
                    if (isNotEmpty()) append(" — ")
                    append("ملغاة: ${m.voidReason}")
                }
            }
            val qty = if (m.type == MovementType.ADJUSTMENT) Format.signed(m.quantity) else m.quantity.toString()
            t.row(listOf(Format.time(m.createdAt), m.type.arabicName + if (m.voided) " (ملغاة)" else "", m.category.arabicName, m.classificationName, qty, note.ifBlank { "-" }))
        }
    }

    private fun drawNotesAndSignatures(data: ReportData) {
        sectionTitle("ملاحظات")
        val notesText = data.shift.closingNotes.ifBlank { " " }
        val h = maxOf(60f, textHeight(notesText, contentWidth - 12f, 10f) + 16f)
        ensureSpace(h)
        canvas.drawRect(RectF(margin, y, margin + contentWidth, y + h), linePaint)
        drawText(notesText, margin + 6f, y + 6f, contentWidth - 12f, 10f)
        y += h + 14f

        ensureSpace(110f)
        val boxW = (contentWidth - 20f) / 2f
        val rightBox = RectF(margin + contentWidth - boxW, y, margin + contentWidth, y + 95f)
        val leftBox = RectF(margin, y, margin + boxW, y + 95f)
        signatureBox(rightBox, "الموظف المسلِّم", data.shift.receiverName)
        signatureBox(leftBox, "الموظف المستلم", data.shift.handoverToName)
        y += 105f
    }

    private fun signatureBox(r: RectF, title: String, name: String) {
        canvas.drawRect(r, linePaint)
        drawText(title, r.left + 6f, r.top + 6f, r.width() - 12f, 11f, bold = true)
        drawText("الاسم: ${name.ifBlank { "........................................" }}", r.left + 6f, r.top + 28f, r.width() - 12f, 10f)
        drawText("التوقيع: ........................................", r.left + 6f, r.top + 50f, r.width() - 12f, 10f)
        drawText("التاريخ والوقت: ..............................", r.left + 6f, r.top + 72f, r.width() - 12f, 10f)
    }
}
