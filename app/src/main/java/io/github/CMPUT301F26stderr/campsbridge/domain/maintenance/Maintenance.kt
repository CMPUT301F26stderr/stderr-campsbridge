package io.github.CMPUT301F26stderr.campsbridge.domain.maintenance

/** Global recovery available only through an explicit administrator request. */
fun interface Maintenance {
    /** Resumes durable work, preserving completed steps and original removal attribution.
     * Failure leaves unfinished work available for a later pass.
     * @param adminId Active administrator requesting recovery.
     */
    suspend fun resume(adminId: String)
}
