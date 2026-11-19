package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Supplies distinct scan recovery feedback without launching native activities. */
class QrScanPreviewProvider : PreviewParameterProvider<QrScanFeedback> {
    override val values: Sequence<QrScanFeedback> = QrScanFeedback.entries.asSequence()
}
