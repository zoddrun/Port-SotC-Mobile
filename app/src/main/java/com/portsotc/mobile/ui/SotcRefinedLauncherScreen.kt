package com.portsotc.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portsotc.mobile.core.HdPresetId
import com.portsotc.mobile.core.SotcLauncherModel
import com.portsotc.mobile.core.SotcLauncherState
import com.portsotc.mobile.core.SotcLauncherStatusKey

/**
 * Refined launcher composition.
 *
 * Every label deliberately avoids emojis and hardware claims. The status is
 * computed by [SotcLauncherModel] from local checks only, so the screen cannot
 * advertise gameplay that has not been prepared on this device.
 */
@Composable
fun SotcRefinedLauncherScreen(
    state: SotcLauncherState,
    onPlay: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onControls: () -> Unit,
    onHdSettings: () -> Unit,
    onDiscInspector: () -> Unit,
    onAchievements: () -> Unit,
    onVault: () -> Unit,
    onDetails: () -> Unit,
    onPresetChange: (String) -> Unit
) {
    val status = SotcLauncherModel.evaluate(state)
    val statusCopy = statusCopy(status.key)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SotcPalette.Basalt)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroHeader()

        Spacer(Modifier.height(4.dp))

        // Readiness card with status dot and headline in a Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SotcPalette.Card),
            shape = RoundedCornerShape(4.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator dot: Sage when READY, Amber otherwise
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (status.key == SotcLauncherStatusKey.READY) {
                                    SotcPalette.Sage
                                } else {
                                    SotcPalette.Amber
                                }
                            )
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = statusCopy.first,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (status.key == SotcLauncherStatusKey.READY) {
                            SotcPalette.Sage
                        } else {
                            SotcPalette.Amber
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(text = statusCopy.second, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = SotcLauncherModel.identityLine(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = SotcPalette.Muted
                )
            }
        }

        // 3-tab segmented control for HD preset selection
        HdPresetSegmentedControl(
            activePresetId = state.hdPresetId,
            onPresetChange = onPresetChange
        )

        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tools = listOf(
                "DISC" to onDiscInspector,
                "MILESTONES" to onAchievements,
                "VAULT" to onVault
            )
            tools.forEach { (label, action) ->
                OutlinedButton(
                    onClick = action,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(2.dp),
                    border = ButtonDefaults.outlinedButtonBorder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }

        if (status.showDetails) {
            androidx.compose.material3.TextButton(onClick = onDetails) {
                Text("VIEW TECHNICAL DETAILS", color = SotcPalette.Muted)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onPlay,
            enabled = status.playEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SotcPalette.Brass,
                contentColor = SotcPalette.Basalt,
                disabledContainerColor = SotcPalette.Hairline,
                disabledContentColor = SotcPalette.Muted
            )
        ) {
            Text(
                text = "PLAY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        Text(
            text = playHint(status.key),
            style = MaterialTheme.typography.bodySmall,
            color = SotcPalette.Muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(2.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavGlyph(NavIconKind.IMPORT)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "IMPORT",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(2.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavGlyph(NavIconKind.SETTINGS)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SETTINGS",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
            OutlinedButton(
                onClick = onControls,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(2.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavGlyph(NavIconKind.CONTROLS)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CONTROLS",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
        // buildSummary raw debug text intentionally removed from home screen.
        // It is displayed only in Settings -> Advanced -> Diagnostics.
    }
}

/**
 * 3-tab segmented control for HD shader preset selection.
 *
 * Active tab: Brass (#C6B282) background. Inactive tabs: transparent with
 * Hairline border. Tabs are BATTERY, BALANCED, ULTRA.
 */
@Composable
private fun HdPresetSegmentedControl(
    activePresetId: String,
    onPresetChange: (String) -> Unit
) {
    val tabs = listOf(
        HdPresetId.BATTERY,
        HdPresetId.BALANCED,
        HdPresetId.ULTRA
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        tabs.forEachIndexed { index, preset ->
            val isActive = activePresetId == preset.id
            val cornerRadius = when (index) {
                0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                tabs.lastIndex -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                else -> RoundedCornerShape(0.dp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(cornerRadius)
                    .background(if (isActive) SotcPalette.Brass else SotcPalette.Basalt)
                    .border(1.dp, SotcPalette.Hairline, cornerRadius)
                    .clickable { onPresetChange(preset.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.id.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) SotcPalette.Basalt else SotcPalette.Ivory,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Atmospheric header drawn with vector primitives: a low sun behind a distant
 * ridgeline. No bitmap or game asset is used, and no gameplay claim is made.
 */
@Composable
private fun HeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF141A22), SotcPalette.Basalt))
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizon = size.height * 0.74f
            drawCircle(
                color = SotcPalette.Brass.copy(alpha = 0.16f),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width / 2f, horizon)
            )
            drawCircle(
                color = SotcPalette.Brass.copy(alpha = 0.28f),
                radius = size.minDimension * 0.24f,
                center = Offset(size.width / 2f, horizon)
            )
            val ridge = Path().apply {
                moveTo(0f, horizon + size.height * 0.08f)
                lineTo(size.width * 0.22f, horizon - size.height * 0.06f)
                lineTo(size.width * 0.42f, horizon + size.height * 0.03f)
                lineTo(size.width * 0.63f, horizon - size.height * 0.11f)
                lineTo(size.width, horizon + size.height * 0.05f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(ridge, SotcPalette.Card)
            drawLine(
                color = SotcPalette.Hairline,
                start = Offset(0f, horizon + size.height * 0.08f),
                end = Offset(size.width, horizon + size.height * 0.08f),
                strokeWidth = 1f
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "S O T C",
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
                color = SotcPalette.Ivory,
                textAlign = TextAlign.Center
            )
            Text(
                text = "SHADOW OF THE COLOSSUS",
                style = MaterialTheme.typography.bodySmall,
                color = SotcPalette.Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

private enum class NavIconKind { IMPORT, SETTINGS, CONTROLS }

/** 20.dp matte vector glyphs; no icon library or image asset is required. */
@Composable
private fun NavGlyph(kind: NavIconKind) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            NavIconKind.IMPORT -> {
                drawLine(SotcPalette.Ivory, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h * 0.62f), 1.6f)
                val arrow = Path().apply {
                    moveTo(w * 0.25f, h * 0.42f)
                    lineTo(w * 0.5f, h * 0.72f)
                    lineTo(w * 0.75f, h * 0.42f)
                }
                drawPath(arrow, SotcPalette.Ivory, style = Stroke(1.6f))
                drawLine(SotcPalette.Muted, Offset(0f, h * 0.9f), Offset(w, h * 0.9f), 1.6f)
            }
            NavIconKind.SETTINGS -> {
                drawCircle(SotcPalette.Ivory, radius = h * 0.22f, center = Offset(w / 2f, h / 2f), style = Stroke(1.6f))
                for (i in 0..3) {
                    val angle = i * Math.PI.toFloat() / 4f
                    val dx = kotlin.math.cos(angle) * w * 0.48f
                    val dy = kotlin.math.sin(angle) * h * 0.48f
                    drawLine(SotcPalette.Ivory, Offset(w / 2f - dx, h / 2f - dy), Offset(w / 2f + dx, h / 2f + dy), 1.6f)
                }
            }
            NavIconKind.CONTROLS -> {
                drawRoundRect(
                    color = SotcPalette.Ivory,
                    topLeft = Offset(0f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.16f),
                    style = Stroke(1.6f)
                )
                drawCircle(SotcPalette.Muted, radius = h * 0.11f, center = Offset(w * 0.28f, h * 0.5f))
                drawCircle(SotcPalette.Ivory, radius = h * 0.11f, center = Offset(w * 0.72f, h * 0.5f))
            }
        }
    }
}

private fun statusCopy(key: SotcLauncherStatusKey): Pair<String, String> = when (key) {
    SotcLauncherStatusKey.NOT_IMPORTED ->
        "IMPORT DISC TO PLAY" to "Import disc to begin."
    SotcLauncherStatusKey.RUNTIME_MISSING ->
        "RUNTIME NOT INSTALLED" to
            "Game data is verified, but this build has no recompiled game runtime."
    SotcLauncherStatusKey.PREPARE_REQUIRED ->
        "DISC TREE PREPARATION REQUIRED" to
            "Extract the disc tree before the first session."
    SotcLauncherStatusKey.READY ->
        "READY TO PLAY" to "Native runtime and disc tree are present on this device."
}

private fun playHint(key: SotcLauncherStatusKey): String = when (key) {
    SotcLauncherStatusKey.NOT_IMPORTED -> "PLAY opens the importer"
    SotcLauncherStatusKey.RUNTIME_MISSING -> "PLAY opens technical details"
    SotcLauncherStatusKey.PREPARE_REQUIRED -> "PLAY opens disc preparation"
    SotcLauncherStatusKey.READY -> "Native runtime · no emulator"
}
