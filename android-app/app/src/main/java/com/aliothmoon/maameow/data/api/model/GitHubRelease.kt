package com.aliothmoon.maameow.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
    val draft: Boolean = false,
) {
    val apkAsset: GitHubAsset? get() = assets.firstOrNull { it.name.endsWith("universal.apk") }
        ?: assets.firstOrNull { it.name == "app-debug.apk" }
}

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    val size: Long = 0,
)
