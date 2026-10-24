package io.github.CMPUT301F26stderr.campsbridge.data.maintenance

import io.github.CMPUT301F26stderr.campsbridge.data.notification.DeliveryJob
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** A draw deadline or durable delivery job discovered through permitted aggregate records. */
internal sealed interface PendingOperation {
    val key: String
    val dueAt: Instant

    /** Draw becomes eligible at the registration close boundary.
     * @param id Lottery identity.
     * @param dueAt Registration closing instant.
     */
    data class Draw(
        val id: String,
        override val dueAt: Instant,
    ) : PendingOperation {
        override val key: String = "draw:$id"
    }

    /** Existing fan-out job; the delivery processor enforces any draw-completion gate.
     * @param job Parent-scoped durable job identity.
     */
    data class Delivery(
        val job: DeliveryJob,
    ) : PendingOperation {
        override val key: String = "${job.scope.path}/${job.id}"
        override val dueAt: Instant = Instant.MIN
    }
}

/** Narrow persistence boundary for discovery and execution, usable by deterministic tests. */
internal interface WorkSource {
    /** Streams relevant operations and owns all underlying subscriptions.
     * @param session Current account and complete capabilities.
     */
    fun observe(session: WorkSession): Flow<List<PendingOperation>>

    /** Executes one operation; durable progress must be safe to retry.
     * @param operation A discovered, currently due operation.
     */
    suspend fun execute(operation: PendingOperation)
}

/**
 * Serializes foreground work while subscriptions and deadline changes request conflated passes.
 * @param source Scoped discovery and persistence operations.
 * @param clock Injectable time source shared with repository deadline checks.
 */
internal class SessionPendingWork(
    private val source: WorkSource,
    private val clock: Clock,
) : PendingWork {
    /**
     * Keeps discovery separate from serial processing so draw writes cannot cancel their own pass.
     * Failed operations retry independently with 1s exponential backoff capped at one minute.
     * Discovery errors clear stale targets and resubscribe with the same bounded policy.
     * @param session Active account and all permitted roles.
     * @param onFailure Nonthrowing failure reporter.
     */
    override suspend fun run(
        session: WorkSession,
        onFailure: (Exception) -> Unit,
    ): Unit =
        coroutineScope {
            val latest = MutableStateFlow(emptyList<PendingOperation>())
            val wake = Channel<Unit>(Channel.CONFLATED)
            launch {
                flow { emitAll(source.observe(session)) }
                    .retryWhen { failure, attempt ->
                        if (failure is CancellationException) throw failure
                        if (failure !is Exception) throw failure
                        latest.value = emptyList()
                        wake.trySend(Unit)
                        onFailure(failure)
                        delay(retryDelay(attempt.coerceAtMost(6).toInt()))
                        true
                    }.collect { operations ->
                        latest.value = operations.distinctBy { it.key }
                        wake.trySend(Unit)
                    }
            }
            val retries = mutableMapOf<String, Retry>()
            var timer: Job? = null
            for (signal in wake) {
                timer?.cancel()
                val operations = latest.value
                retries.keys.retainAll(operations.map { it.key }.toSet())
                for (operation in operations) {
                    // Membership may change while the previous operation is suspended.
                    if (operation !in latest.value) continue
                    val readyAt = maxOf(operation.dueAt, retries[operation.key]?.at ?: Instant.MIN)
                    if (clock.instant() < readyAt) continue
                    try {
                        source.execute(operation)
                        retries.remove(operation.key)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        val attempt = retries[operation.key]?.attempt ?: 0
                        retries[operation.key] =
                            Retry(
                                (attempt + 1).coerceAtMost(6),
                                clock.instant().plusMillis(retryDelay(attempt)),
                            )
                        onFailure(failure)
                    }
                }
                val now = clock.instant()
                val next =
                    latest.value
                        .map {
                            maxOf(it.dueAt, retries[it.key]?.at ?: Instant.MIN)
                        }.filter { it > now }
                        .minOrNull()
                if (next != null) {
                    timer =
                        launch {
                            delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
                            wake.trySend(Unit)
                        }
                }
            }
        }
}

/** One failed operation's independent retry schedule.
 * @param attempt Bounded backoff exponent for the next failure.
 * @param at Earliest permitted retry instant.
 */
private data class Retry(
    val attempt: Int,
    val at: Instant,
)

/** Computes a bounded exponential delay without overflow.
 * @param attempt Zero-based consecutive failure count.
 */
private fun retryDelay(attempt: Int): Long =
    (1000L shl attempt.coerceIn(0, 6)).coerceAtMost(60000)
