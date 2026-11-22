/*
 * Selects one root branch from lifecycle-aware session state without imperative navigation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppNavigation
import io.github.CMPUT301F26stderr.campsbridge.navigation.OnboardingNavigation
import io.github.CMPUT301F26stderr.campsbridge.navigation.SessionNavigationOwner
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/**
 * Selects one root branch from lifecycle-aware session state without imperative navigation.
 * @param model Root session owner.
 * @param onboardingFactory Factory for one account-scoped onboarding flow.
 * @param profileFactory Factory for the established account; no entity snapshots in routes.
 * @param campsiteListFactory Factory for the owner's primary inventory.
 * @param campsiteDetailFactory Factory for a saved identity/context reader.
 * @param availabilityFactory Dependencies for an independent saved-campsite inventory editor.
 * @param campsiteSetupFactory Factory for a local setup parent.
 * @param exploreFactory Factory for this account's discovery/filter graph.
 * @param lotterySetupFactory Factory for this owner's new/saved lottery setup graph.
 * @param lotteryDetailFactory Factory for this owner's saved lottery overview.
 * @param lotteryListFactory Factory for the owner's primary or campsite-filtered list.
 * @param reservationDetailFactory Factory for a saved aggregate, keyed by account/ID/context.
 * @param reservationListFactory Active-list dependencies keyed by account/context/filter.
 * @param inboxFactory Factory for the ready account receipt owner, independent of workspace.
 * @param accountRemoval Activity-owned operation that outlives the profile graph.
 * @param bookingFactory Factory for a winner wizard keyed by account, lottery and campsite IDs.
 * @param adminBrowseFactory Factory for the account-hosted read-only admin Browse graph.
 * @param notificationLogFactory Factory for the independent read-only notification-log graph.
 * @param messageLogFactory Independent saved-message log dependencies.
 * @param notificationPermission Explicit host-owned Android inspection/settings dependency.
 * @param notificationEntries Activity/root tap owner, independent of the session navigation store.
 * @param requestedDestination Optional host-owned saved-record handoff.
 * @param onDestinationOpened Acknowledges the opened handoff.
 * @param reviewsFactory Entry-scoped saved feedback dependencies.
 * @param lotteryDetailsEditFactory Saved organizer details dependencies scoped to the ready account.
 * @param lotteryAllocationsEditFactory Saved allocation dependencies scoped to the current participant.
 * @param campsiteEditFactory Dependencies for saved listing details and independent photos.
 */
