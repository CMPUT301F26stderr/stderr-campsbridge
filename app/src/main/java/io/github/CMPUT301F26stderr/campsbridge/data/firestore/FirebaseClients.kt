package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Construct once, before any repository uses a client. No implicit project or credentials.
 *
 * @param firestore Firestore client for the initialized project or emulator.
 * @param storage Storage client that owns uploaded image bytes.
 */
data class FirebaseClients(
    val firestore: FirebaseFirestore,
    val storage: FirebaseStorage,
) {
    /**
     * Factories binding SDK clients to a configured project or emulator endpoints.
     */
    companion object {
        /**
         * Creates clients bound to an already initialized Firebase application.
         *
         * @param app Already initialized Firebase application for the desired project.
         */
        fun forApp(app: FirebaseApp): FirebaseClients =
            FirebaseClients(FirebaseFirestore.getInstance(app), FirebaseStorage.getInstance(app))

        /**
         * Routes new clients to the selected emulators before repositories use them.
         *
         * @param app Already initialized Firebase application for the desired project.
         * @param host Emulator host reachable from the device, such as 10.0.2.2.
         * @param firestorePort Port of the Firestore emulator.
         * @param storagePort Port of the Storage emulator.
         */
        fun forEmulators(
            app: FirebaseApp,
            host: String,
            firestorePort: Int = 8080,
            storagePort: Int = 9199,
        ): FirebaseClients {
            require(host.isNotBlank())
            return forApp(app).also {
                it.firestore.useEmulator(host, firestorePort)
                it.storage.useEmulator(host, storagePort)
            }
        }
    }
}
