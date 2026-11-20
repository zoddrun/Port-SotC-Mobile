package com.portsotc.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portsotc.mobile.core.HdPresetId
import com.portsotc.mobile.core.HdShaderOverrides
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.core.SotcQuickSettingsModel
import com.portsotc.mobile.core.TouchGripMode

/** Values shown by the sliders are effective values, not guesses at pack internals. */
data class QuickSettingsUiState(
    val settings: Settings,
    val shaderPackReady: Boolean,
    val shaderPackDetail: String,
    val gamepadConnected: Boolean,
    val touchRestored: Boolean,
    val effectiveResolutionScale: Float,
    val effectiveRcas: Float,
    val effectiveBloom: Float,
    val effectiveAnisotropy: Float
)

/**
 * In-session quick settings.
 *
 * Every control changes host-provided settings only. The session notice is
 * explicit that the already-running recompiled runner reads these values at its
 * next launch; nothing here pretends to hot-reload a native session.
 */
@Composable
fun InGameQuickSettingsContent(
    state: QuickSettingsUiState,
    onPresetChange: (String) -> Unit,
    onOverride: ((HdShaderOverrides) -> HdShaderOverrides) -> Unit,
    onClearOverrides: () -> Unit,
    onFrameLimitChange: (Int) -> Unit,
    onTouchScaleChange: (Float) -> Unit,
    onTouchAlphaChange: (Float) -> Unit,
    onGripModeChange: (String) -> Unit,
    onHideTouchWithGamepadChange: (Boolean) -> Unit,
    onRestoreTouch: () -> Unit,
    onEditLayout: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit
) {
    val settings = state.settings
    val modelState = SotcQuickSettingsModel.State(
        settings = settings,
        gamepadConnected = state.gamepadConnected,
        touchRestored = state.touchRestored,
        shaderPackReady = state.shaderPackReady,
        shaderPackDetail = state.shaderPackDetail
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SotcPalette.Basalt)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "QUICK SETTINGS",
                style = MaterialTheme.typography.titleMedium,
                color = SotcPalette.Brass,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("CLOSE", color = SotcPalette.Muted)
            }
        }

        Text(
            text = SotcQuickSettingsModel.sessionNotice(),
            style = MaterialTheme.typography.bodySmall,
            color = SotcPalette.Amber
        )

        SectionTitle("HD SHADER PACK")
        Text(
            text = SotcQuickSettingsModel.hdCaption(modelState),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.shaderPackReady) SotcPalette.Sage else SotcPalette.Amber
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HdPresetId.entries.forEach { preset ->
                FilterChip(
                    selected = settings.hdShaderPreset == preset.id,
                    onClick = { onPresetChange(preset.id) },
                    label = { Text(preset.label, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SotcPalette.Brass,
                        selectedLabelColor = SotcPalette.Basalt,
                        containerColor = SotcPalette.Card,
                        labelColor = SotcPalette.Muted
                    )
                )
            }
        }

        SliderRow(
            label = "RESOLUTION SCALE",
            value = state.effectiveResolutionScale,
            valueRange = 0.5f..1.5f,
            display = "${Math.round(state.effectiveResolutionScale * 100)}%",
            onChange = { value ->
                onOverride { it.copy(resolutionScale = value.toDouble()) }
            }
        )
        SliderRow(
            label = "RCAS SHARPNESS",
            value = state.effectiveRcas,
            valueRange = 0f..0.65f,
            display = String.format(java.util.Locale.US, "%.2f", state.effectiveRcas),
            onChange = { value -> onOverride { it.copy(rcasSharpness = value.toDouble()) } }
        )
        SliderRow(
            label = "BLOOM",
            value = state.effectiveBloom,
            valueRange = 0f..0.45f,
            display = String.format(java.util.Locale.US, "%.2f", state.effectiveBloom),
            onChange = { value -> onOverride { it.copy(bloomIntensity = value.toDouble()) } }
        )
        SliderRow(
            label = "ANISOTROPY",
            value = state.effectiveAnisotropy,
            valueRange = 1f..16f,
            display = "${Math.round(state.effectiveAnisotropy)}x",
            onChange = { value -> onOverride { it.copy(anisotropyLevel = Math.round(value)) } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleCell("FXAA", settings.hdShaderOverrides.fxaaEnabled) { enabled ->
                onOverride { it.copy(fxaaEnabled = enabled) }
            }
            ToggleCell("16:9", settings.hdShaderOverrides.widescreen16x9) { enabled ->
                onOverride { it.copy(widescreen16x9 = enabled) }
            }
        }

        if (!settings.hdShaderOverrides.isEmpty) {
            TextButton(onClick = onClearOverrides) {
                Text("RESET TO NAMED PRESET", color = SotcPalette.Brass)
            }
        }

        HorizontalDivider(color = SotcPalette.Hairline)
        SectionTitle("PACING")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 30, 60).forEach { limit ->
                FilterChip(
                    selected = settings.frameLimitFps == limit,
                    onClick = { onFrameLimitChange(limit) },
                    label = {
                        Text(
                            SotcQuickSettingsModel.frameLimitLabel(limit),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SotcPalette.Brass,
                        selectedLabelColor = SotcPalette.Basalt,
                        containerColor = SotcPalette.Card,
                        labelColor = SotcPalette.Muted
                    )
                )
            }
        }

        HorizontalDivider(color = SotcPalette.Hairline)
        SectionTitle("TOUCH CONTROLS")
        // The recompiled runner clamps touch scale to 0.6..1.4 and opacity to
        // 0.2..1.0; the sliders use those effective ranges so the UI never
        // suggests a value the runtime will silently change.
        SliderRow(
            label = "CONTROL SCALE",
            value = settings.touchScale.coerceIn(0.6f, 1.4f),
            valueRange = 0.6f..1.4f,
            display = String.format(java.util.Locale.US, "%.2f", settings.touchScale),
            onChange = onTouchScaleChange
        )
        SliderRow(
            label = "CONTROL OPACITY",
            value = settings.touchAlpha.coerceIn(0.2f, 1f),
            valueRange = 0.2f..1f,
            display = String.format(java.util.Locale.US, "%.2f", settings.touchAlpha),
            onChange = onTouchAlphaChange
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TouchGripMode.entries.forEach { grip ->
                FilterChip(
                    selected = settings.touchGripMode == grip.id,
                    onClick = { onGripModeChange(grip.id) },
                    label = { Text(grip.label.uppercase(), style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SotcPalette.Brass,
                        selectedLabelColor = SotcPalette.Basalt,
                        containerColor = SotcPalette.Card,
                        labelColor = SotcPalette.Muted
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "HIDE TOUCH WITH GAMEPAD",
                style = MaterialTheme.typography.bodySmall,
                color = SotcPalette.Ivory,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.hideTouchWithGamepad,
                onCheckedChange = onHideTouchWithGamepadChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SotcPalette.Basalt,
                    checkedTrackColor = SotcPalette.Brass,
                    uncheckedThumbColor = SotcPalette.Muted,
                    uncheckedTrackColor = SotcPalette.Hairline
                )
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onEditLayout,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("EDIT LAYOUT", style = MaterialTheme.typography.labelLarge)
            }
            if (SotcQuickSettingsModel.restoreAvailable(modelState)) {
                Button(
                    onClick = onRestoreTouch,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SotcPalette.Brass,
                        contentColor = SotcPalette.Basalt
                    )
                ) {
                    Text("RESTORE TOUCH", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SotcPalette.Hairline,
                contentColor = SotcPalette.Ivory
            )
        ) {
            Text("SAFE EXIT", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = "Safe exit stops the host session and returns to the launcher.",
            style = MaterialTheme.typography.bodySmall,
            color = SotcPalette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = SotcPalette.Brass,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = SotcPalette.Muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = display,
                style = MaterialTheme.typography.bodySmall,
                color = SotcPalette.Ivory
            )
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = SotcPalette.Brass,
                activeTrackColor = SotcPalette.Brass,
                inactiveTrackColor = SotcPalette.Hairline
            )
        )
    }
}

@Composable
private fun ToggleCell(label: String, checked: Boolean?, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SotcPalette.Ivory,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(
            checked = checked == true,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SotcPalette.Basalt,
                checkedTrackColor = SotcPalette.Brass,
                uncheckedThumbColor = SotcPalette.Muted,
                uncheckedTrackColor = SotcPalette.Hairline
            )
        )
    }
}
