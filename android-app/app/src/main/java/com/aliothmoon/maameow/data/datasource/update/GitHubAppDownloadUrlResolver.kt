package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.model.GitHubRelease
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.domain.service.update.resolver.AppDownloadUrlResolver
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class GitHubAppDownloadUrlResolver(
    private val httpClient: HttpClientHelper
) : AppDownloadUrlResolver {

    private val json = JsonUtils.common

    override suspend fun resolve(version: String, channel: UpdateChannel): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tag = if (version.firstOrNull()?.isDigit() == true) "v$version" else version
                httpClient.get(MaaApi.appGitHubReleaseByTag(tag)).use { response ->
                    if (!response.isSuccessful) {
                        throw LocalizedException(
                            uiTextOf(R.string.update_error_github_api_failed, response.code)
                        )
                    }

                    val release = json.decodeFromString<GitHubRelease>(response.body.string())

                    val apkAsset = release.apkAsset
                        ?: throw LocalizedException(uiTextOf(R.string.update_error_github_no_apk))

                    apkAsset.browserDownloadUrl
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Timber.e(e, "GitHub 获取 Release 失败: $version")
            }
        }
}
