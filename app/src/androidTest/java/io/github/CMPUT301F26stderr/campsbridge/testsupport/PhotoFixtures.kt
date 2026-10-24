package io.github.CMPUT301F26stderr.campsbridge.testsupport

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Native activity-result boundary with controlled results and captured launch intents.
 * @param result Selected local content, or null for cancellation.
 */
class ControlledPhotoPicker(
    var result: Uri?,
) : ActivityResultRegistryOwner {
    var lastIntent: Intent? = null
    var launches = 0
    override val activityResultRegistry =
        object : ActivityResultRegistry() {
            /**
             * Captures the real contract intent and delivers a controlled Android result.
             * @param requestCode Registered launcher request.
             * @param contract Production activity contract.
             * @param input Production request arguments.
             * @param options Optional platform launch options.
             */
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                lastIntent = contract.createIntent(context, input)
                launches++
                val selected = result
                dispatchResult(
                    requestCode,
                    if (selected ==
                        null
                    ) {
                        Activity.RESULT_CANCELED
                    } else {
                        Activity.RESULT_OK
                    },
                    selected?.let { Intent().setData(it) },
                )
            }
        }
}

/** Returns a valid local PNG through the debug-only provider, without public network access. */
fun fixturePhotoUri(): Uri {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val directory = File(context.cacheDir, "test-photos").apply { mkdirs() }
    val image = File(directory, "profile-photo.png")
    instrumentation.context.assets.open("profile-photo.png").use { input ->
        image.outputStream().use { output -> input.copyTo(output) }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.testfiles", image)
}
