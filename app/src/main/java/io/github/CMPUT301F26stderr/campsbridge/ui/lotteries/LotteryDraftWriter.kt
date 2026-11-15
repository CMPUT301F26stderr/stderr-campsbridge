/*
 * Coordinates the acknowledged stages of saving or publishing one lottery draft.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Clock

/** Named server stages; the form owns the coroutine and guards overlapping actions. */
enum class LotterySavePhase { IDLE, RECOVERING, SAVING_DETAILS, SAVING_OFFER, PUBLISHING }

/** Requested confirmed outcome, retained by the form across explicit retries. */
enum class LotterySaveAction { DRAFT, PUBLISH }

/** Separates draft creation from the Final editors' organizer details and owned allocations. */
enum class LotterySaveScope { DRAFT, DETAILS, ALLOCATIONS }

/** Frozen reviewed input for one retained target; this is local operation state, not a document.
 * @param id Identity allocated before suspension.
 * @param ownerId Confirmed current owner.
 * @param title Validated display title.
 * @param description Optional description.
 * @param configuration Validated windows and maximum winners.
 * @param offers Complete desired allocations owned by the acting person.
 * @param allowCreate Whether this form may create a missing target; false for a saved draft.
 * @param action Requested draft or publication result.
 * @param publicationUncertain A prior publication still needs server confirmation before mutation.
 * @param scope Fields this editor intends to save; saved-record editors never create or publish.
 */
data class LotterySaveRequest(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val configuration: LotteryConfiguration,
    val offers: List<LotteryOffer>,
    val allowCreate: Boolean,
    val action: LotterySaveAction = LotterySaveAction.DRAFT,
    val publicationUncertain: Boolean = false,
    val scope: LotterySaveScope = LotterySaveScope.DRAFT,
)

/** Confirmed completion versus an explicit retry or unavailable target. */
sealed interface LotterySaveResult {
    /** Requested save is confirmed for the actual identity.
     * @param id Saved target identity for navigation.
     */
    data class Saved(
        val id: String,
    ) : LotterySaveResult

    /** Publication occurred before this attempt applied its input; open the uneditable record.
     * @param id Actual published identity, without a claim that this request's edits were saved.
     */
    data class Published(
        val id: String,
    ) : LotterySaveResult

    /** A stage failed; uncertain publication blocks conflicting form actions until reconciliation.
     * @param stage Failed operation or recovery read.
     * @param publicationUncertain The server could not resolve a publication acknowledgement.
     */
    data class Failed(
        val stage: LotterySavePhase,
        val publicationUncertain: Boolean = false,
    ) : LotterySaveResult

    /** The target is missing, cancelled or unavailable for the requested editing scope. */
    data object Unavailable : LotterySaveResult
}

/** Server-confirmed restoration without automatic writes or an assumed empty offer set. */
sealed interface LotteryDraftRead {
    /** An editable record and every server-confirmed allocation.
     * @param lottery Actual record accessible to this editor.
     * @param offers All confirmed allocations, including old ones requiring reconciliation.
     */
    data class Content(
        val lottery: Lottery,
        val offers: List<LotteryOffer>,
    ) : LotteryDraftRead

    /** An already-published target must open its saved reader instead of editing frozen input.
     * @param id Actual published identity.
     */
    data class Published(
        val id: String,
    ) : LotteryDraftRead

    /** Server-confirmed absence; the form decides whether an initial creation may be retried. */
    data object Missing : LotteryDraftRead

    /** Cancelled, mismatched or foreign-owner target. */
    data object Unavailable : LotteryDraftRead

    /** Server record or allocation read failed; no empty-state claim is made. */
    data object Failed : LotteryDraftRead
}

/** Reconciles one frozen save against server facts; the form owns identity and action guards.
 * @param lotteries Existing repository owning transactions and server-only reads.
 * @param clock Time used to distinguish open published allocation editing from frozen inventory.
 */
