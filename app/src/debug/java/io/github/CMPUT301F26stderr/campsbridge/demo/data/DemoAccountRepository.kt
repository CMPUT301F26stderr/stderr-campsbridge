package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import java.time.Clock

/** Local account operations for the offline demo without Firebase.
 * @param initial Scenario's starting profiles.
 * @param clock Timestamp source for new demo records.
 * @param store Shared local state; isolated by default for helper examples.
 */
class DemoAccountRepository(
    initial: List<Account>,
    private val clock: Clock = Clock.systemUTC(),
    private val store: DemoStore =
        DemoStore(
            initial.associate {
                FirestorePaths.account(it.id) to
                    AccountDocument.encode(it)
            },
        ),
) : AccountRepository {
    private val devices = mutableMapOf<Pair<String, String>, DeviceRegistration>()

    /** Reads a saved profile.
     * @param id Requested account identity.
     */
    override suspend fun get(id: String): Account? =
        store.read { get(FirestorePaths.account(id), AccountDocument) }

    /** Observes one identity without opening external listeners.
     * @param id Requested account identity.
     */
    override fun observe(id: String) =
        store.observe { get(FirestorePaths.account(id), AccountDocument) }

    /** Observes all saved profiles. */
    override fun observeAll() =
        store.observe { list("accounts", AccountDocument) }

    /** Creates one default entrant, preserving existing active facts on retry.
     * @param id Established demo identity.
     * @param profile Requested initial profile fields.
     */
    override suspend fun create(
        id: String,
        profile: ProfileUpdate,
    ): Account =
        store.change {
            val existing = get(FirestorePaths.account(id), AccountDocument)
            if (existing != null) {
                check(existing.isActive)
                existing
            } else {
                Account(
                    id,
                    profile.name,
                    profile.email,
                    profile.phone,
                    setOf(Role.ENTRANT),
                    Preferences(),
                    AccountSettings(),
                    clock.instant(),
                ).also {
                    put(FirestorePaths.account(id), AccountDocument, it)
                }
            }
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

    /** Updates only the name, preserving the latest contacts.
     * @param id Existing account identity.
     * @param name Requested nonblank display name.
     */
    override suspend fun updateName(
        id: String,
        name: String,
    ) {
        edit(id) { it.copy(name = name) }
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
        check(store.read { get(FirestorePaths.account(id), AccountDocument)?.isActive } == true)
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
    private suspend fun edit(
        id: String,
        transform: (Account) -> Account,
    ) {
        store.change {
            val account = active(id)
            put(FirestorePaths.account(id), AccountDocument, transform(account))
        }
    }
}
