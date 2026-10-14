package io.github.CMPUT301F26stderr.campsbridge.domain.account

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import java.time.Instant

/**
 * Capabilities an account can hold simultaneously; the selected UI role is separate state.
 */
enum class Role {
    ENTRANT,
    OWNER,
    ADMIN,
}

/**
 * Shared color-palette preference for standard and color-blind presentation.
 */
enum class ColorMode {
    STANDARD,
    COLOR_BLIND,
}

/**
 * Camping characteristics used to personalize discovery and recommendations.
 *
 * @param tags Unique campsite characteristics or preferred characteristics.
 * @param amenities Unique facilities or preferred facilities.
 */
data class Preferences(
    val tags: Set<String> = emptySet(),
    val amenities: Set<String> = emptySet(),
)

/**
 * Persisted alert and accessibility preferences with finite positive size scales.
 *
 * @param notificationsEnabled Whether alerts are enabled; inbox history is still retained.
 * @param colorMode Shared standard or color-blind palette preference.
 * @param fontScale Finite positive application text scale, applied alongside system scaling.
 * @param controlScale Finite positive application control-size scale.
 */
data class AccountSettings(
    val notificationsEnabled: Boolean = true,
    val colorMode: ColorMode = ColorMode.STANDARD,
    val fontScale: Double = 1.0,
    val controlScale: Double = 1.0,
) {
    init {
        require(fontScale.isFinite() && fontScale > 0)
        require(controlScale.isFinite() && controlScale > 0)
    }
}

/**
 * Device-linked profile and capabilities; removed accounts retain their identity for history.
 *
 * @param id Account document ID.
 * @param name Display name shown in the application.
 * @param email Optional contact email; not a login credential.
 * @param phone Optional contact phone number.
 * @param roles Capabilities held by the account; several may coexist.
 * @param preferences Camping interests used to personalize discovery.
 * @param settings Notification and accessibility preferences.
 * @param createdAt Original record creation time.
 * @param removal Removal metadata, or null for an account/listing not marked removed.
 */
data class Account(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val roles: Set<Role>,
    val preferences: Preferences,
    val settings: AccountSettings,
    val createdAt: Instant,
    val removal: Removal? = null,
) {
    init {
        require(removal != null || (name.isNotBlank() && roles.isNotEmpty()))
    }

    val isActive: Boolean
        get() = removal == null
}

/**
 * Push token for an installation; the token does not establish account identity.
 *
 * @param installationId Installation ID identifying the account’s device registration.
 * @param fcmToken Push token for this installation.
 * @param updatedAt Time this record was most recently updated.
 */
data class DeviceRegistration(
    val installationId: String,
    val fcmToken: String,
    val updatedAt: Instant,
)

/**
 * Editable profile fields, independent of roles, settings, and account identity.
 *
 * @param name Display name shown in the application.
 * @param email Optional contact email; not a login credential.
 * @param phone Optional contact phone number.
 */
data class ProfileUpdate(
    val name: String,
    val email: String?,
    val phone: String?,
)
