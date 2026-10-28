package io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Defines the reserved display-name contract without prescribing its implementation. */
class DisplayNameInputTest {
    /** An empty field needs required-field feedback. */
    @Test fun emptyInputRequiresAName() {
        assertEquals(DisplayNameInput.Required, parseDisplayName(""))
    }

    /** Whitespace alone cannot become a saved name. */
    @Test fun whitespaceRequiresAName() {
        assertEquals(DisplayNameInput.Required, parseDisplayName(" \t\n\u2003 "))
    }

    /** A single nonblank character is sufficient; no length rule is added. */
    @Test fun acceptsOneCharacter() {
        assertEquals(DisplayNameInput.Valid("A"), parseDisplayName("A"))
    }

    /** Only surrounding whitespace is discarded. */
    @Test fun trimsSurroundingWhitespace() {
        assertEquals(DisplayNameInput.Valid("Alex Chen"), parseDisplayName(" \tAlex Chen\n "))
    }

    /** Internal spaces and letter case remain the user's choice. */
    @Test fun preservesInternalSpacesAndCase() {
        assertEquals(DisplayNameInput.Valid("aLex  CHen"), parseDisplayName("aLex  CHen"))
    }

    /** Whitespace inside an accepted name is preserved rather than silently reformatted. */
    @Test fun preservesInternalWhitespace() {
        val name = "Alex\tC.\nChen"
        assertEquals(DisplayNameInput.Valid(name), parseDisplayName("  $name  "))
    }

    /** No screenshot character counter or arbitrary cap limits a nonblank name. */
    @Test fun acceptsLongInputWithoutAnArtificialLimit() {
        val name = "A".repeat(512)
        assertEquals(DisplayNameInput.Valid(name), parseDisplayName(" $name "))
    }

    /** Names can contain non-Latin text and symbols. */
    @Test fun preservesUnicode() {
        assertEquals(DisplayNameInput.Valid("李 小龙 🏕"), parseDisplayName("  李 小龙 🏕  "))
    }

    /** Already accepted text is unchanged. */
    @Test fun retainsAnUnpaddedName() {
        assertEquals(DisplayNameInput.Valid("Alex"), parseDisplayName("Alex"))
    }
}
