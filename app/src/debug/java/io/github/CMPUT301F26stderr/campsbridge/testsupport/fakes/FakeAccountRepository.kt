package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Controlled read dependency; unexpected profile writes fail immediately.
 * @param initial Initial observed account, including null for a missing profile.
 */
class FakeAccountRepository(
    initial: Account? = null,
) : AccountRepository {
    val account = MutableStateFlow(initial)
    val streamFailure = MutableStateFlow<Exception?>(null)
    var readFailure: Exception? = null
    var observations = 0
    var activeObservers = 0
    var writes = 0

    /**
     * Returns controlled data without accessing Firebase.
     * @param id Requested account ID.
     */
    override suspend fun get(id: String): Account? {
        readFailure?.let { throw it }
        return account.value
    }

    /**
     * Tracks the lifetime of each controlled subscription.
     * @param id Requested account ID.
     */
    override fun observe(id: String): Flow<Account?> =
        flow {
            readFailure?.let { throw it }
            observations++
            activeObservers++
            try {
                emitAll(
                    combine(account, streamFailure) { record, failure ->
                        if (failure != null) throw failure
                        record
                    },
                )
            } finally {
                activeObservers--
            }
        }

    /** Maps the controlled single record for debug browsing. */
    override fun observeAll(): Flow<List<Account>> =
        account.map { listOfNotNull(it) }

    /** Rejects unexpected account creation.
     * @param id Requested account ID.
     * @param profile Requested fields.
     */
    override suspend fun create(
        id: String,
        profile: ProfileUpdate,
    ): Account =
        unexpectedWrite()

    /** Rejects unexpected profile mutation.
     * @param id Requested account ID.
     * @param profile Requested fields.
     */
    override suspend fun updateProfile(
        id: String,
        profile: ProfileUpdate,
    ) {
        unexpectedWrite()
    }

    /** Rejects unexpected settings mutation.
     * @param id Requested account ID.
     * @param settings Requested settings.
     */
    override suspend fun updateSettings(
        id: String,
        settings: AccountSettings,
    ) {
        unexpectedWrite()
    }

    /** Rejects unexpected preference mutation.
     * @param id Requested account ID.
     * @param preferences Requested preferences.
     */
    override suspend fun updatePreferences(
        id: String,
        preferences: Preferences,
    ) {
        unexpectedWrite()
    }

    /**
     * Rejects unexpected capability mutation.
     * @param id Requested account ID.
     */
    override suspend fun enableOwner(id: String) {
        unexpectedWrite()
    }

    /** Rejects unexpected device registration.
     * @param id Requested account ID.
     * @param registration Requested token.
     */
    override suspend fun registerDevice(
        id: String,
        registration: DeviceRegistration,
    ) {
        unexpectedWrite()
    }

    /** Rejects unexpected device removal.
     * @param id Requested account ID.
     * @param installationId Requested installation.
     */
    override suspend fun unregisterDevice(
        id: String,
        installationId: String,
    ) {
        unexpectedWrite()
    }

    /** Records and rejects writes not part of the read-only foundation. */
    private fun unexpectedWrite(): Nothing {
        writes++
        error("Unexpected account mutation")
    }
}
