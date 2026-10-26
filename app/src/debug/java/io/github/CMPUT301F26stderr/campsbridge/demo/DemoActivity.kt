package io.github.CMPUT301F26stderr.campsbridge.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings

/** Debug-only shared-component gallery; does not initialize application repositories. */
class DemoActivity : ComponentActivity() {
    /**
     * Displays the local gallery.
     * @param savedInstanceState Previous activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComponentGallery(
                settings =
                    if (intent.getBooleanExtra("large", false)) {
                        AccountSettings(
                            fontScale = 1.25,
                            controlScale = 1.5,
                        )
                    } else {
                        io.github.CMPUT301F26stderr.campsbridge.domain.account
                            .AccountSettings()
                    },
                initialPage = intent.getIntExtra("page", 0),
                initialSection = intent.getIntExtra("section", 0),
            )
        }
    }
}
