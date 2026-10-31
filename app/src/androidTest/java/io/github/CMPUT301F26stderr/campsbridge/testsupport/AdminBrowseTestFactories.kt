package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory

/** Keeps this factory contract available until the feature is implemented. */
internal fun TestAppHost.adminBrowseFactory(): () -> ViewModelProvider.Factory =
    {
        viewModelFactory {}
    }
