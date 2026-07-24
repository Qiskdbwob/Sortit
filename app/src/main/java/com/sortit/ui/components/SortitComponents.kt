package com.sortit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.sortit.ui.LocalSortitColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

fun formatDate(ts: Long): String =
    if (ts <= 0) "-" else SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(ts))

/** Teks data (path, ekstensi, ukuran, timestamp) - dirender monospace supaya kebaca sebagai data, bukan dekorasi. */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        style = style.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.2.sp)
    )
}

/** Label eyebrow kecil huruf kapital, mis. "BERANDA", "MANIFEST HARI INI". */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.6.sp)
    )
}

/**
 * Garis putus-putus tipis - "perforasi" antar kolom/stub, pengganti divider solid.
 * [vertical] = true untuk garis tegak (default, dipakai di tally/stub), false untuk
 * garis datar (mis. pemisah footer kartu).
 */
@Composable
fun DashedDivider(modifier: Modifier = Modifier, vertical: Boolean = true) {
    val color = LocalSortitColors.current.line
    Canvas(modifier = modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
        if (vertical) {
            drawLine(
                color = color,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        } else {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }
    }
}

/**
 * Kartu dasar bergaya tiket manifest: border tipis, sudut nyaris siku, dan
 * bar aksen kiri opsional (mis. menandai rule/monitor yang aktif).
 */
@Composable
fun Ticket(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalSortitColors.current.line)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accent ?: Color.Transparent)
            )
            Column(Modifier.weight(1f).padding(contentPadding)) { content() }
        }
    }
}

/** [Ticket] dengan judul + subjudul bawaan - pengganti SectionCard lama. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    Ticket(modifier = modifier, accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/**
 * Baris tiket ringkas: judul + path opsional di kiri, area "stub" sempit di
 * kanan (dipisah garis putus) untuk stempel status. Dipakai untuk baris
 * monitor & log aktivitas di Beranda.
 */
@Composable
fun StubTicket(
    title: String,
    modifier: Modifier = Modifier,
    path: String? = null,
    stub: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalSortitColors.current.line)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (path != null) {
                    Spacer(Modifier.height(2.dp))
                    OneLinePath(path)
                }
            }
            DashedDivider(Modifier.fillMaxHeight().width(1.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 60.dp)
                    .background(LocalSortitColors.current.stub)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { stub() }
        }
    }
}

enum class StampKind { MOVE, TRASH, WARN, PENDING }

/** Chip status bergaya stempel tinta - miring 2 derajat, border warna semantik. */
@Composable
fun StampBadge(text: String, kind: StampKind, modifier: Modifier = Modifier) {
    val colors = LocalSortitColors.current
    val fg: Color
    val bg: Color
    when (kind) {
        StampKind.MOVE -> { fg = colors.move; bg = colors.moveSoft }
        StampKind.TRASH -> { fg = colors.trash; bg = colors.trashSoft }
        StampKind.WARN -> { fg = colors.warn; bg = colors.warnSoft }
        StampKind.PENDING -> { fg = colors.inkSoft; bg = Color.Transparent }
    }
    Surface(
        modifier = modifier.rotate(-2f),
        shape = RoundedCornerShape(2.dp),
        color = bg,
        border = BorderStroke(1.dp, fg)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                fontSize = 10.sp
            )
        )
    }
}

/** Chip datar (tidak miring) untuk label non-status, mis. "Default". */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, LocalSortitColors.current.line)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .border(1.dp, LocalSortitColors.current.line, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun FileTypeIcon(name: String, mimeType: String?, modifier: Modifier = Modifier) {
    val icon = when {
        mimeType?.startsWith("image/") == true -> Icons.Default.Image
        mimeType?.startsWith("video/") == true -> Icons.Default.Videocam
        mimeType?.startsWith("audio/") == true -> Icons.Default.Audiotrack
        name.endsWith(".pdf", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
    Icon(icon, contentDescription = null, modifier = modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ThumbFallback(name: String, mimeType: String?) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        FileTypeIcon(name, mimeType, Modifier.size(18.dp))
    }
}

@Composable
fun MediaThumb(name: String, path: String, mimeType: String?, isMedia: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    val line = LocalSortitColors.current.line
    if (isMedia) {
        SubcomposeAsyncImage(
            model = File(path),
            contentDescription = name,
            modifier = modifier.clip(shape).border(1.dp, line, shape),
            contentScale = ContentScale.Crop,
            error = { ThumbFallback(name, mimeType) },
            loading = { ThumbFallback(name, mimeType) }
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, line)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FileTypeIcon(name, mimeType, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun OneLinePath(path: String, modifier: Modifier = Modifier) {
    MonoText(
        text = path,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

data class TallyItem(val value: String, val label: String, val color: Color? = null)

/** Strip angka manifest (4 kolom, dipisah garis putus) - pengganti grid 2x2 kartu ikon lama. */
@Composable
fun ManifestTally(kicker: String, items: List<TallyItem>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalSortitColors.current.line)
    ) {
        Column(Modifier.padding(top = 12.dp, bottom = 14.dp)) {
            Kicker(kicker, modifier = Modifier.padding(horizontal = 14.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                items.forEachIndexed { idx, item ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.value,
                            fontWeight = FontWeight.Bold,
                            color = item.color ?: MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (idx != items.lastIndex) {
                        DashedDivider(Modifier.fillMaxHeight().width(10.dp).padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}
