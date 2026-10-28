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
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.validateCampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteAvailabilityStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteCapacityPriceStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteLocationStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteOptionsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsitePhotoStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteReviewStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteRulesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.NoticeTone
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepAction
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.LocalDate
import java.time.YearMonth

/** Eight stateless step contents and explicit draft-save feedback in one shared scaffold.
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
 * @param onSaveDraft Explicit valid-review save; absent integrations keep the action disabled.
 * @param onRetrySave Explicit retry of the retained target after a failed save.
 * @param onPublish Explicit publication after all requested stages; absent consumers disable it.
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
    onSaveDraft: (() -> Unit)? = null,
    onRetrySave: (() -> Unit)? = null,
    onPublish: (() -> Unit)? = null,
) {
    BackHandler {
        if (state !is CampsiteSetupUiState.PublicationRecovery) {
            if (state is CampsiteSetupUiState.Content) onBack() else onCancel()
        }
    }
    if (state !is CampsiteSetupUiState.Content) {
        val visible = (state as? CampsiteSetupUiState.ConfirmDiscard)?.previous ?: state
        AppScreen(
            stringResource(R.string.campsite_setup_title),
            modifier,
            onBack = onCancel,
            backEnabled = visible !is CampsiteSetupUiState.PublicationRecovery,
        ) {
            when (visible) {
                is CampsiteSetupUiState.PublicationRecovery -> {
                    Text(stringResource(R.string.campsite_publication_recovery))
                    if (visible.isLoading) {
                        LoadingContent()
                    } else {
                        FailureContent(
                            stringResource(R.string.campsite_save_recovery_failure),
                            onRetry,
                        )
                    }
                }

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
    val submitting = state.savePhase != CampsiteSavePhase.IDLE
    val canEdit = !submitting && !state.photoNeedsRecovery && !state.publicationPending
    val retrying = state.saveFailure != null
    val retryPublication = review && retrying && state.saveAction == CampsiteSaveAction.PUBLISH
    val retryDraft = review && retrying && state.saveAction == CampsiteSaveAction.DRAFT
    val saveAction = if (retryDraft) onRetrySave else onSaveDraft
    val publishAction = if (retryPublication) onRetrySave else onPublish
    val canSave =
        validateCampsiteDetails(draft) is CampsiteDetailsInput.Value &&
            !state.photoReselectionRequired &&
            (draft.requestedAvailability.isEmpty() || !state.availabilityComparisonPending)
    StepScaffold(
        title = stringResource(R.string.campsite_setup_title),
        progress = stringResource(R.string.campsite_setup_step, draft.step.ordinal + 1, 8),
        onBack = onBack,
        onNext = { if (review) publishAction?.invoke() else onNext() },
        modifier = modifier,
        nextEnabled = if (review) canSave && publishAction != null else canEdit,
        isSubmitting = submitting,
        exitEnabled = !state.photoNeedsRecovery && !state.publicationPending,
        nextLabel =
            stringResource(
                when {
                    retryPublication -> R.string.campsite_retry_publish
                    review -> R.string.publish
                    else -> R.string.next
                },
            ),
        steps = StepProgress(draft.step.ordinal + 1, 8),
        onCancel = onCancel,
        secondaryLabel =
            if (review) {
                stringResource(
                    if (retryDraft) R.string.campsite_retry_save else R.string.save_draft,
                )
            } else {
                null
            },
        onSecondaryAction = if (review) ({ saveAction?.invoke() }) else null,
        secondaryEnabled =
            canSave && saveAction != null && !state.publicationPending &&
                !(state.photoNeedsRecovery && state.saveAction == CampsiteSaveAction.PUBLISH),
        submittingAction =
            if (state.saveAction == CampsiteSaveAction.PUBLISH) {
                StepAction.PRIMARY
            } else {
                StepAction.SECONDARY
            },
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
                if (submitting) {
                    Text(
                        stringResource(
                            when (state.savePhase) {
                                CampsiteSavePhase.PUBLISHING -> {
                                    R.string.campsite_publishing
                                }

                                CampsiteSavePhase.RECOVERING -> {
                                    R.string.campsite_checking_save
                                }

                                CampsiteSavePhase.SAVING_PHOTO -> {
                                    R.string.campsite_saving_photo
                                }

                                CampsiteSavePhase.SAVING_AVAILABILITY -> {
                                    R.string.campsite_saving_availability
                                }

                                else -> {
                                    R.string.campsite_saving_details
                                }
                            },
                        ),
                    )
                } else if (state.saveFailure != null) {
                    InlineNotice(
                        stringResource(R.string.campsite_save_failure_title),
                        stringResource(
                            if (state.publicationPending) {
                                R.string.campsite_publication_recovery
                            } else if (state.photoNeedsRecovery) {
                                R.string.campsite_photo_recovery
                            } else {
                                when (state.saveFailure) {
                                    CampsiteSaveFailure.DETAILS -> {
                                        R.string.campsite_details_save_failure
                                    }

                                    CampsiteSaveFailure.PHOTO -> {
                                        R.string.campsite_photo_save_failure
                                    }

                                    CampsiteSaveFailure.RECOVERY -> {
                                        R.string.campsite_save_recovery_failure
                                    }

                                    CampsiteSaveFailure.PHOTO_RESELECTION -> {
                                        R.string.campsite_photo_reselect
                                    }

                                    CampsiteSaveFailure.AVAILABILITY -> {
                                        R.string.campsite_availability_save_failure
                                    }

                                    CampsiteSaveFailure.PUBLICATION -> {
                                        R.string.campsite_publication_failure
                                    }

                                    CampsiteSaveFailure.AVAILABILITY_PENDING -> {
                                        R.string.campsite_date_save_pending
                                    }
                                }
                            },
                        ),
                        tone = NoticeTone.ERROR,
                    )
                }
                if (state.photoReselectionRequired &&
                    state.saveFailure != CampsiteSaveFailure.PHOTO_RESELECTION
                ) {
                    Text(stringResource(R.string.campsite_photo_reselect))
                }
                if (state.availabilityComparisonPending &&
                    draft.requestedAvailability.isNotEmpty() &&
                    state.saveFailure != CampsiteSaveFailure.AVAILABILITY_PENDING
                ) {
                    Text(stringResource(R.string.campsite_date_save_pending))
                }
                CampsiteReviewStep(draft, state.photoIntent, onEdit, editingEnabled = canEdit)
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

/** Supplies all eight steps and save/recovery variants to Android Studio's preview selector. */
internal class CampsiteSetupPreviewStates : PreviewParameterProvider<CampsiteSetupUiState> {
    private val steps: Sequence<CampsiteSetupUiState.Content> =
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
                inventory = CalendarInventoryRead.Content(emptyList()),
            )
        }

    override val values: Sequence<CampsiteSetupUiState> =
        steps +
            sequenceOf(
                steps.last().copy(savePhase = CampsiteSavePhase.SAVING_DETAILS),
                steps.last().copy(savePhase = CampsiteSavePhase.SAVING_PHOTO),
                steps.last().copy(
                    saveAction = CampsiteSaveAction.PUBLISH,
                    savePhase = CampsiteSavePhase.PUBLISHING,
                ),
                steps.last().copy(
                    saveAction = CampsiteSaveAction.PUBLISH,
                    saveFailure = CampsiteSaveFailure.PUBLICATION,
                ),
                steps.last().copy(
                    saveAction = CampsiteSaveAction.PUBLISH,
                    saveFailure = CampsiteSaveFailure.RECOVERY,
                    publicationPending = true,
                ),
                steps.last().copy(saveFailure = CampsiteSaveFailure.DETAILS),
                steps.last().copy(photoReselectionRequired = true),
                steps.last().copy(
                    draft =
                        steps.last().draft.copy(
                            requestedAvailability = mapOf(LocalDate.of(2027, 5, 15) to true),
                        ),
                ),
                steps.last().copy(
                    photoIntent = CampsitePhotoIntent.Remove,
                    photoRemovalPending = true,
                    saveFailure = CampsiteSaveFailure.PHOTO,
                ),
            )
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
            onSaveDraft = {},
            onRetrySave = {},
            onPublish = {},
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
    SetupPreviewContent(CampsiteSetupPreviewStates().values.elementAt(CampsiteStep.REVIEW.ordinal))
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

/** Failed restored publication keeps recovery reachable without ordinary abandonment. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PublicationRecoveryPreview() {
    SetupPreviewContent(CampsiteSetupUiState.PublicationRecovery(isLoading = false))
}
