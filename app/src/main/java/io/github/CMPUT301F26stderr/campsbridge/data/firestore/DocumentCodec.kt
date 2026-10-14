package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.time.Instant
import java.time.LocalDate

/**
 * Malformed-document failure carrying the affected Firestore path and underlying cause.
 *
 * @param documentPath Firestore path identifying the malformed document.
 * @param detail Description of the malformed or missing stored value.
 * @param cause Underlying failure retained for diagnosis.
 */
class DocumentDecodingException(
    val documentPath: String,
    detail: String,
    cause: Throwable? = null,
) : Exception("$documentPath: $detail", cause)

/**
 * Explicit codecs keep SDK types and storage field names out of application models.
 *
 * @param T Application model represented by the codec or operation.
 */
abstract class DocumentCodec<T> {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    abstract fun encode(value: T): Map<String, Any?>

    /**
     * Builds and validates an application model using the strict field reader.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    protected abstract fun read(fields: Fields): T

    /**
     * Decodes stored fields and wraps invalid model values with document-path context.
     *
     * @param path Firestore document path carrying identity and decoding context.
     * @param data Stored fields to read and validate.
     */
    fun decode(
        path: String,
        data: Map<String, Any?>,
    ): T =
        try {
            read(Fields(path, data))
        } catch (failure: DocumentDecodingException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            throw DocumentDecodingException(path, "Invalid field value", failure)
        } catch (failure: java.time.DateTimeException) {
            throw DocumentDecodingException(path, "Invalid date/time", failure)
        }

    /**
     * Decodes stored fields and wraps invalid model values with document-path context.
     *
     * @param document Firestore snapshot to decode.
     */
    fun decode(document: DocumentSnapshot): T =
        decode(
            document.reference.path,
            document.data
                ?: throw DocumentDecodingException(document.reference.path, "Missing document"),
        )
}

/**
 * Strict typed reader for stored fields, preserving document context in decoding failures.
 *
 * @param path Firestore document path carrying identity and decoding context.
 * @param data Stored fields to read and validate.
 */
class Fields(
    val path: String,
    private val data: Map<String, Any?>,
) {
    val id: String
        get() = path.substringAfterLast('/')

    val parentId: String
        get() = path.substringBeforeLast('/').substringBeforeLast('/').substringAfterLast('/')

    /**
     * Throws a path-aware decoding failure for a missing or invalid field.
     *
     * @param name Stored field name to validate and read.
     */
    fun malformed(name: String): Nothing =
        throw DocumentDecodingException(path, "Missing or invalid '$name'")

    /**
     * Reads a required string, rejecting missing or wrongly typed values.
     *
     * @param name Stored field name to validate and read.
     */
    fun string(name: String): String =
        data[name] as? String ?: malformed(name)

    /**
     * Reads a nullable string; a missing or null field returns null.
     *
     * @param name Stored field name to validate and read.
     */
    fun optionalString(name: String): String? =
        if (data[name] == null) null else string(name)

    /**
     * Reads a required Firestore integer, rejecting other numeric storage types.
     *
     * @param name Stored field name to validate and read.
     */
    fun long(name: String): Long =
        data[name] as? Long ?: malformed(name)

    /**
     * Reads a required Boolean field.
     *
     * @param name Stored field name to validate and read.
     */
    fun boolean(name: String): Boolean =
        data[name] as? Boolean ?: malformed(name)

    /**
     * Reads a finite numeric value as a Double.
     *
     * @param name Stored field name to validate and read.
     */
    fun double(name: String): Double =
        (data[name] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: malformed(name)

    /**
     * Reads a Firestore timestamp as an Instant with nanosecond precision.
     *
     * @param name Stored field name to validate and read.
     */
    fun instant(name: String): Instant =
        (data[name] as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        } ?: malformed(name)

    /**
     * Reads an optional timestamp, returning null for missing or null fields.
     *
     * @param name Stored field name to validate and read.
     */
    fun optionalInstant(name: String): Instant? =
        if (data[name] == null) null else instant(name)

    /**
     * Reads an optional Firestore integer, returning null for missing or null fields.
     *
     * @param name Stored field name to validate and read.
     */
    fun optionalLong(name: String): Long? =
        if (data[name] == null) null else long(name)

    /**
     * Reads a canonical YYYY-MM-DD string as a local date.
     *
     * @param name Stored field name to validate and read.
     */
    fun date(name: String): LocalDate =
        parseDate(string(name))

    /**
     * Reads a GeoPoint as validated SDK-independent coordinates.
     *
     * @param name Stored field name to validate and read.
     */
    fun point(name: String): Coordinates =
        (data[name] as? GeoPoint)?.let { Coordinates(it.latitude, it.longitude) } ?: malformed(name)

    /**
     * Reads a list of strings and rejects invalid elements.
     *
     * @param name Stored field name to validate and read.
     */
    fun strings(name: String): List<String> =
        (data[name] as? List<*>)?.map { it as? String ?: malformed(name) } ?: malformed(name)

    /**
     * Reads unique string values and rejects duplicate stored entries.
     *
     * @param name Stored field name to validate and read.
     */
    fun stringSet(name: String): Set<String> =
        strings(name).let {
            if (it.distinct().size != it.size) malformed(name)
            it.toSet()
        }

    /**
     * Reads a stable enum name, rejecting unknown values.
     *
     * @param E Enum whose stable names are stored in the field.
     * @param name Stored field name to validate and read.
     */
    inline fun <reified E : Enum<E>> enum(name: String): E =
        enumValueOf(string(name))

    /**
     * Reads unique stable enum names into a set, rejecting unknown names.
     *
     * @param E Enum whose stable names are stored in the field.
     * @param name Stored field name to validate and read.
     */
    inline fun <reified E : Enum<E>> enumSet(name: String): Set<E> =
        stringSet(name).map { enumValueOf<E>(it) }.toSet()

    /**
     * Reads a required map using a nested path for decoding error context.
     *
     * @param name Stored field name to validate and read.
     */
    fun nested(name: String): Fields {
        val map = data[name] as? Map<*, *> ?: malformed(name)
        return Fields(
            "$path.$name",
            map.entries.associate { (it.key as? String ?: malformed(name)) to it.value },
        )
    }

    /**
     * Reads a nullable map, returning null for a missing or null field.
     *
     * @param name Stored field name to validate and read.
     */
    fun optionalNested(name: String): Fields? =
        if (data[name] == null) null else nested(name)
}

/**
 * Parses only canonical ten-character YYYY-MM-DD dates.
 *
 * @param value Canonical YYYY-MM-DD local date string.
 */
fun parseDate(value: String): LocalDate =
    LocalDate.parse(value).also { require(it.toString() == value && value.length == 10) }

/**
 * Converts the value to its native Firestore representation.
 */
fun Instant.stored(): Timestamp =
    Timestamp(epochSecond, nano)

/**
 * Converts the value to its native Firestore representation.
 */
fun Coordinates.stored(): GeoPoint =
    GeoPoint(latitude, longitude)
