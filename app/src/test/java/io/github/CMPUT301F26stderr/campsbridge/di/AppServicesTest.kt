package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

/** Two fixed composition contracts keep offline defaults and real bindings explicit and inert. */
class AppServicesTest {
    /** Offline composition retains its local pending work without creating OS/Firebase effects. */
    @Test fun defaultForegroundWorkIsExactlyTheSuppliedRepositoryWork() {
        val repositories = mock(AppRepositories::class.java)
        val pending = mock(PendingWork::class.java)
        val identity = mock(IdentityProvider::class.java)
        val preferences = mock(LocalAppPreferences::class.java)
        `when`(repositories.pendingWork).thenReturn(pending)
        val services = AppServices(repositories, identity, preferences)
        assertSame(pending, services.foregroundWork)
        verify(repositories).pendingWork
        verifyNoMoreInteractions(repositories)
        verifyNoInteractions(pending, identity, preferences)
    }

    /** The real host can supply composed work without invoking any dependency at construction. */
    @Test fun explicitForegroundWorkIsRetainedWithoutStartingAnything() {
        val repositories = mock(AppRepositories::class.java)
        val pending = mock(PendingWork::class.java)
        val identity = mock(IdentityProvider::class.java)
        val preferences = mock(LocalAppPreferences::class.java)
        val services = AppServices(repositories, identity, preferences, foregroundWork = pending)
        assertSame(pending, services.foregroundWork)
        verifyNoInteractions(repositories, pending, identity, preferences)
    }
}
