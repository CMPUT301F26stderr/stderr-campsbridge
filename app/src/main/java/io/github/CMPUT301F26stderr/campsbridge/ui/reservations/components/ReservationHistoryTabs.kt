/*
 * Uses standard Material indicators and scrolling when owner categories need more room.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListCategory

/** Uses standard Material indicators and scrolling when owner categories need more room.
 * @param category Current selected category.
 * @param categories Actor-appropriate display order.
 * @param onSelect Explicit local selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReservationHistoryTabs(
    category: ReservationListCategory,
    categories: List<ReservationListCategory>,
    onSelect: (ReservationListCategory) -> Unit,
) {
    val tabs: @Composable () -> Unit = {
        for (item in categories) {
            Tab(
                selected = item == category,
                onClick = { onSelect(item) },
                text = {
                    Text(
                        stringResource(
                            when (item) {
                                ReservationListCategory.ACTIVE -> R.string.reservation_active
                                ReservationListCategory.PAST -> R.string.reservation_past
                                ReservationListCategory.CANCELLED -> R.string.reservation_cancelled
                            },
                        ),
                    )
                },
            )
        }
    }
    if (categories.size > 2) {
        PrimaryScrollableTabRow(categories.indexOf(category), edgePadding = 0.dp, tabs = tabs)
    } else {
        PrimaryTabRow(categories.indexOf(category), tabs = tabs)
    }
}
