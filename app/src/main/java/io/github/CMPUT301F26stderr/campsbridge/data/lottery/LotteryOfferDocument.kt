package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.parseDate
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer

/**
 * Explicit Firestore codec for [LotteryOffer], deriving identity from the document path.
 */
object LotteryOfferDocument : DocumentCodec<LotteryOffer>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: LotteryOffer): Map<String, Any?> =
        mapOf("nights" to value.nights.map { it.toString() })

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): LotteryOffer =
        LotteryOffer(fields.id, fields.strings("nights").map(::parseDate))
}
