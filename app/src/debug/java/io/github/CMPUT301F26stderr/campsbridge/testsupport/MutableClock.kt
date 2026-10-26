package io.github.CMPUT301F26stderr.campsbridge.testsupport

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Controllable time shared by timezone views of the same fixture clock.
 * @param time Shared controlled instant.
 * @param zoneId Zone used to interpret local dates.
 */
class MutableClock private constructor(
    private val time: ClockTime,
    private val zoneId: ZoneId,
) : Clock() {
    /**
     * Starts a controllable fixture clock.
     * @param current Starting instant.
     * @param zoneId Zone used to interpret local dates.
     */
    constructor(
        current: Instant,
        zoneId: ZoneId = ZoneOffset.UTC,
    ) : this(ClockTime(current), zoneId)

    /** Returns the configured zone. */
    override fun getZone(): ZoneId =
        zoneId

    /** Returns another view of the same time source.
     * @param zone Requested timezone. */
    override fun withZone(zone: ZoneId): Clock =
        MutableClock(time, zone)

    /** Returns the controlled instant. */
    override fun instant(): Instant =
        time.current

    /** Advances fixture time.
     * @param duration Amount of time to advance. */
    fun advance(duration: Duration) {
        time.current = time.current.plus(duration)
    }

    /** Shared fixture time.
     * @param current Controlled instant. */
    private class ClockTime(
        var current: Instant,
    )
}
