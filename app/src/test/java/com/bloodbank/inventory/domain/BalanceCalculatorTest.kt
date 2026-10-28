package com.bloodbank.inventory.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceCalculatorTest {

    // تصنيفات تجريبية: كريات حمراء A+ و B+ و O+ ، وصفائح A
    private val aPos = ClassInfo(1, Category.RBC, "A+", 0)
    private val bPos = ClassInfo(2, Category.RBC, "B+", 2)
    private val oPos = ClassInfo(3, Category.RBC, "O+", 4)
    private val pltA = ClassInfo(10, Category.PLATELETS, "A", 0)
    private val all = listOf(aPos, bPos, oPos, pltA)

    private fun rowsFor(openings: Map<Long, Int>, movements: List<MovementLite>) =
        BalanceCalculator.computeRows(all, openings, movements).associateBy { it.classificationId }

    @Test
    fun `example from request A+ 50 plus 10 minus 15 equals 45`() {
        val rows = rowsFor(
            mapOf(1L to 50),
            listOf(
                MovementLite(1, MovementType.ADD, 10),
                MovementLite(1, MovementType.DISPENSE, 15)
            )
        )
        val a = rows.getValue(1)
        assertEquals(50, a.opening)
        assertEquals(10, a.added)
        assertEquals(15, a.dispensed)
        assertEquals(45, a.closing)
    }

    @Test
    fun `table example from request`() {
        val rows = rowsFor(
            mapOf(1L to 50, 2L to 70, 3L to 60),
            listOf(
                MovementLite(1, MovementType.ADD, 10), MovementLite(1, MovementType.DISPENSE, 15),
                MovementLite(2, MovementType.ADD, 5), MovementLite(2, MovementType.DISPENSE, 20),
                MovementLite(3, MovementType.DISPENSE, 10)
            )
        )
        assertEquals(45, rows.getValue(1).closing)
        assertEquals(55, rows.getValue(2).closing)
        assertEquals(50, rows.getValue(3).closing)
    }

    @Test
    fun `categories and classifications never mix`() {
        // صفائح A وكريات A+ لهما أرقام مختلفة ولا يؤثر أحدهما في الآخر
        val rows = rowsFor(
            mapOf(1L to 50, 10L to 60),
            listOf(MovementLite(10, MovementType.DISPENSE, 5), MovementLite(1, MovementType.ADD, 3))
        )
        assertEquals(53, rows.getValue(1).closing)
        assertEquals(55, rows.getValue(10).closing)
        assertEquals(0, rows.getValue(2).closing)
        val totals = BalanceCalculator.categoryTotals(rows.values.toList())
        assertEquals(53, totals.getValue(Category.RBC).closing)
        assertEquals(55, totals.getValue(Category.PLATELETS).closing)
        assertEquals(0, totals.getValue(Category.PLASMA).closing)
        assertEquals(0, totals.getValue(Category.CRYO).closing)
        assertEquals(108, BalanceCalculator.grandTotal(rows.values.toList()).closing)
    }

    @Test
    fun `voided movements are ignored`() {
        val rows = rowsFor(
            mapOf(1L to 50),
            listOf(
                MovementLite(1, MovementType.DISPENSE, 15, voided = true),
                MovementLite(1, MovementType.DISPENSE, 5)
            )
        )
        assertEquals(45, rows.getValue(1).closing)
        assertEquals(5, rows.getValue(1).dispensed)
    }

    @Test
    fun `adjustment shown separately and affects closing`() {
        val rows = rowsFor(
            mapOf(1L to 50),
            listOf(MovementLite(1, MovementType.ADJUSTMENT, -2), MovementLite(1, MovementType.ADD, 4))
        )
        val a = rows.getValue(1)
        assertEquals(-2, a.adjustment)
        assertEquals(4, a.added)
        assertEquals(52, a.closing)
    }

    @Test
    fun `dispense larger than available is rejected`() {
        assertNotNull(BalanceCalculator.validateMovement(MovementType.DISPENSE, 46, 45))
        assertNull(BalanceCalculator.validateMovement(MovementType.DISPENSE, 45, 45))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.DISPENSE, 0, 45))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.DISPENSE, -3, 45))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.DISPENSE, 1, 0))
    }

    @Test
    fun `add must be positive`() {
        assertNull(BalanceCalculator.validateMovement(MovementType.ADD, 10, 0))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.ADD, 0, 0))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.ADD, -1, 0))
    }

    @Test
    fun `adjustment needs reason and cannot go negative`() {
        assertNotNull(BalanceCalculator.validateMovement(MovementType.ADJUSTMENT, -2, 10, ""))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.ADJUSTMENT, 0, 10, "سبب"))
        assertNotNull(BalanceCalculator.validateMovement(MovementType.ADJUSTMENT, -11, 10, "سبب"))
        assertNull(BalanceCalculator.validateMovement(MovementType.ADJUSTMENT, -10, 10, "سبب"))
        assertNull(BalanceCalculator.validateMovement(MovementType.ADJUSTMENT, 3, 0, "سبب"))
    }

    @Test
    fun `balance before and after`() {
        assertEquals(60, BalanceCalculator.balanceAfter(MovementType.ADD, 10, 50))
        assertEquals(45, BalanceCalculator.balanceAfter(MovementType.DISPENSE, 15, 60))
        assertEquals(43, BalanceCalculator.balanceAfter(MovementType.ADJUSTMENT, -2, 45))
    }

    @Test
    fun `voiding an addition cannot make balance negative`() {
        val add = MovementLite(1, MovementType.ADD, 10)
        assertNotNull(BalanceCalculator.validateVoid(add, 5))
        assertNull(BalanceCalculator.validateVoid(add, 10))
        // إلغاء صرف يزيد الرصيد دائماً فهو مسموح
        assertNull(BalanceCalculator.validateVoid(MovementLite(1, MovementType.DISPENSE, 10), 0))
        assertNotNull(BalanceCalculator.validateVoid(MovementLite(1, MovementType.DISPENSE, 10, voided = true), 0))
    }

    @Test
    fun `carry over uses previous closing and does not double count`() {
        // الخفارة الأولى
        val shift1 = rowsFor(
            mapOf(1L to 50, 2L to 70),
            listOf(MovementLite(1, MovementType.ADD, 10), MovementLite(1, MovementType.DISPENSE, 15))
        )
        val closing1 = shift1.mapValues { it.value.closing }
        // الخفارة الثانية: الرصيد المتوقع = ختامي الأولى، والحركات تبدأ من الصفر
        val expected = BalanceCalculator.expectedOpenings(closing1, all.map { it.id })
        assertEquals(45, expected[1L])
        assertEquals(70, expected[2L])
        assertEquals(0, expected[10L])
        val shift2 = rowsFor(expected, emptyList()).getValue(1)
        assertEquals(45, shift2.opening)
        assertEquals(0, shift2.added)
        assertEquals(0, shift2.dispensed)
        assertEquals(45, shift2.closing)
    }

    @Test
    fun `differences between expected and actual count`() {
        val expected = mapOf(1L to 45, 2L to 70, 3L to 0)
        val actual = mapOf(1L to 44, 2L to 70, 3L to 2)
        val diff = BalanceCalculator.differences(expected, actual)
        assertEquals(mapOf(1L to -1, 3L to 2), diff)
        assertTrue(BalanceCalculator.differences(expected, expected).isEmpty())
    }

    @Test
    fun `negative rows detected`() {
        val rows = BalanceCalculator.computeRows(all, mapOf(1L to 1), listOf(MovementLite(1, MovementType.DISPENSE, 2)))
        assertEquals(listOf(1L), BalanceCalculator.negativeRows(rows).map { it.classificationId })
    }

    @Test
    fun `full shift simulation with many random movements stays consistent`() {
        val random = java.util.Random(42)
        val openings = all.associate { it.id to random.nextInt(50) }
        val balance = openings.toMutableMap()
        val movements = mutableListOf<MovementLite>()
        repeat(500) {
            val c = all[random.nextInt(all.size)].id
            val type = MovementType.entries[random.nextInt(2)]
            val qty = 1 + random.nextInt(8)
            if (BalanceCalculator.validateMovement(type, qty, balance.getValue(c)) == null) {
                movements += MovementLite(c, type, qty)
                balance[c] = BalanceCalculator.balanceAfter(type, qty, balance.getValue(c))
            }
        }
        val rows = BalanceCalculator.computeRows(all, openings, movements)
        rows.forEach { assertEquals(balance.getValue(it.classificationId), it.closing) }
        assertTrue(BalanceCalculator.negativeRows(rows).isEmpty())
    }
}
