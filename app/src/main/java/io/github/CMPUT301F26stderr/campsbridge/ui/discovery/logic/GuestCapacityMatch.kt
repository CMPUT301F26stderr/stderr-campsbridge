package io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic

// Reserved F10 body: this isolated handoff intentionally fails its contract tests.

/** Checks a positive listing capacity against an optional requested group size.
 * @param maxGuests Strictly positive listing capacity, validated before the request.
 * @param requestedGuests Optional group size; null imposes no guest filter.
 * @return True for no request or a request in 1..maxGuests; false for other requests.
 * @throws IllegalArgumentException If maxGuests is nonpositive, even when the request is null.
 */
fun matchesGuestCapacity(
    maxGuests: Long,
    requestedGuests: Long?,
): Boolean =
    false
