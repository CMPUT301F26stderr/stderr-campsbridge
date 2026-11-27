/*
 * Registers two workspace entries sharing one lazily acquired account-root receipt owner.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxTab
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentNotificationsRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentNotificationsViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentTarget
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationListRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationListUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessagesTabLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Registers two workspace entries sharing one lazily acquired account-root receipt owner.
 * @param controller Current account's navigation host.
 * @param state Actual ready identity and workspace state.
 * @param onSelectWorkspace Explicit root capability selection.
 * @param factory Current account's receipt/content dependencies.
 * @param sentFactory The same account's read-only sender history; absent hosts show only received receipts.
 * @param conversationListFactory The account's conversation list per workspace; absent hosts hide the Messages tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun NavGraphBuilder.inboxGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    factory: ViewModelProvider.Factory,
    sentFactory: ViewModelProvider.Factory? = null,
    conversationListFactory: ((ReservationContext) -> ViewModelProvider.Factory)? = null,
) {
    for (role in listOf(Role.ENTRANT, Role.OWNER)) {
        val route = AppDestination.inbox(role)
        val context = if (role == Role.OWNER) ReservationContext.OWNER else ReservationContext.ENTRANT
        composable(route) { entry ->
            val rootEntry = remember(entry) { controller.getBackStackEntry(controller.graph.id) }
            val model: InboxViewModel = viewModel(viewModelStoreOwner = rootEntry, factory = factory)
            // The Messages tab and its unread badge read one account-scoped list owner.
            val conversations: ConversationListViewModel? =
                conversationListFactory?.let {
                    viewModel(
                        viewModelStoreOwner = rootEntry,
                        key = "conversations-${role.name}",
                        factory = it(context),
                    )
                }
            val unread =
                (conversations?.state?.collectAsStateWithLifecycle()?.value as? ConversationListUiState.Content)
                    ?.unread ?: 0
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.inbox_title),
            ) {
                val tabs =
                    buildList {
                        add(InboxTab.RECEIVED)
                        if (role == Role.OWNER && sentFactory != null) add(InboxTab.SENT)
                        if (conversations != null) add(InboxTab.MESSAGES)
                    }
                var selectedName by rememberSaveable { mutableStateOf(InboxTab.RECEIVED.name) }
                val selected = tabs.firstOrNull { it.name == selectedName } ?: InboxTab.RECEIVED
                val pages = rememberSaveableStateHolder()
                Column {
                    if (tabs.size > 1) {
                        PrimaryTabRow(tabs.indexOf(selected)) {
                            for (tab in tabs) {
                                Tab(
                                    tab == selected,
                                    onClick = { selectedName = tab.name },
                                    text = {
                                        when (tab) {
                                            InboxTab.RECEIVED -> {
                                                Text(
                                                    stringResource(
                                                        if (role == Role.OWNER) {
                                                            R.string.inbox_received
                                                        } else {
                                                            R.string.inbox_notifications
                                                        },
                                                    ),
                                                )
                                            }

                                            InboxTab.SENT -> {
                                                Text(stringResource(R.string.inbox_sent))
                                            }

                                            InboxTab.MESSAGES -> {
                                                MessagesTabLabel(unread)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    pages.SaveableStateProvider(selected.name) {
                        when (selected) {
                            InboxTab.SENT -> {
                                val history: SentNotificationsViewModel =
                                    viewModel(viewModelStoreOwner = rootEntry, factory = checkNotNull(sentFactory))
                                SentNotificationsRoute(history) { target ->
                                    controller.navigate(
                                        when (target) {
                                            is SentTarget.Lottery -> {
                                                AppDestination.lotteryDetail(target.id, LotteryContext.OWNER)
                                            }

                                            is SentTarget.Conversation -> {
                                                AppDestination.conversation(target.id, target.context)
                                            }
                                        },
                                    ) { launchSingleTop = true }
                                }
                            }

                            InboxTab.MESSAGES -> {
                                ConversationListRoute(checkNotNull(conversations)) { id ->
                                    controller.navigate(AppDestination.conversation(id, context)) {
                                        launchSingleTop = true
                                    }
                                }
                            }

                            InboxTab.RECEIVED -> {
                                InboxRoute(
                                    model = model,
                                    onOpenLottery = { lotteryId, ctx ->
                                        controller.navigate(AppDestination.lotteryDetail(lotteryId, ctx))
                                    },
                                    onOpenReservation = { resId, ctx ->
                                        controller.navigate(AppDestination.reservationDetail(resId, ctx))
                                    },
                                    onOpenConversation = { id, ctx ->
                                        controller.navigate(AppDestination.conversation(id, ctx)) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
