package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.model.GitHubRelease
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.domain.service.update.checker.AppVersionChecker
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class GitHubAppVersionChecker(
    private val httpClient: HttpClientHelper,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : AppVersionChecker {
    override suspend fun check(current: String, channel: UpdateChannel): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val releases = JsonUtils.common.decodeFromString<List<GitHubRelease>>(read(MaaApi.APP_GITHUB_RELEASES_BETA))
            val release = releases.firstOrNull { !it.draft &&
                (channel == UpdateChannel.BETA || !it.prerelease) && it.apkAsset != null }
                ?: return@withContext UpdateCheckResult.UpToDate(current)
            val metadata = release.assets.firstOrNull { it.name == "output-metadata.json" }
            val newer = if (metadata != null) {
                val output = JsonUtils.common.parseToJsonElement(read(metadata.browserDownloadUrl)).jsonObject
                check(output["applicationId"]?.jsonPrimitive?.content == BuildConfig.APPLICATION_ID) { "Release metadata package mismatch" }
                val apk = output.getValue("elements").jsonArray.single {
                    it.jsonObject["outputFile"]?.jsonPrimitive?.content == release.apkAsset!!.name
                }.jsonObject
                apk.getValue("versionCode").jsonPrimitive.int > currentVersionCode
            } else {
                // Older releases have no APK metadata; retain tag comparison for those only.
                AppDownloader.compareVersions(current, release.tagName) < 0
            }
            Timber.i("Maa_Chess update: tag=%s channel=%s newer=%s metadata=%s", release.tagName, channel, newer, metadata != null)
            if (newer) UpdateCheckResult.Available(UpdateInfo(release.tagName, release.body))
            else UpdateCheckResult.UpToDate(current)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Maa_Chess GitHub update check failed")
            UpdateCheckResult.Error(if (error is LocalizedException) UpdateError.UnknownError(error.uiText)
                else UpdateError.NetworkError(error.message))
        }
    }

    private suspend fun read(url: String): String = httpClient.get(url).use { response ->
        if (!response.isSuccessful) throw LocalizedException(uiTextOf(R.string.update_error_github_api_failed, response.code))
        response.body.string()
    }
}
