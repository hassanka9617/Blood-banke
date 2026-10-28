package com.bloodbank.inventory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val BloodRed = Color(0xFFB71C1C)
val AddGreen = Color(0xFF2E7D32)
val AdjustBlue = Color(0xFF1565C0)

private val colors = lightColorScheme(
    primary = BloodRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    tertiary = AdjustBlue,
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF)
)

/** الثيم العام: ألوان مصرف الدم + اتجاه من اليمين إلى اليسار دائماً. */
@Composable
fun BloodBankTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
