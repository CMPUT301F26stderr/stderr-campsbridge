package io.github.CMPUT301F26stderr.campsbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import io.github.CMPUT301F26stderr.campsbridge.di.appViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.profileViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppRoot
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel

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
        setContent { AppRoot(model) { id -> profileViewModelFactory(services, id) } }
    }
}
