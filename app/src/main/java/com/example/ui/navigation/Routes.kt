package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object BrowseRoute

@Serializable
data object LinksRoute

@Serializable
data object HotspotRoute

enum class LibraryFilter(val label: String) {
    ALL("All"),
    DEVICE("Device"),
    LINKS("Web Links"),
    HISTORY("History"),
    SAVED("Saved")
}

enum class PlayerSheetState {
    HIDDEN,
    MINI,
    EXPANDED
}
