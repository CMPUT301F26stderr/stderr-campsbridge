/*
 * Verifies local durability and time transitions independently of feature screen rendering.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoStore
import io.github.CMPUT301F26stderr.campsbridge.demo.data.documents
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.awaitDeadline
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI

/** Verifies local durability and time transitions independently of feature screen rendering. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedDemoLifecycleTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** Time advancement wakes an existing wait and an idempotent draw creates distinct outcomes. */
    @Test fun advancingTimeWakesWaitingScreensAndDrawsOnce() =
        runTest {
            val data = DemoPreviewData(DemoScenario.ENTRANT)
            val store = DemoStore(data.documents())
            val lotteries = DemoLotteryRepository(store = store)
            val parent = lotteries.get("demo-open")!!
            lotteries.withdraw(parent.id, "demo-other")
            lotteries.join(parent.id, "demo-other")
            val waiter = async { awaitDeadline(store.clock, parent.configuration.bookingOpensAt) }
            runCurrent()
            assertFalse(waiter.isCompleted)
            store.advanceTo(parent.configuration.bookingOpensAt)
            waiter.await()
            assertEquals(DrawResult.COMPLETED, lotteries.draw(parent.id))
            assertEquals(DrawResult.ALREADY_DRAWN, lotteries.draw(parent.id))
            val results = lotteries.observeEntries(parent.id).first()
            assertEquals(setOf(EntryState.OFFERED, EntryState.LOST), results.map { it.state }.toSet())
            assertEquals(0L, lotteries.get(parent.id)!!.waitingCount)
            assertTrue(runCatching { lotteries.join(parent.id, data.entrantId) }.exceptionOrNull() != null)
            assertTrue(
                runCatching {
                    lotteries.update(
                        parent.id,
                        data.ownerId,
                        parent.title,
                        parent.description,
                        parent.configuration.copy(winnerCount = 2),
                    )
                }.exceptionOrNull() !=
                    null,
            )
        }

    /** Area changes and process restart retain committed facts, photo bytes and selected person. */
    @Test fun sharedAreasAndRestartRetainEditsUntilExplicitReset() =
        runTest {
            val directory = files.newFolder()
            val host = DemoHostViewModel(directory)
            val owner = ViewModelStore().apply { put("host", host) }
            val entrant = host.session.filterNotNull().first()
            val id = entrant.scenario.accountId
            entrant.services.repositories.accounts.updateProfile(
                id,
                ProfileUpdate("Alex revised", "alex@example.com", null),
            )
            val target = ImageTarget(ImageTargetType.ACCOUNT, id)
            val photo =
                entrant.services.repositories.images
                    .replace(
                        target,
                        ImageUpload("image/png") {
                            ByteArrayInputStream(byteArrayOf(1, 2, 3))
                        },
                    ).attachment!!
            host.select(DemoScenario.OWNER)
            val hosting = host.session.filterNotNull().first { it.scenario == DemoScenario.OWNER }
            assertEquals(
                "Alex revised",
                hosting.services.repositories.accounts
                    .get(id)!!
                    .name,
            )
            assertEquals("demo-host", hosting.scenario.accountId)
            assertEquals(
                photo,
                hosting.services.repositories.images
                    .get(target),
            )
            owner.clear()
            val restarted = DemoHostViewModel(directory)
            val restartedOwner = ViewModelStore().apply { put("host", restarted) }
            val restored = restarted.session.filterNotNull().first()
            assertEquals(DemoScenario.OWNER, restored.scenario)
            assertEquals(
                "Alex revised",
                restored.services.repositories.accounts
                    .get(id)!!
                    .name,
            )
            assertArrayEquals(
                byteArrayOf(1, 2, 3),
                File(
                    URI(
                        restored.services.repositories.images
                            .downloadUrl(photo.blobId),
                    ),
                ).readBytes(),
            )
            restarted.reset()
            val reset = restarted.session.filterNotNull().first { it !== restored }
            assertEquals(
                "Alex Chen",
                reset.services.repositories.accounts
                    .get(id)!!
                    .name,
            )
            assertNull(
                reset.services.repositories.images
                    .get(target),
            )
            restartedOwner.clear()
        }
}
