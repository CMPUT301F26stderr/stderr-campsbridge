/*
 * Discovery-specific toolbar reused by list/map presentations without performing a search.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.discovery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ConnectedChoices
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Discovery-specific toolbar reused by list/map presentations without performing a search.
 * @param query Caller-owned query.
 * @param onQueryChange Edited query callback.
 * @param mapSelected Current presentation.
 * @param onMapSelected Requested presentation.
 * @param onFilters Opens the filter flow.
 * @param modifier Caller layout.
 * @param onSearch Explicit Search/IME submission; absent in passive gallery usage.
 * @param showViewToggle Whether working list/map presentations exist for this caller.
 * @param onScan Opens the registered native scanner from an explicit click.
 */
@Composable
fun DiscoveryToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    mapSelected: Boolean,
    onMapSelected: (Boolean) -> Unit,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    showViewToggle: Boolean = true,
    onScan: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        AppSearchField(
            query,
            onQueryChange,
            stringResource(R.string.search_campsites),
            onSearch = onSearch,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            OutlinedButton(onFilters) { Text(stringResource(R.string.filters)) }
            if (onScan != null) OutlinedButton(onScan) { Text(stringResource(R.string.scan_qr)) }
            if (showViewToggle) {
                ConnectedChoices(
                    listOf(
                        ChoiceOption("list", stringResource(R.string.list_view)),
                        ChoiceOption("map", stringResource(R.string.map_view)),
                    ),
                    if (mapSelected) "map" else "list",
                    { onMapSelected(it == "map") },
                    Modifier.widthIn(min = 160.dp, max = 240.dp),
                )
            }
        }
    }
}
