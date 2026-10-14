package io.github.CMPUT301F26stderr.campsbridge.domain.account

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owns profile validity and accessibility-setting boundaries, independent of storage. */
class AccountTest {
    /** Active profiles need a visible name and at least one capability. */
    @Test fun activeProfilesRequireNameAndRoles() {
        val account = CampingFixtures.account()
        assertThrows(IllegalArgumentException::class.java) { account.copy(name = "  ") }
        assertThrows(IllegalArgumentException::class.java) { account.copy(roles = emptySet()) }
        assertTrue(account.copy(roles = setOf(Role.ENTRANT, Role.OWNER, Role.ADMIN)).isActive)
    }

    /** Removal permits the scrubbed profile shape while retaining historical identity. */
    @Test fun removedProfilesCanBeScrubbed() {
        val original = CampingFixtures.account()
        val removed =
            original.copy(
                name = "",
                email = null,
                phone = null,
                roles = emptySet(),
                removal = Removal(CampingFixtures.now, "admin"),
            )
        assertFalse(removed.isActive)
        assertEquals(original.id, removed.id)
    }

    /** Accessibility scales accept finite positive values without an invented maximum. */
    @Test fun accessibilityScalesAcceptPositiveValuesOnly() {
        for (scale in listOf(0.25, 1.0, 4.0)) {
            assertEquals(
                scale,
                AccountSettings(fontScale = scale, controlScale = scale).fontScale,
                0.0,
            )
        }
        for (scale in listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
        )) {
            assertThrows(
                IllegalArgumentException::class.java,
            ) { AccountSettings(fontScale = scale) }
            assertThrows(
                IllegalArgumentException::class.java,
            ) { AccountSettings(controlScale = scale) }
        }
    }
}