@Composable
fun AppRoot(
    model: AppViewModel,
    onboardingFactory: (String) -> ViewModelProvider.Factory,
    profileFactory: (String) -> ViewModelProvider.Factory,
    campsiteListFactory: (String) -> ViewModelProvider.Factory,
    campsiteDetailFactory: (String, String, CampsiteDetailContext) -> ViewModelProvider.Factory,
    campsiteSetupFactory: (String, String?) -> ViewModelProvider.Factory,
    availabilityFactory: (String, String) -> ViewModelProvider.Factory,
    exploreFactory: () -> ViewModelProvider.Factory,
    lotterySetupFactory: (String, String?) -> ViewModelProvider.Factory,
    lotteryDetailFactory: (String, String, LotteryContext) -> ViewModelProvider.Factory,
    lotteryListFactory: (String, LotteryContext, String?) -> ViewModelProvider.Factory,
    reservationDetailFactory: (String, String, ReservationContext) -> ViewModelProvider.Factory,
    inboxFactory: (String) -> ViewModelProvider.Factory,
    bookingFactory: (String, String, String) -> ViewModelProvider.Factory,
    reservationListFactory: (String, ReservationContext, String?) -> ViewModelProvider.Factory,
    adminBrowseFactory: () -> ViewModelProvider.Factory,
    notificationLogFactory: () -> ViewModelProvider.Factory,
    messageLogFactory: () -> ViewModelProvider.Factory,
    notificationPermission: NotificationPermission,
    notificationEntries: NotificationEntryViewModel,
    requestedDestination: String? = null,
    onDestinationOpened: () -> Unit = {},
    accountRemoval: AccountRemovalViewModel,
    reviewsFactory: (String, String, Role) -> ViewModelProvider.Factory,
    adminRemovalFactory: ((String) -> ViewModelProvider.Factory)? = null,
    campsiteEditFactory: (String, String) -> ViewModelProvider.Factory,
    lotteryDetailsEditFactory: ((String, String) -> ViewModelProvider.Factory)? = null,
    lotteryAllocationsEditFactory: ((String, String) -> ViewModelProvider.Factory)? = null,
) {
    val actualSession by model.state.collectAsStateWithLifecycle()
    val removal by accountRemoval.state.collectAsStateWithLifecycle()
    LaunchedEffect(actualSession) { accountRemoval.updateSession(actualSession) }
    val state =
        if (removal.phase == AccountRemovalPhase.COMPLETE && actualSession is SessionState.Ready &&
            (actualSession as SessionState.Ready).account.id == removal.request?.accountId
        ) {
            SessionState.Unavailable
        } else {
            actualSession
        }
    val entry by notificationEntries.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { notificationEntries.updateSession(state) }
    val navigationOwner: SessionNavigationOwner = viewModel()
    val navigationState = rememberSaveableStateHolder()
    val navigationKey =
        remember(navigationOwner, navigationState, state) {
            navigationOwner.update(state)?.let(navigationState::removeState)
            navigationOwner.scopeKey
        }
    val settings = rememberRootThemeSettings(actualSession, removal)
    CampsbridgeTheme(settings) {
        when (val session = state) {
            SessionState.Initializing -> {
                StartupContent(false, model::retry)
            }

            SessionState.Failure -> {
                StartupContent(true, model::retry)
            }

            SessionState.Unavailable -> {
                UnavailableAccountScreen()
            }

            is SessionState.SetupRequired -> {
                navigationState.SaveableStateProvider(checkNotNull(navigationKey)) {
                    key(
                        session.accountId,
                    ) {
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides navigationOwner,
                        ) {
                            OnboardingNavigation(onboardingFactory(session.accountId))
                        }
                    }
                }
            }

            is SessionState.Ready -> {
                navigationState.SaveableStateProvider(checkNotNull(navigationKey)) {
                    key(session.account.id) {
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides navigationOwner,
                        ) {
                            AppNavigation(
                                profileFactory(session.account.id),
                                session,
                                model::selectWorkspace,
                                campsiteListFactory(session.account.id),
                                {
                                    id,
                                    context,
                                    ->
                                    campsiteDetailFactory(session.account.id, id, context)
                                },
                                { id -> campsiteSetupFactory(session.account.id, id) },
                                { id -> availabilityFactory(session.account.id, id) },
                                exploreFactory(),
                                { id -> lotterySetupFactory(session.account.id, id) },
                                {
                                    id,
                                    context,
                                    ->
                                    lotteryDetailFactory(session.account.id, id, context)
                                },
                                { context, campsiteId ->
                                    lotteryListFactory(session.account.id, context, campsiteId)
                                },
                                {
                                    id,
                                    context,
                                    ->
                                    reservationDetailFactory(session.account.id, id, context)
                                },
                                remember(session.account.id) { inboxFactory(session.account.id) },
                                { lotteryId, campsiteId ->
                                    bookingFactory(session.account.id, lotteryId, campsiteId)
                                },
                                { context, campsiteId ->
                                    reservationListFactory(session.account.id, context, campsiteId)
                                },
                                adminBrowseFactory(),
                                notificationLogFactory(),
                                messageLogFactory(),
                                notificationPermission,
                                entry,
                                notificationEntries::retry,
                                notificationEntries::dismiss,
                                notificationEntries::consume,
                                requestedDestination = requestedDestination,
                                onDestinationOpened = onDestinationOpened,
                                onDeleteProfile = {
                                    accountRemoval
                                        .request(
                                            session.account.id,
                                        )
                                },
                                reviewsFactory = { id, workspace -> reviewsFactory(session.account.id, id, workspace) },
                                adminRemovalFactory = adminRemovalFactory?.invoke(session.account.id),
                                campsiteEditFactory = { id -> campsiteEditFactory(session.account.id, id) },
                                lotteryDetailsEditFactory =
                                    lotteryDetailsEditFactory?.let { factory ->
                                        { id -> factory(session.account.id, id) }
                                    },
                                lotteryAllocationsEditFactory =
                                    lotteryAllocationsEditFactory?.let { factory ->
                                        { id -> factory(session.account.id, id) }
                                    },
                            )
                        }
                    }
                }
            }
        }
        AccountRemovalHost(
            removal,
            accountRemoval::confirm,
            accountRemoval::dismiss,
            accountRemoval::retry,
            accountRemoval::retryRecovery,
            { removal.request?.let { accountRemoval.request(it.accountId) } },
        )
    }
}
