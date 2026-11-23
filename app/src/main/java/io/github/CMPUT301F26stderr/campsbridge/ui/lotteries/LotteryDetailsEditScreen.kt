/*
 * Shared details groups, separate from each owner's campsite allocation editor.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.ui.common.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.NavigationGroup
import io.github.CMPUT301F26stderr.campsbridge.ui.components.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.NoticeTone
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shared details groups, separate from each owner's campsite allocation editor.
 * @param title Localized menu and child title.
 */
enum class LotteryEditSection(
    @param:StringRes val title: Int,
) {
    BASICS(R.string.lottery_edit_basics),
    REGISTRATION(R.string.lottery_registration_step),
    BOOKING(R.string.lottery_booking_step),
    WINNERS(R.string.lottery_edit_limits),
}

/** Groups editable descriptions and the current saved/configured lottery terms.
 * @param state Saved source, raw input and explicit action feedback.
 * @param onBack Requests leaving under this form's discard rules.
 * @param onRetry Retries the required saved source.
 * @param onSave Explicit organizer details update.
 * @param onSection Opens a local field group without saving.
 */
@Composable
fun LotteryDetailsEditScreen(
    state: LotteryDetailsEditUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onSection: (LotteryEditSection) -> Unit,
) {
    val content = state as? LotteryDetailsEditUiState.Content
    AppScreen(
        stringResource(R.string.lottery_edit_title),
        onBack = onBack,
        backEnabled = content?.isSaving != true,
        bottomContent = {
            if (content != null) {
                ActionFooter {
                    AppActionButton(
                        stringResource(R.string.lottery_edit_save),
                        onSave,
                        Modifier.fillMaxWidth(),
                        enabled = content.isDirty && !content.isSaving,
                        isSubmitting = content.isSaving,
                        progressDescription = stringResource(R.string.lottery_saving_details),
                    )
                }
            }
        },
    ) {
        LotteryDetailsEditBody(state, onRetry) {
            val current = checkNotNull(content)
            Text(
                current.draft.title.ifBlank {
                    stringResource(R.string.lottery_edit_title)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            LotteryConfigurationNotice(current.configurationEditable)
            if (current.contextChanged) {
                InlineNotice(
                    stringResource(R.string.lottery_edit_review_title),
                    stringResource(R.string.lottery_edit_review_body),
                )
            }
            if (current.saveFailed) FailureContent(stringResource(R.string.lottery_edit_save_failed), onSave)
            NavigationGroup(
                buildList<@Composable () -> Unit> {
                    add {
                        NavigationRow(
                            stringResource(LotteryEditSection.BASICS.title),
                            { onSection(LotteryEditSection.BASICS) },
                            supportingText =
                                current.draft.description.lineSequence().firstOrNull()?.takeIf {
                                    it.isNotBlank()
                                },
                            enabled = !current.isSaving,
                        )
                    }
                    for (section in listOf(
                        LotteryEditSection.REGISTRATION,
                        LotteryEditSection.BOOKING,
                        LotteryEditSection.WINNERS,
                    )) {
                        add {
                            NavigationRow(
                                stringResource(section.title),
                                { onSection(section) },
                                supportingText =
                                    if (current.configurationEditable) {
                                        lotteryDraftSummary(
                                            current.draft,
                                            section,
                                        )
                                    } else {
                                        lotteryConfigurationSummary(current.lottery.configuration, section)
                                    },
                                enabled = current.configurationEditable && !current.isSaving,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** Displays local spelling in the menu without interpreting incomplete input as saved terms.
 * @param draft Caller-owned raw details.
 * @param section Schedule window or count inputs being summarized.
 */
@Composable
private fun lotteryDraftSummary(
    draft: LotteryDraft,
    section: LotteryEditSection,
): String {
    if (section == LotteryEditSection.WINNERS) {
        return stringResource(
            R.string.lottery_edit_raw_limits_summary,
            draft.maximumWinnersText,
            draft.waitlistCapacityText.ifBlank { stringResource(R.string.lottery_unlimited) },
        )
    }
    val points =
        when (section) {
            LotteryEditSection.REGISTRATION -> {
                listOf(
                    LotterySchedulePoint.REGISTRATION_OPEN,
                    LotterySchedulePoint.REGISTRATION_CLOSE,
                )
            }

            LotteryEditSection.BOOKING -> {
                listOf(LotterySchedulePoint.BOOKING_OPEN, LotterySchedulePoint.BOOKING_CLOSE)
            }

            else -> {
                emptyList()
            }
        }
    val locale = LocalConfiguration.current.locales[0]
    val unset = stringResource(R.string.lottery_edit_not_set)
    val values =
        points.map { point ->
            val input = draft.schedule[point]
            if (input?.date != null && input.time != null) {
                formatDate(input.date, locale) + ", " +
                    input.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            } else {
                unset
            }
        }
    return if (values.size == 2) stringResource(R.string.lottery_window_range, values[0], values[1]) else ""
}

/** Hosts existing field content while keeping source feedback and Back handling shared.
 * @param section Registered group of local details.
 * @param state Saved source and current input.
 * @param onBack Returns to the retained menu.
 * @param onRetry Retries a failed saved source.
 * @param fields Existing field content connected to the controller by its route.
 */
@Composable
internal fun LotteryDetailsFieldsScreen(
    section: LotteryEditSection,
    state: LotteryDetailsEditUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    AppScreen(
        stringResource(section.title),
        onBack = onBack,
        backEnabled =
            (state as? LotteryDetailsEditUiState.Content)?.isSaving != true,
    ) {
        LotteryDetailsEditBody(state, onRetry) {
            if (state is LotteryDetailsEditUiState.Content && section != LotteryEditSection.BASICS &&
                !state.configurationEditable
            ) {
                LotteryConfigurationNotice(false)
                SummarySection(stringResource(section.title)) {
                    Text(lotteryConfigurationSummary(state.lottery.configuration, section))
                }
            } else {
                if (state is LotteryDetailsEditUiState.Content &&
                    (section == LotteryEditSection.REGISTRATION || section == LotteryEditSection.BOOKING)
                ) {
                    val message =
                        when (state.errors.windows) {
                            LotteryWindowInput.InvalidRegistration -> {
                                if (section ==
                                    LotteryEditSection.REGISTRATION
                                ) {
                                    R.string.lottery_registration_invalid
                                } else {
                                    null
                                }
                            }

                            LotteryWindowInput.BookingBeforeRegistrationClose -> {
                                R.string.lottery_booking_before_registration
                            }

                            LotteryWindowInput.InvalidBooking -> {
                                if (section == LotteryEditSection.BOOKING) R.string.lottery_booking_invalid else null
                            }

                            else -> {
                                null
                            }
                        }
                    if (message !=
                        null
                    ) {
                        InlineNotice(
                            stringResource(section.title),
                            stringResource(message),
                            tone = NoticeTone.ERROR,
                        )
                    }
                }
                fields()
            }
        }
    }
}

/** Keeps configuration state explicit through words rather than disabled color alone.
 * @param editable Whether this saved record still permits configuration changes.
 */
@Composable
private fun LotteryConfigurationNotice(editable: Boolean) {
    InlineNotice(
        stringResource(if (editable) R.string.lottery_edit_configuration else R.string.lottery_edit_locked),
        stringResource(if (editable) R.string.lottery_edit_configuration_hint else R.string.lottery_edit_locked_hint),
    )
}

/** Displays source outcomes independently of a possibly retained local draft.
 * @param state Required saved lottery read.
 * @param onRetry Explicit source retry.
 * @param content Fields or groups for an available target.
 */
@Composable
private fun LotteryDetailsEditBody(
    state: LotteryDetailsEditUiState,
    onRetry: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (state) {
        LotteryDetailsEditUiState.Loading -> {
            LoadingContent()
        }

        LotteryDetailsEditUiState.Failure -> {
            FailureContent(stringResource(R.string.lottery_load_failed), onRetry)
        }

        LotteryDetailsEditUiState.Unavailable -> {
            Text(stringResource(R.string.lottery_unavailable))
        }

        is LotteryDetailsEditUiState.Content -> {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                content = content,
            )
        }
    }
}

/** Formats configured facts from persisted timestamps without converting them back to input.
 * @param configuration Authoritative saved terms.
 * @param section Schedule window or limits being summarized.
 */
@Composable
private fun lotteryConfigurationSummary(
    configuration: LotteryConfiguration,
    section: LotteryEditSection,
): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (section) {
        LotteryEditSection.BASICS -> {
            ""
        }

        LotteryEditSection.REGISTRATION -> {
            stringResource(
                R.string.lottery_window_range,
                formatDateTime(configuration.registrationOpensAt, locale),
                formatDateTime(configuration.registrationClosesAt, locale),
            )
        }

        LotteryEditSection.BOOKING -> {
            stringResource(
                R.string.lottery_window_range,
                formatDateTime(configuration.bookingOpensAt, locale),
                formatDateTime(configuration.bookingClosesAt, locale),
            )
        }

        LotteryEditSection.WINNERS -> {
            stringResource(
                R.string.lottery_edit_limits_summary,
                configuration.winnerCount,
                configuration.waitlistCapacity?.toString() ?: stringResource(R.string.lottery_unlimited),
            )
        }
    }
}
