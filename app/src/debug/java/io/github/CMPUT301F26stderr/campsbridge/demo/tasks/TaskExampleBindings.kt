package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityDifference
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.remainingAvailabilityEdits
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.sortCampsitesByName
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesGuestCapacity
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxItem
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.sortInboxItems
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.unreadInboxCount
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.filterParticipantsByState
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.validateLotteryWindows
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseDisplayName
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseOptionalEmail
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseOptionalPhone
import java.time.LocalDate

/** Explicit real-function ports supplied by each prepared handoff, never fallback algorithms.
 * @param parseName F01 display-name implementation.
 * @param parseEmail F02 email implementation.
 * @param parsePhone F02 phone implementation.
 * @param remainingEdits F08 inventory comparison.
 * @param orderCampsites F11 shared listing ordering.
 * @param matchesText F09 submitted text predicate.
 * @param matchesGuests F10 capacity predicate.
 * @param validateWindows F13 ordered-window validation.
 * @param filterParticipants Exact-state participant filter.
 * @param orderInbox F19 loaded-event ordering.
 * @param countUnread F20 original-receipt counter.
 * @param availableNights F15 eligible-night intersection.
 * @param classifyReservation F17 category rule.
 * @param orderReservations F18 active-list ordering.
 */
data class TaskExampleBindings(
    val parseName: ((String) -> DisplayNameInput)? = null,
    val parseEmail: ((String) -> OptionalContactInput)? = null,
    val parsePhone: ((String) -> OptionalContactInput)? = null,
    val remainingEdits: AvailabilityDifference? = null,
    val orderCampsites: ((List<Campsite>) -> List<Campsite>)? = null,
    val matchesText: ((Campsite, String) -> Boolean)? = null,
    val matchesGuests: ((Long, Long?) -> Boolean)? = null,
    val validateWindows: LotteryWindowValidator? = null,
    val filterParticipants: ((List<LotteryEntry>, EntryState?) -> List<LotteryEntry>)? = null,
    val orderInbox: ((List<InboxItem>) -> List<InboxItem>)? = null,
    val countUnread: ((List<InboxReceipt>) -> Int)? = null,
    val availableNights: ((Set<LocalDate>, List<CalendarNight>) -> Set<LocalDate>)? = null,
    val classifyReservation: ((Reservation, LocalDate) -> ReservationCategory)? = null,
    val orderReservations: ((List<Reservation>) -> List<Reservation>)? = null,
) {
    /** Reports whether this exercise's real functions are connected, independent of peer tasks.
     * @param example Selected task.
     */
    fun isConnected(example: TaskExample): Boolean =
        when (example) {
            TaskExample.DISPLAY_NAME -> parseName != null
            TaskExample.CONTACT -> parseEmail != null && parsePhone != null
            TaskExample.AVAILABILITY -> remainingEdits != null
            TaskExample.CAMPSITE_ORDER -> orderCampsites != null
            TaskExample.TEXT_MATCH -> matchesText != null
            TaskExample.GUEST_MATCH -> matchesGuests != null
            TaskExample.LOTTERY_WINDOWS -> validateWindows != null
            TaskExample.PARTICIPANTS -> filterParticipants != null
            TaskExample.INBOX_ORDER -> orderInbox != null
            TaskExample.UNREAD_COUNT -> countUnread != null
            TaskExample.BOOKING_NIGHTS -> availableNights != null
            TaskExample.RESERVATION_CATEGORY -> classifyReservation != null
            TaskExample.RESERVATION_ORDER -> orderReservations != null
            else -> false
        }
}

/** Lead-owned handoff binding point; connect the same function used by production factories here.
 * Pending ports intentionally stay null until the isolated task handoff supplies its reference.
 */
fun taskExampleBindings(): TaskExampleBindings =
    TaskExampleBindings(
        parseName = ::parseDisplayName,
        parseEmail = ::parseOptionalEmail,
        parsePhone = ::parseOptionalPhone,
        remainingEdits = ::remainingAvailabilityEdits,
        orderCampsites = ::sortCampsitesByName,
        matchesText = ::matchesCampsiteText,
        matchesGuests = ::matchesGuestCapacity,
        validateWindows = ::validateLotteryWindows,
        filterParticipants = ::filterParticipantsByState,
        orderInbox = ::sortInboxItems,
        countUnread = ::unreadInboxCount,
    )
