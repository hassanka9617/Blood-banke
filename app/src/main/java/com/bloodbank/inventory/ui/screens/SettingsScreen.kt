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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.bloodbank.inventory.data.ClassificationEntity
import com.bloodbank.inventory.domain.Category
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.CategoryTabs
import com.bloodbank.inventory.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenBackup: () -> Unit) {
    val classes by vm.classifications.collectAsStateWithLifecycle()
    val hospital by vm.hospitalName.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hospitalText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(hospital) { if (hospitalText.isEmpty()) hospitalText = hospital ?: "" }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var newName by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ClassificationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun toast(r: Result<Unit>, ok: String) {
        scope.launch { snackbar.showSnackbar(r.fold({ ok }, { it.message ?: "خطأ" })) }
    }

    ScreenScaffold("الإعدادات والتصنيفات", onBack, snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اسم المستشفى أو مصرف الدم (يظهر في التقرير)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(hospitalText, { hospitalText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.setHospitalName(hospitalText) { toast(it, "تم الحفظ") } }) { Text("حفظ الاسم") }
                }
            }

            Text("تصنيفات كل صنف", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "لكل صنف تصنيفاته المستقلة. إيقاف تصنيف يخفيه من الخفارات الجديدة دون حذف أي بيانات سابقة.",
                style = MaterialTheme.typography.bodySmall
            )
            CategoryTabs(tab, { tab = it })
            val category = Category.entries[tab]
            val list = classes.filter { it.category == category }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            Card {
                Column(Modifier.padding(8.dp)) {
                    list.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold,
                                color = if (c.active) Color.Unspecified else Color.Gray
                            )
                            IconButton(onClick = { vm.moveClassification(c.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, "للأعلى") }
                            IconButton(onClick = { vm.moveClassification(c.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "للأسفل") }
                            IconButton(onClick = { renaming = c; renameText = c.name }) { Icon(Icons.Filled.Edit, "تعديل الاسم") }
                            Switch(checked = c.active, onCheckedChange = { vm.setClassificationActive(c.id, it) })
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newName, { newName = it },
                            label = { Text("تصنيف جديد لـ ${category.arabicName}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.addClassification(category, newName) { r ->
                                if (r.isSuccess) newName = ""
                                toast(r, "تمت الإضافة")
                            }
                        }) { Text("إضافة") }
                    }
                }
            }
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("النسخ الاحتياطي والاستعادة", fontWeight = FontWeight.Bold)
                    Text("احفظ نسخة من كل البيانات أو استعدها من نسخة سابقة.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onOpenBackup) { Text("فتح النسخ الاحتياطي") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val target = renaming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("تعديل اسم التصنيف") },
            text = {
                Column {
                    OutlinedTextField(renameText, { renameText = it }, singleLine = true)
                    Text("الاسم الجديد يظهر في الخفارات القادمة؛ التقارير السابقة تحتفظ بالاسم القديم.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameClassification(target.id, renameText) { r -> renaming = null; toast(r, "تم التعديل") }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("إلغاء") } }
        )
    }
}
