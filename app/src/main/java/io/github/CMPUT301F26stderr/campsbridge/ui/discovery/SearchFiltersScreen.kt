package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.CountStepper
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.DateRangeInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.PickerField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/** Binds the working editor to its actual parent, consuming no repository write.
 * @param model Discovery parent shared with results.
 * @param onBack Returns to the same query/list context.
 */
@Composable
fun SearchFiltersRoute(
    model: ExploreViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft = state.filterDraft
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(draft == null, lifecycle) {
        if (draft == null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) onBack()
    }
    if (draft != null) {
        SearchFiltersScreen(
            draft,
            state.filterErrors,
            model::editLocation,
            model::selectStayDates,
            model::editGuests,
            model::resetFilters,
            { model.applyFilters() },
            model::cancelFilters,
            initialMonth = model.currentFilterMonth(),
        )
    }
}

/** Ordinary local filter form with explicit Apply/Reset/Cancel semantics.
 * @param draft Working values, separate from applied criteria.
 * @param errors Current local validation feedback.
 * @param onLocationChange Edits the human-readable location.
 * @param onStayChange Edits or clears a paired stay.
 * @param onGuestsChange Edits the optional whole-count value.
 * @param onReset Clears only working fields.
 * @param onApply Validates and applies this working draft.
 * @param onBack Discards the working draft.
 * @param modifier Caller layout.
 * @param initialMonth Caller-clock month for an unselected range.
 */
@Composable
fun SearchFiltersScreen(
    draft: FilterDraft,
    errors: FilterErrors,
    onLocationChange: (String) -> Unit,
    onStayChange: (LocalDate?, LocalDate?) -> Unit,
    onGuestsChange: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialMonth: YearMonth? = null,
) {
    var choosingDates by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val locale = LocalConfiguration.current.locales[0]
    AppScreen(
        stringResource(R.string.filters),
        modifier,
        onBack = onBack,
        actions = { TextButton(onReset) { Text(stringResource(R.string.discovery_reset)) } },
        bottomContent = {
            AppActionButton(
                stringResource(R.string.discovery_apply),
                onApply,
                Modifier.fillMaxWidth().padding(Spacing.screen),
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            AppTextField(
                draft.locationText,
                onLocationChange,
                stringResource(R.string.discovery_location),
            )
            PickerField(
                stringResource(R.string.dates),
                if (draft.checkIn != null && draft.checkOut != null) {
                    stringResource(
                        R.string.stay_dates,
                        formatDate(draft.checkIn, locale),
                        formatDate(draft.checkOut, locale),
                    )
                } else {
                    null
                },
                { choosingDates = true },
                errorMessage =
                    errors.dates?.let {
                        stringResource(
                            if (it ==
                                StayFilterError.MISSING_ENDPOINT
                            ) {
                                R.string.discovery_date_pair
                            } else {
                                R.string.discovery_date_order
                            },
                        )
                    },
            )
            if (draft.checkIn != null || draft.checkOut != null) {
                TextButton(
                    { onStayChange(null, null) },
                ) { Text(stringResource(R.string.discovery_clear_dates)) }
            }
            // The agreed unset display is one; null and one both retain every valid positive-capacity site.
            CountStepper(
                stringResource(R.string.guests),
                draft.guestText
                    .trim()
                    .toLongOrNull()
                    ?.takeIf { it > 0 } ?: 1,
                1,
                Long.MAX_VALUE,
                { onGuestsChange(it.toString()) },
                supportingText =
                    if (errors.guests) {
                        stringResource(
                            R.string.discovery_guests_invalid,
                        )
                    } else {
                        null
                    },
            )
        }
    }
    if (choosingDates) {
        val paired =
            draft.checkIn != null && draft.checkOut != null && draft.checkOut > draft.checkIn
        DateRangeInputDialog(
            if (paired) draft.checkIn else null,
            if (paired) draft.checkOut else null,
            { from, until ->
                onStayChange(from, until)
                choosingDates = false
            },
            {
                choosingDates =
                    false
            },
            initialMonth = initialMonth,
        )
    }
}

/** Shows working criteria with the same shared controls as production. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun SearchFiltersPreview() {
    CampsbridgeTheme {
        SearchFiltersScreen(
            FilterDraft(
                "Jasper",
                LocalDate.of(2027, 5, 14),
                LocalDate.of(2027, 5, 16),
                "4",
            ),
            FilterErrors(),
            {
            },
            { _, _ -> },
            {},
            {},
            {},
            {},
        )
    }
}

/** Shows validation feedback without losing the raw location or incomplete stay. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun SearchFiltersInvalidPreview() {
    CampsbridgeTheme {
        SearchFiltersScreen(
            FilterDraft(
                "Jasper",
                LocalDate.of(2027, 5, 14),
                guestText = "0",
            ),
            FilterErrors(StayFilterError.MISSING_ENDPOINT, true),
            {
            },
            { _, _ -> },
            {},
            {},
            {},
            {},
        )
    }
}
