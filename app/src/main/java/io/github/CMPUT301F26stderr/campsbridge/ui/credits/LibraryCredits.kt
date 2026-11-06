/*
 * Upstream license credit for dependencies outside the generated Android notices.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.credits

/** Upstream license credit for dependencies outside the generated Android notices.
 * @param name Published project or artifact names.
 * @param license Original license name.
 * @param sourceLicenseUrl License file in the original source repository.
 */
internal data class LibraryCredit(
    val name: String,
    val license: String,
    val sourceLicenseUrl: String,
)

// Include inherited Maven licenses and the server dependencies outside the Android dependency graph.
internal val AdditionalLibraryCredits =
    listOf(
        LibraryCredit(
            "Firebase Admin SDK for Node.js (booking service)",
            "Apache License 2.0",
            "https://github.com/firebase/firebase-admin-node/blob/v14.5.0/LICENSE",
        ),
        LibraryCredit(
            "Firebase Functions SDK (booking service)",
            "MIT License",
            "https://github.com/firebase/firebase-functions/blob/v7.4.0/LICENSE",
        ),
        LibraryCredit(
            "Moment Timezone (booking service)",
            "MIT License",
            "https://github.com/moment/moment-timezone/blob/0.6.6/LICENSE",
        ),
        LibraryCredit(
            "Moment.js (booking service)",
            "MIT License",
            "https://github.com/moment/moment/blob/2.31.0/LICENSE",
        ),
        LibraryCredit(
            "Guava failureaccess and listenablefuture",
            "Apache License 2.0",
            "https://github.com/google/guava/blob/master/LICENSE",
        ),
        LibraryCredit(
            "Truth",
            "Apache License 2.0",
            "https://github.com/google/truth/blob/master/LICENSE",
        ),
        LibraryCredit(
            "Byte Buddy and Byte Buddy Agent",
            "Apache License 2.0",
            "https://github.com/raphw/byte-buddy/blob/master/LICENSE",
        ),
        LibraryCredit(
            "Hamcrest Core",
            "BSD 3-Clause License",
            "https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE",
        ),
        LibraryCredit(
            "Objenesis",
            "Apache License 2.0",
            "https://github.com/easymock/objenesis/blob/master/LICENSE.txt",
        ),
    )
