package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences

/**
 * Application-scoped repositories and platform services supplied to ViewModel factories.
 * @param repositories Repository interfaces supplied by the selected application host.
 * @param identity Device-session adapter supplied by the selected application host.
 * @param preferences One installation-local preferences store.
 */
class AppServices(
    val repositories: AppRepositories,
    val identity: IdentityProvider,
    val preferences: LocalAppPreferences,
)
