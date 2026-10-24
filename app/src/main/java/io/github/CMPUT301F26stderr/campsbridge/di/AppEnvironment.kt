package io.github.CMPUT301F26stderr.campsbridge.di

/** Explicit client destination; cloud configuration comes from google-services resources. */
sealed interface AppEnvironment {
    /** Uses the configured Firebase project. */
    data object Cloud : AppEnvironment

    /**
     * Uses only the disposable demo project, configured before any operation.
     * @param host Emulator host reachable from the Android device.
     * @param firestorePort Firestore emulator port.
     * @param authPort Authentication emulator port.
     * @param storagePort Storage emulator port.
     */
    data class Emulators(
        val host: String,
        val firestorePort: Int = 8080,
        val authPort: Int = 9099,
        val storagePort: Int = 9199,
    ) : AppEnvironment {
        init {
            require(host.isNotBlank())
        }
    }
}
