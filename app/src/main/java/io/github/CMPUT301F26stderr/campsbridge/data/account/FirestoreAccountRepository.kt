package io.github.CMPUT301F26stderr.campsbridge.data.account

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.tasks.await
import java.time.Clock

/**
 * Firebase-backed implementation for account persistence, preferences, roles, and installation
 * token registration.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirestoreAccountRepository(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : AccountRepository {
    /**
     * Returns the stored account, or null when its document is absent.
     *
     * @param id Account document ID.
     */
    override suspend fun get(id: String) =
        db.document(FirestorePaths.account(id)).read(AccountDocument)

    /**
     * Observes the account, emitting null if its document is absent.
     *
     * @param id Account document ID.
     */
    override fun observe(id: String) =
        db.document(FirestorePaths.account(id)).observe(AccountDocument)

    /**
     * Observes all readable account records, including retained removal records.
     */
    override fun observeAll() =
        db.collection("accounts").observe(AccountDocument)

    /**
     * Creates a default entrant profile or returns the existing active account without
     * replacing it.
     *
     * @param id Account document ID.
     * @param profile Replacement name/contact fields.
     */
    override suspend fun create(
        id: String,
        profile: ProfileUpdate,
    ): Account {
        require(profile.name.isNotBlank())
        val reference = db.document(FirestorePaths.account(id))
        return db.atomic { tx ->
            val existing = tx.read(reference, AccountDocument)
            if (existing != null) {
                requireOperation(existing.isActive, INACTIVE, "Account removed")
                existing
            } else {
                val account =
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
                tx.set(reference, AccountDocument.encode(account))
                account
            }
        }
    }

    /**
     * Applies a side-effect-free account transform within a retryable transaction.
     *
     * @param id Account document ID.
     * @param transform Pure record transformation; may run again if the transaction retries.
     */
    private suspend fun change(
        id: String,
        transform: (Account) -> Account,
    ) {
        db.atomic { tx ->
            val account = tx.activeAccount(db, id)
            tx.set(
                db.document(FirestorePaths.account(id)),
                AccountDocument.encode(transform(account)),
            )
        }
    }

    /**
     * Replaces name and contact details while retaining account identity and other settings.
     *
     * @param id Account document ID.
     * @param profile Replacement name/contact fields.
     */
    override suspend fun updateProfile(
        id: String,
        profile: ProfileUpdate,
    ) {
        require(profile.name.isNotBlank())
        change(id) { it.copy(name = profile.name, email = profile.email, phone = profile.phone) }
    }

    /**
     * Replaces the account’s alert and accessibility preferences.
     *
     * @param id Account document ID.
     * @param settings Notification and accessibility preferences.
     */
    override suspend fun updateSettings(
        id: String,
        settings: AccountSettings,
    ) =
        change(id) { it.copy(settings = settings) }

    /**
     * Replaces the account’s camping interests used for recommendations.
     *
     * @param id Account document ID.
     * @param preferences Camping interests used to personalize discovery.
     */
    override suspend fun updatePreferences(
        id: String,
        preferences: Preferences,
    ) =
        change(id) { it.copy(preferences = preferences) }

    /**
     * Adds owner capability without removing existing roles; repeat calls are harmless.
     *
     * @param id Account document ID.
     */
    override suspend fun enableOwner(id: String) =
        change(id) { it.copy(roles = it.roles + Role.OWNER) }

    /**
     * Stores an installation token with the current update time for an active account.
     *
     * @param id Account document ID.
     * @param registration Installation/token record to save.
     */
    override suspend fun registerDevice(
        id: String,
        registration: DeviceRegistration,
    ) {
        require(registration.fcmToken.isNotBlank())
        db.atomic { tx ->
            tx.activeAccount(db, id)
            tx.set(
                db.document(FirestorePaths.device(id, registration.installationId)),
                DeviceRegistrationDocument.encode(registration.copy(updatedAt = clock.instant())),
            )
        }
    }

    /**
     * Deletes the installation’s push-token registration; absent registrations are harmless.
     *
     * @param id Account document ID.
     * @param installationId Installation ID identifying the account’s device registration.
     */
    override suspend fun unregisterDevice(
        id: String,
        installationId: String,
    ) {
        db.document(FirestorePaths.device(id, installationId)).delete().await()
    }
}
