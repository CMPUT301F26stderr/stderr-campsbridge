package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable

private const val QR_TEXT = "qr.text"
private const val PERMISSION_DENIED = Activity.RESULT_FIRST_USER
private const val SCAN_FAILED = Activity.RESULT_FIRST_USER + 1

/** Outcome of one user-launched camera scan, before interpreting application links. */
sealed interface QrScanResult {
    /** Raw decoded content.
     * @param text Content returned by the camera detector.
     */
    data class Decoded(
        val text: String,
    ) : QrScanResult

    /** The user left the scanner without selecting content. */
    data object Cancelled : QrScanResult

    /** Camera permission was declined. */
    data object PermissionDenied : QrScanResult

    /** Camera or native initialization could not complete. */
    data object Failed : QrScanResult
}

/** Launches the internal camera adapter without exposing library result types. */
class QrScanContract : ActivityResultContract<Unit, QrScanResult>() {
    /** Builds the explicit internal scanner launch.
     * @param context Launching Activity context.
     * @param input No scanner configuration is required.
     */
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent =
        Intent(context, QrScannerActivity::class.java)

    /** Decodes the platform outcome without interpreting its QR payload.
     * @param resultCode Android Activity completion code.
     * @param intent Optional private result envelope.
     */
    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): QrScanResult =
        when (resultCode) {
            Activity.RESULT_OK -> {
                intent
                    ?.getStringExtra(QR_TEXT)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(QrScanResult::Decoded) ?: QrScanResult.Failed
            }

            Activity.RESULT_CANCELED -> {
                QrScanResult.Cancelled
            }

            PERMISSION_DENIED -> {
                QrScanResult.PermissionDenied
            }

            else -> {
                QrScanResult.Failed
            }
        }
}

/** Registers the camera launch with its route's Activity Result lifecycle.
 * @param onResult Receives one platform outcome without navigation or persistence.
 * @return Launch action for an explicit user interaction.
 */
@Composable
fun rememberQrScanner(onResult: (QrScanResult) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(QrScanContract(), onResult)
    return { launcher.launch(Unit) }
}

/** Delivers one terminal outcome even when camera callbacks race with cancellation.
 * @param finish Activity result and finish action.
 */
internal class QrScanCompletion(
    private val finish: (Int, Intent?) -> Unit,
) {
    private var completed = false

    /** Completes this launch at most once.
     * @param result Decoded content or explicit platform outcome.
     */
    fun complete(result: QrScanResult) {
        if (completed) return
        completed = true
        when (result) {
            is QrScanResult.Decoded -> {
                if (result.text.isNotBlank()) {
                    finish(Activity.RESULT_OK, Intent().putExtra(QR_TEXT, result.text))
                } else {
                    finish(SCAN_FAILED, null)
                }
            }

            QrScanResult.Cancelled -> {
                finish(Activity.RESULT_CANCELED, null)
            }

            QrScanResult.PermissionDenied -> {
                finish(PERMISSION_DENIED, null)
            }

            QrScanResult.Failed -> {
                finish(SCAN_FAILED, null)
            }
        }
    }
}
