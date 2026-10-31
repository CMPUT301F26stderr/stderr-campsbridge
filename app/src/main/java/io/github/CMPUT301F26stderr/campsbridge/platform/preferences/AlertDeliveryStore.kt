package io.github.CMPUT301F26stderr.campsbridge.platform.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/** Installation-local post/suppression bookkeeping, independent of shared Inbox/profile facts. */
interface AlertDeliveryStore {
    /** Reads handled IDs for one account; storage and cancellation failures propagate.
     * @param accountId Established account identity.
     */
    suspend fun handledIds(accountId: String): Set<String>

    /** Adds one handled event atomically and idempotently without replacing other markers.
     * @param accountId Established account identity.
     * @param eventId Immutable event successfully posted or deliberately suppressed.
     */
    suspend fun markHandled(
        accountId: String,
        eventId: String,
    )
}

/** Persists only account-keyed handled event IDs in an application-owned Preferences DataStore.
 * @param store Single owner of the underlying file; may share unrelated preference keys.
 */
class DataStoreAlertDeliveryStore(
    private val store: DataStore<Preferences>,
) : AlertDeliveryStore {
    /** Reads the current account set; failures must never become a guessed empty history.
     * @param accountId Established account identity.
     */
    override suspend fun handledIds(accountId: String): Set<String> =
        store.data
            .first()[handledKey(accountId)]
            ?.toSet()
            .orEmpty()

    /** Atomically adds one ID while preserving other events, accounts and preference values.
     * @param accountId Established account identity.
     * @param eventId Successfully posted/suppressed immutable event.
     */
    override suspend fun markHandled(
        accountId: String,
        eventId: String,
    ) {
        store.edit { values ->
            val key = handledKey(accountId)
            values[key] = values[key].orEmpty() + eventId
        }
    }

    /** Identifies only one account's handled markers, separate from ordinary progress keys.
     * @param accountId Established account identity.
     */
    private fun handledKey(accountId: String): Preferences.Key<Set<String>> =
        stringSetPreferencesKey("alerts.$accountId.handled")
}
