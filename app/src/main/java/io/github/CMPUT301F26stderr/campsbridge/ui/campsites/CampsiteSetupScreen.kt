package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteAvailabilityStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteCapacityPriceStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteLocationStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteOptionsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsitePhotoStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteReviewStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteRulesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.LocalDate
import java.time.YearMonth

/** Eight stateless step contents in one shared scaffold; explicit save integration remains pending.
 * @param state Local input and confirmed-read outcomes.
 * @param onBack Returns within the same draft.
 * @param onNext Validates and advances.
 * @param onCancel Requests exit/discard confirmation.
 * @param onDismissDiscard Retains input.
 * @param onConfirmDiscard Abandons unsaved input.
 * @param onRetry Retries target lookup.
 * @param onEdit Opens a review section.
 * @param onName Local name edit.
 * @param onDescription Local description edit.
 * @param onLabel Local location label edit.
 * @param onZone Local timezone edit.
 * @param onCapacity Local guest-capacity edit.
 * @param onPrice Local exact-price edit.
 * @param onRules Local rules edit.
 * @param onAmenity Changes one amenity ID.
 * @param onTag Changes one tag ID.
 * @param onChoosePhoto Native picker action.
 * @param onDiscardPhoto Retains saved image.
 * @param onRemovePhoto Stages explicit removal.
 * @param onRetryPhoto Retries saved attachment display.
 * @param onMonth Changes visible inventory month.
 * @param onDate Changes one desired offered state.
 * @param onRetryCalendar Retries the visible month.
 * @param onOpenPolicy Opens packaged refund reader.
 * @param modifier Caller layout.
 * @param onChooseLocation Registered map child action; isolated content can omit it.
 */
@Composable
fun CampsiteSetupScreen(
    state: CampsiteSetupUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (CampsiteStep) -> Unit,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onLabel: (String) -> Unit,
    onZone: (String) -> Unit,
    onCapacity: (String) -> Unit,
    onPrice: (String) -> Unit,
    onRules: (String) -> Unit,
    onAmenity: (String, Boolean) -> Unit,
    onTag: (String, Boolean) -> Unit,
    onChoosePhoto: () -> Unit,
    onDiscardPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryPhoto: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onRetryCalendar: () -> Unit,
    onOpenPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseLocation: (() -> Unit)? = null,
) {
    BackHandler { if (state is CampsiteSetupUiState.Content) onBack() else onCancel() }
    if (state !is CampsiteSetupUiState.Content) {
        val visible = (state as? CampsiteSetupUiState.ConfirmDiscard)?.previous ?: state
        AppScreen(stringResource(R.string.campsite_setup_title), modifier, onBack = onCancel) {
            when (visible) {
                CampsiteSetupUiState.Loading -> {
                    LoadingContent()
                }

                CampsiteSetupUiState.Failure -> {
                    FailureContent(
                        stringResource(R.string.campsite_detail_failure),
                        onRetry,
                    )
                }

                CampsiteSetupUiState.Unavailable -> {
                    Text(
                        stringResource(R.string.campsite_unavailable),
                    )
                }

                else -> {
                    Unit
                }
            }
        }
        if (state is CampsiteSetupUiState.ConfirmDiscard) {
            SetupDiscardDialog(onConfirmDiscard, onDismissDiscard)
        }
        return
    }
    val draft = state.draft
    val review = draft.step == CampsiteStep.REVIEW
    StepScaffold(
        title = stringResource(R.string.campsite_setup_title),
        progress = stringResource(R.string.campsite_setup_step, draft.step.ordinal + 1, 8),
        onBack = onBack,
        onNext = onNext,
        modifier = modifier,
        nextEnabled = !review,
        nextLabel = stringResource(if (review) R.string.publish else R.string.next),
        steps = StepProgress(draft.step.ordinal + 1, 8),
        onCancel = onCancel,
        secondaryLabel = if (review) stringResource(R.string.save_draft) else null,
        onSecondaryAction = if (review) ({}) else null,
        secondaryEnabled = false,
        onSkip =
            if (draft.step == CampsiteStep.PHOTO) {
                (
                    {
                        onDiscardPhoto()
                        onNext()
                    }
                )
            } else {
                null
            },
    ) {
        Text(
            stringResource(campsiteStepTitle(draft.step)),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (draft.step) {
            CampsiteStep.BASICS -> {
                CampsiteBasicsStep(
                    draft.name,
                    draft.description,
                    state.errors.nameRequired,
                    onName,
                    onDescription,
                )
            }

            CampsiteStep.PHOTO -> {
                CampsitePhotoStep(
                    state.photoIntent,
                    state.savedPhoto,
                    state.photoReselectionRequired,
                    state.photoSelectionFailed,
                    onChoosePhoto,
                    onDiscardPhoto,
                    onRemovePhoto,
                    onRetryPhoto,
                )
            }

            CampsiteStep.LOCATION -> {
                CampsiteLocationStep(draft, state.errors, onLabel, onZone, onChooseLocation)
            }

            CampsiteStep.CAPACITY_PRICE -> {
                CampsiteCapacityPriceStep(draft, state.errors, onCapacity, onPrice)
            }

            CampsiteStep.OPTIONS -> {
                CampsiteOptionsStep(draft.amenities, draft.tags, onAmenity, onTag)
            }

            CampsiteStep.RULES -> {
                CampsiteRulesStep(draft.rules, onRules, onOpenPolicy)
            }

            CampsiteStep.AVAILABILITY -> {
                CampsiteAvailabilityStep(
                    state.month,
                    state.inventory,
                    draft.requestedAvailability,
                    onMonth,
                    onDate,
                    onRetryCalendar,
                )
            }

            CampsiteStep.REVIEW -> {
                CampsiteReviewStep(draft, state.photoIntent, onEdit)
                Text(stringResource(R.string.campsite_save_pending))
            }
        }
    }
    if (state.discardPrompt) {
        SetupDiscardDialog(onConfirmDiscard, onDismissDiscard)
    }
}

/** Uses the same confirmation during editing and interrupted restoration.
 * @param onConfirm Explicit abandonment.
 * @param onDismiss Retains input.
 */
@Composable
private fun SetupDiscardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationDialog(
        stringResource(R.string.campsite_discard_title),
        stringResource(R.string.campsite_discard_body),
        onConfirm,
        onDismiss,
        onDismiss,
        confirmLabel = stringResource(R.string.campsite_discard_confirm),
        destructive = true,
    )
}

