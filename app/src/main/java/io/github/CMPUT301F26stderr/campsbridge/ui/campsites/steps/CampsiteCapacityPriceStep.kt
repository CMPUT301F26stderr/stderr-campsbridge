package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDraft
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField

/** Raw count and exact decimal input using the existing shared parser feedback.
 * @param draft Current capacity and price spelling.
 * @param errors Feedback from explicit Next.
 * @param onCapacity Local capacity edit.
 * @param onPrice Local price edit.
 */
@Composable
fun CampsiteCapacityPriceStep(
    draft: CampsiteDraft,
    errors: CampsiteDetailsInput.Errors,
    onCapacity: (String) -> Unit,
    onPrice: (String) -> Unit,
) {
    AppTextField(
        draft.maxGuestsText,
        onCapacity,
        stringResource(R.string.campsite_max_guests),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        errorMessage =
            if (errors.capacity !=
                null
            ) {
                stringResource(R.string.campsite_capacity_invalid)
            } else {
                null
            },
    )
    AppTextField(
        draft.priceText,
        onPrice,
        stringResource(R.string.campsite_nightly_price),
        supportingText = stringResource(R.string.campsite_currency_code),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        errorMessage =
            if (errors.price !=
                null
            ) {
                stringResource(R.string.campsite_price_invalid)
            } else {
                null
            },
    )
}
