package com.bloodbank.inventory.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bloodbank.inventory.backup.BackupManager
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.components.ScreenScaffold
import com.bloodbank.inventory.ui.theme.AdjustBlue
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var restoreReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val msg = try {
                BackupManager.exportTo(context, uri); "تم حفظ النسخة الاحتياطية بنجاح"
            } catch (e: Exception) {
                "فشل النسخ الاحتياطي: ${e.message}"
            }
            busy = false
            snackbar.showSnackbar(msg)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val err = BackupManager.restoreFrom(context, uri)
            busy = false
            if (err == null) restoreReady = true else snackbar.showSnackbar(err)
        }
    }

    ScreenScaffold("النسخ الاحتياطي والاستعادة", onBack, snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("البيانات محفوظة على الهاتف فقط. احفظ نسخة احتياطية بانتظام في مكان آمن (بطاقة ذاكرة، Google Drive، أو حاسوب).")
            BigButton(if (busy) "جارٍ العمل..." else "إنشاء نسخة احتياطية") {
                if (!busy) exportLauncher.launch(BackupManager.suggestedFileName())
            }
            Text("الاستعادة تستبدل كل البيانات الحالية بالنسخة المختارة. تُحفظ نسخة أمان تلقائية من البيانات الحالية قبل الاستبدال.", color = Color.Gray)
            BigButton("استعادة من نسخة احتياطية", color = AdjustBlue) {
                if (!busy) restoreLauncher.launch(arrayOf("*/*"))
            }
            Text("ملاحظة: النسخة الاحتياطية تحتوي على كل الخفارات والحركات والتقارير وسجل التدقيق.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (restoreReady) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("تمت الاستعادة") },
            text = { Text("سيُعاد تشغيل التطبيق الآن لتحميل البيانات المستعادة.") },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.let { BackupManager.restartApp(it) } }) { Text("إعادة التشغيل") }
            }
        )
    }
}
