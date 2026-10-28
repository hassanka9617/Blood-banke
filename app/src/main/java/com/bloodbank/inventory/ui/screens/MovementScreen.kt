package com.bloodbank.inventory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.MovementType
import com.bloodbank.inventory.ui.Format
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.CategoryTabs
import com.bloodbank.inventory.ui.components.EmptyMessage
import com.bloodbank.inventory.ui.components.NumberField
import com.bloodbank.inventory.ui.components.ScreenScaffold
import com.bloodbank.inventory.ui.theme.AddGreen
import com.bloodbank.inventory.ui.theme.AdjustBlue
import kotlinx.coroutines.launch
import java.util.UUID

/** شاشة واحدة تخدم: إضافة رصيد، صرف، تسوية. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovementScreen(vm: MainViewModel, type: MovementType, onBack: () -> Unit) {
    val shift by vm.openShift.collectAsStateWithLifecycle()
    val rows by vm.balances.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var qtyText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var decrease by rememberSaveable { mutableStateOf(true) }
    // معرف فريد لكل عملية حفظ: يمنع تسجيل الحركة نفسها مرتين إذا ضُغط الزر مرتين
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var saving by remember { mutableStateOf(false) }

    val title = when (type) {
        MovementType.ADD -> "إضافة رصيد"
        MovementType.DISPENSE -> "صرف"
        MovementType.ADJUSTMENT -> "تسوية / تصحيح موثق"
    }
    val accent = when (type) {
        MovementType.ADD -> AddGreen
        MovementType.DISPENSE -> MaterialTheme.colorScheme.primary
        MovementType.ADJUSTMENT -> AdjustBlue
    }

    ScreenScaffold(title, onBack, snackbar) { padding ->
        if (shift == null) {
            EmptyMessage("لا توجد خفارة مفتوحة. استلم الخفارة أولاً.", padding)
            return@ScreenScaffold
        }
        val category = Category.entries[tab]
        val catRows = rows.filter { it.category == category }
        val selected = catRows.firstOrNull { it.classificationId == selectedId }
        val qty = qtyText.toIntOrNull() ?: 0
        val signedQty = if (type == MovementType.ADJUSTMENT && decrease) -qty else qty
        val available = selected?.closing ?: 0
        val after = BalanceCalculator.balanceAfter(type, signedQty, available)
        val validation = if (selected == null) "اختر الفصيلة أو التصنيف"
        else BalanceCalculator.validateMovement(type, signedQty, available, note)

        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoryTabs(tab, { tab = it; selectedId = -1L })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("اختر الفصيلة / التصنيف:", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    catRows.forEach { r ->
                        FilterChip(
                            selected = r.classificationId == selectedId,
                            onClick = { selectedId = r.classificationId },
                            label = { Text("${r.name}  (${r.closing})") }
                        )
                    }
                }
                if (catRows.isEmpty()) Text("لا توجد تصنيفات لهذا الصنف. أضفها من الإعدادات.")

                if (type == MovementType.ADJUSTMENT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = decrease, onClick = { decrease = true }, label = { Text("نقص (-)") })
                        FilterChip(selected = !decrease, onClick = { decrease = false }, label = { Text("زيادة (+)") })
                    }
                }

                NumberField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = if (type == MovementType.ADJUSTMENT) "قيمة التسوية" else "الكمية",
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Done,
                    onNext = { focus.clearFocus() }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (type == MovementType.ADJUSTMENT) "سبب التسوية (إلزامي)" else "ملاحظة (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (selected != null) "${category.arabicName} — ${selected.name}" else category.arabicName,
                            fontWeight = FontWeight.Bold
                        )
                        Text("الرصيد المتاح قبل العملية: $available")
                        Text(
                            "الرصيد بعد العملية: ${if (selected != null && qty > 0) after.toString() else "-"}",
                            fontWeight = FontWeight.Bold,
                            color = if (after < 0) Color.Red else accent
                        )
                        Text("الوقت: يُسجل تلقائياً عند الحفظ (${Format.time(System.currentTimeMillis())})", style = MaterialTheme.typography.bodySmall)
                        if (validation != null && (qty > 0 || selected == null)) {
                            Text(validation, color = Color.Red)
                        }
                    }
                }

                BigButton(if (saving) "جارٍ الحفظ..." else "حفظ $title", color = accent) {
                    if (saving) return@BigButton
                    if (validation != null) {
                        scope.launch { snackbar.showSnackbar(validation) }
                        return@BigButton
                    }
                    val sel = selected ?: return@BigButton
                    saving = true
                    vm.addMovement(sel.classificationId, type, signedQty, note, requestId) { r ->
                        saving = false
                        r.onSuccess { saved ->
                            requestId = UUID.randomUUID().toString()
                            qtyText = ""
                            note = ""
                            val msg = if (saved) "تم الحفظ: ${type.arabicName} ${Format.signed(BalanceCalculator.effect(type, signedQty))} من ${sel.name}. الرصيد الآن $after"
                            else "هذه الحركة محفوظة مسبقاً"
                            scope.launch { snackbar.showSnackbar(msg) }
                        }.onFailure { e -> scope.launch { snackbar.showSnackbar(e.message ?: "خطأ") } }
                    }
                }

                // آخر حركات من هذا النوع في الخفارة
                val recent = movements.filter { it.type == type }.take(8)
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("آخر العمليات في هذه الخفارة", fontWeight = FontWeight.Bold)
                    recent.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${Format.time(m.createdAt)}  ${m.category.arabicName} ${m.classificationName}")
                            Text(
                                (if (m.type == MovementType.ADJUSTMENT) Format.signed(m.quantity) else m.quantity.toString()) + if (m.voided) " (ملغاة)" else "",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
