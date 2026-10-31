package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationCampsiteChoice
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationEntrantName
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListItem

/** Displays saved campsite choices and retains selections absent from the latest source.
 * @param choices Owner-scoped saved names and identities.
 * @param selected Current filter, or null for all campsites.
 * @param onSelect Reports the raw campsite identity without changing source scope.
 */
@Composable
internal fun ReservationCampsiteFilter(
    choices: List<ReservationCampsiteChoice>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val all = "all"
    val options =
        buildList {
            add(ChoiceOption(all, stringResource(R.string.reservation_all_campsites)))
            choices.forEach { add(ChoiceOption("site:${it.id}", it.name)) }
            if (selected != null && choices.none { it.id == selected }) {
                add(ChoiceOption("site:$selected", selected))
            }
        }
    DropdownField(
        stringResource(R.string.campsite),
        options,
        selected?.let { "site:$it" } ?: all,
        { onSelect(if (it == all) null else it.removePrefix("site:")) },
    )
}

/** Shows a name outcome without losing the saved entrant identity on failure.
 * @param row Stored stay with an independent owner-only label.
 */
@Composable
internal fun ReservationEntrantLabel(row: ReservationListItem) {
    val name = row.name
    val label =
        when (name) {
            is ReservationEntrantName.Available -> {
                name.name
            }

            ReservationEntrantName.Unavailable -> {
                stringResource(
                    R.string.reservation_camper_unavailable,
                )
            }

            ReservationEntrantName.Failure -> {
                stringResource(R.string.reservation_camper_failed)
            }

            else -> {
                stringResource(R.string.reservation_camper_loading)
            }
        }
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (name !is ReservationEntrantName.Available) {
        Text(row.reservation.entrantId, style = MaterialTheme.typography.bodySmall)
    }
}
