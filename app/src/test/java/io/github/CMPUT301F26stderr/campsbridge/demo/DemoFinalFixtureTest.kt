package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoStore
import io.github.CMPUT301F26stderr.campsbridge.demo.data.documents
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises linked Final scenarios through the same local store used by every demo person. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoFinalFixtureTest {
    private val data = DemoPreviewData(DemoScenario.OWNER)
    private val store = DemoStore(data.documents(), start = data.now)

    /** Both owners discover the same saved lottery and its actual campsite owners; admin has all workspaces. */
    @Test fun sharedOwnershipIsDiscoverableFromBothDemoPeople() =
        runTest {
            val lotteries = DemoLotteryRepository(store = store)
            val shared = lotteries.observeParticipating(data.ownerId).first().single { it.id == "demo-shared" }
            assertEquals(shared, lotteries.observeParticipating("demo-cohost").first().single { it.id == shared.id })
            val owners =
                lotteries
                    .getOffers(shared.id)
                    .map { offer ->
                        store.read { require("campsites/${offer.campsiteId}", CampsiteDocument).ownerId }
                    }.toSet()
            assertEquals(setOf(data.ownerId, "demo-cohost"), owners)
            assertEquals(owners, shared.participantOwnerIds)
            assertNotNull(DemoScenario.entries.singleOrNull { it.accountId == "demo-cohost" })
            assertTrue(store.read { require("accounts/demo-admin", AccountDocument).roles.containsAll(Role.entries) })
        }

    /** Rating-only, text-only and combined records are queryable and independently editable. */
    @Test fun feedbackExamplesUseRealAuthorsAndCampsites() =
        runTest {
            val reviews = DemoReviewRepository(store)
            val records = data.campsites.flatMap { reviews.observeReviews(it.id).first() }
            assertTrue(records.any { it.rating != null && it.body == null })
            assertTrue(records.any { it.rating == null && it.body != null })
            val combined = records.first { it.rating != null && it.body != null }
            for (record in records) {
                assertNotNull(store.read { get("accounts/${record.authorId}", AccountDocument) })
                assertNotNull(store.read { get("campsites/${record.campsiteId}", CampsiteDocument) })
            }
            reviews.setReviewText(combined.campsiteId, combined.authorId, "The trail was easy to find.")
            val updated =
                DemoReviewRepository(store)
                    .observeReviews(combined.campsiteId)
                    .first()
                    .single { it.authorId == combined.authorId }
            assertEquals(combined.rating, updated.rating)
            assertEquals("The trail was easy to find.", updated.body)
        }

    /** Second-owner conversations and sent announcements resolve to their saved reservation and receipts. */
    @Test fun conversationsAndAnnouncementsHaveTheirActualRecipients() =
        runTest {
            val stay = store.read { list("reservations", ReservationDocument) }.single { it.campsiteId == "demo-cedar" }
            val messages = DemoMessageRepository(store).observeConversation(stay.id).first()
            assertEquals(setOf(stay.entrantId, "demo-cohost"), messages.map { it.senderId }.toSet())
            assertEquals(messages.sortedBy { it.createdAt }, messages)
            for (message in messages) {
                val eventId = DocumentKeys.compound(NotificationType.NEW_MESSAGE.name, message.id)
                val recipient = if (message.senderId == stay.entrantId) "demo-cohost" else stay.entrantId
                assertEquals(
                    message.id,
                    store.read { require("notifications/$eventId", NotificationDocument).subject.id },
                )
                assertNotNull(store.read { get("accounts/$recipient/inbox/$eventId", InboxReceiptDocument) })
            }
            val sent =
                store
                    .read { list("notifications", NotificationDocument) }
                    .filter { it.type == NotificationType.OWNER_ANNOUNCEMENT && it.senderId == "demo-cohost" }
            assertTrue(sent.any { it.subject.id == stay.lotteryId })
            assertTrue(
                sent.all {
                    store.read { get("accounts/${stay.entrantId}/inbox/${it.id}", InboxReceiptDocument) } !=
                        null
                },
            )
        }
}
