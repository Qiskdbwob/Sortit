package com.sortit.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// Palet "manifest depot": tinta + kertas + satu aksen, plus stempel semantik
// (move/trash/warn) untuk status - menggantikan trio ungu-mint-amber lama.
// ============================================================================

private val PaperLight = Color(0xFFECEEE6)
private val SurfaceLight = Color(0xFFF6F7F1)
private val Surface2Light = Color(0xFFE2E4D8)
private val InkLight = Color(0xFF1B1D17)
private val InkSoftLight = Color(0xFF5B5F52)
private val LineLight = Color(0xFFD7D6C6)

private val AccentLight = Color(0xFF28486B)
private val AccentSoftLight = Color(0xFFDEE6EC)

private val MoveLight = Color(0xFF2C6E49)
private val MoveSoftLight = Color(0xFFE1EAE3)
private val TrashLight = Color(0xFF8A3B2B)
private val TrashSoftLight = Color(0xFFF0E3DF)
private val WarnLight = Color(0xFFA9762A)
private val WarnSoftLight = Color(0xFFF1E9D8)

private val PaperDark = Color(0xFF15160F)
private val SurfaceDark = Color(0xFF1D1F17)
private val Surface2Dark = Color(0xFF262820)
private val InkOnDark = Color(0xFFEDEEE3)
private val InkSoftDark = Color(0xFFA6A996)
private val LineDark = Color(0xFF3A3C31)

private val AccentDark = Color(0xFF9BC2E0)
private val AccentSoftDark = Color(0xFF223245)

private val MoveDark = Color(0xFF8FC79E)
private val MoveSoftDark = Color(0xFF1F2E22)
private val TrashDark = Color(0xFFE1A08B)
private val TrashSoftDark = Color(0xFF33231D)
private val WarnDark = Color(0xFFDDB876)
private val WarnSoftDark = Color(0xFF34291A)

private val Light = lightColorScheme(
    primary = AccentLight,
    onPrimary = SurfaceLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
    secondary = MoveLight,
    onSecondary = SurfaceLight,
    secondaryContainer = MoveSoftLight,
    onSecondaryContainer = MoveLight,
    tertiary = WarnLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = WarnSoftLight,
    onTertiaryContainer = WarnLight,
    error = TrashLight,
    onError = SurfaceLight,
    errorContainer = TrashSoftLight,
    onErrorContainer = TrashLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = InkSoftLight,
    outline = InkLight,
    outlineVariant = LineLight
)

private val Dark = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0F2430),
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    secondary = MoveDark,
    onSecondary = Color(0xFF11241A),
    secondaryContainer = MoveSoftDark,
    onSecondaryContainer = MoveDark,
    tertiary = WarnDark,
    onTertiary = Color(0xFF2A1F0C),
    tertiaryContainer = WarnSoftDark,
    onTertiaryContainer = WarnDark,
    error = TrashDark,
    onError = Color(0xFF2C160F),
    errorContainer = TrashSoftDark,
    onErrorContainer = TrashDark,
    background = PaperDark,
    onBackground = InkOnDark,
    surface = SurfaceDark,
    onSurface = InkOnDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = InkSoftDark,
    outline = InkOnDark,
    outlineVariant = LineDark
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(5.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

private val BaseType = Typography()
private val AppTypography = BaseType.copy(
    labelSmall = BaseType.labelSmall.copy(letterSpacing = 0.8.sp),
    labelMedium = BaseType.labelMedium.copy(letterSpacing = 0.4.sp),
    titleLarge = BaseType.titleLarge.copy(letterSpacing = (-0.2).sp)
)

/**
 * Warna semantik di luar slot bawaan Material3 - dipakai oleh stempel status
 * (move/trash/warn), garis tiket, dan area stub. Diakses lewat [LocalSortitColors].
 */
data class SortitExtendedColors(
    val line: Color,
    val stub: Color,
    val inkSoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val move: Color,
    val moveSoft: Color,
    val trash: Color,
    val trashSoft: Color,
    val warn: Color,
    val warnSoft: Color
)

private val LightExtended = SortitExtendedColors(
    line = LineLight, stub = Surface2Light, inkSoft = InkSoftLight,
    accent = AccentLight, accentSoft = AccentSoftLight,
    move = MoveLight, moveSoft = MoveSoftLight,
    trash = TrashLight, trashSoft = TrashSoftLight,
    warn = WarnLight, warnSoft = WarnSoftLight
)

private val DarkExtended = SortitExtendedColors(
    line = LineDark, stub = Surface2Dark, inkSoft = InkSoftDark,
    accent = AccentDark, accentSoft = AccentSoftDark,
    move = MoveDark, moveSoft = MoveSoftDark,
    trash = TrashDark, trashSoft = TrashSoftDark,
    warn = WarnDark, warnSoft = WarnSoftDark
)

val LocalSortitColors = compositionLocalOf { LightExtended }

/**
 * [darkTheme] null = ikuti sistem; true = malam; false = siang.
 */
@Composable
fun SortitTheme(
    useDynamicColor: Boolean = false,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = when {
        // Material You: aktif hanya kalau user enable DAN device support
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    val extended = if (dark) DarkExtended else LightExtended

    CompositionLocalProvider(LocalSortitColors provides extended) {
        MaterialTheme(colorScheme = colorScheme, shapes = AppShapes, typography = AppTypography, content = content)
    }
}
