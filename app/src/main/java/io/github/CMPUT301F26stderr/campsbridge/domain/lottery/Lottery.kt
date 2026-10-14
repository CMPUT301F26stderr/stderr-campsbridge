package io.github.CMPUT301F26stderr.campsbridge.domain.lottery

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import java.time.Instant
import java.time.LocalDate

/**
 * Ordered registration/booking windows and positive winner/waitlist limits.
 *
 * @param registrationOpensAt Inclusive time at which entrants may begin joining.
 * @param registrationClosesAt Exclusive joining deadline and earliest draw time.
 * @param bookingOpensAt Inclusive time at which selected winners may book.
 * @param bookingClosesAt Exclusive deadline for booking or declining an unused win.
 * @param winnerCount Maximum selected entrants; fewer are selected if fewer join.
 * @param waitlistCapacity Positive waiting-list limit, or null for unlimited registration
 * capacity.
 */
data class LotteryConfiguration(
    val registrationOpensAt: Instant,
    val registrationClosesAt: Instant,
    val bookingOpensAt: Instant,
    val bookingClosesAt: Instant,
    val winnerCount: Long,
    val waitlistCapacity: Long?,
) {
    init {
        require(registrationOpensAt < registrationClosesAt)
        require(registrationClosesAt <= bookingOpensAt && bookingOpensAt < bookingClosesAt)
        require(winnerCount > 0)
        require(waitlistCapacity == null || waitlistCapacity > 0)
    }
}

/**
 * Owner-created draw with derived lifecycle, waiting count, and optional cancellation.
 *
 * @param id Lottery document ID.
 * @param ownerId Account ID of the campsite or lottery owner.
 * @param title Display title for the lottery or announcement.
 * @param description Listing or lottery description.
 * @param configuration Registration/booking windows and participation limits.
 * @param waitingCount Maintained count of entries currently in WAITING state.
 * @param createdAt Original record creation time.
 * @param publishedAt First publication time, or null while still a draft.
 * @param drawnAt Draw completion time, or null until all outcomes have committed.
 * @param cancellation Cancellation metadata, or null while not cancelled.
 */
data class Lottery(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val configuration: LotteryConfiguration,
    val waitingCount: Long,
    val createdAt: Instant,
    val publishedAt: Instant? = null,
    val drawnAt: Instant? = null,
    val cancellation: Cancellation? = null,
) {
    init {
        require(title.isNotBlank())
        require(waitingCount >= 0)
    }

    /**
     * Checks whether this active published lottery currently accepts participation.
     *
     * @param now Instant used to evaluate the window or record the operation.
     */
    fun registrationIsOpen(now: Instant) =
        publishedAt != null &&
            cancellation == null &&
            drawnAt == null &&
            now >= configuration.registrationOpensAt &&
            now < configuration.registrationClosesAt

    /**
     * Checks whether this active published lottery has completed its draw and currently
     * permits booking.
     *
     * @param now Instant used to evaluate the window or record the operation.
     */
    fun bookingIsOpen(now: Instant) =
        publishedAt != null &&
            cancellation == null &&
            drawnAt != null &&
            now >= configuration.bookingOpensAt &&
            now < configuration.bookingClosesAt
}

/**
 * Unique sorted stay nights allocated to one campsite within a lottery.
 *
 * @param campsiteId Campsite document ID.
 * @param nights Nonempty, unique, sorted local stay dates offered by the lottery.
 */
data class LotteryOffer(
    val campsiteId: String,
    val nights: List<LocalDate>,
) {
    init {
        require(nights.isNotEmpty() && nights == nights.distinct().sorted())
    }
}

/**
 * Persisted participation/outcome states; booked and expired labels are derived separately.
 */
enum class EntryState {
    WAITING,
    WITHDRAWN,
    LOST,
    OFFERED,
    DECLINED,
}

/**
 * One account’s participation and draw outcome in one lottery.
 *
 * @param lotteryId Lottery document ID.
 * @param accountId Account ID whose participation or data is addressed.
 * @param state Persisted participation or draw outcome.
 * @param joinedAt Most recent joining time for this participation record.
 * @param stateChangedAt Time the entry last changed participation or outcome state.
 */
data class LotteryEntry(
    val lotteryId: String,
    val accountId: String,
    val state: EntryState,
    val joinedAt: Instant,
    val stateChangedAt: Instant,
)

/**
 * Whether a draw completed in this call or was already complete.
 */
enum class DrawResult {
    COMPLETED,
    ALREADY_DRAWN,
}
