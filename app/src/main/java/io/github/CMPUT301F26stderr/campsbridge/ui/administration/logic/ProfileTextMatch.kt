/*
 * Searches current identity and contact fields locally, including retained records.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import java.util.Locale

/** Searches current identity and contact fields locally, including retained records.
 * @param account Account to check.
 * @param query Raw search text; blank matches every account.
 */
fun matchesProfileText(
    account: Account,
    query: String,
): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    return listOf(account.id, account.name, account.email.orEmpty())
        .any { it.lowercase(Locale.ROOT).contains(needle) }
}
