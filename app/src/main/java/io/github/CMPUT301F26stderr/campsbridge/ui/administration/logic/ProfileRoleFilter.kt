/*
 * Filters profiles by their actual capabilities while retaining source order.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/** A null role includes every account; capabilities may coexist.
 * @param account Account to check.
 * @param role Required role, or null for any.
 */
fun matchesProfileRole(
    account: Account,
    role: Role?,
): Boolean =
    role == null || role in account.roles

/** Filters actual capability membership while preserving source order and records.
 * @param accounts Accounts to filter.
 * @param role Required role, or null for every account.
 */
fun filterProfilesByRole(
    accounts: List<Account>,
    role: Role?,
): List<Account> =
    accounts.filter { matchesProfileRole(it, role) }
