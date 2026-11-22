/*
 * Manages participant announcement composition, explicit send attempts, and retry/recovery.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

/** Manages participant announcement composition, explicit send attempts, and retry/recovery.
 * @param actorId Acting owner account identity.
 * @param lotteryId Target lottery identity.
 * @param lotteries Lottery repository source.
 * @param accounts Account repository source.
 * @param notifications Notification repository source.
 * @param savedState Restorable state primitives.
 * @param idGenerator Deterministic ID allocation for attempts.
 */
class AnnouncementComposeViewModel(
    private val actorId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val accounts: AccountRepository,
    private val notifications: NotificationRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(restoreUiState())
    val state = mutableState.asStateFlow()

    private var inFlightJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                accounts.observe(actorId),
                lotteries.observe(lotteryId),
            ) { account, lottery ->
                val canSend =
                    account != null && Role.OWNER in account.roles &&
                        lottery != null && lottery.publishedAt != null &&
                        actorId in lottery.participantOwnerIds
                mutableState.value =
                    mutableState.value.copy(
                        canSend = canSend,
                        lottery = lottery,
                        isLoading = false,
                    )
            }.catch {
                mutableState.value =
                    mutableState.value.copy(
                        canSend = false,
                        isLoading = false,
                    )
            }.collect()
        }
    }

    /** Restores state from SavedStateHandle. */
    private fun restoreUiState(): AnnouncementComposeUiState {
        val savedId: String? = savedState["attempt_id"]
        val attempt =
            if (savedId != null) {
                AnnouncementAttempt(
                    id = savedId,
                    lotteryId = savedState["attempt_lottery_id"] ?: lotteryId,
                    senderId = savedState["attempt_sender_id"] ?: actorId,
                    title = savedState["attempt_title"] ?: "",
                    body = savedState["attempt_body"] ?: "",
                )
            } else {
                null
            }
        val phaseStr: String? = savedState["phase"]
        val phase =
            if (phaseStr != null) {
                runCatching { AnnouncementPhase.valueOf(phaseStr) }.getOrDefault(AnnouncementPhase.IDLE)
            } else {
                AnnouncementPhase.IDLE
            }
        val title: String = savedState["title"] ?: attempt?.title ?: ""
        val body: String = savedState["body"] ?: attempt?.body ?: ""

        return AnnouncementComposeUiState(
            title = attempt?.title ?: title,
            body = attempt?.body ?: body,
            phase = phase,
            attempt = attempt,
        )
    }

    /** Updates the title field if composition is editable. */
    fun editTitle(value: String) {
        val current = mutableState.value
        if (current.attempt != null && current.phase != AnnouncementPhase.IDLE) return
        savedState["title"] = value
        mutableState.value = current.copy(title = value, titleInvalid = false)
    }

    /** Updates the body field if composition is editable. */
    fun editBody(value: String) {
        val current = mutableState.value
        if (current.attempt != null && current.phase != AnnouncementPhase.IDLE) return
        savedState["body"] = value
        mutableState.value = current.copy(body = value, bodyInvalid = false)
    }

    /** Validates inputs and initiates explicit send attempt. */
    fun send() {
        val current = mutableState.value
        if (!current.canSend) return
        if (current.phase != AnnouncementPhase.IDLE || current.attempt != null) return

        val titleBlank = current.title.isBlank()
        val bodyBlank = current.body.isBlank()

        if (titleBlank || bodyBlank) {
            mutableState.value =
                current.copy(
                    titleInvalid = titleBlank,
                    bodyInvalid = bodyBlank,
                )
            return
        }

        val attemptId = idGenerator()
        val attempt =
            AnnouncementAttempt(
                id = attemptId,
                lotteryId = lotteryId,
                senderId = actorId,
                title = current.title,
                body = current.body,
            )

        saveAttempt(attempt, AnnouncementPhase.SENDING)
        mutableState.value =
            current.copy(
                titleInvalid = false,
                bodyInvalid = false,
                phase = AnnouncementPhase.SENDING,
                attempt = attempt,
                title = attempt.title,
                body = attempt.body,
            )

        performSend(attempt)
    }

    /** Performs the send operation for the frozen attempt. */
    private fun performSend(attempt: AnnouncementAttempt) {
        if (attempt.senderId != actorId) {
            setPhase(AnnouncementPhase.CONFLICT)
            return
        }
        inFlightJob?.cancel()
        inFlightJob =
            viewModelScope.launch {
                setPhase(AnnouncementPhase.SENDING)
                try {
                    notifications.announce(
                        attempt.id,
                        attempt.lotteryId,
                        attempt.senderId,
                        Announcement(attempt.title, attempt.body),
                    )
                    onSendSuccess()
                } catch (_: Exception) {
                    checkOutcome(attempt)
                }
            }
    }

    /** Checks whether an in-flight or failed attempt was actually saved. */
    private fun checkOutcome(attempt: AnnouncementAttempt) {
        inFlightJob?.cancel()
        inFlightJob =
            viewModelScope.launch {
                setPhase(AnnouncementPhase.CHECKING)
                if (attempt.senderId != actorId) {
                    setPhase(AnnouncementPhase.CONFLICT)
                    return@launch
                }
                try {
                    val existing = notifications.get(attempt.id)
                    if (existing != null) {
                        if (existing.type == NotificationType.OWNER_ANNOUNCEMENT &&
                            existing.subject == NotificationSubject(SubjectType.LOTTERY, attempt.lotteryId) &&
                            existing.senderId == attempt.senderId &&
                            existing.announcement == Announcement(attempt.title, attempt.body)
                        ) {
                            onSendSuccess()
                        } else {
                            setPhase(AnnouncementPhase.CONFLICT)
                        }
                    } else {
                        setPhase(AnnouncementPhase.ABSENT)
                    }
                } catch (_: Exception) {
                    setPhase(AnnouncementPhase.RETRY)
                }
            }
    }

    /** Retries a failed or unconfirmed attempt. */
    fun retrySend() {
        val current = mutableState.value
        val attempt = current.attempt ?: return
        when (current.phase) {
            AnnouncementPhase.ABSENT -> performSend(attempt)
            AnnouncementPhase.RETRY, AnnouncementPhase.CHECKING, AnnouncementPhase.CONFLICT -> checkOutcome(attempt)
            else -> {
                if (current.phase == AnnouncementPhase.IDLE) performSend(attempt)
            }
        }
    }

    /** Clears attempt lock and allows editing draft as a new announcement. */
    fun editAttempt() {
        val current = mutableState.value
        val attempt = current.attempt
        val newTitle = attempt?.title ?: current.title
        val newBody = attempt?.body ?: current.body
        clearAttempt()
        savedState["title"] = newTitle
        savedState["body"] = newBody
        mutableState.value =
            current.copy(
                phase = AnnouncementPhase.IDLE,
                attempt = null,
                title = newTitle,
                body = newBody,
            )
    }

    /** Consumes single-shot sent success state. */
    fun consumeSent(): Boolean {
        val current = mutableState.value.sent
        if (current) {
            mutableState.value = mutableState.value.copy(sent = false)
            return true
        }
        return false
    }

    /** Handles back navigation requests with optional confirmation dialogs. */
    fun requestBack(): Boolean {
        val current = mutableState.value
        if (current.sent) return true
        if (current.attempt != null || current.phase == AnnouncementPhase.SENDING || current.phase == AnnouncementPhase.CHECKING) {
            mutableState.value = current.copy(showLeaveUnknownConfirmation = true)
            return false
        }
        if (current.title.isNotBlank() || current.body.isNotBlank()) {
            mutableState.value = current.copy(showDiscardConfirmation = true)
            return false
        }
        return true
    }

    /** Hides the discard confirmation dialog. */
    fun dismissDiscardConfirmation() {
        mutableState.value = mutableState.value.copy(showDiscardConfirmation = false)
    }

    /** Confirms discarding the draft and resets state. */
    fun confirmDiscard() {
        savedState["title"] = ""
        savedState["body"] = ""
        clearAttempt()
        mutableState.value =
            mutableState.value.copy(
                title = "",
                body = "",
                showDiscardConfirmation = false,
                attempt = null,
                phase = AnnouncementPhase.IDLE,
            )
    }

    /** Hides the leave unknown confirmation dialog. */
    fun dismissLeaveUnknownConfirmation() {
        mutableState.value = mutableState.value.copy(showLeaveUnknownConfirmation = false)
    }

    /** Confirms leaving despite unknown/in-flight operation. */
    fun confirmLeaveUnknown() {
        savedState["title"] = ""
        savedState["body"] = ""
        clearAttempt()
        mutableState.value =
            mutableState.value.copy(
                title = "",
                body = "",
                showLeaveUnknownConfirmation = false,
                attempt = null,
                phase = AnnouncementPhase.IDLE,
            )
    }

    private fun saveAttempt(attempt: AnnouncementAttempt, phase: AnnouncementPhase) {
        savedState["attempt_id"] = attempt.id
        savedState["attempt_lottery_id"] = attempt.lotteryId
        savedState["attempt_sender_id"] = attempt.senderId
        savedState["attempt_title"] = attempt.title
        savedState["attempt_body"] = attempt.body
        savedState["phase"] = phase.name
    }

    private fun setPhase(phase: AnnouncementPhase) {
        savedState["phase"] = phase.name
        mutableState.value = mutableState.value.copy(phase = phase)
    }

    private fun clearAttempt() {
        savedState.remove<String>("attempt_id")
        savedState.remove<String>("attempt_lottery_id")
        savedState.remove<String>("attempt_sender_id")
        savedState.remove<String>("attempt_title")
        savedState.remove<String>("attempt_body")
        savedState.remove<String>("phase")
    }

    private fun onSendSuccess() {
        clearAttempt()
        savedState["title"] = ""
        savedState["body"] = ""
        mutableState.value =
            mutableState.value.copy(
                title = "",
                body = "",
                phase = AnnouncementPhase.IDLE,
                sent = true,
                attempt = null,
            )
    }
}
