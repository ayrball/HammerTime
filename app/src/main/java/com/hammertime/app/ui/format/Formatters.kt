package com.hammertime.app.ui.format

import java.util.Locale

/** 92.314 -> "01:32.31" */
fun formatLapTime(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val remSecs = seconds % 60
    return String.format(Locale.US, "%02d:%05.2f", mins, remSecs)
}

/** 65 -> "01:05" */
fun formatClock(totalSeconds: Int): String =
    String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
