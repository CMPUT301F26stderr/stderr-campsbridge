package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import java.time.LocalDate

/**
 * Validated document identities and deterministic keys shared by readers and writers.
 */
object DocumentKeys {
    /**
     * Validates a Firestore-safe document ID, including reserved names and UTF-8 length.
     *
     * @param value Candidate ID checked for reserved forms, slashes, and UTF-8 size.
     */
    fun id(value: String): String {
        require(
            value.isNotBlank() &&
                value != "." &&
                value != ".." &&
                '/' !in value &&
                !value.matches(Regex("__.*__")),
        )
        require(value.toByteArray(Charsets.UTF_8).size <= 1500)
        return value
    }

    /**
     * Length prefixes make tuples unambiguous, including IDs containing underscores.
     *
     * @param parts Validated ID components in their original tuple order.
     */
    fun compound(vararg parts: String): String =
        id(
            parts.joinToString("") {
                val p = id(it)
                "${p.length}:$p"
            },
        )

    /**
     * Derives the unique key consuming one lottery win for one entrant.
     *
     * @param lotteryId Lottery document ID.
     * @param entrantId Account ID of the entrant using the winning opportunity.
     */
    fun reservation(
        lotteryId: String,
        entrantId: String,
    ): String =
        compound(lotteryId, entrantId)

    /**
     * Encodes the stable account/campsite image slot without duplicating target fields.
     *
     * @param target Account or campsite image slot.
     */
    fun attachment(target: ImageTarget): String =
        id("${target.type.name.lowercase()}_${id(target.id)}")

    /**
     * Decodes an attachment key back into its parent type and identity.
     *
     * @param attachmentId Encoded account/campsite attachment ID.
     */
    fun target(attachmentId: String): ImageTarget {
        val type =
            when (attachmentId.substringBefore('_')) {
                "account" -> ImageTargetType.ACCOUNT
                "campsite" -> ImageTargetType.CAMPSITE
                else -> throw IllegalArgumentException("Invalid image attachment ID")
            }
        return ImageTarget(type, id(attachmentId.substringAfter('_')))
    }

    /**
     * Derives the Storage object path for one immutable uploaded blob.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    fun blob(blobId: String): String =
        "images/${id(blobId)}/original"
}

/**
 * Centralized collection/document paths built from validated identities.
 */
object FirestorePaths {
    /**
     * Builds the validated Firestore document path for a account.
     *
     * @param id Account document ID.
     */
    fun account(id: String) =
        "accounts/${DocumentKeys.id(id)}"

    /**
     * Builds the validated Firestore document path for a installation token.
     *
     * @param account Account document ID.
     * @param installation Installation document ID.
     */
    fun device(
        account: String,
        installation: String,
    ) =
        "${account(account)}/devices/${DocumentKeys.id(installation)}"

    /**
     * Builds the validated Firestore document path for a inbox receipt.
     *
     * @param account Account document ID.
     * @param notification Shared notification document ID.
     */
    fun receipt(
        account: String,
        notification: String,
    ) =
        "${account(account)}/inbox/${DocumentKeys.id(notification)}"

    /**
     * Builds the validated Firestore document path for a campsite.
     *
     * @param id Campsite document ID.
     */
    fun campsite(id: String) =
        "campsites/${DocumentKeys.id(id)}"

    /**
     * Builds the validated Firestore document path for a calendar night.
     *
     * @param campsite Campsite document ID.
     * @param date Local campsite calendar date.
     */
    fun night(
        campsite: String,
        date: LocalDate,
    ) =
        "${campsite(campsite)}/calendar/$date"

    /**
     * Builds the validated Firestore document path for a lottery.
     *
     * @param id Lottery document ID.
     */
    fun lottery(id: String) =
        "lotteries/${DocumentKeys.id(id)}"

    /**
     * Builds the validated Firestore document path for a campsite allocation.
     *
     * @param lottery Lottery document ID.
     * @param campsite Campsite document ID.
     */
    fun offer(
        lottery: String,
        campsite: String,
    ) =
        "${lottery(lottery)}/offers/${DocumentKeys.id(campsite)}"

    /**
     * Builds the validated Firestore document path for a lottery participation.
     *
     * @param lottery Lottery document ID.
     * @param account Account document ID.
     */
    fun entry(
        lottery: String,
        account: String,
    ) =
        "${lottery(lottery)}/entries/${DocumentKeys.id(account)}"

    /**
     * Builds the validated Firestore document path for a reservation.
     *
     * @param id Reservation document ID.
     */
    fun reservation(id: String) =
        "reservations/${DocumentKeys.id(id)}"

    /**
     * Builds the validated Firestore document path for a simulated payment.
     *
     * @param id Reservation document ID.
     */
    fun payment(id: String) =
        "payments/${DocumentKeys.id(id)}"

    /**
     * Builds the validated Firestore document path for a image attachment.
     *
     * @param target Account or campsite image slot.
     */
    fun image(target: ImageTarget) =
        "images/${DocumentKeys.attachment(target)}"

    /**
     * Builds the validated Firestore document path for a review.
     *
     * @param campsite Campsite document ID.
     * @param author Review author account ID.
     */
    fun review(
        campsite: String,
        author: String,
    ) =
        "${campsite(campsite)}/reviews/${DocumentKeys.id(author)}"

    /**
     * Builds the validated Firestore document path for a review reply.
     *
     * @param campsite Campsite document ID.
     * @param author Review author account ID.
     * @param reply Reply document ID within the selected review.
     */
    fun reply(
        campsite: String,
        author: String,
        reply: String,
    ) =
        "${review(campsite, author)}/replies/${DocumentKeys.id(reply)}"

    /**
     * Builds the validated Firestore document path for a review reaction.
     *
     * @param campsite Campsite document ID.
     * @param author Review author account ID.
     * @param reactor Reacting account ID within the selected review.
     */
    fun reaction(
        campsite: String,
        author: String,
        reactor: String,
    ) =
        "${review(campsite, author)}/reactions/${DocumentKeys.id(reactor)}"

    /**
     * Builds the validated Firestore document path for a notification event.
     *
     * @param id Notification event document ID.
     */
    fun notification(id: String) =
        "notifications/${DocumentKeys.id(id)}"

    /**
     * Builds the validated Firestore document path for a message.
     *
     * @param id Message document ID.
     */
    fun message(id: String) =
        "messages/${DocumentKeys.id(id)}"
}
