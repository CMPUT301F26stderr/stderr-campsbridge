/*
 * Connects explicit saved-details actions and resumed navigation outcomes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWindowStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWinnersStep
import kotlinx.coroutines.delay

/** Connects explicit saved-details actions and resumed navigation outcomes.
 * @param model Saved organizer editor shared by its children.
 * @param onExit Returns to the same saved reader.
 * @param onSection Opens one retained local field group.
 */
@Composable
fun LotteryDetailsEditRoute(
    model: LotteryDetailsEditViewModel,
    onExit: () -> Unit,
    onSection: (LotteryEditSection) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val completed by model.completion.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LotteryDetailsLifecycle(model, state)
    LaunchedEffect(lifecycle, completed, exit) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            if (completed != null) {
                model.acknowledgeCompletion()
                onExit()
            } else if (exit) {
                model.acknowledgeExit()
                onExit()
            }
        }
    }
    LotteryDetailsEditScreen(state, model::requestExit, model::retry, {
        model.save()
        val errors = (model.state.value as? LotteryDetailsEditUiState.Content)?.errors
        val invalid = errors?.let(::lotteryErrorSection)
        invalid?.let(onSection)
    }, onSection)
    if (discard) {
        ConfirmationDialog(
            title = stringResource(R.string.lottery_edit_discard_title),
            description = stringResource(R.string.lottery_edit_discard_body),
            onConfirm = model::confirmDiscard,
            onCancel = model::dismissDiscard,
            onDismiss = model::dismissDiscard,
            confirmLabel = stringResource(R.string.campsite_discard_confirm),
            destructive = true,
        )
    }
}

/** Reuses the authoring field groups without invoking create, allocation or publish stages.
 * @param model Saved organizer editor.
 * @param section Registered details group.
 * @param onBack Returns to the editor menu while retaining local input.
 */
@Composable
fun LotteryDetailsSectionRoute(
    model: LotteryDetailsEditViewModel,
    section: LotteryEditSection,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LotteryDetailsLifecycle(model, state)
    LotteryDetailsFieldsScreen(section, state, onBack, model::retry) {
        val content = state as? LotteryDetailsEditUiState.Content
        if (content != null) {
            val draft = content.draft
            when (section) {
                LotteryEditSection.BASICS -> {
                    LotteryBasicsStep(draft, content.errors.titleRequired, model::editTitle, model::editDescription)
                }

                LotteryEditSection.REGISTRATION -> {
                    LotteryWindowStep(
                        draft.schedule,
                        content.errors.schedule,
                        LotterySchedulePoint.REGISTRATION_OPEN,
                        LotterySchedulePoint.REGISTRATION_CLOSE,
                        model.currentScheduleMonth(),
                        model::editScheduleDate,
                        model::editScheduleTime,
                    )
                }

                LotteryEditSection.BOOKING -> {
                    LotteryWindowStep(
                        draft.schedule,
                        content.errors.schedule,
                        LotterySchedulePoint.BOOKING_OPEN,
                        LotterySchedulePoint.BOOKING_CLOSE,
                        model.currentScheduleMonth(),
                        model::editScheduleDate,
                        model::editScheduleTime,
                    )
                }

                LotteryEditSection.WINNERS -> {
                    LotteryWinnersStep(
                        draft.maximumWinnersText,
                        draft.waitlistCapacityText,
                        content.errors.winnerCount,
                        content.errors.capacity,
                        model::editWinnerCount,
                        model::editWaitlistCapacity,
                    )
                }
            }
        }
    }
}

/** Rechecks the shared boundary on resume and once at the saved registration opening.
 * @param model Owner of configuration rules and the shared clock.
 * @param state Latest saved record used to restart the deadline effect after context changes.
 */
@Composable
private fun LotteryDetailsLifecycle(
    model: LotteryDetailsEditViewModel,
    state: LotteryDetailsEditUiState,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val opening = (state as? LotteryDetailsEditUiState.Content)?.lottery?.configuration?.registrationOpensAt
    LaunchedEffect(model, lifecycle, opening) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            model.refreshTime()
            model.millisUntilConfigurationFreeze()?.let {
                delay(it)
                model.refreshTime()
            }
        }
    }
}

/** Opens the field group owning the existing validation feedback.
 * @param errors Shared parser and window feedback from the explicit Save action.
 */
private fun lotteryErrorSection(errors: LotterySetupErrors): LotteryEditSection? =
    when {
        errors.titleRequired -> LotteryEditSection.BASICS

        errors.schedule.keys.any {
            it == LotterySchedulePoint.REGISTRATION_OPEN ||
                it == LotterySchedulePoint.REGISTRATION_CLOSE
        } ||
            errors.windows == LotteryWindowInput.InvalidRegistration -> LotteryEditSection.REGISTRATION

        errors.schedule.isNotEmpty() || errors.windows != null -> LotteryEditSection.BOOKING

        errors.winnerCount != null || errors.capacity != null -> LotteryEditSection.WINNERS

        else -> null
    }
