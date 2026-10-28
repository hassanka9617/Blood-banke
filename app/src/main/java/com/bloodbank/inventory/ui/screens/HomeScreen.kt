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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.ui.Format
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.ScreenScaffold
import com.bloodbank.inventory.ui.theme.AddGreen

@Composable
fun HomeScreen(vm: MainViewModel, navigate: (String) -> Unit) {
    val shift by vm.openShift.collectAsStateWithLifecycle()
    val rows by vm.balances.collectAsStateWithLifecycle()
    val hospital by vm.hospitalName.collectAsStateWithLifecycle()

    ScreenScaffold(title = hospital?.ifBlank { null } ?: "مخزون مصرف الدم", onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val s = shift
            if (s == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("لا توجد خفارة مفتوحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("ابدأ باستلام الخفارة وجرد المخزون الفعلي.")
                    }
                }
                BigButton("استلام الخفارة") { navigate("receive") }
            } else {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("الخفارة الحالية: ${s.shiftNumber.ifBlank { "#${s.id}" }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("الاستلام: ${Format.dateTime(s.openedAt)}")
                        if (s.receiverName.isNotBlank()) Text("الموظف: ${s.receiverName}")
                        Spacer(Modifier.height(8.dp))
                        val totals = BalanceCalculator.categoryTotals(rows)
                        Category.entries.forEach { c ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.arabicName)
                                Text(totals.getValue(c).closing.toString(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("إضافة رصيد", Modifier.weight(1f), AddGreen) { navigate("movement/ADD") }
                    BigButton("صرف", Modifier.weight(1f)) { navigate("movement/DISPENSE") }
                }
                BigButton("الرصيد الحالي", color = MaterialTheme.colorScheme.secondary) { navigate("balance") }
                BigButton("إنهاء الخفارة والجرد النهائي", color = Color(0xFF4A148C)) { navigate("close") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { navigate("reports") }, modifier = Modifier.fillMaxWidth()) { Text("تقارير الخفارات السابقة") }
            OutlinedButton(onClick = { navigate("settings") }, modifier = Modifier.fillMaxWidth()) { Text("الإعدادات والتصنيفات") }
            Text(
                "التطبيق لحساب المخزون والتوثيق فقط، ولا يحل محل إجراءات التوافق أو القرارات الطبية المعتمدة.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}
