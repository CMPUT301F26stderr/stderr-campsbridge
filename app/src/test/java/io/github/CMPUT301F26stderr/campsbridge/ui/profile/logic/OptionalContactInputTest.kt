package io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Deterministic F02 contract tests; no Android, persistence or production parser fallback. */
class OptionalContactInputTest {
    /** Blank optional fields deliberately clear the saved contact, including whitespace-only input. */
    @Test fun blankFieldsClearBothContacts() {
        for (text in listOf("", " ", "\t\n  ")) {
            assertEquals(OptionalContactInput.Valid(null), parseOptionalEmail(text))
            assertEquals(OptionalContactInput.Valid(null), parseOptionalPhone(text))
        }
    }

    /** Accepted email spelling is preserved; only outside whitespace is removed. */
    @Test fun acceptedEmailsKeepSpellingCaseAndUnicode() {
        val cases =
            listOf(
                "a@b" to "a@b",
                "  Name+tag@Example.COM  " to "Name+tag@Example.COM",
                "\tδοκιμή@παράδειγμα\n" to "δοκιμή@παράδειγμα",
                "user@localhost" to "user@localhost",
                "x".repeat(256) + "@b" to "x".repeat(256) + "@b",
            )
        for ((text, expected) in cases) {
            assertEquals(
                OptionalContactInput.Valid(expected),
                parseOptionalEmail(text),
            )
        }
    }

    /** Exactly one @ with two nonempty whitespace-free parts is the complete email rule. */
    @Test fun invalidEmailPartsAtSignsAndWhitespaceAreRejected() {
        for (text in listOf(
            "@",
            "a@",
            "@b",
            "a",
            "a@@b",
            "a@b@c",
            "a b@c",
            "a@b c",
            "a\tb@c",
            "a@b\nc",
        )) {
            assertEquals(text, OptionalContactInput.Invalid, parseOptionalEmail(text))
        }
    }

    /** Phone values stay strings; leading zeros and permitted internal punctuation are retained. */
    @Test fun acceptedPhonesKeepPermittedCharactersWithoutExtraFormatting() {
        val cases =
            listOf(
                "1" to "1",
                "+ 1" to "+ 1",
                "001" to "001",
                "  +1 (780) 123-4567  " to "+1 (780) 123-4567",
                "\t(780) 123 4567\n" to "(780) 123 4567",
                ")1(" to ")1(",
                "1".repeat(256) to "1".repeat(256),
            )
        for ((text, expected) in cases) {
            assertEquals(
                OptionalContactInput.Valid(expected),
                parseOptionalPhone(text),
            )
        }
    }

    /** A phone needs an ASCII digit and may contain only the explicitly permitted characters. */
    @Test fun missingDigitsAndDisallowedPhoneCharactersAreRejected() {
        for (text in listOf(
            "+",
            "() -",
            "abc",
            "123x4",
            "1.2",
            "1/2",
            "1\t2",
            "1\n2",
            "١",
            "１",
            "1١",
        )) {
            assertEquals(text, OptionalContactInput.Invalid, parseOptionalPhone(text))
        }
    }

    /** A plus is optional, appears once and precedes every other nontrimmed character. */
    @Test fun repeatedOrMisplacedPlusIsRejected() {
        for (text in listOf("++1", "1+", "1+2", "(+)1", "+1+2")) {
            assertEquals(text, OptionalContactInput.Invalid, parseOptionalPhone(text))
        }
    }
}
