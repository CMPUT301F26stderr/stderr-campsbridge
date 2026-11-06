package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.NotificationLogViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader

/** Supplies the real graph owner using only this host's controlled log and context sources. */
internal fun TestAppHost.notificationLogFactory(): () -> ViewModelProvider.Factory =
    {
        viewModelFactory {
            initializer {
                notificationLogCreations++
                NotificationLogViewModel(
                    logNotifications,
                    NotificationContentLoader(logNotifications, lotteries, bookings),
                    createSavedStateHandle(),
                )
            }
        }
    }
