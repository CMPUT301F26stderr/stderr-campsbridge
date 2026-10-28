package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Named server stages; the form owns the coroutine and guards overlapping actions. */
enum class LotterySavePhase { IDLE, RECOVERING, SAVING_DETAILS, SAVING_OFFER, PUBLISHING }

/** Requested confirmed outcome, retained by the form across explicit retries. */
enum class LotterySaveAction { DRAFT, PUBLISH }

/** Frozen reviewed input for one retained target; this is local operation state, not a document.
 * @param id Identity allocated before suspension.
 * @param ownerId Confirmed current owner.
 * @param title Validated display title.
 * @param description Optional description.
 * @param configuration Validated windows and maximum winners.
 * @param offer The single chosen campsite and sorted nonempty nights.
 * @param allowCreate Whether this form may create a missing target; false for a saved draft.
 * @param action Requested draft or publication result.
 * @param publicationUncertain A prior publication still needs server confirmation before mutation.
 */
data class LotterySaveRequest(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val configuration: LotteryConfiguration,
    val offer: LotteryOffer,
    val allowCreate: Boolean,
    val action: LotterySaveAction = LotterySaveAction.DRAFT,
    val publicationUncertain: Boolean = false,
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

    /** The target is missing, belongs to another owner or is cancelled. */
    data object Unavailable : LotterySaveResult
}

/** Server-confirmed restoration without automatic writes or an assumed empty offer set. */
sealed interface LotteryDraftRead {
    /** An editable record and every server-confirmed allocation.
     * @param lottery Actual owned unpublished record.
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
 */
class LotteryDraftWriter(
    private val lotteries: LotteryRepository,
) {
    /** Saves details, reconciles one allocation, then optionally publishes the same target.
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
            if (existing == null && (!request.allowCreate || publicationUncertain)) {
                return LotterySaveResult.Unavailable
            }
            if (existing != null && !available(existing, request.id, request.ownerId)) {
                return LotterySaveResult.Unavailable
            }
            if (existing?.publishedAt != null) return LotterySaveResult.Published(existing.id)
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
            if (!available(saved, request.id, request.ownerId)) return LotterySaveResult.Unavailable
            if (saved.publishedAt != null) return LotterySaveResult.Published(saved.id)
            if (saved.title != request.title || saved.description != request.description ||
                saved.configuration != request.configuration
            ) {
                lotteries.update(
                    request.id,
                    request.ownerId,
                    request.title,
                    request.description,
                    request.configuration,
                )
                currentCoroutineContext().ensureActive()
            }

            stage = LotterySavePhase.SAVING_OFFER
            onPhase(stage)
            val confirmed = lotteries.getOffers(request.id)
            currentCoroutineContext().ensureActive()
            for (old in confirmed
                .filterNot {
                    it.campsiteId == request.offer.campsiteId
                }.sortedBy { it.campsiteId }) {
                lotteries.removeOffer(request.id, request.ownerId, old.campsiteId)
                currentCoroutineContext().ensureActive()
            }
            if (confirmed.firstOrNull { it.campsiteId == request.offer.campsiteId } !=
                request.offer
            ) {
                lotteries.setOffer(request.id, request.ownerId, request.offer)
                currentCoroutineContext().ensureActive()
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
     */
    suspend fun read(
        id: String,
        ownerId: String,
    ): LotteryDraftRead =
        try {
            val lottery = lotteries.get(id)
            currentCoroutineContext().ensureActive()
            when {
                lottery == null -> {
                    LotteryDraftRead.Missing
                }

                !available(lottery, id, ownerId) -> {
                    LotteryDraftRead.Unavailable
                }

                lottery.publishedAt != null -> {
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
     */
    private fun available(
        lottery: Lottery,
        id: String,
        ownerId: String,
    ): Boolean =
        lottery.id == id && lottery.ownerId == ownerId && lottery.cancellation == null
}
