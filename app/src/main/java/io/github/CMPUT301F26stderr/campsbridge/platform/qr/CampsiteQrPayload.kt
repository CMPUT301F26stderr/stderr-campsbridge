package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Stable promotional links identify a campsite without embedding its editable details. */
object CampsiteQrPayload {
    /** Encodes one saved campsite identity as a promotional link.
     * @param campsiteId Nonblank saved campsite identity.
     */
    fun encode(campsiteId: String): String {
        require(campsiteId.isNotBlank())
        val segment = URLEncoder.encode(campsiteId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "campsbridge://campsite/$segment"
    }

    /** Reads a campsite identity, returning null for an unrecognized or malformed link.
     * @param text Decoded QR text.
     */
    fun decode(text: String): String? {
        val uri =
            try {
                URI(text)
            } catch (_: URISyntaxException) {
                return null
            }
        val path = uri.rawPath ?: return null
        if (uri.scheme != "campsbridge" || uri.rawAuthority != "campsite" ||
            uri.rawQuery != null || uri.rawFragment != null ||
            !path.startsWith("/") || path.drop(1).contains('/')
        ) {
            return null
        }
        return uri.path.drop(1).takeIf { it.isNotBlank() }
    }
}