class LotteryDraftWriter(
    private val lotteries: LotteryRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Saves the requested fields, replacing owned allocations atomically when included.
     * Every attempt reads current server facts before deciding which stages need writes.
     * Cancellation propagates; publication acknowledgement failures receive a read-only check.
     * @param request Frozen reviewed input and retained identity.
     * @param onPhase Synchronous stage feedback owned by the caller's state.
     */
    suspend fun save(
        request: LotterySaveRequest,
        onPhase: (LotterySavePhase) -> Unit = {},
    ): LotterySaveResult {
        var stage = LotterySavePhase.RECOVERING
        var publicationUncertain = request.publicationUncertain
        try {
            onPhase(stage)
            val existing = lotteries.get(request.id)
            currentCoroutineContext().ensureActive()
            if (existing == null &&
                (!request.allowCreate || publicationUncertain || request.scope != LotterySaveScope.DRAFT)
            ) {
                return LotterySaveResult.Unavailable
            }
            if (existing != null && !available(existing, request.id, request.ownerId, request.scope)) {
                return LotterySaveResult.Unavailable
            }
            if (existing?.publishedAt != null && (request.scope == LotterySaveScope.DRAFT || publicationUncertain)) {
                return LotterySaveResult.Published(existing.id)
            }
            if (request.scope != LotterySaveScope.DRAFT && request.action == LotterySaveAction.PUBLISH) {
                return LotterySaveResult.Unavailable
            }
            if (existing != null && request.scope == LotterySaveScope.ALLOCATIONS && !sameDetails(existing, request)) {
                return LotterySaveResult.Unavailable
            }
            publicationUncertain = false

            stage = LotterySavePhase.SAVING_DETAILS
            onPhase(stage)
            val saved =
                existing ?: lotteries.createDraft(
                    request.id,
                    request.ownerId,
                    request.title,
                    request.description,
                    request.configuration,
                )
            currentCoroutineContext().ensureActive()
            if (!available(saved, request.id, request.ownerId, request.scope)) return LotterySaveResult.Unavailable
            if (saved.publishedAt != null &&
                request.scope == LotterySaveScope.DRAFT
            ) {
                return LotterySaveResult.Published(saved.id)
            }
            if (request.scope != LotterySaveScope.ALLOCATIONS && !sameDetails(saved, request)) {
                lotteries.update(
                    request.id,
                    request.ownerId,
                    request.title,
                    request.description,
                    request.configuration,
                )
                currentCoroutineContext().ensureActive()
            }

            if (request.scope != LotterySaveScope.DETAILS) {
                stage = LotterySavePhase.SAVING_OFFER
                onPhase(stage)
                val confirmed = lotteries.getOffers(request.id)
                currentCoroutineContext().ensureActive()
                if (confirmed.size != request.offers.size || confirmed.toSet() != request.offers.toSet()) {
                    lotteries.replaceOwnerOffers(request.id, request.ownerId, request.offers)
                    currentCoroutineContext().ensureActive()
                }
            }
            if (request.action == LotterySaveAction.PUBLISH) {
                stage = LotterySavePhase.PUBLISHING
                onPhase(stage)
                lotteries.publish(request.id, request.ownerId)
                currentCoroutineContext().ensureActive()
            }
            return LotterySaveResult.Saved(request.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            return if (stage == LotterySavePhase.PUBLISHING) {
                recoverPublication(request.id, request.ownerId, onPhase)
            } else {
                LotterySaveResult.Failed(stage, publicationUncertain)
            }
        }
    }

    /** Reads a saved target and every allocation without replaying an interrupted mutation.
     * Cancellation propagates; confirmed absence stays distinct from failed reads.
     * @param id Actual saved target.
     * @param ownerId Current owner whose editable target is requested.
     * @param scope Draft authoring or the selected saved-record editor.
     */
    suspend fun read(
        id: String,
        ownerId: String,
        scope: LotterySaveScope = LotterySaveScope.DRAFT,
    ): LotteryDraftRead =
        try {
            val lottery = lotteries.get(id)
            currentCoroutineContext().ensureActive()
            when {
                lottery == null -> {
                    LotteryDraftRead.Missing
                }

                !available(lottery, id, ownerId, scope) -> {
                    LotteryDraftRead.Unavailable
                }

                lottery.publishedAt != null && (
                    scope == LotterySaveScope.DRAFT ||
                        (
                            scope == LotterySaveScope.ALLOCATIONS &&
                                clock.instant() >= lottery.configuration.registrationOpensAt
                        )
                ) -> {
                    LotteryDraftRead.Published(id)
                }

                else -> {
                    val offers = lotteries.getOffers(id)
                    currentCoroutineContext().ensureActive()
                    LotteryDraftRead.Content(lottery, offers)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            LotteryDraftRead.Failed
        }

    /** Reconciles a previously attempted publication without requiring validation or more writes.
     * The caller must retain the uncertain target and freeze its inputs until this resolves.
     * Cancellation propagates without clearing the caller's retained uncertainty.
     * @param id Retained identity from the uncertain publication.
     * @param ownerId Owner responsible for the previous attempt.
     * @param onPhase Caller-owned progress callback.
     */
    suspend fun recoverPublication(
        id: String,
        ownerId: String,
        onPhase: (LotterySavePhase) -> Unit = {},
    ): LotterySaveResult =
        try {
            onPhase(LotterySavePhase.RECOVERING)
            val lottery = lotteries.get(id)
            currentCoroutineContext().ensureActive()
            when {
                lottery == null || !available(lottery, id, ownerId) -> {
                    LotterySaveResult.Unavailable
                }

                lottery.publishedAt != null -> {
                    LotterySaveResult.Saved(lottery.id)
                }

                else -> {
                    LotterySaveResult.Failed(LotterySavePhase.PUBLISHING)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            LotterySaveResult.Failed(LotterySavePhase.RECOVERING, publicationUncertain = true)
        }

    /** Rejects unrelated identities and unavailable lifecycle facts before further writes.
     * @param lottery Actual server record.
     * @param id Expected retained identity.
     * @param ownerId Expected current owner.
     * @param scope Organizer-only fields or the participant's own allocations.
     */
    private fun available(
        lottery: Lottery,
        id: String,
        ownerId: String,
        scope: LotterySaveScope = LotterySaveScope.DRAFT,
    ): Boolean =
        lottery.id == id && ownerId in lottery.participantOwnerIds && lottery.cancellation == null &&
            (scope == LotterySaveScope.ALLOCATIONS || lottery.ownerId == ownerId)

    /** Compares shared fields so allocation-only saves cannot claim unrelated edits succeeded.
     * @param lottery Confirmed current server record.
     * @param request Frozen input submitted by the editor.
     */
    private fun sameDetails(
        lottery: Lottery,
        request: LotterySaveRequest,
    ): Boolean =
        lottery.title == request.title && lottery.description == request.description &&
            lottery.configuration == request.configuration
}
