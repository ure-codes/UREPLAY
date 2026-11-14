package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class VideoSourceType {
    DEVICE,
    WEB_LINK,
    HOTSPOT
}
