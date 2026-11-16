/*
 * Custom application vector icons extending Material Icons.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.icons

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A QR code icon extension for [Icons.Filled] and [Icons.Default]. */
val Icons.Filled.QrCode: ImageVector
    get() {
        var icon = _qrCode
        if (icon != null) return icon
        icon = ImageVector.Builder(
            name = "Filled.QrCode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Top-left finder pattern
                moveTo(3f, 3f)
                lineTo(11f, 3f)
                lineTo(11f, 11f)
                lineTo(3f, 11f)
                close()
                moveTo(5f, 5f)
                lineTo(5f, 9f)
                lineTo(9f, 9f)
                lineTo(9f, 5f)
                close()
                moveTo(6.5f, 6.5f)
                lineTo(8.5f, 6.5f)
                lineTo(8.5f, 8.5f)
                lineTo(6.5f, 8.5f)
                close()

                // Top-right finder pattern
                moveTo(13f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 11f)
                lineTo(13f, 11f)
                close()
                moveTo(15f, 5f)
                lineTo(15f, 9f)
                lineTo(19f, 9f)
                lineTo(19f, 5f)
                close()
                moveTo(16.5f, 6.5f)
                lineTo(18.5f, 6.5f)
                lineTo(18.5f, 8.5f)
                lineTo(16.5f, 8.5f)
                close()

                // Bottom-left finder pattern
                moveTo(3f, 13f)
                lineTo(11f, 13f)
                lineTo(11f, 21f)
                lineTo(3f, 21f)
                close()
                moveTo(5f, 15f)
                lineTo(5f, 19f)
                lineTo(9f, 19f)
                lineTo(9f, 15f)
                close()
                moveTo(6.5f, 16.5f)
                lineTo(8.5f, 16.5f)
                lineTo(8.5f, 18.5f)
                lineTo(6.5f, 18.5f)
                close()

                // Bottom-right QR data modules
                moveTo(13f, 13f)
                lineTo(16f, 13f)
                lineTo(16f, 16f)
                lineTo(13f, 16f)
                close()

                moveTo(18f, 13f)
                lineTo(21f, 13f)
                lineTo(21f, 15f)
                lineTo(18f, 15f)
                close()

                moveTo(13f, 18f)
                lineTo(16f, 18f)
                lineTo(16f, 21f)
                lineTo(13f, 21f)
                close()

                moveTo(18f, 17f)
                lineTo(21f, 17f)
                lineTo(21f, 21f)
                lineTo(18f, 21f)
                close()
            }
        }.build()
        _qrCode = icon
        return icon
    }

private var _qrCode: ImageVector? = null
