package com.bloodbank.inventory.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloodbank.inventory.domain.BalanceCalculator
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.BalanceTable
import com.bloodbank.inventory.ui.components.CategoryTabs
import com.bloodbank.inventory.ui.components.EmptyMessage
import com.bloodbank.inventory.ui.components.ScreenScaffold
import com.bloodbank.inventory.ui.components.SummaryTable

@Composable
fun BalanceScreen(vm: MainViewModel, onBack: () -> Unit) {
    val shift by vm.openShift.collectAsStateWithLifecycle()
    val rows by vm.balances.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ScreenScaffold("الرصيد الحالي", onBack) { padding ->
        if (shift == null) {
            EmptyMessage("لا توجد خفارة مفتوحة.", padding)
            return@ScreenScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoryTabs(tab, { tab = it }, includeSummary = true)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                if (tab < Category.entries.size) {
                    val cat = Category.entries[tab]
                    val catRows = rows.filter { it.category == cat }
                    Text(cat.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    BalanceTable(catRows, BalanceCalculator.categoryTotals(catRows).getValue(cat))
                } else {
                    Text("ملخص المخزون كله", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    SummaryTable(BalanceCalculator.categoryTotals(rows), BalanceCalculator.grandTotal(rows))
                }
                Text(
                    "الرصيد الحالي = المستلم + المضاف − المصروف (± التسوية). يتحدث تلقائياً بعد كل عملية.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
