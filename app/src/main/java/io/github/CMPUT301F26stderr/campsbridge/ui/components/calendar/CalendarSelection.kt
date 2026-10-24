package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import java.time.LocalDate

/** Hoisted visual selection; persistence and stay eligibility remain caller-owned. */
sealed interface CalendarSelection {
    /** No highlighted dates. */
    data object None : CalendarSelection

    /** Individually selected nights.
     * @param dates Dates highlighted by the caller. */
    data class Dates(
        val dates: Set<LocalDate>,
    ) : CalendarSelection

    /**
     * Half-open stay highlighting.
     * @param checkIn First selected night.
     * @param checkOut Checkout date, excluded from selected nights; must follow check-in.
     */
    data class Stay(
        val checkIn: LocalDate,
        val checkOut: LocalDate,
    ) : CalendarSelection {
        init {
            require(checkOut > checkIn)
        }
    }

    /**
     * Reports whether a date is highlighted by this selection.
     * @param date Local date being rendered.
     */
    operator fun contains(date: LocalDate): Boolean =
        when (this) {
            None -> false
            is Dates -> date in dates
            is Stay -> date >= checkIn && date < checkOut
        }
}
