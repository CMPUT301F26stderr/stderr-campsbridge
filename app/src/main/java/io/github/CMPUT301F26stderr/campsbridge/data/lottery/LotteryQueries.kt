package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Owns lottery read queries and the join through canonical campsite offer membership.
 * @param db Firestore client used for scoped queries and listeners.
 */
internal class LotteryQueries(
    private val db: FirebaseFirestore,
) {
    /**
     * Builds a validated reference to the lottery document.
     *
     * @param id Lottery document ID.
     */
    private fun ref(id: String) =
        db.document(FirestorePaths.lottery(id))

    /**
     * Returns the lottery record, or null when absent.
     *
     * @param id Lottery document ID.
     */
    suspend fun get(id: String) =
        ref(id).read(LotteryDocument)

    /**
     * Observes one lottery, emitting null when its document is absent.
     *
     * @param id Lottery document ID.
     */
    fun observe(id: String) =
        ref(id).observe(LotteryDocument)

    /**
     * Observes all readable lotteries, including drafts and historical records.
     */
    fun observeAll() =
        db.collection("lotteries").observe(LotteryDocument)

    /**
     * Observes lotteries created by the specified owner.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    fun observeForOwner(ownerId: String) =
        db.collection("lotteries").whereEqualTo("ownerId", ownerId).observe(LotteryDocument)

    /**
     * Observes the campsite/night allocations belonging to a lottery.
     *
     * @param id Lottery document ID.
     */
    fun observeOffers(id: String) =
        ref(id).collection("offers").observe(LotteryOfferDocument)

    /**
     * Observes participation records; callers show draw outcomes only after draw completion.
     *
     * @param id Lottery document ID.
     */
    fun observeEntries(id: String) =
        ref(id).collection("entries").observe(LotteryEntryDocument)

    /**
     * Observes an account’s entries across lotteries using the queryable account ID.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    fun observeForEntrant(accountId: String) =
        db
            .collectionGroup("entries")
            .whereEqualTo("accountId", accountId)
            .observe(LotteryEntryDocument)

    /**
     * Joins matching offer identities with the site's owner's published, noncancelled lotteries.
     * Orders registration opening first, then stable ID; time eligibility remains caller-owned.
     * @param campsiteId Published campsite whose associated lotteries are requested.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun publishedForCampsite(campsiteId: String): Flow<List<Lottery>> =
        db
            .document(FirestorePaths.campsite(campsiteId))
            .observe(CampsiteDocument)
            .map { site -> site?.takeIf { it.isPublished }?.ownerId }
            .distinctUntilChanged()
            .flatMapLatest { ownerId ->
                if (ownerId == null) {
                    flowOf(emptyList())
                } else {
                    val memberships =
                        db
                            .collectionGroup("offers")
                            .whereEqualTo("campsiteId", campsiteId)
                            .observe(::lotteryIdForOffer)
                    val published =
                        db
                            .collection("lotteries")
                            .whereEqualTo("ownerId", ownerId)
                            .whereNotEqualTo("publishedAt", null)
                            .observe(LotteryDocument)
                    combine(memberships, published) { ids, lotteries ->
                        val members = ids.toSet()
                        lotteries
                            .filter { it.id in members && it.cancellation == null }
                            .sortedWith(
                                compareBy<Lottery> {
                                    it.configuration.registrationOpensAt
                                }.thenBy { it.id },
                            )
                    }
                }
            }.distinctUntilChanged()
}

/**
 * Validates an offer and recovers its owning lottery from the canonical document path.
 * @param document Matching offer snapshot; SDK context remains inside data code.
 */
private fun lotteryIdForOffer(document: DocumentSnapshot): String {
    val parent = document.reference.parent.parent
    if (parent == null || parent.parent.path != "lotteries") {
        throw DocumentDecodingException(document.reference.path, "Expected a lottery offer")
    }
    LotteryOfferDocument.decode(document)
    return parent.id
}
