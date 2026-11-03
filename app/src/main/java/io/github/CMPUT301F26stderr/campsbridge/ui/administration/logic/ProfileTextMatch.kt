package io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import java.util.Locale

/** Searches current identity and contact fields locally, including retained records. */
fun matchesProfileText(account: Account, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    return listOf(account.id, account.name, account.email.orEmpty())
        .any { it.lowercase(Locale.ROOT).contains(needle) }
}
