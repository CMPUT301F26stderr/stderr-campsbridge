package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationAlert
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPublisher
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationText
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.AlertDeliveryStore
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.IOException

/** Owns one session's confirmed cutoff, eligible events and post/suppression bookkeeping.
 * @param notifications Confirmed history and live account receipts.
 * @param accounts Current saved preference observation, never settings writes.
 * @param content Existing immutable event/subject loader.
 * @param permission Fresh actual device allowance.
 * @param handled Installation/account-keyed handled markers.
 * @param publisher OS effect boundary with no repository access.
 * @param textFor Pure resource/template rendering outside the SDK publisher.
 */
class NotificationAlertCoordinator(
    private val notifications: NotificationRepository,
    private val accounts: AccountRepository,
    private val content: NotificationContentLoader,
    private val permission: NotificationPermission,
    private val handled: AlertDeliveryStore,
    private val publisher: NotificationPublisher,
    private val textFor: (NotificationContent.Loaded) -> NotificationText,
) {
    /** Returns the confirmed current receipt identities; read/cancellation failures propagate.
     * @param accountId Established recipient identity.
     */
    suspend fun prepareHistory(accountId: String): Set<String> {
        val receipts = notifications.getInbox(accountId)
        currentCoroutineContext().ensureActive()
        return receipts.map { it.notificationId }.toSet()
    }

    /** Runs until the foreground/session owner cancels it; arrivals never navigate or mark read.
     * @param session Active account/capabilities supplied by the existing foreground owner.
     * @param initialIds Confirmed cutoff IDs; null is uninitialized, empty is confirmed empty.
     * @param onFailure Nonthrowing diagnostic callback for independently retryable failures.
     */
    suspend fun run(
        session: WorkSession,
        initialIds: Set<String>?,
        onFailure: (Exception) -> Unit,
    ): Unit =
        coroutineScope {
            val history = initialIds?.toSet() ?: establishHistory(session.accountId, onFailure)
            currentCoroutineContext().ensureActive()
            val latestAccount = MutableStateFlow<Account?>(null)
            launch {
                observeAccount(session.accountId, onFailure).collect { latestAccount.value = it }
            }
            // Only the serial receipt collector mutates jobs; each child owns its action stage.
            val jobs = mutableMapOf<String, Job>()
            flow { emitAll(notifications.observeInbox(session.accountId)) }
                .retryWhen { failure, attempt ->
                    awaitRetry(failure, attempt, onFailure)
                    true
                }.collect { receipts ->
                    val currentIds = receipts.map { it.notificationId }.toSet() - history
                    for (removed in jobs.keys - currentIds) jobs.remove(removed)?.cancel()
                    for (id in currentIds) {
                        if (id !in jobs) {
                            jobs[id] =
                                launch {
                                    processEvent(session.accountId, id, latestAccount, onFailure)
                                }
                        }
                    }
                }
            awaitCancellation()
        }

    /** Retries only uninitialized history; no observation/cache snapshot supplies the cutoff.
     * @param accountId Established recipient identity.
     * @param onFailure Nonthrowing initialization diagnostic.
     */
    private suspend fun establishHistory(
        accountId: String,
        onFailure: (Exception) -> Unit,
    ): Set<String> {
        var attempt = 0L
        while (true) {
            try {
                return prepareHistory(accountId)
            } catch (failure: Exception) {
                awaitRetry(failure, attempt++, onFailure)
            }
        }
    }

    /** Invalidates unknown preferences before bounded resubscription after a read failure.
     * @param accountId Current session identity; another returned record never supplies settings.
     * @param onFailure Nonthrowing source diagnostic.
     */
    private fun observeAccount(
        accountId: String,
        onFailure: (Exception) -> Unit,
    ): Flow<Account?> =
        flow { emitAll(accounts.observe(accountId)) }
            .map { account -> account?.takeIf { it.id == accountId && it.isActive } }
            .retryWhen { failure, attempt ->
                emit(null)
                awaitRetry(failure, attempt, onFailure)
                true
            }

    /** Retains completed effect state while retrying its independent durable marker.
     * @param accountId Recipient whose saved preference and markers apply.
     * @param eventId Actual receipt/event identity.
     * @param latestAccount Latest valid observed preference; null waits without suppression.
     * @param onFailure Nonthrowing per-event diagnostic.
     */
    private suspend fun processEvent(
        accountId: String,
        eventId: String,
        latestAccount: StateFlow<Account?>,
        onFailure: (Exception) -> Unit,
    ) {
        var actionCompleted = false
        var attempt = 0L
        while (true) {
            try {
                currentCoroutineContext().ensureActive()
                if (!actionCompleted) {
                    val previous = handled.handledIds(accountId)
                    currentCoroutineContext().ensureActive()
                    if (eventId in previous) return
                    latestAccount.filterNotNull().first()
                    val loaded = content.load(eventId)
                    currentCoroutineContext().ensureActive()
                    if (loaded !is NotificationContent.Loaded) {
                        throw IOException("Could not read notification event $eventId")
                    }
                    if (loaded.subject == NotificationSubjectContent.Failure) {
                        throw IOException("Could not read linked record for event $eventId")
                    }
                    val account = latestAccount.filterNotNull().first()
                    currentCoroutineContext().ensureActive()
                    if (loaded.subject != NotificationSubjectContent.Unsupported &&
                        account.settings.notificationsEnabled && permission.status().canPost
                    ) {
                        val text = textFor(loaded)
                        currentCoroutineContext().ensureActive()
                        publisher.post(
                            NotificationAlert(
                                accountId,
                                eventId,
                                loaded.notification.subject,
                                text,
                            ),
                        )
                    }
                    actionCompleted = true
                }
                handled.markHandled(accountId, eventId)
                currentCoroutineContext().ensureActive()
                return
            } catch (failure: Exception) {
                awaitRetry(failure, attempt++, onFailure)
            }
        }
    }
}

/** Reports one expected failure and waits without swallowing caller cancellation or fatal errors.
 * @param failure Source, event, publication or bookkeeping failure.
 * @param attempt Consecutive failure index; delay saturates at one minute.
 * @param onFailure Nonthrowing diagnostic callback.
 */
private suspend fun awaitRetry(
    failure: Throwable,
    attempt: Long,
    onFailure: (Exception) -> Unit,
) {
    if (failure is CancellationException) throw failure
    if (failure !is Exception) throw failure
    currentCoroutineContext().ensureActive()
    onFailure(failure)
    delay((1000L shl attempt.coerceIn(0, 6).toInt()).coerceAtMost(60000L))
}
