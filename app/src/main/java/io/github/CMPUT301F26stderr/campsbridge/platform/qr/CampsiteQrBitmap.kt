package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeEncoder

/** Generates a campsite QR image on a worker thread; the caller owns the returned bitmap.
 * @param campsiteId Nonblank saved campsite identity.
 */
fun createCampsiteQrBitmap(campsiteId: String): Bitmap {
    val payload = CampsiteQrPayload.encode(campsiteId)
    check(OpenCVLoader.initLocal()) { "OpenCV could not be loaded" }
    val code = Mat()
    val bordered = Mat()
    val scaled = Mat()
    try {
        QRCodeEncoder.create().encode(payload, code)
        Core.copyMakeBorder(code, bordered, 4, 4, 4, 4, Core.BORDER_CONSTANT, Scalar.all(255.0))
        Imgproc.resize(bordered, scaled, Size(), 8.0, 8.0, Imgproc.INTER_NEAREST)
        return Bitmap.createBitmap(scaled.cols(), scaled.rows(), Bitmap.Config.ARGB_8888).also {
            Utils.matToBitmap(scaled, it)
        }
    } finally {
        scaled.release()
        bordered.release()
        code.release()
    }
}
