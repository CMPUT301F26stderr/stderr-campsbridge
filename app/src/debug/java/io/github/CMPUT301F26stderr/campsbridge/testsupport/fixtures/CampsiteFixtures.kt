package io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.time.Instant

/** Supplies a realistic saved listing without network data or generated identifiers.
 * @param id Stable fixture identity.
 * @param ownerId Account owning the fixture.
 * @param published Whether this fixture is discoverable.
 */
fun sampleCampsite(
    id: String = "site-1",
    ownerId: String = "account-1",
    published: Boolean = true,
): Campsite =
    Campsite(
        id,
        ownerId,
        CampsiteDetails(
            "River Camp",
            "A quiet riverside campsite.",
            Coordinates(53.0, -118.0),
            "Jasper, Alberta",
            "America/Edmonton",
            8,
            setOf("water"),
            setOf("lakeside"),
            6500,
            "Keep the campsite clean.",
        ),
        Instant.parse("2027-05-01T12:00:00Z"),
        if (published) Instant.parse("2027-05-01T13:00:00Z") else null,
    )
