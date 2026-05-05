package com.kavach.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.kavach.app.data.local.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin ViewModel that exposes SessionDataStore to KavachNavHost. */
@HiltViewModel
class NavHostViewModel @Inject constructor(
    val sessionDataStore: SessionDataStore
) : ViewModel()
