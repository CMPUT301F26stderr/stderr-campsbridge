package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork

/** Repository dependencies consumed by implemented features, supplied by the selected host. */
interface AppRepositories {
    val accounts: AccountRepository
    val images: ImageRepository
    val pendingWork: PendingWork
}
