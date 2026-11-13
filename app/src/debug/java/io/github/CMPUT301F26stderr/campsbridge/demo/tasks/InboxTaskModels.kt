/*
 * Isolates ordering or count while retaining actual event hydration and receipt actions.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel

/** Isolates ordering or count while retaining actual event hydration and receipt actions. */
internal fun TaskExampleSession.inbox(): InboxViewModel =
    model(InboxViewModel::class.java) {
        InboxViewModel(
            TaskExampleData.ACCOUNT_ID,
            notifications,
            NotificationContentLoader(notifications, lotteries, bookings, FakeMessageRepository()),
            sorter =
                if (example ==
                    TaskExample.INBOX_ORDER
                ) {
                    bindings.orderInbox
                } else {
                    { it -> it }
                },
            countUnread =
                if (example ==
                    TaskExample.UNREAD_COUNT
                ) {
                    bindings.countUnread
                } else {
                    { _ -> 2 }
                },
        )
    }
