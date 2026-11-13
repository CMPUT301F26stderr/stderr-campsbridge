package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.king.wechat.qrcode.WeChatQRCodeDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the packaged native encoder and offline detector with real generated images. */
@RunWith(AndroidJUnit4::class)
class WeChatQrCompatibilityTest {
    /** Bundled detector models decode the generated promotional image without downloading models. */
    @Test fun generatedImageDecodesToThePromotionalLink() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = createCampsiteQrBitmap("river bend/+雪")
        try {
            WeChatQRCodeDetector.init(context)
            assertEquals(bitmap.width, bitmap.height)
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            assertEquals(
                listOf("campsbridge://campsite/river%20bend%2F%2B%E9%9B%AA"),
                WeChatQRCodeDetector.detectAndDecode(bitmap),
            )
        } finally {
            bitmap.recycle()
        }
    }
}
