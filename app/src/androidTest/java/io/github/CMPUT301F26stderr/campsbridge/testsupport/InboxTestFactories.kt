/*
 * Creates an InboxViewModel using TestAppHost repositories and tracks creations.
 */

package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationContextResolver

/** Creates an InboxViewModel using TestAppHost repositories and tracks creations. */
internal fun TestAppHost.inboxFactory(): (String) -> ViewModelProvider.Factory =
    { accountId ->
        viewModelFactory {
            initializer {
                inboxCreations++
                InboxViewModel(
                    accountId = accountId,
                    notifications = notifications,
                    conversationResolver = ConversationContextResolver(campsites),
                    content =
                        NotificationContentLoader(
                            notifications = notifications,
                            lotteries = lotteries,
                            bookings = bookings,
                            messages = messages,
                        ),
                )
            }
        }
    }
