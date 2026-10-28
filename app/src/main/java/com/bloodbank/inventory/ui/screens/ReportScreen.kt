package com.bloodbank.inventory.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloodbank.inventory.data.ReportData
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.pdf.PdfActions
import com.bloodbank.inventory.pdf.ReportPdfGenerator
import com.bloodbank.inventory.ui.Format
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.BalanceTable
import com.bloodbank.inventory.ui.components.EmptyMessage
import com.bloodbank.inventory.ui.components.ScreenScaffold
import com.bloodbank.inventory.ui.components.SummaryTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportScreen(vm: MainViewModel, shiftId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ReportData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pdf by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(shiftId) {
        vm.loadReport(shiftId) { r ->
            r.onSuccess { d ->
                data = d
                scope.launch {
                    pdf = try {
                        withContext(Dispatchers.IO) { ReportPdfGenerator(context).generate(d) }
                    } catch (e: Exception) {
                        snackbar.showSnackbar("تعذر إنشاء PDF: ${e.message}"); null
                    }
                }
            }.onFailure { error = it.message }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pdf
        if (uri != null && f != null) {
            scope.launch {
                val msg = try {
                    withContext(Dispatchers.IO) { PdfActions.saveTo(context, f, uri) }
                    "تم حفظ التقرير"
                } catch (e: Exception) {
                    "فشل الحفظ: ${e.message}"
                }
                snackbar.showSnackbar(msg)
            }
        }
    }

    ScreenScaffold("تقرير الخفارة", onBack, snackbar) { padding ->
        val d = data
        when {
            error != null -> EmptyMessage(error!!, padding)
            d == null -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp)); CircularProgressIndicator()
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val s = d.shift
                Text(d.hospitalName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("خفارة رقم ${s.shiftNumber.ifBlank { "#${s.id}" }}")
                Text("من ${Format.dateTime(s.openedAt)} إلى ${Format.dateTime(s.closedAt)}")
                Text("المسلِّم: ${s.receiverName.ifBlank { "-" }} — المستلم: ${s.handoverToName.ifBlank { "-" }}")

                val f = pdf
                if (f == null) {
                    Text("جارٍ إنشاء ملف PDF...")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("حفظ PDF", Modifier.weight(1f)) { saveLauncher.launch(f.name) }
                        BigButton("طباعة", Modifier.weight(1f)) { PdfActions.print(context, f, f.nameWithoutExtension) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { PdfActions.share(context, f) }, modifier = Modifier.weight(1f)) { Text("مشاركة") }
                        OutlinedButton(onClick = {
                            if (!PdfActions.open(context, f)) scope.launch { snackbar.showSnackbar("لا يوجد تطبيق لعرض PDF؛ استخدم الحفظ أو المشاركة") }
                        }, modifier = Modifier.weight(1f)) { Text("عرض") }
                    }
                }

                val totals = BalanceCalculator.categoryTotals(d.rows)
                Category.entries.forEach { cat ->
                    val rows = d.rows.filter { it.category == cat }
                    Card {
                        Column(Modifier.padding(6.dp)) {
                            Text(cat.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp))
                            BalanceTable(rows, totals.getValue(cat), closingLabel = "الرصيد النهائي")
                        }
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(6.dp)) {
                        Text("الملخص الشامل", fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                        SummaryTable(totals, BalanceCalculator.grandTotal(d.rows))
                    }
                }
                if (s.closingNotes.isNotBlank()) Text("ملاحظات: ${s.closingNotes}")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
