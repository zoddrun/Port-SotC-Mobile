package com.portsotc.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onDetails: () -> Unit
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
        Text(
            text = "S O T C",
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 10.sp,
            color = SotcPalette.Ivory,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "SHADOW OF THE COLOSSUS · NATIVE PORT",
            style = MaterialTheme.typography.bodySmall,
            color = SotcPalette.Muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SotcPalette.Card),
            shape = RoundedCornerShape(4.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = statusCopy.first,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (status.key == SotcLauncherStatusKey.READY) {
                        SotcPalette.Sage
                    } else {
                        SotcPalette.Amber
                    }
                )
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SotcPalette.Card),
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.shaderPackReady) SotcPalette.Sage else SotcPalette.Amber
                            )
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "HD SHADER PACK",
                        style = MaterialTheme.typography.titleMedium,
                        color = SotcPalette.Brass
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = SotcLauncherModel.hdCaption(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = SotcPalette.Ivory
                )
                TextButton(onClick = onHdSettings) {
                    Text("CONFIGURE HD", color = SotcPalette.Brass)
                }
            }
        }

        if (status.showDetails) {
            TextButton(onClick = onDetails) {
                Text("VIEW TECHNICAL DETAILS", color = SotcPalette.Muted)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onPlay,
            enabled = status.playEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(2.dp),
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
                    .height(48.dp),
                shape = RoundedCornerShape(2.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
            ) {
                Text("IMPORT", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(2.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
            ) {
                Text("SETTINGS", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onControls,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(2.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SotcPalette.Ivory)
            ) {
                Text("CONTROLS", style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            text = state.buildSummary,
            style = MaterialTheme.typography.bodySmall,
            color = SotcPalette.Muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

private fun statusCopy(key: SotcLauncherStatusKey): Pair<String, String> = when (key) {
    SotcLauncherStatusKey.NOT_IMPORTED ->
        "NO GAME IMPORTED" to "Import a supported ISO to begin."
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
