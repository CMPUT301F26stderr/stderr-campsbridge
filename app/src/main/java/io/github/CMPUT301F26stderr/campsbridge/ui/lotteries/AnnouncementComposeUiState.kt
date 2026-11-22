package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery

/** Required saved lottery/actor outcome, separate from local composition and send recovery. */
enum class AnnouncementRead { LOADING, READY, UNAVAILABLE, FAILURE }

/** One explicit retained announcement attempt, without claiming recipient delivery or reads. */
enum class AnnouncementPhase { IDLE, SENDING, CHECKING, RETRY, ABSENT, CONFLICT }

/** Complete original request retained for same-ID recovery.
 * @param id Allocated immutable event identity.
 * @param lotteryId Original saved lottery.
 * @param senderId Original participating owner.
 * @param title Exact submitted title.
 * @param body Exact submitted multiline body.
 */
data class AnnouncementAttempt(
    val id: String,
    val lotteryId: String,
    val senderId: String,
    val title: String,
    val body: String,
)

/** Saved context and independently retained local form/command state.
 * @param read Required source outcome.
 * @param lottery Actual saved context when available.
 * @param canSend Current active participant can compose and reconcile announcements.
 * @param hasRecipients The current lottery has at least one nonwithdrawn entrant.
 * @param title Raw local or frozen title.
 * @param body Raw local or frozen multiline body.
 * @param titleInvalid An explicit Send found a blank title.
 * @param bodyInvalid An explicit Send found a blank body.
 * @param attempt Complete frozen request, absent before a valid Send.
 * @param phase Current command/reconciliation stage.
 * @param sent The original operation was confirmed and can return once.
 * @param confirmingDiscard An explicit unsent-form abandonment confirmation is open.
 */
data class AnnouncementComposeUiState(
    val read: AnnouncementRead = AnnouncementRead.LOADING,
    val lottery: Lottery? = null,
    val canSend: Boolean = false,
    val hasRecipients: Boolean = false,
    val title: String = "",
    val body: String = "",
    val titleInvalid: Boolean = false,
    val bodyInvalid: Boolean = false,
    val attempt: AnnouncementAttempt? = null,
    val phase: AnnouncementPhase = AnnouncementPhase.IDLE,
    val sent: Boolean = false,
    val confirmingDiscard: Boolean = false,
)
