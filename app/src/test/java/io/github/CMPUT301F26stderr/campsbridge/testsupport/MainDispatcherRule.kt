package io.github.CMPUT301F26stderr.campsbridge.testsupport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Gives ViewModel tests a controlled Main dispatcher and restores it afterward.
 * @param dispatcher Dispatcher sharing the test's scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    /** Installs the test dispatcher before a test.
     * @param description Current JUnit test. */
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    /** Releases Main after a test.
     * @param description Current JUnit test. */
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
