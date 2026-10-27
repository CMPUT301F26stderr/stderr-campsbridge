package io.github.CMPUT301F26stderr.campsbridge.ui.credits

/** Upstream license credit for a library whose Maven child POM omits inherited terms.
 * @param name Published project or artifact names.
 * @param license Original license name.
 * @param sourceLicenseUrl License file in the original source repository.
 */
internal data class LibraryCredit(
    val name: String,
    val license: String,
    val sourceLicenseUrl: String,
)

// Other runtime notices come from the generated OSS reader. These inherited-license entries
// are missing from its child-POM scan; keep their upstream identities and terms explicit.
internal val AdditionalLibraryCredits =
    listOf(
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
