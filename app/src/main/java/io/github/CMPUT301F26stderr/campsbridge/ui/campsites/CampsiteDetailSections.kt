package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.CampsiteMap
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOption
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOptions
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AccountAvatar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.TagLabel
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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(site.details.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                site.details.locationLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        CampsiteOwnerCard(owner, onRetryOwner)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                stringResource(R.string.campsite_about_description),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                site.details.description.ifBlank {
                    stringResource(
                        R.string.campsite_no_description,
                    )
                },
            )
        }
        StoredOptions(
            stringResource(R.string.campsite_tags),
            site.details.tags,
            CampingOptions.tags,
            asTags = true,
        )
        StoredOptions(
            stringResource(R.string.campsite_amenities),
            site.details.amenities,
            CampingOptions.amenities,
        )
    }
}

/** Groups the host read independently from the surrounding saved listing facts.
 * @param owner Current host-name read outcome.
 * @param onRetry Retries only the failed host read.
 */
@Composable
private fun CampsiteOwnerCard(
    owner: CampsiteOwnerState,
    onRetry: () -> Unit,
) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            Modifier.padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(null, stringResource(R.string.campsite_owner))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(R.string.campsite_owner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (owner) {
                    CampsiteOwnerState.Loading -> {
                        LoadingContent()
                    }

                    CampsiteOwnerState.Failure -> {
                        FailureContent(stringResource(R.string.campsite_owner_failure), onRetry)
                    }

                    CampsiteOwnerState.Unavailable -> {
                        Text(stringResource(R.string.campsite_owner_unavailable))
                    }

                    is CampsiteOwnerState.Ready -> {
                        Text(owner.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
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
    CampsiteSectionFrame(
        CampsiteDetailSection.MAP,
        state,
        onBack,
        onRetry,
        scrollable = false,
    ) { site ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val summaryMaximum = maxHeight / 3
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                CampsiteMap(
                    site.details.location,
                    Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.medium),
                )
                OutlinedCard(
                    // Long saved labels may scroll, while the map keeps most of the viewport.
                    Modifier.fillMaxWidth().heightIn(max = summaryMaximum),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Text(site.details.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            site.details.locationLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
    inventory: CalendarInventoryRead,
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
            inventory == CalendarInventoryRead.Failure -> {
                FailureContent(
                    stringResource(R.string.campsite_availability_read_failure),
                    onRetryInventory,
                )
            }

            inventory == CalendarInventoryRead.Loading || month == null -> {
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
 * @param scrollable Whether facts scroll or content uses the available height, as a native map does.
 * @param content Content shown only for an actual presentable record.
 */
@Composable
private fun CampsiteSectionFrame(
    section: CampsiteDetailSection,
    state: CampsiteDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    scrollable: Boolean = true,
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
                    (
                        if (scrollable) {
                            Modifier.verticalScroll(
                                rememberScrollState(),
                            )
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ).padding(vertical = Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.large),
                ) { content(state.campsite) }
            }
        }
    }
}

/** Resolves known IDs through the shared catalog while retaining unknown saved values.
 * @param title Localized group label.
 * @param values Actual saved option IDs.
 * @param catalog Shared localized choices for this group.
 * @param asTags Whether to wrap passive labels instead of grouping amenity rows.
 */
@Composable
private fun StoredOptions(
    title: String,
    values: Set<String>,
    catalog: List<CampingOption>,
    asTags: Boolean = false,
) {
    val labels =
        values.map { value ->
            val known = catalog.firstOrNull { it.id == value }
            if (known == null) value else stringResource(known.label)
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        when {
            labels.isEmpty() -> {
                Text(stringResource(R.string.campsite_no_options))
            }

            asTags -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    labels.forEach { TagLabel(it) }
                }
            }

            else -> {
                OutlinedCard(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    labels.forEachIndexed { index, label ->
                        if (index >
                            0
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        ListItem(headlineContent = { Text(label) })
                    }
                }
            }
        }
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

/** Checks that a long saved summary scrolls independently of the usable native map. */
@Preview(
    name = "Long map summary",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun CampsiteMapLongPreview() {
    val site = previewCampsite()
    CampsbridgeTheme {
        CampsiteMapScreen(
            CampsiteDetailUiState.Content(
                site.copy(
                    details =
                        site.details.copy(
                            name = "Athabasca Riverside Family Camp and Woodland Retreat",
                            locationLabel =
                                "Along the Athabasca River, near " +
                                    "Jasper National Park, Alberta. Follow the " +
                                    "riverside access road to the clearing " +
                                    "at the end of the trail.",
                        ),
                ),
            ),
            {},
            {},
        )
    }
}

/** Shows the existing read-only calendar geometry with an explicitly confirmed empty month. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CampsiteAvailabilityPreview() {
    CampsbridgeTheme {
        CampsiteAvailabilityScreen(
            CampsiteDetailUiState.Content(previewCampsite()),
            YearMonth.of(2027, 5),
            CalendarInventoryRead.Content(emptyList()),
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
