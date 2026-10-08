package com.hammertime.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.ui.theme.HudBorder
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.HudDimens
import com.hammertime.app.ui.theme.HudShapes
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.TextMuted

/** Rounded translucent panel used for every HUD readout. */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(HudCardBg, HudShapes.card)
            .border(1.dp, accent?.copy(alpha = 0.75f) ?: HudBorder, HudShapes.card)
            .padding(contentPadding),
        content = content
    )
}

/** Small muted caption that sits above a value. */
@Composable
fun HudLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

/** Pill-shaped status/call-to-action. Clickable when [onClick] is given. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .background(HudCardBg, HudShapes.chip)
            .border(1.dp, color.copy(alpha = 0.8f), HudShapes.chip)
            .then(clickable)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.size(6.dp))
        }
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

/** Round icon button with a full-size touch target. */
@Composable
fun HudIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    background: Color = HudCardBg,
    size: Dp = HudDimens.touchTarget
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .border(1.dp, HudBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Slow 0.35..1.0 alpha pulse for "live" indicators. */
@Composable
fun rememberPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
}

/** Keeps HUD content out of the display cutout / rounded corners, plus a consistent screen margin. */
@Composable
fun Modifier.hudSafePadding(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
        .padding(horizontal = HudDimens.edge, vertical = 8.dp)

/** Resetting wipes laps, gate and run state, so it must never be a single accidental tap. */
@Composable
fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset session?") },
        text = { Text("This clears the current run, all laps and the finish gate. It can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Reset", color = NeonRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
