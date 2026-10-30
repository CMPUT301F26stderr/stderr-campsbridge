package io.github.CMPUT301F26stderr.campsbridge.ui.administration

/** Separates confirmed absence from unresolved or failed inspection reads.
 * @param T Actual repository result, without another persisted model.
 */
sealed interface AdminRead<out T> {
    /** The requested read has not produced a confirmed result. */
    data object Loading : AdminRead<Nothing>

    /** The requested source failed and can be retried independently. */
    data object Failure : AdminRead<Nothing>

    /** A confirmed target is missing or inconsistent with its requested identity. */
    data object Unavailable : AdminRead<Nothing>

    /** Actual confirmed data, including retained removal facts.
     * @param value Original application record or rows.
     */
    data class Content<T>(
        val value: T,
    ) : AdminRead<T>
}
