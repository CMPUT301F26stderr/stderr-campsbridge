/*
 * Displays saved stays in the selected history category; the caller owns navigation, source reads and selected filter.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCampsiteFilter
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCancellationSummary
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCard
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationEntrantLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationHistoryTabs
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays saved stays in the selected history category; the caller owns navigation, source reads and selected filter.
 * @param state Scoped list outcome.
 * @param context Current workspace presentation.
 * @param campsiteId Selected owner filter, or null for all.
 * @param onCampsite Reports a selected campsite.
 * @param onOpen Opens the saved reservation identity.
 * @param onRetry Retries the authoritative source.
 * @param onRetryNames Retries failed supplemental names.
 * @param modifier Caller layout.
 * @param listState Retained scroll position.
 * @param photos Supplemental photos keyed by campsite identity.
 * @param onRetryPhoto Retries one failed photo.
 * @param category Selected local category.
 * @param categories Categories supported by this presentation.
 * @param onCategory Reports an explicit tab selection.
 */
@Composable
fun ReservationListScreen(
    state: ReservationListUiState,
    context: ReservationContext,
    campsiteId: String?,
    onCampsite: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryNames: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    photos: Map<String, ImageSlotState> = emptyMap(),
    onRetryPhoto: (String) -> Unit = {},
    category: ReservationListCategory = ReservationListCategory.ACTIVE,
    categories: List<ReservationListCategory> =
        if (context ==
            ReservationContext.OWNER
        ) {
            ReservationListCategory.entries.toList()
        } else {
            listOf(ReservationListCategory.ACTIVE, ReservationListCategory.PAST)
        },
    onCategory: (ReservationListCategory) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        ReservationHistoryTabs(category, categories, onCategory)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            if (context == ReservationContext.OWNER) {
                item("filter") {
                    ReservationCampsiteFilter(
                        (state as? ReservationListUiState.Content)?.campsites.orEmpty(),
                        campsiteId,
                        onCampsite,
                    )
                }
            }
            when (state) {
                ReservationListUiState.Loading -> {
                    item("loading") {
                        LoadingContent(stringResource(R.string.reservation_list_loading))
                    }
                }

                ReservationListUiState.Failure -> {
                    item("failure") {
                        FailureContent(stringResource(R.string.reservation_list_failed), onRetry)
                    }
                }

                is ReservationListUiState.Content -> {
                    if (state.classificationPending) {
                        item("classification-pending") {
                            InlineNotice(
                                stringResource(R.string.reservation_categories),
                                stringResource(R.string.reservation_classification_pending),
                            )
                        }
                    }
                    if (state.orderingPending) {
                        item("ordering-pending") {
                            InlineNotice(
                                stringResource(R.string.reservation_ordering),
                                stringResource(R.string.reservation_ordering_pending),
                            )
                        }
                    }
                    if (state.items.any { it.name == ReservationEntrantName.Failure }) {
                        item("name-retry") {
                            TextButton(
                                onClick = onRetryNames,
                            ) { Text(stringResource(R.string.reservation_retry_names)) }
                        }
                    }
                    if (!state.classificationPending && state.items.isEmpty()) {
                        item("empty") {
                            EmptyContent(
                                stringResource(
                                    when (category) {
                                        ReservationListCategory.ACTIVE -> {
                                            if (campsiteId ==
                                                null
                                            ) {
                                                R.string.reservation_list_empty
                                            } else {
                                                R.string.reservation_filtered_empty
                                            }
                                        }

                                        ReservationListCategory.PAST -> {
                                            if (campsiteId ==
                                                null
                                            ) {
                                                R.string.reservation_past_empty
                                            } else {
                                                R.string.reservation_past_filtered_empty
                                            }
                                        }

                                        ReservationListCategory.CANCELLED -> {
                                            if (campsiteId ==
                                                null
                                            ) {
                                                R.string.reservation_cancelled_empty
                                            } else {
                                                R.string.reservation_cancelled_filtered_empty
                                            }
                                        }
                                    },
                                ),
                            )
                        }
                    }
                    items(state.items, key = { "reservation:${it.reservation.id}" }) { row ->
                        val record = row.reservation
                        ReservationCard(
                            record,
                            stringResource(
                                when (row.category) {
                                    ReservationCategory.UPCOMING -> R.string.reservation_upcoming
                                    ReservationCategory.IN_PROGRESS -> R.string.reservation_in_progress
                                    ReservationCategory.PAST -> R.string.reservation_completed
                                    ReservationCategory.CANCELLED -> R.string.reservation_cancelled
                                },
                            ),
                            { onOpen(record.id) },
                            Modifier.testTag("reservation:${record.id}"),
                            photo = photos[record.campsiteId] ?: ImageSlotState(),
                            onRetryPhoto = { onRetryPhoto(record.campsiteId) },
                        ) {
                            if (context == ReservationContext.OWNER) ReservationEntrantLabel(row)
                            ReservationCancellationSummary(record)
                        }
                    }
                }
            }
        }
    }
}
