package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel

/** Creates an InboxViewModel for the given account.
 * @param services Current host's repository interfaces.
 * @param accountId Ready recipient identity, independent of workspace selection.
 */
fun inboxViewModelFactory(
    services: AppServices,
    accountId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            InboxViewModel(
                accountId = accountId,
                notifications = services.repositories.notifications,
                content = NotificationContentLoader(
                    notifications = services.repositories.notifications,
                    lotteries = services.repositories.lotteries,
                    bookings = services.repositories.bookings,
                ),
            )
        }
    }
