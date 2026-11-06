package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Calls authenticated booking functions using Firebase's callable HTTP protocol.
 * Auth tokens come from the same application as Firestore; no device/push token is needed.
 * @param app Initialized Firebase application for this service graph.
 */
class CallableFunctions(
    private val app: FirebaseApp,
) {
    private var emulator: Pair<String, Int>? = null

    /** Selects a local Functions service before any repository operation.
     * @param host Explicit emulator host reachable from the client.
     * @param port This run's leased Functions port.
     */
    fun useEmulator(
        host: String,
        port: Int,
    ) {
        require(host.isNotBlank() && port in 1..65535)
        emulator = host to port
    }

    /** Sends an Auth token and explicit inputs, preserving the server's domain failure category.
     * @param name Booking callable name.
     * @param values Operation inputs; cloud functions ignore the emulator-only clock.
     * @return Confirmed callable result object.
     * @throws IOException When authentication or transport cannot confirm the operation.
     * @throws PersistenceFailure When the server rejects the operation with a known reason.
     * @throws DocumentDecodingException When the stored reservation is malformed.
     */
    suspend fun call(
        name: String,
        values: Map<String, Any?>,
    ): JSONObject {
        require(name in setOf("reserve", "cancelReservation"))
        val user = FirebaseAuth.getInstance(app).currentUser ?: throw IOException("No signed-in account")
        val token = user.getIdToken(false).await().token ?: throw IOException("No authentication token")
        val project = checkNotNull(app.options.projectId)
        val base =
            emulator?.let { (host, port) -> "http://$host:$port/$project/us-central1" }
                ?: "https://us-central1-$project.cloudfunctions.net"
        return withContext(Dispatchers.IO) {
            val connection = URL("$base/$name").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Authorization", "Bearer $token")
                val bytes = JSONObject(mapOf("data" to values)).toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body =
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IOException("Callable returned no response")
                currentCoroutineContext().ensureActive()
                val response = JSONObject(body)
                response.optJSONObject("error")?.let { error ->
                    if (error.optString("status") == "DATA_LOSS") {
                        val path = error.optJSONObject("details")?.optString("documentPath").orEmpty()
                        throw DocumentDecodingException(path, error.optString("message"))
                    }
                    val reason = error.optJSONObject("details")?.optString("reason")
                    val category = PersistenceFailure.Reason.entries.firstOrNull { it.name == reason }
                    if (category != null) throw PersistenceFailure(category, error.optString("message"))
                    throw IOException(error.optString("message", "Callable failed"))
                }
                if (status !in 200..299) throw IOException("Callable failed ($status)")
                response.optJSONObject("result") ?: response.optJSONObject("data")
                    ?: throw IOException("Callable returned no result")
            } finally {
                connection.disconnect()
            }
        }
    }
}
