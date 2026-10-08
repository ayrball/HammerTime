package com.hammertime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.ui.components.HudCard
import com.hammertime.app.ui.components.hudSafePadding
import com.hammertime.app.ui.theme.DarkBackground
import com.hammertime.app.ui.theme.HudShapes
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.TextMuted

/** First-run screen: says what each permission is for before the system prompt appears. */
@Composable
fun PermissionScreen(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .hudSafePadding(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = "HAMMERTIME",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Drag timing and lap telemetry over live video, using your phone's own sensors.",
                fontSize = 16.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrant,
                modifier = Modifier.heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                shape = HudShapes.chip,
                contentPadding = PaddingValues(horizontal = 28.dp)
            ) {
                Text("Allow access", fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Reason(Icons.Filled.Videocam, "Camera", "Live road view behind the HUD, and recording runs to video.", NeonGreen)
            Reason(Icons.Filled.GpsFixed, "Location", "GPS speed and position for timing, lap detection and the finish gate.", NeonGreen)
            Reason(Icons.Filled.Mic, "Microphone · optional", "Adds audio to recorded video. Everything else works without it.", TextMuted)
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String, tint: Color) {
    HudCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(body, fontSize = 13.sp, color = TextMuted)
            }
        }
    }
}
