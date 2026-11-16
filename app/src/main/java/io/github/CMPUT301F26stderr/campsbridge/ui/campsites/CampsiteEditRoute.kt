package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteCapacityPriceStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteLocationStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteOptionsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteRulesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import kotlinx.coroutines.launch

/** Connects the saved editor and consumes results only after its page is resumed.
 * @param model Saved edit graph owner.
 * @param onExit Returns to the same manager after confirmed save or explicit abandonment.
 * @param onSection Opens a retained local details section.
 * @param onPhoto Opens independent image controls within this graph.
 * @param onAvailability Opens the existing saved inventory editor.
 */
@Composable
fun CampsiteEditRoute(
    model: CampsiteEditViewModel,
    onExit: () -> Unit,
    onSection: (CampsiteEditSection) -> Unit,
    onPhoto: () -> Unit,
    onAvailability: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photo.state.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val feedback = rememberCoroutineScope()
    val savedWithPhotoPending = stringResource(R.string.campsite_edit_saved_photo_pending)
    LaunchedEffect(lifecycle, completion, exit) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            if (completion != null) {
                model.acknowledgeCompletion()
                if (model.photo.state.value.candidate != null) {
                    feedback.launch { snackbar.showSnackbar(savedWithPhotoPending) }
                } else {
                    onExit()
                }
            } else if (exit) {
                model.acknowledgeExit()
                onExit()
            }
        }
    }
    CampsiteEditScreen(state, photo, model::requestExit, model::retry, {
        model.save()
        val errors = (model.state.value as? CampsiteEditUiState.Content)?.errors
        val invalid =
            when {
                errors?.nameRequired == true -> CampsiteEditSection.BASICS
                errors?.locationRequired == true || errors?.timeZoneInvalid == true -> CampsiteEditSection.LOCATION
                errors?.capacity != null || errors?.price != null -> CampsiteEditSection.CAPACITY_PRICE
                else -> null
            }
        invalid?.let(onSection)
    }, onSection, onPhoto, onAvailability, snackbar)
    if (discard) {
        ConfirmationDialog(
            title = stringResource(R.string.campsite_discard_title),
            description = stringResource(R.string.campsite_discard_body),
            confirmLabel = stringResource(R.string.campsite_discard_confirm),
            onConfirm = model::confirmDiscard,
            onCancel = model::dismissDiscard,
            onDismiss = model::dismissDiscard,
            destructive = true,
        )
    }
}

/** Binds each existing field group to this saved editor's local actions.
 * @param model Saved edit graph owner.
 * @param section Registered details section.
 * @param onBack Returns to the retained edit summary.
 * @param onChooseLocation Opens the existing working-pin picker.
 * @param onOpenPolicy Opens the current refund policy without losing input.
 */
@Composable
fun CampsiteEditSectionRoute(
    model: CampsiteEditViewModel,
    section: CampsiteEditSection,
    onBack: () -> Unit,
    onChooseLocation: () -> Unit,
    onOpenPolicy: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val content = state as? CampsiteEditUiState.Content
    CampsiteEditFieldsScreen(stringResource(section.title), state, onBack, model::retry) {
        if (content != null) {
            val draft = content.draft
            when (section) {
                CampsiteEditSection.BASICS -> {
                    CampsiteBasicsStep(
                        draft.name,
                        draft.description,
                        content.errors.nameRequired,
                        model::editName,
                        model::editDescription,
                    )
                }

                CampsiteEditSection.LOCATION -> {
                    CampsiteLocationStep(
                        draft,
                        content.errors,
                        model::editLocationLabel,
                        model::editTimeZone,
                        onChooseLocation,
                    )
                }

                CampsiteEditSection.CAPACITY_PRICE -> {
                    CampsiteCapacityPriceStep(
                        draft,
                        content.errors,
                        model::editMaxGuests,
                        model::editPrice,
                    )
                }

                CampsiteEditSection.OPTIONS -> {
                    CampsiteOptionsStep(
                        draft.amenities,
                        draft.tags,
                        model::setAmenity,
                        model::setTag,
                    )
                }

                CampsiteEditSection.RULES -> {
                    CampsiteRulesStep(draft.rules, model::editRules, onOpenPolicy)
                }
            }
        }
    }
}
