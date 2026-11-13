/*
 * One private offline dataset, committed to disk before observers see a successful change.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.image.ImageAttachmentDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.data.messaging.MessageDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.demo.DemoPreviewData
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_FOUND
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.ObservableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** One private offline dataset, committed to disk before observers see a successful change.
 * @param initial Baseline documents, encoded with the application's existing codecs.
 * @param file Optional private snapshot; absent for isolated helper examples.
 * @param start Initial controlled time.
 */
class DemoStore(
    initial: Map<String, Map<String, Any?>> = emptyMap(),
    private val file: File? = null,
    start: Instant = Instant.parse("2027-05-10T18:00:00Z"),
) {
    private val state = MutableStateFlow(if (file?.isFile == true) load(file) else initial)
    private val lock = Mutex()
    val clock =
        DemoClock(
            (state.value["_demo/clock"]?.get("at") as? Timestamp)?.let {
                Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
            }
                ?: start,
        )

    /** Reads one consistent committed snapshot.
     * @param block Pure query over the current documents.
     */
    fun <T> read(block: DemoDocuments.() -> T): T =
        DemoDocuments(state.value.toMutableMap()).block()

    /** Observes query results, suppressing unrelated document changes.
     * @param block Pure query reapplied after each commit.
     */
    fun <T> observe(block: DemoDocuments.() -> T) =
        state.map { DemoDocuments(it.toMutableMap()).block() }.distinctUntilChanged()

    /** Commits coordinated changes once; a validation or disk failure publishes no partial state.
     * @param block Validated local operation; writes stay private until it returns.
     */
    suspend fun <T> change(block: DemoDocuments.() -> T): T =
        lock.withLock {
            val documents = state.value.toMutableMap()
            val result = DemoDocuments(documents).block()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable + (if (file != null) Dispatchers.IO else EmptyCoroutineContext)) {
                if (file != null) save(file, documents)
                (documents["_demo/clock"]?.get("at") as? Timestamp)?.let {
                    clock.set(Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()))
                }
                state.value = documents.toMap()
            }
            result
        }

    /** Advances only this demonstration's time and wakes existing deadline observers.
     * @param at Next instant; rewinding would invalidate the saved history.
     */
    suspend fun advanceTo(at: Instant) {
        change {
            require(at >= clock.instant())
            raw["_demo/clock"] = mapOf("at" to Timestamp(at.epochSecond, at.nano))
        }
    }
}

/** A transaction/query view; codec use keeps the local and cloud document contracts aligned.
 * @param raw Private transaction copy or a committed query snapshot.
 */
class DemoDocuments internal constructor(
    val raw: MutableMap<String, Map<String, Any?>>,
) {
    /** Decodes a saved record, returning null for confirmed absence.
     * @param path Record identity.
     * @param codec Existing application codec.
     */
    fun <T> get(
        path: String,
        codec: DocumentCodec<T>,
    ): T? =
        raw[path]?.let { codec.decode(path, it) }

    /** Requires a saved record before proceeding.
     * @param path Record identity.
     * @param codec Existing application codec.
     */
    fun <T> require(
        path: String,
        codec: DocumentCodec<T>,
    ): T =
        get(path, codec)
            ?: throw io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure(
                NOT_FOUND,
                "Record not found",
            )

    /** Lists direct children only, retaining insertion order.
     * @param collection Parent collection path.
     * @param codec Existing application codec.
     */
    fun <T> list(
        collection: String,
        codec: DocumentCodec<T>,
    ): List<T> =
        raw
            .filterKeys {
                it.substringBeforeLast('/') ==
                    collection
            }.map { (path, value) -> codec.decode(path, value) }

    /** Replaces one record in the private transaction.
     * @param path Record identity.
     * @param codec Existing application codec.
     * @param value Validated model to encode.
     */
    fun <T> put(
        path: String,
        codec: DocumentCodec<T>,
        value: T,
    ) {
        raw[path] = codec.encode(value)
    }

    /** Checks the active actor and requested capability.
     * @param id Stable demo person.
     * @param role Required capability, if any.
     */
    fun active(
        id: String,
        role: Role? = null,
    ): Account =
        require(Paths.account(id), AccountDocument).also {
            requireOperation(it.isActive, INACTIVE, "Account removed")
            requireOperation(role == null || role in it.roles, NOT_ALLOWED, "Account lacks this role")
        }

    /** Publishes one event and its actual local receipts in the originating transaction.
     * @param event Stable event fact.
     * @param recipients Captured recipient identities.
     */
    fun notify(
        event: Notification,
        recipients: List<String>,
    ) {
        if (get(Paths.notification(event.id), NotificationDocument) != null) return
        put(Paths.notification(event.id), NotificationDocument, event)
        recipients.distinct().forEach {
            put(
                Paths.receipt(it, event.id),
                InboxReceiptDocument,
                InboxReceipt(event.id, null),
            )
        }
    }
}

