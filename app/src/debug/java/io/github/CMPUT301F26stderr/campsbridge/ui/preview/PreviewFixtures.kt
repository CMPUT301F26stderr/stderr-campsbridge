package io.github.CMPUT301F26stderr.campsbridge.ui.preview

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import java.time.Instant

/**
 * Realistic existing account fixture shared by debug hosts and tests.
 * @param roles Capabilities exposed by the fixture.
 */
fun sampleAccount(roles: Set<Role> = setOf(Role.ENTRANT)): Account =
    Account(
        "account-1",
        "Alex Chen",
        "alex@example.com",
        null,
        roles,
        Preferences(),
        AccountSettings(),
        Instant.parse("2026-10-01T12:00:00Z"),
    )
