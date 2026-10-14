package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.account

import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owns profile persistence and independent update boundaries through real Firebase clients. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Creating an existing identity preserves its profile, roles and original creation time. */
    @Test(timeout = 60000)
    fun creationIsIdempotentAndOwnerCapabilityIsAdditive() =
        runBlocking {
            val actor = firebase.actor()
            val accounts = actor.container.accounts
            val original =
                accounts.create(
                    actor.id,
                    ProfileUpdate("Camper", "first@example.test", null),
                )
            accounts.enableOwner(actor.id)
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            val existing =
                accounts.create(
                    actor.id,
                    ProfileUpdate("Replacement", null, "7805550100"),
                )
            assertEquals(original.copy(roles = setOf(Role.ENTRANT, Role.OWNER)), existing)
            accounts.enableOwner(actor.id)
            assertEquals(existing, accounts.get(actor.id))
        }

    /** Profile, accessibility and recommendation updates preserve one another's state. */
    @Test(timeout = 60000)
    fun updatesPreserveUnrelatedProfileFields() =
        runBlocking {
            val actor = firebase.actor()
            val accounts = actor.container.accounts
            val original = accounts.create(actor.id, ProfileUpdate("Camper", null, null))
            val settings = AccountSettings(notificationsEnabled = false, fontScale = 1.5)
            val preferences = Preferences(setOf("quiet"), setOf("water"))
            accounts.updateSettings(actor.id, settings)
            accounts.updatePreferences(actor.id, preferences)
            accounts.updateProfile(
                actor.id,
                ProfileUpdate("Updated", "new@example.test", "7805550100"),
            )
            assertEquals(
                original.copy(
                    name = "Updated",
                    email = "new@example.test",
                    phone = "7805550100",
                    settings = settings,
                    preferences = preferences,
                ),
                accounts.get(actor.id),
            )
        }

    /** Token replacement records repository time and unregistering is safely repeatable. */
    @Test(timeout = 60000)
    fun deviceRegistrationReplacesTokenAndCanBeRemovedTwice() =
        runBlocking {
            val actor = firebase.actor()
            val accounts = actor.container.accounts
            accounts.create(actor.id, ProfileUpdate("Camper", null, null))
            val oldTime = firebase.clock.current.minusSeconds(60)
            accounts.registerDevice(
                actor.id,
                DeviceRegistration("installation", "old-token", oldTime),
            )
            accounts.registerDevice(
                actor.id,
                DeviceRegistration("installation", "new-token", oldTime),
            )
            val document =
                actor.clients.firestore.document(
                    "accounts/${actor.id}/devices/installation",
                )
            val stored = document.get().await()
            assertEquals("new-token", stored.getString("fcmToken"))
            assertEquals(
                firebase.clock.current.epochSecond,
                stored.getTimestamp("updatedAt")!!.seconds,
            )
            accounts.unregisterDevice(actor.id, "installation")
            accounts.unregisterDevice(actor.id, "installation")
            assertFalse(document.get().await().exists())
        }
}
