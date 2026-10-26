package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigation
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteCard
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteRow
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.StatusLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.StaySummary
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.components.DiscoveryToolbar
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.LotteryCard
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCard
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.YearMonth
import java.time.ZoneId

/** Realistic feature content without repository construction, derived business rules or writes.
 * @param scroll Caller-retained content scroll position.
 * @param onAction Reports which local example action was exercised.
 */
@Composable
internal fun ContentGallery(
    scroll: LazyListState,
    onAction: (String) -> Unit,
) {
    val site =
        galleryCampsite(
            stringResource(R.string.gallery_sample_site),
            stringResource(R.string.gallery_sample_location),
            stringResource(R.string.gallery_sample_description),
            stringResource(R.string.gallery_sample_rules),
        )
    val lottery = galleryLottery(stringResource(R.string.gallery_sample_lottery))
    val reservation = galleryReservation(site)
    var query by remember { mutableStateOf("") }
    var map by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("explore") }
    val filterLabel = stringResource(R.string.filters)
    val rulesLabel = stringResource(R.string.gallery_rules)
    val paymentLabel = stringResource(R.string.gallery_payment)
    val reservationLabel = stringResource(R.string.gallery_reservations)
    var month by remember { mutableStateOf(YearMonth.from(GalleryDate)) }
    var dates by remember { mutableStateOf(setOf(GalleryDate)) }
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        item {
            GallerySection(stringResource(R.string.gallery_campsite_cards)) {
                CampsiteCard(site, GALLERY_PHOTO, {
                    onAction(site.details.name)
                }, status = stringResource(R.string.gallery_registration_open))
                CampsiteRow(site, GALLERY_PHOTO, { onAction(site.details.name) })
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_summary)) {
                StaySummary(site.details.name, GalleryDate, GalleryDate.plusDays(2), 4, 13000)
                DetailRow(rulesLabel, site.details.rules, onEdit = { onAction(rulesLabel) })
                NavigationRow(
                    paymentLabel,
                    { onAction(paymentLabel) },
                )
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_lottery_cards)) {
                LotteryCard(
                    lottery,
                    stringResource(R.string.gallery_registration_open),
                    formatDateTime(
                        lottery.configuration.registrationClosesAt,
                        ZoneId.of(site.details.timeZone),
                        locale,
                    ),
                    { onAction(lottery.title) },
                )
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_reservation_cards)) {
                ReservationCard(reservation, stringResource(R.string.gallery_confirmed), {
                    onAction(reservationLabel)
                })
                StatusLabel(stringResource(R.string.gallery_confirmed), Icons.Default.CheckCircle)
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_calendar)) {
                val days =
                    (1..month.lengthOfMonth()).associate { day ->
                        month.atDay(day) to
                            CalendarDayState(
                                if (day ==
                                    20
                                ) {
                                    CalendarDayStatus.OCCUPIED
                                } else {
                                    CalendarDayStatus.AVAILABLE
                                },
                                day != 20,
                            )
                    }
                CampsiteCalendar(
                    CalendarState(
                        month,
                        days,
                        CalendarSelection.Dates(dates),
                        selectionEnabled = true,
                    ),
                    { month = it },
                    { date ->
                        dates =
                            if (date in
                                dates
                            ) {
                                dates - date
                            } else {
                                dates + date
                            }
                    },
                )
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_search)) {
                DiscoveryToolbar(
                    query,
                    { query = it },
                    map,
                    { map = it },
                    { onAction(filterLabel) },
                )
            }
        }
        item {
            GallerySection(stringResource(R.string.gallery_navigation)) {
                WorkspaceNavigation(
                    listOf(
                        WorkspaceNavigationItem(
                            "explore",
                            stringResource(R.string.gallery_explore),
                            Icons.Default.Search,
                        ),
                        WorkspaceNavigationItem(
                            "lotteries",
                            stringResource(R.string.gallery_lotteries),
                            Icons.Default.Place,
                        ),
                        WorkspaceNavigationItem(
                            "reservations",
                            stringResource(R.string.gallery_reservations),
                            Icons.Default.DateRange,
                        ),
                        WorkspaceNavigationItem(
                            "inbox",
                            stringResource(R.string.gallery_inbox),
                            Icons.Default.Email,
                            "2",
                        ),
                        WorkspaceNavigationItem(
                            "profile",
                            stringResource(R.string.gallery_profile),
                            Icons.Default.Person,
                        ),
                    ),
                    selected,
                    { selected = it },
                )
            }
        }
        item {
            GallerySection(
                stringResource(R.string.gallery_photo_editor),
            ) { GalleryPhotoEditor() }
        }
        item {
            GallerySection(stringResource(R.string.gallery_photos)) {
                RemoteImage(
                    null,
                    stringResource(R.string.gallery_missing_photo),
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    compactFailure = true,
                )
                RemoteImage(
                    "file:///android_asset/missing.jpg",
                    stringResource(R.string.gallery_bad_photo),
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    compactFailure = true,
                )
            }
        }
    }
}

/** Demonstrates local photo selection, retained failed saves and removal without persistence. */
@Composable
private fun GalleryPhotoEditor() {
    val context = LocalContext.current
    val candidate =
        remember(context) {
            SelectedPhoto(
                GALLERY_PHOTO,
                ImageUpload("image/jpeg") {
                    context.assets.open("gallery_campsite.jpg")
                },
            )
        }
    var state by remember {
        mutableStateOf(
            ImageSlotState(
                loadState = ImageLoadState.READY,
                attachment =
                    ImageAttachment(
                        ImageTarget(ImageTargetType.CAMPSITE, "sample"),
                        "sample",
                        GalleryInstant,
                    ),
                displayUrl = GALLERY_PHOTO,
            ),
        )
    }
    ImageSlot(
        state,
        onChoosePhoto = { state = state.copy(candidate = candidate) },
        onSave = {
            state =
                state.copy(
                    failure = ImageFailure.SAVE,
                )
        },
        onDiscard = { state = state.copy(candidate = null, failure = null) },
        onRemove = { state = ImageSlotState(loadState = ImageLoadState.READY) },
        onRetry = { state = state.copy(failure = null) },
        imageDescription = stringResource(R.string.campsite),
        emptyIcon = Icons.Default.Place,
        imageModifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}
