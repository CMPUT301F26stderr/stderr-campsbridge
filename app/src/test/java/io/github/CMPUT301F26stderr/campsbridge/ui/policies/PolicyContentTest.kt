package io.github.CMPUT301F26stderr.campsbridge.ui.policies

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Packaged topic/version contracts; copy meaning is also reviewed against US 01.11.01. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PolicyContentTest {
    /** Current guidelines include campsite rules, selection/booking and simulation topics in order. */
    @Test fun guidelinesHaveCompleteTopics() {
        val content = policyContent(PolicyId.GUIDELINES)
        assertNotNull(content)
        assertEquals("guidelines-v1", content!!.version)
        assertEquals(
            listOf(
                PolicySectionId.CAMPSITE_ETIQUETTE,
                PolicySectionId.LOTTERY_SELECTION,
                PolicySectionId.PAYMENTS_AND_REFUNDS,
            ),
            content.sections.map {
                it.id
            },
        )
        assertReadable(content)
    }

    /** The recorded booking version resolves full refund and single-use-win facts offline. */
    @Test fun refundHasExactBookingVersionAndCompleteTopics() {
        val content = policyContent(PolicyId.REFUND, BookingPolicy.VERSION)
        assertNotNull(content)
        assertEquals(BookingPolicy.VERSION, content!!.version)
        assertEquals(
            listOf(
                PolicySectionId.PAYMENTS_AND_REFUNDS,
                PolicySectionId.SINGLE_USE_WIN,
            ),
            content.sections.map {
                it.id
            },
        )
        assertReadable(content)
    }

    /** A future/unknown version never silently resolves to today's text. */
    @Test fun unknownAndCrossDocumentVersionsAreUnavailable() {
        for (id in PolicyId.entries) {
            assertNull(policyContent(id, "unknown"))
            assertNull(policyContent(id, ""))
        }
        assertNull(policyContent(PolicyId.REFUND, PolicyId.GUIDELINES.currentVersion))
        assertNull(policyContent(PolicyId.GUIDELINES, BookingPolicy.VERSION))
    }

    /** Shared simulation text has one resource owner in both readers. */
    @Test fun repeatedTopicUsesTheSameContentResource() {
        val guidelines =
            checkNotNull(policyContent(PolicyId.GUIDELINES)).sections.single {
                it.id ==
                    PolicySectionId.PAYMENTS_AND_REFUNDS
            }
        val refund =
            checkNotNull(policyContent(PolicyId.REFUND)).sections.single {
                it.id ==
                    PolicySectionId.PAYMENTS_AND_REFUNDS
            }
        assertEquals(guidelines, refund)
    }

    /** Confirms every required heading/body is available as actual nonblank packaged text.
     * @param content Exact resolved version under test.
     */
    private fun assertReadable(content: PolicyContent) {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(context.getString(content.title).isNotBlank())
        assertEquals(
            content.sections.size,
            content.sections
                .map { it.id }
                .toSet()
                .size,
        )
        content.sections.forEach {
            assertTrue(context.getString(it.title).isNotBlank())
            assertTrue(context.getString(it.body).isNotBlank())
        }
    }
}
