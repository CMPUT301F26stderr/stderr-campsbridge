package io.github.CMPUT301F26stderr.campsbridge.domain.account

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for account persistence, preferences, roles, and installation token
 * registration.
 */
interface AccountRepository {
    /**
     * Returns the stored account, or null when its document is absent.
     *
     * @param id Account document ID.
     */
    suspend fun get(id: String): Account?

    /**
     * Observes the account, emitting null if its document is absent.
     *
     * @param id Account document ID.
     */
    fun observe(id: String): Flow<Account?>

    /**
     * Observes all readable account records, including retained removal records.
     */
    fun observeAll(): Flow<List<Account>>

    /**
     * Creates a default entrant profile or returns the existing active account without
     * replacing it.
     *
     * @param id Account document ID.
     * @param profile Replacement name/contact fields.
     */
    suspend fun create(
        id: String,
        profile: ProfileUpdate,
    ): Account

    /**
     * Replaces name and contact details while retaining account identity and other settings.
     *
     * @param id Account document ID.
     * @param profile Replacement name/contact fields.
     */
    suspend fun updateProfile(
        id: String,
        profile: ProfileUpdate,
    )

    /**
     * Replaces the account’s alert and accessibility preferences.
     *
     * @param id Account document ID.
     * @param settings Notification and accessibility preferences.
     */
    suspend fun updateSettings(
        id: String,
        settings: AccountSettings,
    )

    /**
     * Replaces the account’s camping interests used for recommendations.
     *
     * @param id Account document ID.
     * @param preferences Camping interests used to personalize discovery.
     */
    suspend fun updatePreferences(
        id: String,
        preferences: Preferences,
    )

    /**
     * Adds owner capability without removing existing roles; repeat calls are harmless.
     *
     * @param id Account document ID.
     */
    suspend fun enableOwner(id: String)

    /**
     * Stores an installation token with the current update time for an active account.
     *
     * @param id Account document ID.
     * @param registration Installation/token record to save.
     */
    suspend fun registerDevice(
        id: String,
        registration: DeviceRegistration,
    )

    /**
     * Deletes the installation’s push-token registration; absent registrations are harmless.
     *
     * @param id Account document ID.
     * @param installationId Installation ID identifying the account’s device registration.
     */
    suspend fun unregisterDevice(
        id: String,
        installationId: String,
    )
}
