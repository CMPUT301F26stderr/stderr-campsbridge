package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Controlled account dependency; writes fail unless a test explicitly enables them.
 * @param initial Initial observed account, including null for a missing profile.
 */
class FakeAccountRepository(
    initial: Account? = null,
) : AccountRepository {
    val account = MutableStateFlow(initial)
    val streamFailure = MutableStateFlow<Exception?>(null)
    var readFailure: Exception? = null
    var getHandler: (suspend (String) -> Account?)? = null
    val readIds = mutableListOf<String>()
    var observations = 0
    var activeObservers = 0
    var writes = 0
    var allowWrites = false
    var createCalls = 0
    val createdIds = mutableListOf<String>()
    var profileWrites = 0
    val profileUpdates = mutableListOf<ProfileUpdate>()
    var profileGate: CompletableDeferred<Unit>? = null
    var profileFailure: Exception? = null
    var preferenceWrites = 0
    var ownerWrites = 0
    var createGate: CompletableDeferred<Unit>? = null
    var createFailure: Exception? = null
    var createAcknowledgementFailure: Exception? = null
    var preferenceFailure: Exception? = null
    var ownerFailure: Exception? = null
    var ownerGate: CompletableDeferred<Unit>? = null
    var ownerHandler: (suspend (String) -> Unit)? = null
    val ownerIds = mutableListOf<String>()
    val settingsRequests = mutableListOf<Pair<String, AccountSettings>>()
    var settingsHandler: (suspend (String, AccountSettings) -> Unit)? = null

    /**
     * Returns controlled data without accessing Firebase.
     * @param id Requested account ID.
     */
    override suspend fun get(id: String): Account? {
        readIds.add(id)
        val handler = getHandler
        if (handler != null) return handler(id)
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

    /** Creates a controlled record when explicitly enabled, preserving existing facts on retry.
     * @param id Requested account ID.
     * @param profile Requested fields.
     */
    override suspend fun create(
        id: String,
        profile: ProfileUpdate,
    ): Account {
        if (!allowWrites) unexpectedWrite()
        writes++
        createCalls++
        createdIds += id
        createGate?.await()
        createFailure?.let { throw it }
        val result =
            account.value ?: Account(
                id,
                profile.name,
                profile.email,
                profile.phone,
                setOf(Role.ENTRANT),
                Preferences(),
                AccountSettings(),
                Instant.EPOCH,
            ).also { account.value = it }
        check(result.isActive)
        createAcknowledgementFailure?.let { throw it }
        return result
    }

    /** Updates only profile fields when the test explicitly permits mutations.
     * @param id Requested account ID.
     * @param profile Requested fields.
     */
    override suspend fun updateProfile(
        id: String,
        profile: ProfileUpdate,
    ) {
        if (!allowWrites) unexpectedWrite()
        writes++
        profileWrites++
        profileUpdates += profile
        profileGate?.await()
        profileFailure?.let { throw it }
        val current = checkNotNull(account.value)
        check(current.isActive)
        account.value =
            current.copy(name = profile.name, email = profile.email, phone = profile.phone)
    }

    /** Requires a configured settings result and records the exact requested replacement.
     * @param id Requested account ID.
     * @param settings Requested settings.
     */
    override suspend fun updateSettings(
        id: String,
        settings: AccountSettings,
    ) {
        settingsRequests.add(id to settings)
        val handler = settingsHandler ?: unexpectedWrite()
        handler(id, settings)
    }

    /** Replaces controlled interests when mutations are explicitly enabled.
     * @param id Requested account ID.
     * @param preferences Requested interests.
     */
    override suspend fun updatePreferences(
        id: String,
        preferences: Preferences,
    ) {
        if (!allowWrites) unexpectedWrite()
        writes++
        preferenceWrites++
        preferenceFailure?.let { throw it }
        account.value = checkNotNull(account.value).copy(preferences = preferences)
    }

    /** Adds owner capability only after the controlled persistence gate succeeds.
     * @param id Requested account ID.
     */
    override suspend fun enableOwner(id: String) {
        if (!allowWrites) unexpectedWrite()
        writes++
        ownerWrites++
        ownerIds.add(id)
        ownerGate?.await()
        ownerFailure?.let { throw it }
        ownerHandler?.let {
            it(id)
            return
        }
        val current = checkNotNull(account.value)
        check(current.isActive)
        account.value = current.copy(roles = current.roles + Role.OWNER)
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
