/*
 * Creates an InboxViewModel using TestAppHost repositories and tracks creations.
 */

package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel

/** Creates an InboxViewModel using TestAppHost repositories and tracks creations. */
internal fun TestAppHost.inboxFactory(): (String) -> ViewModelProvider.Factory =
    { accountId ->
        viewModelFactory {
            initializer {
                inboxCreations++
                InboxViewModel(
                    accountId = accountId,
                    notifications = notifications,
                    content =
                        NotificationContentLoader(
                            notifications = notifications,
                            lotteries = lotteries,
                            bookings = bookings,
                        ),
                )
            }
        }
    }
