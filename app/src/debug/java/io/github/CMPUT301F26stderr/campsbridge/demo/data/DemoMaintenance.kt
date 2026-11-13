package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.Maintenance
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository

/** Resumes the offline store's removal jobs and detached photo files.
 * @param store Shared records used to authorize the current admin.
 * @param moderation Existing target cleanup owner.
 * @param images Existing local photo cleanup owner.
 */
class DemoMaintenance(
    private val store: DemoStore,
    private val moderation: ModerationRepository,
    private val images: DemoImageRepository,
) : Maintenance {
    /** Checks the administrator before resuming local cleanup.
     * @param adminId Active demo administrator.
     */
    override suspend fun resume(adminId: String) {
        store.read { active(adminId, Role.ADMIN) }
        moderation.resumeRemovals()
        images.resumeCleanup()
    }
}
