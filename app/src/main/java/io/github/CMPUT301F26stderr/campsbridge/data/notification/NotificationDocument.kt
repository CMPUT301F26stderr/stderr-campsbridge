package io.github.CMPUT301F26stderr.campsbridge.data.notification

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject

/**
 * Explicit Firestore codec for [Notification], deriving identity from the document path.
 */
object NotificationDocument : DocumentCodec<Notification>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Notification): Map<String, Any?> =
        mapOf(
            "senderId" to value.senderId,
            "type" to value.type.name,
            "subject" to mapOf("type" to value.subject.type.name, "id" to value.subject.id),
            "announcement" to
                value.announcement?.let { mapOf("title" to it.title, "body" to it.body) },
            "createdAt" to value.createdAt.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Notification =
        Notification(
            fields.id,
            fields.optionalString("senderId"),
            fields.enum("type"),
            fields.nested("subject").let { NotificationSubject(it.enum("type"), it.string("id")) },
            fields.optionalNested("announcement")?.let {
                Announcement(it.string("title"), it.string("body"))
            },
            fields.instant("createdAt"),
        )
}
