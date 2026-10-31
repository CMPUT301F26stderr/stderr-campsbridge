package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import java.time.Clock

/**
 * Application-scoped repositories and platform services supplied to ViewModel factories.
 * @param repositories Repository interfaces supplied by the selected application host.
 * @param identity Device-session adapter supplied by the selected application host.
 * @param preferences One installation-local preferences store.
 * @param clock Shared time source used by persistence and feature factories.
 * @param foregroundWork Real composed work; offline hosts default to their existing local work.
 */
class AppServices(
    val repositories: AppRepositories,
    val identity: IdentityProvider,
    val preferences: LocalAppPreferences,
    val clock: Clock = Clock.systemUTC(),
    val foregroundWork: PendingWork = repositories.pendingWork,
)