/** Controlled time shared by timezone views and deadline consumers.
 * @param at Initial instant.
 * @param zone Current calendar timezone.
 * @param changes Shared time signal.
 */
class DemoClock(
    at: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
    override val changes: MutableStateFlow<Instant> = MutableStateFlow(at),
) : Clock(),
    ObservableClock {
    /** Returns the current calendar zone. */
    override fun getZone(): ZoneId =
        zone

    /** Shares controlled time with another zone.
     * @param zone Requested calendar zone.
     */
    override fun withZone(zone: ZoneId): Clock =
        DemoClock(instant(), zone, changes)

    /** Returns the latest committed demonstration time. */
    override fun instant(): Instant =
        changes.value

    /** Publishes a committed time change.
     * @param at New instant.
     */
    internal fun set(at: Instant) {
        changes.value = at
    }
}

/** Encodes the coherent baseline without constructing or initializing Firebase clients. */
fun DemoPreviewData.documents(): Map<String, Map<String, Any?>> =
    buildMap {
        val docs = DemoDocuments(this)
        accounts.forEach { docs.put(Paths.account(it.id), AccountDocument, it) }
        campsites.forEach { docs.put(Paths.campsite(it.id), CampsiteDocument, it) }
        calendars.forEach { (id, nights) ->
            nights.forEach { docs.put(Paths.night(id, it.date), CalendarNightDocument, it) }
        }
        lotteries.forEach { docs.put(Paths.lottery(it.id), LotteryDocument, it) }
        offers.forEach { (id, offers) ->
            offers.forEach { docs.put(Paths.offer(id, it.campsiteId), LotteryOfferDocument, it) }
        }
        this@documents.entries.forEach { docs.put(Paths.entry(it.lotteryId, it.accountId), LotteryEntryDocument, it) }
        reservations.forEach { docs.put(Paths.reservation(it.id), ReservationDocument, it) }
        reviews.forEach { docs.put(Paths.review(it.campsiteId, it.authorId), ReviewDocument, it) }
        messages.forEach { docs.put(Paths.message(it.id), MessageDocument, it) }
        events.forEach { docs.put(Paths.notification(it.id), NotificationDocument, it) }
        inboxes.forEach { (id, receipts) ->
            receipts.forEach { docs.put(Paths.receipt(id, it.notificationId), InboxReceiptDocument, it) }
        }
        photoReferences.keys.forEach {
            docs.put(Paths.image(it), ImageAttachmentDocument, ImageAttachment(it, "sample-${it.type}-${it.id}", now))
        }
    }

/** Converts codec values to private JSON, retaining exact integers, timestamps and coordinates.
 * @param value Encoded document value.
 */
private fun pack(value: Any?): Any =
    when (value) {
        null -> JSONObject.NULL
        is Timestamp -> JSONObject(mapOf("@time" to value.seconds, "nanos" to value.nanoseconds))
        is GeoPoint -> JSONObject(mapOf("@point" to value.latitude, "longitude" to value.longitude))
        is Map<*, *> -> JSONObject(value.entries.associate { it.key.toString() to pack(it.value) })
        is List<*> -> JSONArray(value.map(::pack))
        else -> value
    }

/** Restores the current private snapshot format; no migration or old-schema defaults.
 * @param value Parsed JSON value.
 */
private fun unpack(value: Any?): Any? =
    when (value) {
        JSONObject.NULL, null -> {
            null
        }

        is JSONObject -> {
            when {
                value.has("@time") -> Timestamp(value.getLong("@time"), value.getInt("nanos"))
                value.has("@point") -> GeoPoint(value.getDouble("@point"), value.getDouble("longitude"))
                else -> value.keys().asSequence().associateWith { unpack(value.get(it)) }
            }
        }

        is JSONArray -> {
            List(value.length()) { unpack(value.get(it)) }
        }

        is Int -> {
            value.toLong()
        }

        else -> {
            value
        }
    }

/** Reads a previously committed private snapshot.
 * @param file Local snapshot path.
 */
@Suppress("UNCHECKED_CAST")
private fun load(file: File): Map<String, Map<String, Any?>> =
    unpack(JSONObject(file.readText())) as Map<String, Map<String, Any?>>

/** Atomically replaces the snapshot before confirming a local operation.
 * @param file Private snapshot path.
 * @param documents New coordinated state.
 */
private fun save(
    file: File,
    documents: Map<String, Map<String, Any?>>,
) {
    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
    val temporary = File(file.parentFile, "${file.name}.new")
    try {
        temporary.outputStream().use { stream ->
            stream.write(pack(documents).toString().toByteArray())
            stream.fd.sync()
        }
        // Android's same-directory rename replaces the old snapshot atomically.
        if (!temporary.renameTo(file)) throw IOException("Could not commit offline records")
    } catch (failure: Exception) {
        temporary.delete()
        throw failure
    }
}
