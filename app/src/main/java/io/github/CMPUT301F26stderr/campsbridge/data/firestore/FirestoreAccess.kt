package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads a document from the server, returning null when absent.
 *
 * @param T Application model represented by the codec or operation.
 * @param codec Codec converting stored fields into the application model.
 */
internal suspend fun <T> DocumentReference.read(codec: DocumentCodec<T>): T? =
    get(Source.SERVER).await().let { if (it.exists()) codec.decode(it) else null }

/**
 * Streams the latest decoded snapshots and releases the listener when collection ends.
 * Listener/decoding failures terminate the Flow.
 *
 * @param T Application model represented by the codec or operation.
 * @param codec Codec converting stored fields into the application model.
 */
internal fun <T> DocumentReference.observe(codec: DocumentCodec<T>): Flow<T?> =
    callbackFlow {
        val registration =
            addSnapshotListener { snapshot, failure ->
                if (failure != null) {
                    close(failure)
                } else if (snapshot != null) {
                    try {
                        trySend(if (snapshot.exists()) codec.decode(snapshot) else null)
                    } catch (failure: Exception) {
                        close(failure)
                    }
                }
            }
        awaitClose { registration.remove() }
    }.buffer(Channel.CONFLATED)

/**
 * Streams the latest decoded snapshots and releases the listener when collection ends.
 * Listener/decoding failures terminate the Flow.
 *
 * @param T Application model represented by the codec or operation.
 * @param codec Codec converting stored fields into the application model.
 */
internal fun <T> Query.observe(codec: DocumentCodec<T>): Flow<List<T>> =
    callbackFlow {
        val registration =
            addSnapshotListener { snapshot, failure ->
                if (failure != null) {
                    close(failure)
                } else if (snapshot != null) {
                    try {
                        trySend(snapshot.documents.map(codec::decode))
                    } catch (failure: Exception) {
                        close(failure)
                    }
                }
            }
        awaitClose { registration.remove() }
    }.buffer(Channel.CONFLATED)

/**
 * Reads query results from the server and decodes every document.
 *
 * @param T Application model represented by the codec or operation.
 * @param codec Codec converting stored fields into the application model.
 */
internal suspend fun <T> Query.read(codec: DocumentCodec<T>): List<T> =
    get(Source.SERVER).await().documents.map(codec::decode)

/**
 * Reads and decodes a document in the caller’s transaction, returning null when absent.
 *
 * @param T Application model represented by the codec or operation.
 * @param reference Document reference read within the current transaction.
 * @param codec Codec converting stored fields into the application model.
 */
internal fun <T> Transaction.read(
    reference: DocumentReference,
    codec: DocumentCodec<T>,
): T? =
    get(reference).let { if (it.exists()) codec.decode(it) else null }

/**
 * Reads a required document in this transaction, failing with NOT_FOUND when absent.
 *
 * @param T Application model represented by the codec or operation.
 * @param reference Document reference read within the current transaction.
 * @param codec Codec converting stored fields into the application model.
 */
internal fun <T> Transaction.require(
    reference: DocumentReference,
    codec: DocumentCodec<T>,
): T =
    read(reference, codec)
        ?: throw PersistenceFailure(PersistenceFailure.Reason.NOT_FOUND, reference.path)

/**
 * Runs a retryable transaction and unwraps typed operation/decoding failures from Firebase
 * wrappers.
 *
 * @param T Application model represented by the codec or operation.
 * @param body Side-effect-free transaction callback; Firestore may rerun it.
 */
internal suspend fun <T> FirebaseFirestore.atomic(body: (Transaction) -> T): T =
    try {
        runTransaction { body(it) }.await()
    } catch (failure: Exception) {
        // Firebase wraps exceptions thrown by a transaction callback. Preserve typed domain
        // failures.
        var cause: Throwable? = failure
        while (cause != null) {
            if (cause is PersistenceFailure) throw cause
            if (cause is DocumentDecodingException) throw cause
            cause = cause.cause
        }
        throw failure
    }
