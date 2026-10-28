package com.bloodbank.inventory.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloodbank.inventory.ui.Format
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.ScreenScaffold

@Composable
fun ReportsScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val shifts by vm.closedShifts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val filtered = if (q.isEmpty()) shifts else shifts.filter { s ->
        s.shiftNumber.contains(q, ignoreCase = true) ||
            Format.date(s.openedAt).contains(q) || Format.date(s.closedAt).contains(q) ||
            s.receiverName.contains(q, ignoreCase = true) || s.handoverToName.contains(q, ignoreCase = true)
    }

    ScreenScaffold("تقارير الخفارات السابقة", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                query, { query = it },
                label = { Text("بحث برقم الخفارة أو التاريخ (مثال 2026/10/22) أو اسم الموظف") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            if (filtered.isEmpty()) {
                Text(if (shifts.isEmpty()) "لا توجد خفارات مغلقة بعد." else "لا توجد نتائج.", Modifier.padding(16.dp))
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("خفارة رقم ${s.shiftNumber.ifBlank { "#${s.id}" }}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("من ${Format.dateTime(s.openedAt)} إلى ${Format.dateTime(s.closedAt)}")
                            Text("المسلِّم: ${s.receiverName.ifBlank { "-" }} — المستلم: ${s.handoverToName.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
