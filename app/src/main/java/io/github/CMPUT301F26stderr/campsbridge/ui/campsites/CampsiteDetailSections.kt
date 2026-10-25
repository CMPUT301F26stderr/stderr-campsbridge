package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.CampsiteMap
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOption
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOptions
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.YearMonth

/** Names registered detail sections consistently across overview rows and child app bars.
 * @param section Actual read-only child.
 */
@Composable
internal fun campsiteSectionTitle(section: CampsiteDetailSection): String =
    stringResource(
        when (section) {
            CampsiteDetailSection.OVERVIEW -> R.string.campsite
            CampsiteDetailSection.ABOUT -> R.string.campsite_about
            CampsiteDetailSection.MAP -> R.string.campsite_map_title
            CampsiteDetailSection.AVAILABILITY -> R.string.availability_title
            CampsiteDetailSection.RULES -> R.string.campsite_rules_title
            CampsiteDetailSection.LOTTERIES -> R.string.campsite_available_lotteries
        },
    )

/** Presents actual listing description/options and an independently loaded owner label.
 * @param state Parent listing result.
 * @param owner Optional owner result.
 * @param onBack Returns to the saved overview.
 * @param onRetry Retries only the parent listing.
 * @param onRetryOwner Retries only the failed owner read.
 */
@Composable
fun CampsiteAboutScreen(
    state: CampsiteDetailUiState,
    owner: CampsiteOwnerState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryOwner: () -> Unit,
) {
    CampsiteSectionFrame(CampsiteDetailSection.ABOUT, state, onBack, onRetry) { site ->
        Text(site.details.name, style = MaterialTheme.typography.headlineSmall)
        Text(site.details.locationLabel)
        Text(stringResource(R.string.campsite_owner), style = MaterialTheme.typography.titleMedium)
        when (owner) {
            CampsiteOwnerState.Loading -> {
                LoadingContent()
            }

            CampsiteOwnerState.Failure -> {
                FailureContent(stringResource(R.string.campsite_owner_failure), onRetryOwner)
            }

            CampsiteOwnerState.Unavailable -> {
                Text(stringResource(R.string.campsite_owner_unavailable))
            }

            is CampsiteOwnerState.Ready -> {
                Text(owner.name)
            }
        }
        Text(
            stringResource(R.string.campsite_about_description),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(site.details.description.ifBlank { stringResource(R.string.campsite_no_description) })
        StoredOptions(
            stringResource(R.string.campsite_tags),
            site.details.tags,
            CampingOptions.tags,
        )
        StoredOptions(
            stringResource(R.string.campsite_amenities),
            site.details.amenities,
            CampingOptions.amenities,
        )
    }
}

/** Displays the saved coordinate through the existing read-only platform adapter.
 * @param state Parent listing result.
 * @param onBack Returns to the saved overview.
 * @param onRetry Retries only the parent listing.
 */
@Composable
fun CampsiteMapScreen(
    state: CampsiteDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    CampsiteSectionFrame(CampsiteDetailSection.MAP, state, onBack, onRetry) { site ->
        Text(site.details.name, style = MaterialTheme.typography.headlineSmall)
        Text(site.details.locationLabel)
        CampsiteMap(site.details.location, Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

/** Displays known offered/booked nights without date-selection or persistence actions.
 * @param state Parent listing result.
 * @param month Actual retained local month, available after parent resolution.
 * @param inventory Independent visible-range outcome.
 * @param onBack Returns to the saved overview.
 * @param onRetry Retries only the parent listing.
 * @param onRetryInventory Retries only the visible range.
 * @param onMonthChange Requests another local month.
 */
@Composable
fun CampsiteAvailabilityScreen(
    state: CampsiteDetailUiState,
    month: YearMonth?,
    inventory: CampsiteInventoryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryInventory: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    CampsiteSectionFrame(CampsiteDetailSection.AVAILABILITY, state, onBack, onRetry) { site ->
        Text(site.details.name, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.campsite_read_only_availability))
        Text(site.details.timeZone)
        when {
            inventory == CampsiteInventoryUiState.Failure -> {
                FailureContent(
                    stringResource(R.string.campsite_availability_read_failure),
                    onRetryInventory,
                )
            }

            inventory == CampsiteInventoryUiState.Loading || month == null -> {
                LoadingContent()
            }

            else -> {
                CampsiteCalendar(
                    offeredNightCalendarState(month, inventory, emptyMap())
                        .copy(selectionEnabled = false),
                    onMonthChange,
                    {},
                )
            }
        }
    }
}

/** Reads current listing rules and links to the exact packaged policy readers.
 * @param state Parent listing result; accepted historical terms belong to reservations.
 * @param onBack Returns to the saved overview.
 * @param onRetry Retries only the parent listing.
 * @param onOpenPolicy Opens the selected packaged document within this detail graph.
 */
@Composable
fun CampsiteRulesScreen(
    state: CampsiteDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenPolicy: (PolicyId) -> Unit,
) {
    CampsiteSectionFrame(CampsiteDetailSection.RULES, state, onBack, onRetry) { site ->
        Text(site.details.name, style = MaterialTheme.typography.headlineSmall)
        Text(site.details.rules.ifBlank { stringResource(R.string.campsite_no_rules) })
        Text(stringResource(R.string.up_to_guests, site.details.maxGuests))
        NavigationRow(stringResource(R.string.onboarding_guidelines), {
            onOpenPolicy(PolicyId.GUIDELINES)
        })
        NavigationRow(
            stringResource(R.string.refund_policy_title),
            { onOpenPolicy(PolicyId.REFUND) },
        )
    }
}

/** Shares one frame and parent-outcome treatment across small read-only section contents.
 * @param section Actual child title.
 * @param state Authoritative saved listing outcome.
 * @param onBack Returns to the actual parent destination.
 * @param onRetry Retries the saved listing read.
 * @param content Content shown only for an actual presentable record.
 */
@Composable
private fun CampsiteSectionFrame(
    section: CampsiteDetailSection,
    state: CampsiteDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable ColumnScope.(Campsite) -> Unit,
) {
    AppScreen(campsiteSectionTitle(section), onBack = onBack) {
        when (state) {
            CampsiteDetailUiState.Loading -> {
                LoadingContent()
            }

            CampsiteDetailUiState.Failure -> {
                FailureContent(stringResource(R.string.campsite_detail_failure), onRetry)
            }

            CampsiteDetailUiState.Unavailable -> {
                Text(stringResource(R.string.campsite_unavailable))
            }

            is CampsiteDetailUiState.Content -> {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) { content(state.campsite) }
            }
        }
    }
}

/** Resolves known IDs through the shared catalog while retaining unknown saved values.
 * @param title Localized group label.
 * @param values Actual saved option IDs.
 * @param catalog Shared localized choices for this group.
 */
@Composable
private fun StoredOptions(
    title: String,
    values: Set<String>,
    catalog: List<CampingOption>,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (values.isEmpty()) Text(stringResource(R.string.campsite_no_options))
    for (value in values) {
        val known = catalog.firstOrNull { it.id == value }
        Text(if (known == null) value else stringResource(known.label))
    }
}

/** Shows real-shaped owner/options content without starting repository reads. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteAboutPreview() {
    CampsbridgeTheme {
        CampsiteAboutScreen(
            CampsiteDetailUiState.Content(previewCampsite()),
            CampsiteOwnerState.Ready("Alex Morgan"),
            {},
            {},
            {},
        )
    }
}

/** Previews the platform map's tooling surface rather than fabricated SDK tiles. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CampsiteMapPreview() {
    CampsbridgeTheme { CampsiteMapScreen(CampsiteDetailUiState.Content(previewCampsite()), {}, {}) }
}

/** Shows the existing read-only calendar geometry with an explicitly confirmed empty month. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CampsiteAvailabilityPreview() {
    CampsbridgeTheme {
        CampsiteAvailabilityScreen(
            CampsiteDetailUiState.Content(previewCampsite()),
            YearMonth.of(2027, 5),
            CampsiteInventoryUiState.Content(emptyList()),
            {},
            {},
            {},
            {},
        )
    }
}

/** Shows current rules and shared policy entry rows without fake help actions. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CampsiteRulesPreview() {
    CampsbridgeTheme {
        CampsiteRulesScreen(CampsiteDetailUiState.Content(previewCampsite()), {}, {}, {})
    }
}
