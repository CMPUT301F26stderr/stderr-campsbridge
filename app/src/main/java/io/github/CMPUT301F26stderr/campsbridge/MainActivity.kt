package io.github.CMPUT301F26stderr.campsbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/**
 * Starter Compose host; application bootstrap and feature navigation are not wired yet.
 */
class MainActivity : ComponentActivity() {
    /**
     * Hosts the starter greeting under the Compose theme and edge-to-edge scaffold.
     *
     * @param savedInstanceState Previously saved activity state, or null on initial
     * creation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampsbridgeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

/**
 * Displays the starter greeting for the supplied name.
 *
 * @param name Display name shown in the application.
 * @param modifier Layout and semantics supplied by the composable’s caller.
 */
@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Hello $name!",
        modifier = modifier,
    )
}

/**
 * Previews the starter greeting without application dependencies.
 */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CampsbridgeTheme {
        Greeting("Android")
    }
}
