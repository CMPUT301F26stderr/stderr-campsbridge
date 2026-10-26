package io.github.CMPUT301F26stderr.campsbridge.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirebaseClients
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.FirebaseIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.DataStoreAppPreferences
import java.time.Clock

/**
 * Builds one consistent environment before exposing any repositories to controllers.
 * @param context Application context, never an Activity.
 * @param environment Explicit cloud/emulator selection.
 */
class AppContainerFactory(
    private val context: Context,
    private val environment: AppEnvironment,
) {
    /** Creates application services once; does not sign in, seed profiles, or run maintenance. */
    fun create(): AppServices {
        val app =
            when (val target = environment) {
                AppEnvironment.Cloud -> {
                    checkNotNull(
                        FirebaseApp.initializeApp(context),
                    ) { "Firebase configuration is missing" }
                }

                is AppEnvironment.Emulators -> {
                    FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions
                            .Builder()
                            .setProjectId("demo-stderr")
                            .setApplicationId("1:123456789:android:emulator")
                            .setApiKey("emulator-only-api-key")
                            .setStorageBucket("demo-stderr.appspot.com")
                            .build(),
                        "campsbridge-emulator",
                    )
                }
            }
        val auth = FirebaseAuth.getInstance(app)
        val clients =
            when (val target = environment) {
                AppEnvironment.Cloud -> {
                    FirebaseClients.forApp(app)
                }

                is AppEnvironment.Emulators -> {
                    auth.useEmulator(target.host, target.authPort)
                    FirebaseClients.forEmulators(
                        app,
                        target.host,
                        target.firestorePort,
                        target.storagePort,
                    )
                }
            }
        val store =
            PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile(
                    "application",
                )
            }
        val clock = Clock.systemUTC()
        return AppServices(
            AppContainer(clients, clock),
            FirebaseIdentityProvider(auth),
            DataStoreAppPreferences(store),
            clock,
        )
    }
}
