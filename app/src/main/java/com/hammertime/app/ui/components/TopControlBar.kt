package com.hammertime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.domain.model.AppMode
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.ui.format.formatClock
import com.hammertime.app.ui.theme.HudBorder
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.HudDimens
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.NeonYellow
import com.hammertime.app.ui.theme.TextMuted
import java.util.Locale

/**
 * Single row across the top of the camera HUD:
 * mode switch | mode-specific status (centre) | GPS, recording, record button, menu.
 *
 * Rarely used actions live in the overflow menu so every control left on the bar can be a proper
 * 48dp touch target. Reset is confirmed.
 */
@Composable
fun TopControlBar(
    currentMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    gpsPoint: GpsPoint?,
    satelliteCount: Int,
    isRecording: Boolean,
    recordingSeconds: Int,
    onToggleRecording: () -> Unit,
    onShowDashboard: (() -> Unit)?,
    onSetFinishGate: (() -> Unit)?,
    onCalibrateMount: () -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeSwitch(currentMode, onModeChange)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GpsChip(gpsPoint, satelliteCount)
            if (isRecording) RecChip(recordingSeconds)
            RecordButton(isRecording, onToggleRecording)
            OverflowMenu(onShowDashboard, onSetFinishGate, onCalibrateMount, onResetData)
        }
    }
}

@Composable
private fun ModeSwitch(current: AppMode, onChange: (AppMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(HudCardBg, RoundedCornerShape(24.dp))
            .border(1.dp, HudBorder, RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeTab("DRAG", current == AppMode.DRAG) { onChange(AppMode.DRAG) }
        ModeTab("TRACK", current == AppMode.TRACK) { onChange(AppMode.TRACK) }
    }
}

@Composable
private fun ModeTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) NeonCyan else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            color = if (selected) Color.Black else Color.LightGray
        )
    }
}

@Composable
private fun GpsChip(gps: GpsPoint?, satellites: Int) {
    val locked = gps != null
    val color = when {
        !locked -> NeonRed
        satellites >= 8 && gps!!.accuracyMeters <= 10f -> NeonGreen
        else -> NeonYellow
    }
    val summary = if (locked) "GPS $satellites satellites, accuracy ${"%.0f".format(gps!!.accuracyMeters)} metres" else "No GPS lock"

    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(HudCardBg, HudShapesChip)
            .border(1.dp, HudBorder, HudShapesChip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
            contentDescription = summary,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = if (locked) "$satellites SAT" else "NO LOCK",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            if (locked) {
                Text(
                    text = String.format(Locale.US, "±%.1fm", gps!!.accuracyMeters),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
        }
    }
}

private val HudShapesChip = RoundedCornerShape(10.dp)

@Composable
private fun RecChip(seconds: Int) {
    val pulse by rememberPulse()
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(HudCardBg, HudShapesChip)
            .border(1.dp, NeonRed.copy(alpha = 0.7f), HudShapesChip)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(pulse)
                .background(NeonRed, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatClock(seconds),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(HudDimens.touchTarget)
            .background(if (isRecording) NeonRed else HudCardBg, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .semantics { contentDescription = if (isRecording) "Stop recording" else "Start recording" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        } else {
            Box(modifier = Modifier.size(20.dp).background(NeonRed, CircleShape))
        }
    }
}

@Composable
private fun OverflowMenu(
    onShowDashboard: (() -> Unit)?,
    onSetFinishGate: (() -> Unit)?,
    onCalibrateMount: () -> Unit,
    onResetData: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Box {
        HudIconButton(Icons.Filled.MoreVert, "More actions", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onShowDashboard != null) {
                MenuItem("Full dashboard", Icons.Filled.Dashboard) { open = false; onShowDashboard() }
            }
            if (onSetFinishGate != null) {
                MenuItem("Set finish gate here", Icons.Filled.Flag) { open = false; onSetFinishGate() }
            }
            MenuItem("Zero mount", Icons.Filled.Tune) { open = false; onCalibrateMount() }
            MenuItem("Reset session", Icons.Filled.Refresh, NeonRed) { open = false; confirmReset = true }
        }
    }

    if (confirmReset) {
        ResetConfirmDialog(
            onConfirm = { confirmReset = false; onResetData() },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick
    )
}
