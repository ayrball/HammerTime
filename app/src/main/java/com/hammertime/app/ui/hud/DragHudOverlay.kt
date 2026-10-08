package com.hammertime.app.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.domain.model.DragRunStatus
import com.hammertime.app.domain.model.DragSplitResult
import com.hammertime.app.domain.model.DragState
import com.hammertime.app.ui.components.HudCard
import com.hammertime.app.ui.components.HudLabel
import com.hammertime.app.ui.components.StatusChip
import com.hammertime.app.ui.components.hudSafePadding
import com.hammertime.app.ui.components.rememberPulse
import com.hammertime.app.ui.theme.HudDimens
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonYellow
import com.hammertime.app.ui.theme.TextDim
import com.hammertime.app.ui.theme.TextMuted
import java.util.Locale
import kotlin.math.min

private const val QUARTER_MILE_METERS = 402.336
private const val METERS_TO_FEET = 3.28084

/** The elapsed time to show: live while running, the final 1/4 mile ET once finished. */
fun DragState.displayedRunSeconds(): Double = when (status) {
    DragRunStatus.FINISHED -> splits["1/4 Mile"]?.elapsedSeconds ?: activeRunSeconds
    else -> activeRunSeconds
}

/** Run state as a glanceable chip; sits in the centre of the top control bar. */
@Composable
fun DragStatusChip(status: DragRunStatus, modifier: Modifier = Modifier) {
    when (status) {
        DragRunStatus.WAITING_FOR_STOP -> StatusChip("COME TO A STOP TO ARM", TextMuted, modifier)
        DragRunStatus.ARMED_READY_FOR_LAUNCH -> {
            val pulse by rememberPulse()
            StatusChip("READY · HAMMER DOWN", NeonGreen, modifier.alpha(0.55f + 0.45f * pulse))
        }
        DragRunStatus.LAUNCHED -> StatusChip("RUN IN PROGRESS", NeonCyan, modifier)
        DragRunStatus.FINISHED -> StatusChip("RUN COMPLETE", NeonYellow, modifier)
    }
}

@Composable
fun DragHudOverlay(
    dragState: DragState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .hudSafePadding()
            .padding(top = HudDimens.belowTopBar - 8.dp)
    ) {
        RunTimerCard(dragState, Modifier.align(Alignment.TopStart))
        SplitsCard(dragState, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun RunTimerCard(state: DragState, modifier: Modifier = Modifier) {
    val running = state.status == DragRunStatus.LAUNCHED
    val finished = state.status == DragRunStatus.FINISHED
    val color = when {
        running -> Color.White
        finished -> NeonYellow
        else -> TextDim
    }

    HudCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        HudLabel(if (finished) "1/4 MILE ET" else "RUN TIME")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.US, "%.2f", state.displayedRunSeconds()),
                fontSize = 52.sp,
                lineHeight = 56.sp,
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            Text(
                text = "s",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun SplitsCard(state: DragState, modifier: Modifier = Modifier) {
    HudCard(
        modifier = modifier.width(236.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        HudLabel("SPLITS")
        Spacer(modifier = Modifier.height(2.dp))

        SplitRow("0-30 MPH", state.splits["0-30"])
        SplitRow("0-60 MPH", state.splits["0-60"])
        SplitRow("0-100 MPH", state.splits["0-100"])
        SplitRow("1/8 MILE", state.splits["1/8 Mile"], showTrap = true)
        SplitRow("1/4 MILE", state.splits["1/4 Mile"], showTrap = true, emphasise = true)

        Spacer(modifier = Modifier.height(6.dp))
        RunProgress(state)
    }
}

/**
 * Fixed-height row: the layout must not shift as splits complete mid-run, so the trap-speed column
 * is always reserved and the value text is always the same size.
 */
@Composable
private fun SplitRow(
    label: String,
    split: DragSplitResult?,
    showTrap: Boolean = false,
    emphasise: Boolean = false
) {
    val done = split != null
    val valueColor = when {
        !done -> TextDim
        emphasise -> NeonYellow
        else -> NeonGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(25.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) Color.White else TextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (showTrap && split?.trapSpeedMph != null) {
                String.format(Locale.US, "%.0f mph", split.trapSpeedMph)
            } else "",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(62.dp)
        )
        Text(
            text = split?.let { String.format(Locale.US, "%.2fs", it.elapsedSeconds) } ?: "--.--s",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(66.dp)
        )
    }
}

@Composable
private fun RunProgress(state: DragState) {
    val active = state.status == DragRunStatus.LAUNCHED || state.status == DragRunStatus.FINISHED
    val fraction = if (active) min((state.distanceMeters / QUARTER_MILE_METERS).toFloat(), 1f) else 0f
    val barColor = if (state.status == DragRunStatus.FINISHED) NeonYellow else NeonCyan

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        HudLabel("RUN DISTANCE")
        Text(
            text = if (active) {
                String.format(Locale.US, "%.0f / 1320 ft", min(state.distanceMeters, QUARTER_MILE_METERS) * METERS_TO_FEET)
            } else "-- / 1320 ft",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(TextDim.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(barColor, RoundedCornerShape(3.dp))
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
}
