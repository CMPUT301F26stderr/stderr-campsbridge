package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Controlled server facts, independent live offers and explicit mutation outcomes for tests.
 * @param initial Initial saved records.
 */
class FakeLotteryRepository(
    initial: List<Lottery> = emptyList(),
) : LotteryRepository {
    val recordFailure = MutableStateFlow<Exception?>(null)
    val offerFailure = MutableStateFlow<Exception?>(null)
    val offerFailures = MutableStateFlow<Map<String, Exception>>(emptyMap())
    val observedOfferIds = mutableListOf<String>()
    val associationFailure = MutableStateFlow<Exception?>(null)
    val associations = MutableStateFlow<Map<String, List<Lottery>>>(emptyMap())
    val associationRequests = mutableListOf<String>()
    var activeAssociationObservers = 0
        private set
    val ownerFailure = MutableStateFlow<Exception?>(null)
    val entrantFailure = MutableStateFlow<Exception?>(null)
    val entriesFailure = MutableStateFlow<Exception?>(null)
    val entriesRequests = mutableListOf<String>()
    var activeEntriesObservers = 0
        private set
    val entries = MutableStateFlow<List<LotteryEntry>>(emptyList())
    val ownerRequests = mutableListOf<String>()
    val entrantRequests = mutableListOf<String>()
    val observedRecordIds = mutableListOf<String>()
    var activeOwnerObservers = 0
        private set
    var activeEntrantObservers = 0
        private set
    var activeRecordObservers = 0
    var activeOfferObservers = 0
    val records = MutableStateFlow(initial)
    val serverOffers = mutableMapOf<String, List<LotteryOffer>>()
    val liveOffers = MutableStateFlow<Map<String, List<LotteryOffer>>>(emptyMap())
    val events = mutableListOf<String>()
    val detailsRequests = mutableListOf<DetailsRequest>()
    val offerRequests = mutableListOf<LotteryOffer>()
    var getHandler: (suspend (String) -> Lottery?)? = null
    var getOffersHandler: (suspend (String) -> List<LotteryOffer>)? = null
    var createHandler: (suspend (DetailsRequest) -> Lottery)? = null
    var updateHandler: (suspend (DetailsRequest) -> Unit)? = null
    var setOfferHandler: (suspend (String, String, LotteryOffer) -> Unit)? = null
    var removeOfferHandler: (suspend (String, String, String) -> Unit)? = null
    var publishHandler: (suspend (String, String) -> Unit)? = null
    var joinHandler: (suspend (String, String) -> Unit)? = null
    var withdrawHandler: (suspend (String, String) -> Unit)? = null
    var drawHandler: (suspend (String) -> DrawResult)? = null

    /** Captures the exact arguments supplied to create/update without simulating a transaction.
     * @param id Retained target identity.
     * @param ownerId Requesting owner.
     * @param title Submitted title.
     * @param description Submitted optional text.
     * @param configuration Submitted no-cap windows and winner count.
     */
    data class DetailsRequest(
        val id: String,
        val ownerId: String,
        val title: String,
        val description: String,
        val configuration: LotteryConfiguration,
    )

    /** Reads controlled server state; a configured null remains a confirmed missing record.
     * @param id Requested target.
     */
    override suspend fun get(id: String): Lottery? {
        events.add("get:$id")
        val handler = getHandler
        return if (handler != null) handler(id) else records.value.firstOrNull { it.id == id }
    }

    /** Reads the server fixture independently of intentionally stale live emissions.
     * @param id Requested parent.
     */
    override suspend fun getOffers(id: String): List<LotteryOffer> {
        events.add("offers:$id")
        return getOffersHandler?.invoke(id) ?: serverOffers[id].orEmpty()
    }

    /** Observes the supplied saved-record stream.
     * @param id Requested target.
     */
    override fun observe(id: String): Flow<Lottery?> =
        flow {
            observedRecordIds.add(id)
            activeRecordObservers++
            try {
                combine(records, recordFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows.firstOrNull { it.id == id }
                }.collect { emit(it) }
            } finally {
                activeRecordObservers--
            }
        }

    /** Exposes independently supplied live allocation facts.
     * @param id Requested parent.
     */
    override fun observeOffers(id: String): Flow<List<LotteryOffer>> =
        flow {
            observedOfferIds.add(id)
            activeOfferObservers++
            try {
                combine(liveOffers, offerFailure, offerFailures) { rows, failure, scopedFailures ->
                    (failure ?: scopedFailures[id])?.let { throw it }
                    rows[id].orEmpty()
                }.collect { emit(it) }
            } finally {
                activeOfferObservers--
            }
        }

    /** Returns controlled owner rows without implementing a global feature scan.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Lottery>> =
        flow {
            ownerRequests.add(ownerId)
            activeOwnerObservers++
            try {
                combine(records, ownerFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows.filter { it.ownerId == ownerId }
                }.collect { emit(it) }
            } finally {
                activeOwnerObservers--
            }
        }

    /** Rejects global reads until their actual administration consumer configures them. */
    override fun observeAll(): Flow<List<Lottery>> =
        unsupported()

    /** Exposes controlled lottery-scoped entries without any account-wide query.
     * @param id Requested lottery.
     */
    override fun observeEntries(id: String): Flow<List<LotteryEntry>> =
        flow {
            entriesRequests.add(id)
            activeEntriesObservers++
            try {
                combine(entries, entriesFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows.filter { it.lotteryId == id }
                }.collect { emit(it) }
            } finally {
                activeEntriesObservers--
            }
        }

    /** Exposes own membership fixtures with controlled failure and listener lifetime.
     * @param accountId Requested account.
     */
    override fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>> =
        flow {
            entrantRequests.add(accountId)
            activeEntrantObservers++
            try {
                combine(entries, entrantFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows.filter { it.accountId == accountId }
                }.collect { emit(it) }
            } finally {
                activeEntrantObservers--
            }
        }

    /** Exposes explicit scoped associations without reconstructing the persistence query.
     * @param campsiteId Requested campsite.
     */
    override fun observePublishedForCampsite(campsiteId: String): Flow<List<Lottery>> =
        flow {
            associationRequests.add(campsiteId)
            activeAssociationObservers++
            try {
                combine(associations, associationFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows[campsiteId].orEmpty()
                }.collect { emit(it) }
            } finally {
                activeAssociationObservers--
            }
        }

    /** Captures creation and requires an explicitly supplied outcome.
     * @param id Retained target.
     * @param ownerId Requesting owner.
     * @param title Submitted title.
     * @param description Submitted description.
     * @param configuration Submitted windows and limits.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ): Lottery {
        events.add("create:$id")
        val request = DetailsRequest(id, ownerId, title, description, configuration)
        detailsRequests.add(request)
        return checkNotNull(createHandler) { "Configure draft creation" }(request)
    }

    /** Captures an update without silently saving fixture data.
     * @param id Retained target.
     * @param ownerId Requesting owner.
     * @param title Submitted title.
     * @param description Submitted description.
     * @param configuration Submitted windows and limits.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ) {
        events.add("update:$id")
        val request = DetailsRequest(id, ownerId, title, description, configuration)
        detailsRequests.add(request)
        checkNotNull(updateHandler) { "Configure draft updates" }(request)
    }

    /** Captures replacement of one allocation.
     * @param id Retained parent.
     * @param ownerId Requesting owner.
     * @param offer Submitted allocation.
     */
    override suspend fun setOffer(
        id: String,
        ownerId: String,
        offer: LotteryOffer,
    ) {
        events.add("set:${offer.campsiteId}")
        offerRequests.add(offer)
        checkNotNull(setOfferHandler) { "Configure offer saving" }(id, ownerId, offer)
    }

    /** Captures an old allocation's removal, allowing uncertain outcomes to be supplied.
     * @param id Retained parent.
     * @param ownerId Requesting owner.
     * @param campsiteId Allocation to remove.
     */
    override suspend fun removeOffer(
        id: String,
        ownerId: String,
        campsiteId: String,
    ) {
        events.add("remove:$campsiteId")
        checkNotNull(removeOfferHandler) { "Configure offer removal" }(id, ownerId, campsiteId)
    }

    /** Captures publication without fabricating its acknowledgement.
     * @param id Retained target.
     * @param ownerId Requesting owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) {
        events.add("publish:$id")
        checkNotNull(publishHandler) { "Configure publication" }(id, ownerId)
    }

    /** Captures joining and requires an explicitly supplied outcome.
     * @param id Requested lottery.
     * @param accountId Requesting account.
     */
    override suspend fun join(
        id: String,
        accountId: String,
    ) {
        events.add("join:$id:$accountId")
        checkNotNull(joinHandler) { "Configure joining" }(id, accountId)
    }

    /** Captures withdrawal and requires an explicitly supplied outcome.
     * @param id Requested lottery.
     * @param accountId Requesting account.
     */
    override suspend fun withdraw(
        id: String,
        accountId: String,
    ) {
        events.add("withdraw:$id:$accountId")
        checkNotNull(withdrawHandler) { "Configure withdrawal" }(id, accountId)
    }

    /** Rejects unconfigured decline.
     * @param id Requested lottery.
     * @param accountId Requesting account.
     */
    override suspend fun decline(
        id: String,
        accountId: String,
    ): Unit =
        unsupported()

    /** Captures the saved identity and requires an explicit draw outcome.
     * @param id Requested lottery.
     */
    override suspend fun draw(id: String): DrawResult {
        events.add("draw:$id")
        return checkNotNull(drawHandler) { "Configure drawing" }(id)
    }

    /** Rejects unconfigured cancellation.
     * @param id Requested lottery.
     * @param actorId Requesting actor.
     * @param reason Optional supplied explanation.
     */
    override suspend fun cancel(
        id: String,
        actorId: String,
        reason: String?,
    ): Unit =
        unsupported()

    /** Fails on a test path that has no deliberately configured contract yet. */
    private fun unsupported(): Nothing =
        error("Configure this lottery operation before using it")
}
