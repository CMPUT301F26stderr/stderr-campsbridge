package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingState
import java.text.NumberFormat

/** Formats optional rating facts without confusing empty reviews with a failed source.
 * @param state Current rating outcome.
 */
@Composable
internal fun ratingLabel(state: RatingState): String =
    when (state) {
        RatingState.Loading -> {
            stringResource(R.string.discovery_rating_loading)
        }

        RatingState.Failure -> {
            stringResource(R.string.discovery_rating_failure)
        }

        is RatingState.Ready -> {
            val mean = state.summary.mean
            if (mean == null) {
                stringResource(R.string.discovery_no_ratings)
            } else {
                val number =
                    NumberFormat
                        .getNumberInstance(LocalConfiguration.current.locales[0])
                        .apply {
                            minimumFractionDigits = 1
                            maximumFractionDigits = 1
                        }.format(mean)
                stringResource(R.string.discovery_rating_summary, number, state.summary.count)
            }
        }
    }
