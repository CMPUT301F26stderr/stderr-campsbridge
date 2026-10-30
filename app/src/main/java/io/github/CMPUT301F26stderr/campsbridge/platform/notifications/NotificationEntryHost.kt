package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Forwards validated launch and warm tap envelopes to the existing root owner.
 *
 * Current launch identity is always supplied, including after restoration; the root owns replay
 * suppression. Warm delivery updates Activity.intent so recreation retains the latest envelope.
 * @param onEntry Root action; readiness and replay handling remain outside the Activity.
 */
fun ComponentActivity.bindNotificationEntries(onEntry: (NotificationEntry) -> Unit) {
    NotificationEntry.fromIntent(intent)?.let(onEntry)
    val listener =
        Consumer<Intent> { incoming ->
            intent = incoming
            NotificationEntry.fromIntent(incoming)?.let(onEntry)
        }
    addOnNewIntentListener(listener)
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) removeOnNewIntentListener(listener)
        },
    )
}
