package io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase

import android.content.ComponentName
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.components.ComponentDiscoveryService
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirebaseClients
import io.github.CMPUT301F26stderr.campsbridge.di.AppContainer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * Adjustable time shared by repositories without sleeps or dependence on wall-clock deadlines.
 *
 * @param current Initial application time.
 */
class TestClock(
    var current: Instant = Instant.now(),
) : Clock() {
    /** Returns the current controlled application time. */
    override fun instant(): Instant =
        current

    /** Application instants use UTC; stay dates still use their campsite timezone. */
    override fun getZone(): ZoneId =
        ZoneOffset.UTC

    /**
     * Returns a fixed clock at the requested zone for the same controlled instant.
     *
     * @param zone Requested clock zone.
     */
    override fun withZone(zone: ZoneId): Clock =
        fixed(current, zone)
}

/**
 * Authenticated client and production composition root for one emulator actor.
 *
 * @param id Anonymous emulator identity.
 * @param app Firebase application owning the clients.
 * @param clients Emulator-bound Firestore and Storage clients.
 * @param container Real repository composition root.
 */
data class EmulatorActor(
    val id: String,
    val app: FirebaseApp,
    val clients: FirebaseClients,
    val container: AppContainer,
)

/**
 * Resets only the disposable demo project; owns per-scenario clients and emulator authentication.
 * Fixture setup bypasses rules through the emulator's owner token; repository actions do not.
 */
class FirebaseEmulatorRule : ExternalResource() {
    val projectId = "demo-stderr"
    val clock = TestClock()
    val firestoreEndpoint = localEmulatorEndpoint("FIRESTORE_EMULATOR_HOST")
    val authEndpoint = localEmulatorEndpoint("FIREBASE_AUTH_EMULATOR_HOST")
    val storageEndpoint = localEmulatorEndpoint("FIREBASE_STORAGE_EMULATOR_HOST")
    private val actors = mutableListOf<EmulatorActor>()
    private var storageTransport: AutoCloseable? = null
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** Refuses missing emulator configuration and clears persisted state before the scenario. */
    override fun before() {
        storageTransport = installStorageHttpTransport(storageEndpoint)
        request("$storageEndpoint/internal/reset", "POST")
        request(
            "$firestoreEndpoint/emulator/v1/projects/$projectId/databases/(default)/documents",
            "DELETE",
        )
        clock.current = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    }

    /** Terminates SDK clients so observation/network work does not escape a scenario. */
    override fun after() =
        runBlocking {
            for (actor in actors) {
                actor.clients.firestore
                    .terminate()
                    .await()
                actor.app.delete()
            }
            actors.clear()
            http.close()
            storageTransport?.close()
            storageTransport = null
        }

    /** Creates an isolated anonymous actor through the Auth emulator and real SDK clients. */
    suspend fun actor(): EmulatorActor {
        val application = RuntimeEnvironment.getApplication()
        // Install the test-only Auth registrar in the test host's component-discovery service.
        val service =
            application.packageManager.getServiceInfo(
                ComponentName(application, ComponentDiscoveryService::class.java),
                PackageManager.GET_META_DATA,
            )
        service.metaData.putString(
            "com.google.firebase.components:com.google.firebase.auth.FirebaseAuthRegistrar",
            "com.google.firebase.components.ComponentRegistrar",
        )
        Shadows.shadowOf(application.packageManager).addOrUpdateService(service)
        val options =
            FirebaseOptions
                .Builder()
                .setProjectId(projectId)
                .setApplicationId("1:123456789:android:emulator")
                .setApiKey("fake-api-key")
                .setStorageBucket("$projectId.appspot.com")
                .build()
        val app = FirebaseApp.initializeApp(application, options, UUID.randomUUID().toString())
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator(authEndpoint.host, authEndpoint.port)
        val id = checkNotNull(auth.signInAnonymously().await().user).uid
        val clients =
            FirebaseClients.forEmulators(
                app,
                firestoreEndpoint.host,
                firestorePort = firestoreEndpoint.port,
                storagePort = storageEndpoint.port,
            )
        clients.firestore.firestoreSettings =
            FirebaseFirestoreSettings
                .Builder(clients.firestore.firestoreSettings)
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        return EmulatorActor(
            id,
            app,
            clients,
            AppContainer(clients, clock, Random(17)),
        ).also(actors::add)
    }

    /**
     * Sends a fixture/reset request to the local emulator, never to a cloud endpoint.
     *
     * @param url Local emulator endpoint.
     * @param method HTTP operation.
     * @param body Optional UTF-8 fixture payload.
     * @param contentType MIME type of the fixture payload.
     * @return Response body, or null when a GET addresses a missing document/object.
     */
    fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        contentType: String = "application/json",
    ): String? {
        val uri = URI(url)
        require(
            uri.scheme == "http" &&
                uri.rawAuthority in
                setOf(
                    firestoreEndpoint.rawAuthority,
                    authEndpoint.rawAuthority,
                    storageEndpoint.rawAuthority,
                ),
        )
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer owner")
                .header("Content-Type", contentType)
                .method(
                    method,
                    body?.let(HttpRequest.BodyPublishers::ofString)
                        ?: HttpRequest.BodyPublishers.noBody(),
                ).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (method == "GET" && response.statusCode() == 404) return null
        check(response.statusCode() in 200..299) {
            "Emulator $method failed (${response.statusCode()}): ${response.body()}"
        }
        return response.body()
    }
}

/**
 * Reads the CLI-selected loopback service address and refuses cloud endpoints.
 * @param variable Standard Firebase emulator environment variable.
 * @return Local HTTP base URI with the run's assigned port.
 */
private fun localEmulatorEndpoint(variable: String): URI {
    val address =
        checkNotNull(System.getenv(variable)) {
            "Run scripts/check-firebase.sh; $variable is required for local repository tests."
        }
    return URI("http://$address").also { endpoint ->
        require(
            endpoint.host == "127.0.0.1" && endpoint.port in 1..65535 &&
                endpoint.rawAuthority == address,
        ) {
            "$variable must address a local emulator."
        }
    }
}
