package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences

/**
 * Application-scoped repositories and platform services supplied to ViewModel factories.
 * @param repositories Existing persistence composition root.
 * @param identity Device-session adapter for the selected Firebase environment.
 * @param preferences One installation-local preferences store.
 */
class AppServices(
    val repositories: AppContainer,
    val identity: IdentityProvider,
    val preferences: LocalAppPreferences,
)
