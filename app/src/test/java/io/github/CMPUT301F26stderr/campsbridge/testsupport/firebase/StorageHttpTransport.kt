package io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase

import com.google.firebase.storage.network.NetworkRequest
import com.google.firebase.storage.network.connection.HttpURLConnectionFactory
import java.net.HttpURLConnection
import java.net.URI
import java.util.TreeMap

/**
 * Gives the Storage SDK Android's case-insensitive response headers on the JVM host.
 * All requests still use real HTTP, SDK authentication and emulator rules. The JDK's
 * case-sensitive header map otherwise hides the emulator's resumable-upload URL.
 * @param storageEndpoint This run's loopback Storage service.
 * @return Scope restoring the SDK's original transport factory when closed.
 */
fun installStorageHttpTransport(storageEndpoint: URI): AutoCloseable {
    val field = NetworkRequest::class.java.getDeclaredField("connectionFactory")
    field.isAccessible = true
    val original = field.get(null)
    field.set(
        null,
        HttpURLConnectionFactory { endpoint ->
            require(
                endpoint.protocol == "http" && endpoint.authority == storageEndpoint.rawAuthority,
            )
            AndroidHeaderConnection(endpoint.openConnection() as HttpURLConnection)
        },
    )
    return AutoCloseable { field.set(null, original) }
}

/**
 * Forwards real HTTP while matching Android's response-header lookup semantics.
 * @param delegate JDK connection to the local Storage emulator.
 */
private class AndroidHeaderConnection(
    private val delegate: HttpURLConnection,
) : HttpURLConnection(delegate.url) {
    /** Opens the real local connection. */
    override fun connect() =
        delegate.connect()

    /** Releases the real local connection. */
    override fun disconnect() =
        delegate.disconnect()

    /** Reports the underlying proxy configuration. */
    override fun usingProxy() =
        delegate.usingProxy()

    /** Returns the emulator's response code. */
    override fun getResponseCode() =
        delegate.responseCode

    /** Returns the emulator's declared response length. */
    override fun getContentLength() =
        delegate.contentLength

    /** Returns the actual response body. */
    override fun getInputStream() =
        delegate.inputStream

    /** Returns the actual error body. */
    override fun getErrorStream() =
        delegate.errorStream

    /** Streams upload bytes to the actual connection. */
    override fun getOutputStream() =
        delegate.outputStream

    /**
     * Forwards the SDK's HTTP method.
     * @param method HTTP operation.
     */
    override fun setRequestMethod(method: String) =
        delegate.setRequestMethod(method)

    /**
     * Forwards authentication and metadata unchanged.
     * @param key Header name.
     * @param value Header value.
     */
    override fun setRequestProperty(
        key: String,
        value: String,
    ) =
        delegate.setRequestProperty(key, value)

    /**
     * Configures real request-body transfer.
     * @param enabled Whether the request carries a body.
     */
    override fun setDoOutput(enabled: Boolean) =
        delegate.setDoOutput(enabled)

    /**
     * Configures real response-body transfer.
     * @param enabled Whether the response carries a body.
     */
    override fun setDoInput(enabled: Boolean) =
        delegate.setDoInput(enabled)

    /**
     * Forwards cache policy.
     * @param enabled Whether cached responses are allowed.
     */
    override fun setUseCaches(enabled: Boolean) =
        delegate.setUseCaches(enabled)

    /** Returns the original response headers with Android-compatible name lookup. */
    override fun getHeaderFields(): Map<String, List<String>> =
        TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER).apply {
            delegate.headerFields.forEach { (key, values) -> if (key != null) put(key, values) }
        }
}
