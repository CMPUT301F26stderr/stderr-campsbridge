package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import org.junit.Assert.assertEquals
import org.junit.Test

/** Explicit capability priority and preference contracts independent of UI state. */
class WorkspaceSelectionTest {
    /** A preference is honored only when permitted; otherwise use entrant, owner, admin. */
    @Test fun preferenceNeverGrantsCapability() {
        assertEquals(null, permittedWorkspace(emptySet(), null))
        assertEquals(
            Role.OWNER,
            permittedWorkspace(setOf(Role.ENTRANT, Role.OWNER, Role.ADMIN), Role.OWNER),
        )
        assertEquals(
            Role.ENTRANT,
            permittedWorkspace(setOf(Role.ENTRANT, Role.OWNER, Role.ADMIN), null),
        )
        assertEquals(Role.OWNER, permittedWorkspace(setOf(Role.OWNER, Role.ADMIN), Role.ENTRANT))
        assertEquals(Role.ADMIN, permittedWorkspace(setOf(Role.ADMIN), null))
        assertEquals(Role.ADMIN, permittedWorkspace(setOf(Role.ADMIN), Role.ADMIN))
    }
}
