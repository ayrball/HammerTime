package com.hammertime.app.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(
    onPreviewViewCreated: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                onPreviewViewCreated(this)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
