package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/**
 * Honors a permitted preference, otherwise choosing entrant, owner, then admin.
 * @param roles Actual account capabilities.
 * @param preferred Local preference, which never grants a capability.
 * @return A permitted workspace, or null when no capabilities are available.
 */
fun permittedWorkspace(
    roles: Set<Role>,
    preferred: Role?,
): Role? {
    if (preferred != null && preferred in roles) return preferred
    return Role.entries.firstOrNull { it in roles }
}
