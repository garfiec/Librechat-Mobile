package com.garfiec.librechat.core.network.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One GitHub release, as `GET /repos/{owner}/{repo}/releases` lists it, trimmed to what the app reads. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
