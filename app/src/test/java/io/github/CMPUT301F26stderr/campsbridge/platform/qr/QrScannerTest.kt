package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Verifies the scanner's Android launch and one-result boundary without opening a camera. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QrScannerTest {
    private val contract = QrScanContract()

    /** Explicit launches resolve to a private Activity with its own camera-compatible theme. */
    @Test fun launchResolvesToTheInternalScanner() {
        val context = RuntimeEnvironment.getApplication()
        val intent = contract.createIntent(context, Unit)
        assertEquals("${context.packageName}.platform.qr.QrScannerActivity", intent.component?.className)
        val info = context.packageManager.getActivityInfo(intent.component!!, 0)
        assertFalse(info.exported)
        assertNotNull(info.themeResource.takeIf { it != 0 })
    }

    /** Raw content survives the Activity boundary, including foreign links handled by the caller. */
    @Test fun decodedTextIsPreservedWithoutApplicationParsing() {
        for (text in listOf("campsbridge://campsite/river%20bend", "https://example.org/雪")) {
            val results = mutableListOf<QrScanResult>()
            QrScanCompletion { code, intent -> results += contract.parseResult(code, intent) }
                .complete(QrScanResult.Decoded(text))
            assertEquals(listOf(QrScanResult.Decoded(text)), results)
        }
    }

    /** Permission, initialization failure and Back remain distinct; malformed success is a failure. */
    @Test fun platformOutcomesRemainDistinct() {
        for (expected in listOf(QrScanResult.Cancelled, QrScanResult.PermissionDenied, QrScanResult.Failed)) {
            var result: QrScanResult? = null
            QrScanCompletion { code, intent -> result = contract.parseResult(code, intent) }.complete(expected)
            assertEquals(expected, result)
        }
        assertEquals(QrScanResult.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, null))
        assertEquals(QrScanResult.Failed, contract.parseResult(Activity.RESULT_OK, null))
        var blank: QrScanResult? = null
        QrScanCompletion { code, intent -> blank = contract.parseResult(code, intent) }
            .complete(QrScanResult.Decoded(" "))
        assertEquals(QrScanResult.Failed, blank)
    }

    /** Repeated detector callbacks and a late cancellation cannot replace the first result. */
    @Test fun onlyTheFirstTerminalOutcomeIsDelivered() {
        val results = mutableListOf<QrScanResult>()
        val completion = QrScanCompletion { code, intent -> results += contract.parseResult(code, intent) }
        completion.complete(QrScanResult.Decoded("first"))
        completion.complete(QrScanResult.Decoded("second"))
        completion.complete(QrScanResult.Cancelled)
        assertEquals(listOf(QrScanResult.Decoded("first")), results)
    }
}
