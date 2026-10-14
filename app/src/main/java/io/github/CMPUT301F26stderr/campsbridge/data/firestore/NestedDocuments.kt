package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal

/**
 * Serializes audit metadata as a nested Firestore map.
 */
fun Removal.stored() =
    mapOf("at" to at.stored(), "actorId" to actorId, "reason" to reason)

/**
 * Serializes audit metadata as a nested Firestore map.
 */
fun Cancellation.stored() =
    mapOf("at" to at.stored(), "actorId" to actorId, "reason" to reason)

/**
 * Reads nested audit metadata with strict timestamp and actor validation.
 */
fun Fields.removal() =
    Removal(instant("at"), string("actorId"), optionalString("reason"))

/**
 * Reads nested audit metadata with strict timestamp and actor validation.
 */
fun Fields.cancellation() =
    Cancellation(instant("at"), string("actorId"), optionalString("reason"))
