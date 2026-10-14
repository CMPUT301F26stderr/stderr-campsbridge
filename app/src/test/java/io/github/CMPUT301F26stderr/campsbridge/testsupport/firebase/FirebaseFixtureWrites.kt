package io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.json.JSONObject

/**
 * Writes starting data through the emulator owner endpoint without invoking business operations.
 *
 * @param path Document path within the disposable project.
 * @param fields Stored fields defining the initial state.
 */
fun FirebaseEmulatorRule.seed(
    path: String,
    fields: Map<String, Any?>,
) {
    val payload = JSONObject(mapOf("fields" to fields.mapValues { firestoreValue(it.value) }))
    request(
        "http://127.0.0.1:8080/v1/projects/$projectId/databases/(default)/documents/$path",
        "PATCH",
        payload.toString(),
    )
}

/**
 * Converts fixture values to the public Firestore REST representation.
 *
 * @param value Native stored value in a fixture.
 */
private fun firestoreValue(value: Any?): JSONObject =
    when (value) {
        null -> {
            JSONObject(mapOf("nullValue" to JSONObject.NULL))
        }

        is String -> {
            JSONObject(mapOf("stringValue" to value))
        }

        is Boolean -> {
            JSONObject(mapOf("booleanValue" to value))
        }

        is Long -> {
            JSONObject(mapOf("integerValue" to value.toString()))
        }

        is Double -> {
            JSONObject(mapOf("doubleValue" to value))
        }

        is Timestamp -> {
            JSONObject(
                mapOf(
                    "timestampValue" to
                        java.time.Instant
                            .ofEpochSecond(value.seconds, value.nanoseconds.toLong())
                            .toString(),
                ),
            )
        }

        is GeoPoint -> {
            JSONObject(
                mapOf(
                    "geoPointValue" to
                        mapOf(
                            "latitude" to value.latitude,
                            "longitude" to value.longitude,
                        ),
                ),
            )
        }

        is List<*> -> {
            JSONObject(mapOf("arrayValue" to mapOf("values" to value.map(::firestoreValue))))
        }

        is Map<*, *> -> {
            JSONObject(
                mapOf(
                    "mapValue" to
                        mapOf(
                            "fields" to
                                value.entries.associate { (key, nested) ->
                                    check(key is String)
                                    key to firestoreValue(nested)
                                },
                        ),
                ),
            )
        }

        else -> {
            error("Unsupported fixture value: ${value.javaClass.name}")
        }
    }

/**
 * Creates an authenticated actor with a seeded profile, independent of account-creation behavior.
 *
 * @param roles Capabilities required for this scenario.
 */
suspend fun FirebaseEmulatorRule.registeredActor(vararg roles: Role): EmulatorActor {
    val actor = actor()
    val account =
        CampingFixtures
            .account(
                actor.id,
            ).copy(roles = roles.toSet(), createdAt = clock.instant())
    seed("accounts/${actor.id}", AccountDocument.encode(account))
    return actor
}

/**
 * Inspects document presence independently of a business reader's access policy.
 *
 * @param path Document path within the disposable project.
 */
fun FirebaseEmulatorRule.documentExists(path: String): Boolean =
    request("http://127.0.0.1:8080/v1/projects/$projectId/databases/(default)/documents/$path") !=
        null

/**
 * Removes starting data to arrange an interrupted or inconsistent persisted state.
 *
 * @param path Document path within the disposable project.
 */
fun FirebaseEmulatorRule.erase(path: String) {
    request(
        "http://127.0.0.1:8080/v1/projects/$projectId/databases/(default)/documents/$path",
        "DELETE",
    )
}
