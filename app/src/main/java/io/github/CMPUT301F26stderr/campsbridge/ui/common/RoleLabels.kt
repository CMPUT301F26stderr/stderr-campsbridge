package io.github.CMPUT301F26stderr.campsbridge.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/**
 * Shared localized capability label.
 * @param role Actual account capability.
 */
@Composable
fun roleLabel(role: Role): String =
    stringResource(
        when (role) {
            Role.ENTRANT -> R.string.role_entrant
            Role.OWNER -> R.string.role_owner
            Role.ADMIN -> R.string.role_admin
        },
    )
