package io.github.CMPUT301F26stderr.campsbridge.data.account

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/**
 * Explicit Firestore codec for [Account], deriving identity from the document path.
 */
object AccountDocument : DocumentCodec<Account>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Account): Map<String, Any?> =
        mapOf(
            "name" to value.name,
            "email" to value.email,
            "phone" to value.phone,
            "roles" to value.roles.map { it.name }.sorted(),
            "preferences" to
                mapOf(
                    "tags" to value.preferences.tags.sorted(),
                    "amenities" to value.preferences.amenities.sorted(),
                ),
            "settings" to
                mapOf(
                    "notificationsEnabled" to value.settings.notificationsEnabled,
                    "colorMode" to value.settings.colorMode.name,
                    "fontScale" to value.settings.fontScale,
                    "controlScale" to value.settings.controlScale,
                ),
            "createdAt" to value.createdAt.stored(),
            "removal" to value.removal?.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Account =
        Account(
            fields.id,
            fields.string("name"),
            fields.optionalString("email"),
            fields.optionalString("phone"),
            fields.enumSet<Role>("roles"),
            fields.nested("preferences").let {
                Preferences(it.stringSet("tags"), it.stringSet("amenities"))
            },
            fields.nested("settings").let {
                AccountSettings(
                    it.boolean("notificationsEnabled"),
                    it.enum("colorMode"),
                    it.double("fontScale"),
                    it.double("controlScale"),
                )
            },
            fields.instant("createdAt"),
            fields.optionalNested("removal")?.removal(),
        )
}
