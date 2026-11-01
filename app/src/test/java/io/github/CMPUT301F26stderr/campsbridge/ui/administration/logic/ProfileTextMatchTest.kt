package io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

/** Eight fixed pure profile-search contracts for the actual Browse query dependency. */
class ProfileTextMatchTest {
    private val account =
        sampleAccount().copy(
            id = "camper-I_日本?",
            name = "Alex River",
            email = "alex@example.ca",
            phone = "780-555-0199",
            preferences = Preferences(tags = setOf("solar")),
        )

    /** Blank raw queries impose no filter, including on a scrubbed retained account. */
    @Test fun blankAndWhitespaceQueriesMatchAnyRetainedAccount() {
        val scrubbed =
            account.copy(
                name = "",
                email = null,
                roles = emptySet(),
                removal = Removal(Instant.EPOCH, "admin"),
            )
        for (row in listOf(account, scrubbed)) {
            for (query in listOf("", " ", "\t\n")) assertTrue(matchesProfileText(row, query))
        }
    }

    /** Trimmed query matches a case-insensitive substring inside the original display name. */
    @Test fun nameMatchingTrimsOnlyTheQueryAndIgnoresCase() {
        assertTrue(matchesProfileText(account, "  rIvEr  "))
        assertTrue(matchesProfileText(account, "LEX RI"))
        assertFalse(matchesProfileText(account, "Riverbank"))
    }

    /** Optional contact email matches without treating null as searchable text. */
    @Test fun optionalEmailMatchesAndNullEmailRemainsSafe() {
        assertTrue(matchesProfileText(account, " EXAMPLE.CA "))
        val noEmail = account.copy(email = null)
        assertFalse(matchesProfileText(noEmail, "example.ca"))
        assertFalse(matchesProfileText(noEmail, "null"))
    }

    /** Identity search retains Unicode and punctuation without interpreting them as patterns. */
    @Test fun identitySupportsLiteralUnicodeAndPunctuationSubstrings() {
        assertTrue(matchesProfileText(account, "I_日本?"))
        assertTrue(matchesProfileText(account, " CAMPER-I_ "))
        assertFalse(matchesProfileText(account, "camper.*"))
    }

    /** Device locale does not change ROOT case folding for account identity/name/email. */
    @Test fun turkishDefaultStillUsesRootCaseFolding() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(matchesProfileText(account.copy(name = "INDIGO"), "indigo"))
            assertTrue(matchesProfileText(account, "camper-i_"))
            assertTrue(matchesProfileText(account.copy(email = "INFO@example.ca"), "info@"))
        } finally {
            Locale.setDefault(before)
        }
    }

