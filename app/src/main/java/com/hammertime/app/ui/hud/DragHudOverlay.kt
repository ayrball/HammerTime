package com.hammertime.app.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.domain.model.DragRunStatus
import com.hammertime.app.domain.model.DragSplitResult
import com.hammertime.app.domain.model.DragState
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonYellow
import java.util.Locale

@Composable
fun DragHudOverlay(
    dragState: DragState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 68.dp) // Sits below top bar and above bottom speedometer
    ) {
        // Left Column: Status Badge and Active Timer
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(HudCardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val (statusText, statusColor) = when (dragState.status) {
                DragRunStatus.WAITING_FOR_STOP -> Pair("STOP VEHICLE TO ARM", Color.Gray)
                DragRunStatus.ARMED_READY_FOR_LAUNCH -> Pair("READY! HAMMER DOWN", NeonGreen)
                DragRunStatus.LAUNCHED -> Pair("RUN IN PROGRESS", NeonCyan)
                DragRunStatus.FINISHED -> Pair("RUN COMPLETED", NeonYellow)
            }

            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%.2fs", dragState.activeRunSeconds),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            Text(
                text = String.format(Locale.US, "PEAK G: %.2fG", dragState.peakAccelG),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonYellow,
                fontFamily = FontFamily.Monospace
            )
        }

        // Right Column: Target Splits (0-30, 0-60, 0-100, 1/8 Mile, 1/4 Mile)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(HudCardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .width(210.dp)
        ) {
            Text(
                text = "DRAG TIMING SPLITS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(4.dp))

            SplitRow("0 - 30 MPH", dragState.splits["0-30"])
            SplitRow("0 - 60 MPH", dragState.splits["0-60"])
            SplitRow("0 - 100 MPH", dragState.splits["0-100"])
            SplitRow("1/8 MILE", dragState.splits["1/8 Mile"])
            SplitRow("1/4 MILE", dragState.splits["1/4 Mile"])
        }
    }
}

@Composable
private fun SplitRow(label: String, split: DragSplitResult?) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (split != null) Color.White else Color.Gray,
            modifier = Modifier.weight(1f)
        )

        if (split != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%.2fs", split.elapsedSeconds),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen
                )
                if (split.trapSpeedMph != null) {
                    Text(
                        text = String.format(Locale.US, "%.1f mph", split.trapSpeedMph),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            Text(
                text = "--.--s",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
            )
        }
    }
}