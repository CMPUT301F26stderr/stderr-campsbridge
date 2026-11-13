package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import android.util.Log
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.king.camera.scan.AnalyzeResult
import com.king.wechat.qrcode.DecodeResult
import com.king.wechat.qrcode.WeChatQRCodeDetector
import com.king.wechat.qrcode.scanning.WeChatCameraScanActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

/** Adapts the library-owned camera preview to the application's scan result contract. */
class QrScannerActivity : WeChatCameraScanActivity() {
    private val completion =
        QrScanCompletion { code, data ->
            setResult(code, data)
            finish()
        }

    /** Loads bundled models before constructing an analyzer or requesting camera access. */
    override fun initUI() {
        onBackPressedDispatcher.addCallback(this) { completion.complete(QrScanResult.Cancelled) }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(OpenCVLoader.initLocal()) { "OpenCV could not be loaded" }
                    WeChatQRCodeDetector.init(applicationContext)
                    // init logs some failures instead of throwing; verify the detector is usable.
                    WeChatQRCodeDetector.getScaleFactor()
                }
                lifecycle.withStarted { if (!isFinishing) initializeCameraUi() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failInitialization(failure)
            } catch (failure: LinkageError) {
                failInitialization(failure)
            }
        }
    }

    /** Lets the library bind and start its camera only after native initialization succeeds. */
    private fun initializeCameraUi() {
        super.initUI()
    }

    /** Keeps initialization errors inside the platform adapter.
     * @param failure Native, camera or model initialization failure.
     */
    private fun failInitialization(failure: Throwable) {
        Log.e("QrScanner", "Cannot initialize camera scanner", failure)
        if (!isFinishing) completion.complete(QrScanResult.Failed)
    }

    /** Preserves explicit permission denial instead of reporting an ordinary cancellation.
     * @param granted Android camera permission result.
     */
    override fun requestCameraPermissionResult(granted: Boolean) {
        if (isFinishing) return
        if (granted) {
            super.requestCameraPermissionResult(true)
        } else {
            completion.complete(QrScanResult.PermissionDenied)
        }
    }

    /** Reports decoded content after the camera analyzer completes.
     * @param result Library-owned decoded result.
     */
    override fun onScanResultCallback(result: AnalyzeResult<DecodeResult>) {
        val text = result.result.texts.firstOrNull { it.isNotBlank() } ?: return
        runOnUiThread {
            if (!isFinishing) {
                cameraScan.setAnalyzeImage(false)
                completion.complete(QrScanResult.Decoded(text))
            }
        }
    }
}
