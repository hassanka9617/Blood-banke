package com.bloodbank.inventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bloodbank.inventory.data.ReceivingDraft
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.ui.Format
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.EmptyMessage
import com.bloodbank.inventory.ui.components.NumberField
import com.bloodbank.inventory.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun ReceiveShiftScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var draft by remember { mutableStateOf<ReceivingDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val qty = remember { mutableStateMapOf<Long, String>() }
    var shiftNumber by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf("") }
    var deliverer by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showReview by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (vm.openShift.value != null) {
            error = "توجد خفارة مفتوحة حالياً. أنهِ الخفارة الحالية قبل استلام خفارة جديدة."
            return@LaunchedEffect
        }
        vm.prepareReceiving { r ->
            r.onSuccess { d ->
                draft = d
                shiftNumber = d.suggestedShiftNumber
                deliverer = d.previousShift?.let { p -> p.handoverToName.ifBlank { p.receiverName } } ?: ""
                // تعبئة تلقائية من الرصيد الختامي للخفارة السابقة، ويمكن تعديلها حسب الجرد الفعلي
                d.classifications.forEach { c -> qty[c.id] = d.expected?.get(c.id)?.toString() ?: "" }
            }.onFailure { error = it.message }
        }
    }

    ScreenScaffold("استلام الخفارة", onBack, snackbar) { padding ->
        val d = draft
        when {
            error != null -> EmptyMessage(error!!, padding)
            d == null -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp)); CircularProgressIndicator()
            }
            else -> {
                val actual = d.classifications.associate { it.id to (qty[it.id]?.toIntOrNull() ?: 0) }
                val diffs = d.expected?.let { BalanceCalculator.differences(it, actual) } ?: emptyMap()

                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("بيانات الخفارة", fontWeight = FontWeight.Bold)
                            Text("التاريخ والوقت: ${Format.dateTime(System.currentTimeMillis())} (يُسجل تلقائياً عند الاعتماد)")
                            OutlinedTextField(shiftNumber, { shiftNumber = it }, label = { Text("رقم الخفارة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(receiver, { receiver = it }, label = { Text("اسم الموظف المستلم (أنت)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(deliverer, { deliverer = it }, label = { Text("اسم الموظف المسلِّم (السابق)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (d.previousShift != null) {
                        Text(
                            "تمت تعبئة الأعداد تلقائياً من الرصيد الختامي للخفارة السابقة (${d.previousShift.shiftNumber.ifBlank { "#" + d.previousShift.id }}). عدّل أي رقم حسب الجرد الفعلي.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text("هذه أول خفارة في التطبيق: أدخل الأعداد التي استلمتها فعلياً.", color = MaterialTheme.colorScheme.secondary)
                    }

                    Category.entries.forEach { cat ->
                        val classes = d.classifications.filter { it.category == cat }
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cat.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("المجموع: ${classes.sumOf { actual[it.id] ?: 0 }}", fontWeight = FontWeight.Bold)
                                }
                                if (classes.isEmpty()) Text("لا توجد تصنيفات مفعلة لهذا الصنف (من الإعدادات)")
                                classes.forEach { c ->
                                    val exp = d.expected?.get(c.id)
                                    val diff = diffs[c.id]
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.name, Modifier.width(84.dp), fontWeight = FontWeight.Bold)
                                        NumberField(
                                            value = qty[c.id] ?: "",
                                            onValueChange = { qty[c.id] = it },
                                            label = "العدد",
                                            modifier = Modifier.weight(1f),
                                            imeAction = ImeAction.Next,
                                            onNext = { focus.moveFocus(FocusDirection.Down) },
                                            isError = diff != null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.width(90.dp)) {
                                            if (exp != null) Text("المتوقع: $exp", style = MaterialTheme.typography.bodySmall)
                                            if (diff != null) Text("الفرق: ${Format.signed(diff)}", color = Color.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (diffs.isNotEmpty()) {
                        Text(
                            "يوجد ${diffs.size} فرق بين العدد الفعلي والمتوقع. يجب توضيح السبب قبل الاعتماد.",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEBEE)).padding(8.dp)
                        )
                    }
                    OutlinedTextField(
                        note, { note = it },
                        label = { Text(if (diffs.isNotEmpty()) "توثيق الفروقات (إلزامي)" else "ملاحظات الاستلام (اختياري)") },
                        isError = diffs.isNotEmpty() && note.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    BigButton("مراجعة الأعداد قبل الاعتماد") {
                        if (diffs.isNotEmpty() && note.isBlank()) {
                            scope.launch { snackbar.showSnackbar("اكتب توثيق الفروقات أولاً") }
                        } else showReview = true
                    }
                    Spacer(Modifier.height(24.dp))
                }

                if (showReview) {
                    AlertDialog(
                        onDismissRequest = { if (!saving) showReview = false },
                        title = { Text("مراجعة الاستلام") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text("رقم الخفارة: ${shiftNumber.ifBlank { "-" }}")
                                Text("المستلم: ${receiver.ifBlank { "-" }} — المسلِّم: ${deliverer.ifBlank { "-" }}")
                                Spacer(Modifier.height(8.dp))
                                Category.entries.forEach { cat ->
                                    val classes = d.classifications.filter { it.category == cat }
                                    Text("${cat.arabicName}: ${classes.sumOf { actual[it.id] ?: 0 }}", fontWeight = FontWeight.Bold)
                                    Text(classes.joinToString("   ") { "${it.name}=${actual[it.id] ?: 0}" }, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (diffs.isNotEmpty()) Text("عدد الفروقات: ${diffs.size}", color = Color.Red)
                                Text("بعد الاعتماد يُحفظ هذا الجرد كرصيد افتتاحي ثابت للخفارة.", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = !saving, onClick = {
                                saving = true
                                vm.confirmReceiving(shiftNumber, receiver, deliverer, actual, note) { r ->
                                    saving = false
                                    r.onSuccess { showReview = false; onDone() }
                                        .onFailure { e -> showReview = false; scope.launch { snackbar.showSnackbar(e.message ?: "خطأ") } }
                                }
                            }) { Text(if (saving) "جارٍ الحفظ..." else "اعتماد الاستلام") }
                        },
                        dismissButton = { TextButton(enabled = !saving, onClick = { showReview = false }) { Text("رجوع للتعديل") } }
                    )
                }
            }
        }
    }
}
