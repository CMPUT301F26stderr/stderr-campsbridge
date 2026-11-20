package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Checks reachable allocation summaries and exports the production screens at enlarged scales. */
class LotteryAllocationRenderingTest {
    @get:Rule val compose = createComposeRule()

    /** Each campsite and the footer remain reachable in selection and review with long names. */
    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun multipleCampsitesRemainReachableInSelectionAndReview() {
        val first = previewCampsite()
        val second =
            first.copy(
                id = "coastal",
                details = first.details.copy(name = "Coastal family campsite", timeZone = "America/Vancouver"),
            )
        val day = LocalDate.of(2027, 5, 14)
        val draft =
            LotteryDraft(
                title = "May family camping",
                campsiteId = first.id,
                allocations =
                    listOf(
                        LotteryOffer(first.id, listOf(day)),
                        LotteryOffer(second.id, listOf(day.plusDays(1))),
                    ),
                schedule =
                    LotterySchedulePoint.entries
                        .mapIndexed { index, point ->
                            point to LotteryLocalDateTime(LocalDate.of(2027, 5, 5 + index), LocalTime.of(9, 0))
                        }.toMap(),
                maximumWinnersText = "2",
            )
        val configuration =
            LotteryConfiguration(
                Instant.parse("2027-05-05T15:00:00Z"),
                Instant.parse("2027-05-06T15:00:00Z"),
                Instant.parse("2027-05-07T15:00:00Z"),
                Instant.parse("2027-05-08T15:00:00Z"),
                2,
                null,
            )
        val font = mutableStateOf(1f)
        val step = mutableStateOf(LotteryStep.SITE)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font.value)) {
                Box(
                    Modifier
                        .width(
                            if (font.value ==
                                1f
                            ) {
                                390.dp
                            } else {
                                360.dp
                            },
                        ).height(844.dp)
                        .testTag("allocation-preview"),
                ) {
                    key(font.value, step.value) {
                        CampsbridgeTheme(AccountSettings(controlScale = font.value.toDouble())) {
                            LotterySetupScreen(
                                LotterySetupUiState(
                                    draft.copy(step = step.value),
                                    LotteryCampsiteChoices.Content(listOf(first, second)),
                                    configuration = configuration,
                                ),
                                YearMonth.of(2027, 5),
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                { _, _ -> },
                                { _, _ -> },
                                {},
                                {},
                                onEdit = {},
                            )
                        }
                    }
                }
            }
        }
        val directory =
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "lottery151")
        assertTrue(directory.isDirectory || directory.mkdirs())
        for (scale in listOf(1f, 2f)) {
            for (page in listOf(LotteryStep.SITE, LotteryStep.REVIEW)) {
                compose.runOnIdle {
                    font.value = scale
                    step.value = page
                }
                compose.onNodeWithText(second.details.name).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(first.details.name).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(if (page == LotteryStep.SITE) "Next" else "Save draft").assertIsDisplayed()
                capture(File(directory, "${page.name.lowercase()}-${scale.toInt()}x.png"))
            }
        }
    }

    /** Saves the bounded screen without system UI.
     * @param file Test-owned output image.
     */
    private fun capture(file: File) {
        val bitmap = compose.onNodeWithTag("allocation-preview").captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
