package io.github.CMPUT301F26stderr.campsbridge

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.github.CMPUT301F26stderr.campsbridge.di.appViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.availabilityViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.campsiteDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.campsiteListViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.campsiteSetupViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.exploreViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.inboxViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotteryDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotteryListViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotterySetupViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.onboardingViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.profileViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.reservationDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.platform.lifecycle.ForegroundWorkRunner
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppRoot
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.workSession
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Android host; session rules and persistence operations belong to injected ViewModels. */
class MainActivity : ComponentActivity() {
    /**
     * Retains the root model across recreation and hosts production navigation.
     * @param savedInstanceState Previously saved activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = (application as CampsbridgeApplication).services
        val model = ViewModelProvider(this, appViewModelFactory(services))[AppViewModel::class.java]
        lifecycleScope.launch {
            ForegroundWorkRunner(services.repositories.pendingWork) { failure ->
                Log.w("PendingWork", "Foreground persistence operation will retry", failure)
            }.run(
                model.state.map { it.workSession() },
                lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
            )
        }
        setContent {
            AppRoot(
                model,
                { id -> onboardingViewModelFactory(services, id) },
                { id -> profileViewModelFactory(services, id) },
                { id -> campsiteListViewModelFactory(services, id) },
                {
                    accountId,
                    id,
                    context,
                    ->
                    campsiteDetailViewModelFactory(services, accountId, id, context)
                },
                { accountId, id -> campsiteSetupViewModelFactory(services, accountId, id) },
                { accountId, id -> availabilityViewModelFactory(services, accountId, id) },
                { exploreViewModelFactory(services) },
                { ownerId, id -> lotterySetupViewModelFactory(services, ownerId, id) },
                {
                    accountId,
                    id,
                    context,
                    ->
                    lotteryDetailViewModelFactory(services, accountId, id, context)
                },
                {
                    accountId,
                    context,
                    campsiteId,
                    ->
                    lotteryListViewModelFactory(services, accountId, context, campsiteId)
                },
                {
                    accountId,
                    id,
                    context,
                    ->
                    reservationDetailViewModelFactory(services, accountId, id, context)
                },
                { accountId -> inboxViewModelFactory(services, accountId) },
            )
        }
    }
}
