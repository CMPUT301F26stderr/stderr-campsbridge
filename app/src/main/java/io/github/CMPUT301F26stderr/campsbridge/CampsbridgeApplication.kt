package io.github.CMPUT301F26stderr.campsbridge

import android.app.Application
import io.github.CMPUT301F26stderr.campsbridge.di.AppContainerFactory
import io.github.CMPUT301F26stderr.campsbridge.di.AppEnvironment
import io.github.CMPUT301F26stderr.campsbridge.di.AppServices

/** Owns one lazy service graph; content previews and test hosts do not start an Auth session. */
class CampsbridgeApplication : Application() {
    val services: AppServices by lazy {
        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        val environment =
            if (host.isEmpty()) {
                AppEnvironment.Cloud
            } else {
                AppEnvironment.Emulators(
                    host,
                )
            }
        AppContainerFactory(applicationContext, environment).create()
    }
}
