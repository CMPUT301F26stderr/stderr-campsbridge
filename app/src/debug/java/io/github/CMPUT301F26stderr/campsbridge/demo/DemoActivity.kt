package io.github.CMPUT301F26stderr.campsbridge.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Debug-only shared-component gallery; does not initialize application repositories. */
class DemoActivity : ComponentActivity() {
    /**
     * Displays the local gallery.
     * @param savedInstanceState Previous activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ComponentGallery() }
    }
}
