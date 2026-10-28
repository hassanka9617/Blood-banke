package com.bloodbank.inventory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloodbank.inventory.domain.BalanceRow
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.domain.Totals
import com.bloodbank.inventory.ui.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) }
    ) { padding -> content(padding) }
}

/** تبويبات الأصناف الأربعة (مع تبويب "الملخص" اختيارياً). index = -1 يعني الملخص. */
@Composable
fun CategoryTabs(selected: Int, onSelect: (Int) -> Unit, includeSummary: Boolean = false) {
    val tabs = Category.entries.map { it.arabicName } + if (includeSummary) listOf("الملخص") else emptyList()
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
        tabs.forEachIndexed { i, t ->
            Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(t) })
        }
    }
}

/** حقل أرقام فقط. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        modifier = modifier
    )
}

@Composable
fun RowScope.TableCell(text: String, weight: Float, bold: Boolean = false, color: Color = Color.Unspecified) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).padding(vertical = 8.dp, horizontal = 2.dp),
        textAlign = TextAlign.Center,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        fontSize = 14.sp
    )
}

/** جدول الأرصدة: الفصيلة | المستلم | المضاف | المصروف | (التسوية) | الرصيد */
@Composable
fun BalanceTable(rows: List<BalanceRow>, total: Totals?, closingLabel: String = "الرصيد الحالي") {
    val showAdjust = rows.any { it.adjustment != 0 }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell("الفصيلة", 1.2f, true)
            TableCell("المستلم", 1f, true)
            TableCell("المضاف", 1f, true)
            TableCell("المصروف", 1f, true)
            if (showAdjust) TableCell("التسوية", 1f, true)
            TableCell(closingLabel, 1.2f, true)
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TableCell(r.name, 1.2f, true)
                TableCell(r.opening.toString(), 1f)
                TableCell(r.added.toString(), 1f, color = if (r.added > 0) Color(0xFF2E7D32) else Color.Unspecified)
                TableCell(r.dispensed.toString(), 1f, color = if (r.dispensed > 0) MaterialTheme.colorScheme.primary else Color.Unspecified)
                if (showAdjust) TableCell(Format.signed(r.adjustment), 1f)
                TableCell(r.closing.toString(), 1.2f, true)
            }
            HorizontalDivider()
        }
        if (total != null) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFFDECEC)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell("الإجمالي", 1.2f, true)
                TableCell(total.opening.toString(), 1f, true)
                TableCell(total.added.toString(), 1f, true)
                TableCell(total.dispensed.toString(), 1f, true)
                if (showAdjust) TableCell(Format.signed(total.adjustment), 1f, true)
                TableCell(total.closing.toString(), 1.2f, true)
            }
        }
    }
}

/** جدول ملخص الأصناف الأربعة + المجموع الكلي. */
@Composable
fun SummaryTable(categoryTotals: Map<Category, Totals>, grand: Totals) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
            TableCell("الصنف", 1.8f, true)
            TableCell("المستلم", 1f, true)
            TableCell("المضاف", 1f, true)
            TableCell("المصروف", 1f, true)
            TableCell("الرصيد", 1f, true)
        }
        Category.entries.forEach { c ->
            val t = categoryTotals.getValue(c)
            Row(Modifier.fillMaxWidth()) {
                TableCell(c.arabicName, 1.8f, true)
                TableCell(t.opening.toString(), 1f)
                TableCell(t.added.toString(), 1f)
                TableCell(t.dispensed.toString(), 1f)
                TableCell(t.closing.toString(), 1f, true)
            }
            HorizontalDivider()
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFFFDECEC))) {
            TableCell("المجموع الكلي", 1.8f, true)
            TableCell(grand.opening.toString(), 1f, true)
            TableCell(grand.added.toString(), 1f, true)
            TableCell(grand.dispensed.toString(), 1f, true)
            TableCell(grand.closing.toString(), 1f, true)
        }
        if (grand.adjustment != 0) {
            Text("يشمل الرصيد تسويات معتمدة صافيها ${Format.signed(grand.adjustment)}", fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
fun EmptyMessage(text: String, padding: PaddingValues = PaddingValues()) {
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
    }
}
