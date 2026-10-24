package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

/** Dynamic query membership owns and releases every related subscription. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScopedObservationTest {
    /** Empty membership is an empty result and does not open child observations. */
    @Test fun emptyMembershipNeedsNoChildren() =
        runTest {
            var calls = 0
            assertEquals(
                emptyList<Int>(),
                observeRelated(flowOf(emptyList())) {
                    calls++
                    flowOf(listOf(1))
                }.first(),
            )
            assertEquals(0, calls)
        }

    /** Membership changes replace subscriptions; reordering/duplicate keys do not restart them. */
    @Test fun membershipReplacementReleasesObsoleteChildren() =
        runTest {
            val keys = MutableStateFlow(listOf("a", "b"))
            val values =
                mapOf(
                    "a" to MutableStateFlow(listOf(1)),
                    "b" to MutableStateFlow(listOf(2)),
                    "c" to MutableStateFlow(listOf(3)),
                )
            val active = mutableSetOf<String>()
            var starts = 0
            var latest = emptyList<Int>()
            val collector =
                backgroundScope.launch {
                    observeRelated(keys) { key ->
                        flow {
                            starts++
                            active.add(key)
                            try {
                                emitAll(values.getValue(key))
                            } finally {
                                active.remove(key)
                            }
                        }
                    }.collect { latest = it }
                }
            runCurrent()
            assertEquals(listOf(1, 2), latest)
            assertEquals(setOf("a", "b"), active)
            keys.value = listOf("b", "a", "a")
            runCurrent()
            assertEquals(2, starts)
            keys.value = listOf("c", "b")
            runCurrent()
            assertEquals(setOf("b", "c"), active)
            assertEquals(listOf(2, 3), latest)
            values.getValue("a").value = listOf(99)
            runCurrent()
            assertEquals(listOf(2, 3), latest)
            values.getValue("c").value = listOf(30)
            runCurrent()
            assertEquals(listOf(2, 30), latest)
            collector.cancelAndJoin()
            assertEquals(emptySet<String>(), active)
        }

    /** A child failure propagates and closes its siblings rather than emitting empty success. */
    @Test fun childFailureReleasesEveryChild() =
        runTest {
            val fail = CompletableDeferred<Unit>()
            val active = mutableSetOf<String>()
            val failure = IOException("child offline")
            val outcome =
                async {
                    runCatching {
                        observeRelated(flowOf(listOf("a", "b"))) { key ->
                            flow<List<Int>> {
                                active.add(key)
                                try {
                                    if (key == "b") {
                                        fail.await()
                                        throw failure
                                    }
                                    emit(listOf(1))
                                    awaitCancellation()
                                } finally {
                                    active.remove(key)
                                }
                            }
                        }.collect()
                    }.exceptionOrNull()
                }
            runCurrent()
            assertEquals(setOf("a", "b"), active)
            fail.complete(Unit)
            val actual = outcome.await()
            assertNotNull(actual)
            assertEquals(IOException::class.java, actual!!.javaClass)
            assertEquals(failure.message, actual.message)
            assertEquals(emptySet<String>(), active)
        }

    /** A failing parent query also cancels its active related observations. */
    @Test fun parentFailureReleasesEveryChild() =
        runTest {
            val fail = CompletableDeferred<Unit>()
            var active = false
            val failure = IOException("parent offline")
            val keys =
                flow {
                    emit(listOf("a"))
                    fail.await()
                    throw failure
                }
            val outcome =
                async {
                    runCatching {
                        observeRelated(keys) {
                            flow<List<Int>> {
                                active = true
                                try {
                                    emit(listOf(1))
                                    awaitCancellation()
                                } finally {
                                    active = false
                                }
                            }
                        }.collect()
                    }.exceptionOrNull()
                }
            runCurrent()
            assertEquals(true, active)
            fail.complete(Unit)
            val actual = outcome.await()
            assertNotNull(actual)
            assertEquals(IOException::class.java, actual!!.javaClass)
            assertEquals(failure.message, actual.message)
            assertEquals(false, active)
        }
}
