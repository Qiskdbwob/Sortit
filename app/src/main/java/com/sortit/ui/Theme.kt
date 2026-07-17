package com.sortit.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Purple = Color(0xFF6D5BD0)
private val PurpleDark = Color(0xFFD0BCFF)
private val Ink = Color(0xFF141218)
private val Paper = Color(0xFFF7F2FA)
private val Mint = Color(0xFF7DD3C0)
private val Amber = Color(0xFFFFB74D)

private val Light = lightColorScheme(
    primary = Purple,
    secondary = Mint,
    tertiary = Amber,
    background = Paper,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink
)

private val Dark = darkColorScheme(
    primary = PurpleDark,
    secondary = Mint,
    tertiary = Amber,
    background = Ink,
    surface = Color(0xFF1D1B20),
    onPrimary = Color(0xFF381E72),
    onBackground = Color(0xFFE6E0E9),
    onSurface = Color(0xFFE6E0E9)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp)
)

@Composable
fun SortitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colorScheme, shapes = AppShapes, content = content)
}
