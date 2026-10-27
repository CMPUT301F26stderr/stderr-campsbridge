package io.github.CMPUT301F26stderr.campsbridge.ui.discovery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        AppSearchField(
            query,
            onQueryChange,
            stringResource(R.string.search_campsites),
            onSearch = onSearch,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            OutlinedButton(onFilters) { Text(stringResource(R.string.filters)) }
            if (showViewToggle) {
                FilterChip(!mapSelected, onClick = {
                    onMapSelected(false)
                }, label = { Text(stringResource(R.string.list_view)) })
                FilterChip(mapSelected, onClick = {
                    onMapSelected(true)
                }, label = { Text(stringResource(R.string.map_view)) })
            }
        }
    }
}
