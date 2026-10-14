package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation

/**
 * Reads the account in this transaction and rejects removed accounts or missing required
 * capability.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param id Application entity document ID.
 * @param role Required capability, or null to accept any active account.
 */
internal fun Transaction.activeAccount(
    db: FirebaseFirestore,
    id: String,
    role: Role? = null,
): Account {
    val account = require(db.document(FirestorePaths.account(id)), AccountDocument)
    requireOperation(account.isActive, INACTIVE, "Account removed")
    requireOperation(role == null || role in account.roles, NOT_ALLOWED, "Account lacks role")
    return account
}