/** Resolves the heading for an actual step rather than the prototype frame number.
 * @param step Current position.
 */
private fun campsiteStepTitle(step: CampsiteStep): Int =
    when (step) {
        CampsiteStep.BASICS -> R.string.campsite_setup_basics
        CampsiteStep.PHOTO -> R.string.campsite_setup_photo
        CampsiteStep.LOCATION -> R.string.campsite_setup_location
        CampsiteStep.CAPACITY_PRICE -> R.string.campsite_setup_capacity
        CampsiteStep.OPTIONS -> R.string.campsite_setup_options
        CampsiteStep.RULES -> R.string.campsite_setup_rules
        CampsiteStep.AVAILABILITY -> R.string.campsite_setup_availability
        CampsiteStep.REVIEW -> R.string.campsite_setup_review
    }

/** Supplies all eight realistic local content states to Android Studio's preview selector. */
internal class CampsiteSetupPreviewStates : PreviewParameterProvider<CampsiteSetupUiState> {
    override val values: Sequence<CampsiteSetupUiState> =
        CampsiteStep.entries.asSequence().map { step ->
            val site = previewCampsite()
            CampsiteSetupUiState.Content(
                savedPhoto = ImageSlotState(loadState = ImageLoadState.READY),
                draft =
                    CampsiteDraft(
                        name = site.details.name,
                        description = site.details.description,
                        location = site.details.location,
                        locationLabel = site.details.locationLabel,
                        timeZone = site.details.timeZone,
                        maxGuestsText = "8",
                        priceText = "65.00",
                        amenities = site.details.amenities,
                        tags = site.details.tags,
                        rules = site.details.rules,
                        step = step,
                    ),
                month = YearMonth.of(2027, 5),
                inventory = CampsiteInventoryUiState.Content(emptyList()),
            )
        }
}

/** Invokes production content with passive preview callbacks, never simulated persistence.
 * @param state Supplied preview state.
 */
@Composable
private fun SetupPreviewContent(state: CampsiteSetupUiState) {
    CampsbridgeTheme {
        CampsiteSetupScreen(
            state,
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            { _, _ -> },
            { _, _ -> },
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            onChooseLocation = {},
        )
    }
}

/** Renders each actual step under the common theme and footer.
 * @param state Supplied state for one preview.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CampsiteSetupPreview(
    @PreviewParameter(CampsiteSetupPreviewStates::class) state: CampsiteSetupUiState,
) {
    SetupPreviewContent(state)
}

/** A narrow large-font review retains natural scrolling and reachable footer actions. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LargeSetupReviewPreview() {
    SetupPreviewContent(CampsiteSetupPreviewStates().values.last())
}

/** Initial failure retains its title, Back and explicit retry. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SetupFailurePreview() {
    SetupPreviewContent(CampsiteSetupUiState.Failure)
}

/** Loading keeps the same title and return action. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SetupLoadingPreview() {
    SetupPreviewContent(CampsiteSetupUiState.Loading)
}

/** Explicit invalid input retains its field and shared footer. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InvalidSetupPreview() {
    SetupPreviewContent(
        CampsiteSetupUiState.Content(
            CampsiteDraft(),
            errors = CampsiteDetailsInput.Errors(nameRequired = true),
            month = YearMonth.of(2027, 5),
        ),
    )
}
