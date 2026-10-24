package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock

/** In-memory account operations for the offline demo; no Firebase or failure-injection hooks.
 * @param initial Scenario's starting profiles.
 * @param clock Timestamp source for new demo records.
 */
class DemoAccountRepository(
    initial: List<Account>,
    private val clock: Clock = Clock.systemUTC(),
) : AccountRepository {
    private val records = MutableStateFlow(initial.associateBy { it.id })
    private val devices = mutableMapOf<Pair<String, String>, DeviceRegistration>()

    /** Reads only this scenario's profile map.
     * @param id Requested account identity.
     */
    override suspend fun get(id: String): Account? =
        records.value[id]

    /** Observes one identity without opening external listeners.
     * @param id Requested account identity.
     */
    override fun observe(id: String) =
        records.map { it[id] }.distinctUntilChanged()

    /** Observes the scenario's current profiles. */
    override fun observeAll() =
        records.map { it.values.toList() }

    /** Creates one default entrant, preserving existing active facts on retry.
     * @param id Established demo identity.
     * @param profile Requested initial profile fields.
     */
    override suspend fun create(
        id: String,
        profile: ProfileUpdate,
    ): Account {
        val existing = records.value[id]
        if (existing != null) {
            check(existing.isActive)
            return existing
        }
        val record =
            Account(
                id,
                profile.name,
                profile.email,
                profile.phone,
                setOf(Role.ENTRANT),
                Preferences(),
                AccountSettings(),
                clock.instant(),
            )
        records.update { if (id in it) it else it + (id to record) }
        return checkNotNull(records.value[id]).also { check(it.isActive) }
    }

    /** Changes profile fields without changing capabilities or preferences.
     * @param id Existing account identity.
     * @param profile Requested profile fields.
     */
    override suspend fun updateProfile(
        id: String,
        profile: ProfileUpdate,
    ) {
        edit(id) { it.copy(name = profile.name, email = profile.email, phone = profile.phone) }
    }

    /** Changes only alert and accessibility settings.
     * @param id Existing account identity.
     * @param settings Requested settings.
     */
    override suspend fun updateSettings(
        id: String,
        settings: AccountSettings,
    ) {
        edit(id) { it.copy(settings = settings) }
    }

    /** Changes only camping interests.
     * @param id Existing account identity.
     * @param preferences Requested interests.
     */
    override suspend fun updatePreferences(
        id: String,
        preferences: Preferences,
    ) {
        edit(id) { it.copy(preferences = preferences) }
    }

    /** Adds owner capability without removing entrant access.
     * @param id Existing account identity.
     */
    override suspend fun enableOwner(id: String) {
        edit(id) { it.copy(roles = it.roles + Role.OWNER) }
    }

    /** Keeps registration local; the demo sends no push messages.
     * @param id Existing account identity.
     * @param registration Local registration value.
     */
    override suspend fun registerDevice(
        id: String,
        registration: DeviceRegistration,
    ) {
        check(records.value[id]?.isActive == true)
        devices[id to registration.installationId] = registration.copy(updatedAt = clock.instant())
    }

    /** Removes a local registration idempotently.
     * @param id Account identity.
     * @param installationId Local installation identity.
     */
    override suspend fun unregisterDevice(
        id: String,
        installationId: String,
    ) {
        devices.remove(id to installationId)
    }

    /** Replaces one active record while preserving every other identity.
     * @param id Existing account identity.
     * @param transform Requested account update.
     */
    private fun edit(
        id: String,
        transform: (Account) -> Account,
    ) {
        records.update { current ->
            val account = checkNotNull(current[id])
            check(account.isActive)
            current + (id to transform(account))
        }
    }
}
