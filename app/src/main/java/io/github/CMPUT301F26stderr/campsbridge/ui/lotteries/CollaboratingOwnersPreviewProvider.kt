/*
 * Supplies owner identities, lock reasons and retained form feedback without repository access.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Supplies owner identities, lock reasons and retained form feedback without repository access. */
class CollaboratingOwnersPreviewProvider : PreviewParameterProvider<CollaboratingOwnersUiState> {
    private val lottery =
        (LotteryDetailsEditPreviewProvider().values.first() as LotteryDetailsEditUiState.Content)
            .lottery
            .copy(participantOwnerIds = setOf("owner-a", "owner-b"))
    private val organizer =
        CollaboratingOwnerChoice(
            "owner-a",
            "Jordan Lee and the Riverbend campsite team",
            isOrganizer = true,
            hasAllocations = true,
            canChange = false,
            showId = false,
        )
    private val contributor =
        CollaboratingOwnerChoice(
            "owner-b",
            "Sam Rivera",
            isOrganizer = false,
            hasAllocations = true,
            canChange = false,
            showId = true,
        )
    private val addition =
        CollaboratingOwnerChoice(
            "owner-c",
            "Sam Rivera",
            isOrganizer = false,
            hasAllocations = false,
            canChange = true,
            showId = true,
        )
    private val content =
        CollaboratingOwnersUiState(
            targetLoad = LotteryTargetLoad.READY,
            lottery = lottery,
            selectedOwnerIds = lottery.participantOwnerIds + addition.id,
            savedOwnerIds = lottery.participantOwnerIds,
            owners = listOf(organizer, contributor, addition),
            canEdit = true,
            canSave = true,
        )
    private val newContributor =
        CollaboratingOwnerChoice(
            "owner-d",
            "Taylor and the forest campsite group",
            isOrganizer = false,
            hasAllocations = true,
            canChange = true,
            showId = false,
        )

    /** Covers editable, locked, saving, failed, conflicted and loading outcomes. */
    override val values: Sequence<CollaboratingOwnersUiState> =
        sequenceOf(
            content,
            content.copy(
                query = "No matching new person",
                selectedOwnerIds = lottery.participantOwnerIds,
                owners = listOf(organizer, contributor),
            ),
            content.copy(canEdit = false, canSave = false),
            content.copy(isSaving = true, canSave = false),
            content.copy(saveFailed = true),
            content.copy(
                contextChanged = true,
                lottery =
                    lottery.copy(
                        participantOwnerIds =
                            lottery.participantOwnerIds + newContributor.id,
                    ),
                savedOwnerIds = lottery.participantOwnerIds + newContributor.id,
                owners = listOf(organizer, contributor, newContributor, addition),
                canSave = false,
            ),
            content.copy(
                lottery = lottery.copy(participantOwnerIds = setOf(organizer.id)),
                savedOwnerIds = setOf(organizer.id),
                needsPublishedCampsites = true,
                canSave = false,
                owners = listOf(organizer, contributor.copy(hasAllocations = false, canChange = true), addition),
            ),
            CollaboratingOwnersUiState(),
            CollaboratingOwnersUiState(targetLoad = LotteryTargetLoad.FAILURE),
            CollaboratingOwnersUiState(targetLoad = LotteryTargetLoad.UNAVAILABLE),
        )
}
