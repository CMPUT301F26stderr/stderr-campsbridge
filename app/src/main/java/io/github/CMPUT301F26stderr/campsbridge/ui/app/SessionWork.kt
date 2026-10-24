package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession

/** Supplies recovery capabilities only after account setup is complete. */
internal fun SessionState.workSession(): WorkSession? =
    (this as? SessionState.Ready)?.account?.let { WorkSession(it.id, it.roles) }
