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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentNotificationsRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentNotificationsViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentTarget
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext

/** Registers two workspace entries sharing one lazily acquired account-root receipt owner.
 * @param controller Current account's navigation host.
 * @param state Actual ready identity and workspace state.
 * @param onSelectWorkspace Explicit root capability selection.
 * @param factory Current account's receipt/content dependencies.
 * @param sentFactory The same account's read-only sender history; absent hosts show only received receipts.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun NavGraphBuilder.inboxGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    factory: ViewModelProvider.Factory,
    sentFactory: ViewModelProvider.Factory? = null,
) {
    for (role in listOf(Role.ENTRANT, Role.OWNER)) {
        val route = AppDestination.inbox(role)
        composable(route) { entry ->
            val rootEntry = remember(entry) { controller.getBackStackEntry(controller.graph.id) }
            val model: InboxViewModel = viewModel(viewModelStoreOwner = rootEntry, factory = factory)
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.inbox_title),
            ) {
                var sent by rememberSaveable { mutableStateOf(false) }
                val pages = rememberSaveableStateHolder()
                Column {
                    if (role == Role.OWNER && sentFactory != null) {
                        PrimaryTabRow(if (sent) 1 else 0) {
                            Tab(
                                !sent,
                                onClick = { sent = false },
                                text = { Text(stringResource(R.string.inbox_received)) },
                            )
                            Tab(sent, onClick = { sent = true }, text = { Text(stringResource(R.string.inbox_sent)) })
                        }
                    }
                    val sentHistory = sentFactory?.takeIf { role == Role.OWNER && sent }
                    pages.SaveableStateProvider(sentHistory != null) {
                        if (sentHistory != null) {
                            val history: SentNotificationsViewModel =
                                viewModel(viewModelStoreOwner = rootEntry, factory = sentHistory)
                            SentNotificationsRoute(history) { target ->
                                controller.navigate(
                                    when (target) {
                                        is SentTarget.Lottery -> {
                                            AppDestination.lotteryDetail(
                                                target.id,
                                                LotteryContext.OWNER,
                                            )
                                        }

                                        is SentTarget.Conversation -> {
                                            AppDestination.conversation(
                                                target.id,
                                                target.context,
                                            )
                                        }
                                    },
                                ) { launchSingleTop = true }
                            }
                        } else {
                            InboxRoute(
                                model = model,
                                onOpenLottery = { lotteryId, ctx ->
                                    controller.navigate(AppDestination.lotteryDetail(lotteryId, ctx))
                                },
                                onOpenReservation = { resId, ctx ->
                                    controller.navigate(AppDestination.reservationDetail(resId, ctx))
                                },
                                onOpenConversation = { id, context ->
                                    controller.navigate(AppDestination.conversation(id, context)) {
                                        launchSingleTop =
                                            true
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
