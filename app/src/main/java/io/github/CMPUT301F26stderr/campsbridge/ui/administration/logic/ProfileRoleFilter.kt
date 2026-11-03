package io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/** A null role includes every account; capabilities may coexist. */
fun matchesProfileRole(account: Account, role: Role?): Boolean = role == null || role in account.roles
