package io.github.CMPUT301F26stderr.campsbridge.ui.app

/** Root operation/recovery state, independent of whether the profile graph still exists. */
enum class AccountRemovalPhase {
    IDLE,
    CONFIRMING,
    REMOVING,
    RESTORING,
    RETRY,
    NOT_STARTED,
    RECOVERY_FAILED,
    UNAVAILABLE,
    COMPLETE,
}

/** Original confirmed removal identity, never rebound to a later demo person.
 * @param accountId Original removal target.
 * @param actorId Original same-account actor.
 */
data class AccountRemovalRequest(
    val accountId: String,
    val actorId: String,
)

/** Root removal feedback; account marking alone never confirms completed cleanup.
 * @param phase Confirmation, actual cleanup or explicit restored recovery outcome.
 * @param request Original target/actor, retained after incomplete cleanup.
 */
data class AccountRemovalUiState(
    val phase: AccountRemovalPhase = AccountRemovalPhase.IDLE,
    val request: AccountRemovalRequest? = null,
) {
    val blocksPersonChange: Boolean get() =
        phase !in
            setOf(AccountRemovalPhase.IDLE, AccountRemovalPhase.COMPLETE, AccountRemovalPhase.NOT_STARTED)
}
