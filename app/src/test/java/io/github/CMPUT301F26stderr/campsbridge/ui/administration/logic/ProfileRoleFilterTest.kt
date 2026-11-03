package io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Seven fixed pure role-filter contracts for the real read-only Browse caller. */
class ProfileRoleFilterTest {
    private val entrant = sampleAccount(setOf(Role.ENTRANT)).copy(id = "entrant")
    private val owner = sampleAccount(setOf(Role.OWNER)).copy(id = "owner")
    private val both = sampleAccount(setOf(Role.ENTRANT, Role.OWNER)).copy(id = "both")
    private val admin = sampleAccount(setOf(Role.ADMIN)).copy(id = "admin")
    private val removed =
        entrant.copy(
            id = "removed",
            name = "",
            roles = emptySet(),
            removal = Removal(Instant.EPOCH, "admin"),
        )

    /** Empty input stays empty for no filter and every actual capability. */
    @Test fun emptyInputHasNoMembersForAnyRole() {
        for (role in listOf(null) + Role.entries) {
            assertEquals(emptyList<Account>(), filterProfilesByRole(emptyList(), role))
        }
    }

    /** No requested filter preserves retained/removed/repeated input records in their order. */
    @Test fun noFilterReturnsAllOriginalRecordsIncludingRemovedAndRepeatedRows() {
        val rows = listOf(admin, removed, both, entrant, owner, both)
        assertEquals(rows, filterProfilesByRole(rows, null))
    }

    /** Entrant membership includes multirole accounts without granting unrelated capabilities. */
    @Test fun entrantIncludesItsActualMultiroleMembers() {
        assertEquals(
            listOf(both, entrant),
            filterProfilesByRole(listOf(owner, both, admin, entrant), Role.ENTRANT),
        )
    }

    /** Owner filtering retains source order instead of sorting names or IDs. */
    @Test fun ownerMembersStayInSourceOrder() {
        val rows = listOf(both, entrant, owner, admin)
        assertEquals(listOf(both, owner), filterProfilesByRole(rows, Role.OWNER))
    }

    /** Admin is an actual held capability, including when combined with other capabilities. */
    @Test fun adminUsesActualMembershipRatherThanOwnerOrEntrantPresence() {
        val combined = owner.copy(id = "combined", roles = setOf(Role.OWNER, Role.ADMIN))
        assertEquals(
            listOf(admin, combined),
            filterProfilesByRole(listOf(owner, admin, entrant, combined), Role.ADMIN),
        )
    }

    /** This helper has no implicit moderation/removal policy beyond supplied role membership. */
    @Test fun retainedRemovedMemberIsNotSilentlyHidden() {
        val retained = owner.copy(removal = Removal(Instant.EPOCH, "admin"))
        assertEquals(
            listOf(retained),
            filterProfilesByRole(listOf(removed, retained, entrant), Role.OWNER),
        )
    }

    /** Filtering preserves every field and repetition without mutating a caller-owned list. */
    @Test fun inputAndReturnedAccountFieldsStayUnchanged() {
        val detailed =
            both.copy(
                name = "River Camper",
                email = "river@example.ca",
                phone = "780-555-0100",
            )
        val input = mutableListOf(admin, detailed, owner, detailed)
        val before = input.toList()
        assertEquals(listOf(detailed, owner, detailed), filterProfilesByRole(input, Role.OWNER))
        assertEquals(before, input)
    }
}
