package io.github.CMPUT301F26stderr.campsbridge.data.account

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration

/**
 * Explicit Firestore codec for [DeviceRegistration], deriving identity from the document path.
 */
object DeviceRegistrationDocument : DocumentCodec<DeviceRegistration>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: DeviceRegistration): Map<String, Any?> =
        mapOf(
            "fcmToken" to value.fcmToken,
            "updatedAt" to value.updatedAt.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): DeviceRegistration =
        DeviceRegistration(fields.id, fields.string("fcmToken"), fields.instant("updatedAt"))
}
