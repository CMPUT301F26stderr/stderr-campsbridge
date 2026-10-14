package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito

/**
 * Owns the shared SDK-to-Flow adapter's lifetime and latest-state policy, without claiming
 * persistence guarantees. Parameterization checks both independent listener entry points.
 * @param query Whether the scenario subscribes to a query rather than one document.
 */
@RunWith(Parameterized::class)
class FirestoreObservationTest(
    private val query: Boolean,
) {
    /** Cancellation removes the SDK registration even when no snapshot ever arrives. */
    @Test(timeout = 10000)
    fun cancellationRemovesListener() =
        runBlocking {
            val subscription = Subscription(query)
            val collector = launch { subscription.flow.collect() }
            subscription.started.await()
            collector.cancelAndJoin()
            Mockito.verify(subscription.registration).remove()
        }

    /** Backend errors terminate observation with the original failure and release its registration. */
    @Test(timeout = 10000)
    fun backendFailureTerminatesAndReleasesListener() =
        runBlocking {
            val subscription = Subscription(query)
            val result = CompletableDeferred<Throwable?>()
            val collector =
                launch {
                    result.complete(runCatching { subscription.flow.collect() }.exceptionOrNull())
                }
            subscription.started.await()
            val failure = Mockito.mock(FirebaseFirestoreException::class.java)
            subscription.fail(failure)
            assertSame(failure, result.await())
            collector.join()
            Mockito.verify(subscription.registration).remove()
        }

    /** A busy observer receives the newest state rather than an unbounded backlog of stale snapshots. */
    @Test(timeout = 10000)
    fun slowCollectorReceivesLatestSnapshot() =
        runBlocking {
            val subscription = Subscription(query)
            val firstReceived = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val received = mutableListOf<List<Long>>()
            val collector =
                launch {
                    subscription.flow.take(2).collect { value ->
                        received.add(value)
                        if (received.size == 1) {
                            firstReceived.complete(Unit)
                            release.await()
                        }
                    }
                }
            subscription.started.await()
            subscription.emit(1)
            firstReceived.await()
            subscription.emit(2)
            subscription.emit(3)
            release.complete(Unit)
            collector.join()
            assertEquals(listOf(listOf(1L), listOf(3L)), received)
            Mockito.verify(subscription.registration).remove()
        }

    companion object {
        /** Exercises both independently implemented Firebase listener adapters. */
        @JvmStatic
        @Parameterized.Parameters(name = "query={0}")
        fun sources(): List<Array<Boolean>> =
            listOf(arrayOf(false), arrayOf(true))
    }
}

/**
 * Captures only the SDK subscription seam; production callback decoding/buffering runs unchanged.
 * @param query Whether the subscription exposes a query snapshot.
 */
private class Subscription(
    private val query: Boolean,
) {
    val registration: ListenerRegistration = Mockito.mock(ListenerRegistration::class.java)
    val started = CompletableDeferred<Unit>()
    private lateinit var documentListener: EventListener<DocumentSnapshot>
    private lateinit var queryListener: EventListener<QuerySnapshot>
    val flow: Flow<List<Long>>

    init {
        if (query) {
            val source = Mockito.mock(Query::class.java)
            Mockito
                .`when`(
                    source.addSnapshotListener(Mockito.any<EventListener<QuerySnapshot>>()),
                ).thenAnswer {
                    queryListener = it.getArgument(0)
                    started.complete(Unit)
                    registration
                }
            flow = source.observe(RevisionDocument)
        } else {
            val source = Mockito.mock(DocumentReference::class.java)
            Mockito
                .`when`(
                    source.addSnapshotListener(Mockito.any<EventListener<DocumentSnapshot>>()),
                ).thenAnswer {
                    documentListener = it.getArgument(0)
                    started.complete(Unit)
                    registration
                }
            flow = source.observe(RevisionDocument).map { listOfNotNull(it) }
        }
    }

    /**
     * Supplies a real codec-compatible snapshot through the captured SDK callback.
     * @param revision Observable value identifying the incoming state.
     */
    fun emit(revision: Long) {
        val reference = Mockito.mock(DocumentReference::class.java)
        Mockito.`when`(reference.path).thenReturn("observations/example")
        val snapshot = Mockito.mock(DocumentSnapshot::class.java)
        Mockito.`when`(snapshot.reference).thenReturn(reference)
        Mockito.`when`(snapshot.exists()).thenReturn(true)
        Mockito.`when`(snapshot.data).thenReturn(mapOf("revision" to revision))
        if (query) {
            val result = Mockito.mock(QuerySnapshot::class.java)
            Mockito.`when`(result.documents).thenReturn(listOf(snapshot))
            queryListener.onEvent(result, null)
        } else {
            documentListener.onEvent(snapshot, null)
        }
    }

    /**
     * Supplies the SDK's failure signal without replacing the production Flow adapter.
     * @param failure Error passed by the backend listener.
     */
    fun fail(failure: FirebaseFirestoreException) {
        if (query) queryListener.onEvent(null, failure) else documentListener.onEvent(null, failure)
    }
}

/** Minimal document for observing state changes without duplicating a feature's schema contract. */
private object RevisionDocument : DocumentCodec<Long>() {
    /**
     * Encodes the test's observable revision.
     * @param value Incoming revision.
     */
    override fun encode(value: Long): Map<String, Any?> =
        mapOf("revision" to value)

    /**
     * Decodes the test's observable revision using production strict field access.
     * @param fields Snapshot field reader.
     */
    override fun read(fields: Fields): Long =
        fields.long("revision")
}
