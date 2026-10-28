package com.bloodbank.inventory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.MovementType
import com.bloodbank.inventory.ui.Format
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.BalanceTable
import com.bloodbank.inventory.ui.components.EmptyMessage
import com.bloodbank.inventory.ui.components.ScreenScaffold
import com.bloodbank.inventory.ui.components.SummaryTable
import kotlinx.coroutines.launch

@Composable
fun CloseShiftScreen(vm: MainViewModel, onBack: () -> Unit, onClosed: (Long) -> Unit) {
    val shift by vm.openShift.collectAsStateWithLifecycle()
    val rows by vm.balances.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var handoverTo by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    ScreenScaffold("إنهاء الخفارة والجرد النهائي", onBack, snackbar) { padding ->
        val s = shift
        if (s == null) {
            EmptyMessage("لا توجد خفارة مفتوحة.", padding); return@ScreenScaffold
        }
        val totals = BalanceCalculator.categoryTotals(rows)
        val negative = BalanceCalculator.negativeRows(rows)
        val active = movements.filter { !it.voided }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("الخفارة: ${s.shiftNumber.ifBlank { "#${s.id}" }} — من ${Format.dateTime(s.openedAt)} حتى الآن", fontWeight = FontWeight.Bold)
            Text("الحركات: ${active.count { it.type == MovementType.ADD }} إضافة، ${active.count { it.type == MovementType.DISPENSE }} صرف")

            Category.entries.forEach { cat ->
                val catRows = rows.filter { it.category == cat }
                Card {
                    Column(Modifier.padding(6.dp)) {
                        Text(cat.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp))
                        BalanceTable(catRows, totals.getValue(cat), closingLabel = "الرصيد النهائي")
                    }
                }
            }
            Card {
                Column(Modifier.padding(6.dp)) {
                    Text("الملخص الشامل", fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                    SummaryTable(totals, BalanceCalculator.grandTotal(rows))
                }
            }

            if (negative.isNotEmpty()) {
                Text("لا يمكن الإنهاء: يوجد رصيد سالب في ${negative.joinToString { it.name }}", color = Color.Red)
            }

            OutlinedTextField(handoverTo, { handoverTo = it }, label = { Text("اسم الموظف المستلم للخفارة التالية") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات التقرير") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = reviewed, onCheckedChange = { reviewed = it })
                Text("راجعتُ الأعداد والحركات وأؤكد صحتها")
            }
            BigButton("اعتماد إنهاء الخفارة", color = Color(0xFF4A148C)) {
                when {
                    !reviewed -> scope.launch { snackbar.showSnackbar("يرجى تأكيد مراجعة الأعداد أولاً") }
                    negative.isNotEmpty() -> scope.launch { snackbar.showSnackbar("يوجد رصيد سالب") }
                    else -> { confirm = true }
                }
            }
            Text(
                "بعد الاعتماد: يُحفظ التقرير، ويصبح الرصيد النهائي هو الرصيد المتوقع للخفارة التالية، ولا يمكن تعديل حركات هذه الخفارة.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }

        if (confirm) {
            AlertDialog(
                onDismissRequest = { if (!saving) confirm = false },
                title = { Text("تأكيد إنهاء الخفارة") },
                text = { Text("إجمالي الرصيد النهائي: ${BalanceCalculator.grandTotal(rows).closing} وحدة. هل تريد اعتماد التسليم؟") },
                confirmButton = {
                    TextButton(enabled = !saving, onClick = {
                        saving = true
                        vm.closeShift(handoverTo, notes) { r ->
                            saving = false
                            confirm = false
                            r.onSuccess { id -> onClosed(id) }
                                .onFailure { e -> scope.launch { snackbar.showSnackbar(e.message ?: "خطأ") } }
                        }
                    }) { Text(if (saving) "جارٍ الاعتماد..." else "اعتماد") }
                },
                dismissButton = { TextButton(enabled = !saving, onClick = { confirm = false }) { Text("إلغاء") } }
            )
        }
    }
}
