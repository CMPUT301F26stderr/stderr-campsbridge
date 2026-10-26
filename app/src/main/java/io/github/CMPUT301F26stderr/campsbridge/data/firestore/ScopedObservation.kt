package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Observes current related records and cancels children when membership changes or collection ends.
 * @param keys Related identities; duplicate keys and ordering have no meaning.
 * @param observe Observation opened for each distinct identity.
 * @param T Related application record type.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun <T> observeRelated(
    keys: Flow<List<String>>,
    observe: (String) -> Flow<List<T>>,
): Flow<List<T>> =
    keys.map { it.distinct().sorted() }.distinctUntilChanged().flatMapLatest { ids ->
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ids.map(observe)) { groups -> groups.toList().flatten() }
        }
    }
