package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.NoticeTone
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.CountStepper
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AccountAvatar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.PreferenceChips
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.SwitchRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Ordinary controlled input examples; local state simulates presentation only.
 * @param scroll Caller-retained page scroll position.
 */
@Composable
internal fun ControlsGallery(scroll: LazyListState) {
    var intent by rememberSaveable { mutableStateOf("entrant") }
    var count by rememberSaveable { mutableStateOf(4L) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("65.00") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var zone by rememberSaveable { mutableStateOf("edmonton") }
    var confirmation by remember { mutableStateOf(false) }
    var wizard by remember { mutableStateOf(false) }
    var updated by remember { mutableStateOf(false) }
    val exampleName = stringResource(R.string.gallery_sample_name)
    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        item {
            GallerySection(stringResource(R.string.gallery_choices)) {
                ChoiceRow(stringResource(R.string.gallery_find_campsite), intent == "entrant", {
                    intent =
                        "entrant"
                })
                ChoiceRow(stringResource(R.string.gallery_host_campsite), intent == "owner", {
                    intent =
                        "owner"
                })
                GalleryPreferences()
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_counts)) {
                CountStepper(
                    stringResource(R.string.guests),
                    count,
                    1,
                    8,
                    { count = it },
                    supportingText = stringResource(R.string.gallery_max_guests),
                )
                CountStepper(
                    stringResource(R.string.gallery_disabled),
                    8,
                    1,
                    8,
                    {},
                    enabled = false,
                )
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_fields)) {
                AppTextField(
                    name,
                    { name = it },
                    stringResource(R.string.gallery_name),
                    supportingText = stringResource(R.string.gallery_optional),
                )
                AppTextField(
                    description,
                    { description = it },
                    stringResource(R.string.gallery_description),
                    singleLine = false,
                    minLines = 3,
                    supportingText = stringResource(R.string.gallery_description_hint),
                )
                AppTextField(
                    price,
                    { price = it },
                    stringResource(R.string.gallery_price),
                    prefix = { Text("CAD") },
                    suffix = { Text(stringResource(R.string.gallery_per_night_unit)) },
                )
                TextButton(
                    onClick = { name = exampleName },
                ) { Text(stringResource(R.string.gallery_fill)) }
            }
        }
        item {
            GallerySection(
                stringResource(R.string.gallery_picker_section),
            ) { GalleryPickers() }
        }
        item {
            GallerySection(stringResource(R.string.gallery_menu_section)) {
                AccountAvatar(null, exampleName)
                DropdownField(
                    stringResource(R.string.gallery_timezone),
                    listOf(
                        ChoiceOption("edmonton", "America/Edmonton"),
                        ChoiceOption("vancouver", "America/Vancouver"),
                    ),
                    zone,
                    {
                        zone =
                            it
                    },
                )
                NavigationRow(stringResource(R.string.gallery_personal_details), { updated = true })
                NavigationRow(stringResource(R.string.gallery_contact), {
                    updated = true
                }, supportingText = exampleName)
                SwitchRow(
                    stringResource(R.string.gallery_notifications),
                    enabled,
                    { enabled = it },
                    supportingText = stringResource(R.string.gallery_alert_hint),
                )
                CheckboxRow(stringResource(R.string.gallery_agree), accepted, { accepted = it })
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_actions)) {
                AppActionButton(
                    stringResource(R.string.gallery_save),
                    { updated = true },
                    Modifier.fillMaxWidth(),
                )
                AppActionButton(stringResource(R.string.gallery_secondary), {
                    updated = true
                }, style = ActionStyle.SECONDARY)
                AppActionButton(stringResource(R.string.gallery_pending), {}, isSubmitting = true)
                AppActionButton(stringResource(R.string.gallery_disabled), {}, enabled = false)
                AppActionButton(stringResource(R.string.gallery_confirm), {
                    confirmation = true
                }, style = ActionStyle.TEXT)
                AppActionButton(stringResource(R.string.remove), {
                    confirmation = true
                }, style = ActionStyle.DESTRUCTIVE)
                TextButton(
                    onClick = { wizard = true },
                ) { Text(stringResource(R.string.gallery_step_preview)) }
                if (updated) Text(stringResource(R.string.gallery_updated))
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_feedback)) {
                InlineNotice(
                    stringResource(R.string.gallery_notice_title),
                    stringResource(R.string.gallery_notice_body),
                )
                InlineNotice(
                    stringResource(R.string.gallery_error_title),
                    stringResource(R.string.gallery_error_body),
                    tone = NoticeTone.ERROR,
                    action = {
                        TextButton(
                            onClick = { updated = true },
                        ) { Text(stringResource(R.string.retry)) }
                    },
                )
                AppTextField(
                    "",
                    {
                    },
                    stringResource(
                        R.string.gallery_name,
                    ),
                    errorMessage = stringResource(R.string.gallery_name_error),
                )
                LoadingContent()
                EmptyContent(stringResource(R.string.gallery_empty))
                FailureContent(stringResource(R.string.gallery_failure), { updated = true })
            }
        }
    }
    if (confirmation) {
        ConfirmationDialog(
            stringResource(R.string.gallery_dialog),
            stringResource(R.string.gallery_dialog_body),
            { confirmation = false },
            { confirmation = false },
            { confirmation = false },
            destructive = true,
        )
    }
    if (wizard) GalleryWizard { wizard = false }
}

/** Keeps sample selection local, independent of reserved production preference logic. */
@Composable
private fun GalleryPreferences() {
    var selected by remember { mutableStateOf(setOf("water")) }
    val options =
        listOf(
            ChoiceOption("water", stringResource(R.string.gallery_water)),
            ChoiceOption("lakeside", stringResource(R.string.gallery_lakeside)),
            ChoiceOption("family", stringResource(R.string.gallery_family)),
            ChoiceOption("stars", stringResource(R.string.gallery_stargazing)),
        )
    PreferenceChips(options, selected, { id ->
        selected = if (id in selected) selected - id else selected + id
    })
}

/** Full-window wizard preview exercises shared progress, custom action, skip and cancellation.
 * @param onClose Returns to the gallery without persistence.
 */
@Composable
private fun GalleryWizard(onClose: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    Dialog(onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StepScaffold(
            stringResource(R.string.gallery_step_title),
            stringResource(R.string.gallery_step_progress, step),
            onBack = { if (step > 1) step-- else onClose() },
            onNext = { if (step < 3) step++ else onClose() },
            nextLabel = stringResource(if (step < 3) R.string.next else R.string.gallery_continue),
            steps = StepProgress(step, 3),
            onSkip = onClose,
            onCancel = onClose,
        ) {
            Text(stringResource(R.string.gallery_step_explanation))
            GalleryPreferences()
        }
    }
}
