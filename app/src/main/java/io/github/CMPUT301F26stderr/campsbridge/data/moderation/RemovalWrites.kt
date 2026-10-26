package io.github.CMPUT301F26stderr.campsbridge.data.moderation

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal

/** Transaction writes shared by authorized removal requests and their recorded cleanup. */
internal object RemovalWrites {
    /**
     * Records durable dependent cleanup within the caller’s removal transaction.
     *
     * @param db Firestore client for the removal record and durable job.
     * @param tx Caller-owned Firestore transaction; reads must precede writes.
     * @param target Account or campsite image slot.
     * @param removal Recorded removal attribution retained throughout cleanup.
     */
    fun enqueue(
        db: FirebaseFirestore,
        tx: com.google.firebase.firestore.Transaction,
        target: ImageTarget,
        removal: Removal,
    ) {
        tx.set(
            db.collection("_removals").document(DocumentKeys.attachment(target)),
            removal.stored(),
        )
    }

    /**
     * Retains the first removal metadata and queues dependent campsite cleanup
     * transactionally.
     *
     * @param db Firestore client for the removal record and durable job.
     * @param tx Caller-owned Firestore transaction; reads must precede writes.
     * @param site Campsite already read within the caller’s transaction.
     * @param requested Requested removal metadata; an existing first-removal record takes
     * precedence.
     */
    fun markSite(
        db: FirebaseFirestore,
        tx: com.google.firebase.firestore.Transaction,
        site: Campsite,
        requested: Removal,
    ) {
        val removal = site.removal ?: requested
        tx.update(db.document(FirestorePaths.campsite(site.id)), "removal", removal.stored())
        enqueue(db, tx, ImageTarget(ImageTargetType.CAMPSITE, site.id), removal)
    }
}
