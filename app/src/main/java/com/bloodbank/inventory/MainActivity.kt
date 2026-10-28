package com.bloodbank.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bloodbank.inventory.domain.MovementType
import com.bloodbank.inventory.ui.MainViewModel
import com.bloodbank.inventory.ui.screens.BackupScreen
import com.bloodbank.inventory.ui.screens.BalanceScreen
import com.bloodbank.inventory.ui.screens.CloseShiftScreen
import com.bloodbank.inventory.ui.screens.HomeScreen
import com.bloodbank.inventory.ui.screens.MovementScreen
import com.bloodbank.inventory.ui.screens.ReceiveShiftScreen
import com.bloodbank.inventory.ui.screens.ReportScreen
import com.bloodbank.inventory.ui.screens.ReportsScreen
import com.bloodbank.inventory.ui.screens.SettingsScreen
import com.bloodbank.inventory.ui.theme.BloodBankTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BloodBankTheme {
                val vm: MainViewModel = viewModel()
                val nav = rememberNavController()
                val back: () -> Unit = { nav.popBackStack() }
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm) { route -> nav.navigate(route) } }
                    composable("receive") {
                        ReceiveShiftScreen(vm, onBack = back, onDone = {
                            nav.popBackStack("home", inclusive = false)
                        })
                    }
                    composable(
                        "movement/{type}",
                        arguments = listOf(navArgument("type") { type = NavType.StringType })
                    ) { entry ->
                        val type = MovementType.valueOf(entry.arguments?.getString("type") ?: MovementType.ADD.name)
                        MovementScreen(vm, type, onBack = back)
                    }
                    composable("balance") { BalanceScreen(vm, onBack = back) }
                    composable("close") {
                        CloseShiftScreen(vm, onBack = back, onClosed = { id ->
                            nav.navigate("report/$id") { popUpTo("home") }
                        })
                    }
                    composable("reports") { ReportsScreen(vm, onBack = back, onOpen = { id -> nav.navigate("report/$id") }) }
                    composable(
                        "report/{shiftId}",
                        arguments = listOf(navArgument("shiftId") { type = NavType.LongType })
                    ) { entry ->
                        ReportScreen(vm, entry.arguments?.getLong("shiftId") ?: 0L, onBack = back)
                    }
                    composable("settings") { SettingsScreen(vm, onBack = back, onOpenBackup = { nav.navigate("backup") }) }
                    composable("backup") { BackupScreen(vm, onBack = back) }
                }
            }
        }
    }
}
