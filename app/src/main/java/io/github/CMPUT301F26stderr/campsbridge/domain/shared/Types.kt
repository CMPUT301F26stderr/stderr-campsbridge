package io.github.CMPUT301F26stderr.campsbridge.domain.shared

import java.time.Instant

/**
 * Finite latitude/longitude values within geographic bounds, independent of Firebase.
 *
 * @param latitude Finite latitude in degrees, between -90 and 90.
 * @param longitude Finite longitude in degrees, between -180 and 180.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

/**
 * Removal audit metadata retained while dependent cleanup resumes.
 *
 * @param at Time the removal, cancellation, or event occurred.
 * @param actorId Account ID responsible for the requested operation.
 * @param reason Optional explanation recorded with the operation.
 */
data class Removal(
    val at: Instant,
    val actorId: String,
    val reason: String? = null,
)

/**
 * Cancellation audit metadata for an application entity.
 *
 * @param at Time the removal, cancellation, or event occurred.
 * @param actorId Account ID responsible for the requested operation.
 * @param reason Optional explanation recorded with the operation.
 */
data class Cancellation(
    val at: Instant,
    val actorId: String,
    val reason: String? = null,
)

/**
 * Expected operation failures. Transport/decoding failures remain distinguishable.
 *
 * @param reason Typed category distinguishing expected operation failures.
 * @param message Human-readable failure detail.
 */
class PersistenceFailure(
    val reason: Reason,
    message: String,
) : Exception(message) {
    /**
     * Stable categories for expected persistence-operation failures.
     */
    enum class Reason {
        NOT_FOUND,
        INACTIVE,
        NOT_ALLOWED,
        CONFLICT,
        WINDOW_CLOSED,
        CAPACITY_REACHED,
        ALREADY_USED,
        STALE_QUOTE,
        INVALID_STATE,
    }
}

/**
 * Throws a typed expected failure when a required operation condition is false.
 *
 * @param condition Condition required for the operation to proceed.
 * @param reason Failure category to throw when the condition is false.
 * @param message Human-readable failure detail.
 */
fun requireOperation(
    condition: Boolean,
    reason: PersistenceFailure.Reason,
    message: String,
) {
    if (!condition) throw PersistenceFailure(reason, message)
}
