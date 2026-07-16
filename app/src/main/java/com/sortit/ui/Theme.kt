package com.sortit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet "modern": primary ungu #553F83, secondary hitam #111111.
private val Purple = Color(0xFF553F83)
private val Black = Color(0xFF111111)
private val White = Color(0xFFFFFFFF)

private val Light = lightColorScheme(
    primary = Purple, secondary = Black, surface = Purple, onSurface = White, background = White, onBackground = Black
)
private val Dark = darkColorScheme(
    primary = Purple, secondary = Black, surface = Purple, onSurface = White, background = Black, onBackground = White
)

@Composable
fun SortitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
