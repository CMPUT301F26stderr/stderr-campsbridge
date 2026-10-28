package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityDifference
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.sortCampsitesByName
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxItem
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.validateLotteryWindows
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseDisplayName
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseOptionalEmail
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseOptionalPhone
import java.time.LocalDate

/** Explicit task ports; each handoff connects its assigned function and retains completed peers.
 * @param parseName F01 display-name implementation.
 * @param parseEmail F02 email implementation.
 * @param parsePhone F02 phone implementation.
 * @param remainingEdits F08 comparison of requested and confirmed offered values.
 * @param orderCampsites F11 stable campsite ordering.
 * @param matchesText F09 submitted text predicate.
 * @param matchesGuests F10 guest-capacity predicate.
 * @param validateWindows F13 ordered-window validation.
 * @param filterParticipants Exact-state participant selection.
 * @param orderInbox F19 loaded-event ordering.
 * @param countUnread F20 original-receipt counter.
 * @param availableNights F15 eligible-night intersection.
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
    val orderInbox: ((List<InboxItem>) -> List<InboxItem>)? = null,
    val countUnread: ((List<InboxReceipt>) -> Int)? = null,
    val availableNights: ((Set<LocalDate>, List<CalendarNight>) -> Set<LocalDate>)? = null,
    val filterParticipants: ((List<LotteryEntry>, EntryState?) -> List<LotteryEntry>)? = null,
) {
    /** Reports whether the selected task's actual functions are supplied.
     * @param example Selected task identity.
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
            else -> false
        }
}

/** Connects reviewed task functions in isolated handoffs; shared defaults remain pending. */
fun taskExampleBindings(): TaskExampleBindings =
    TaskExampleBindings(
        parseName = ::parseDisplayName,
        parseEmail = ::parseOptionalEmail,
        parsePhone = ::parseOptionalPhone,
        orderCampsites = ::sortCampsitesByName,
        validateWindows = ::validateLotteryWindows,
    )
