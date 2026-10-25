package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository

/** Repository dependencies consumed by implemented features, supplied by the selected host. */
interface AppRepositories {
    val accounts: AccountRepository
    val campsites: CampsiteRepository
    val lotteries: LotteryRepository
    val reviews: ReviewRepository
    val images: ImageRepository
    val pendingWork: PendingWork
}
