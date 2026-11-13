package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GitHubAppVersionCheckerTest {
    private val http = mockk<HttpClientHelper>()
    private val checker = GitHubAppVersionChecker(http, 80)
    private val metadataUrl = "https://github.com/highlights0/Maa_Chess/releases/download/test/output-metadata.json"
    private val apkUrl = "https://github.com/highlights0/Maa_Chess/releases/download/test/app-debug.apk"

    private fun release(tag: String, preview: Boolean = false, metadata: Boolean = true) =
        """{"tag_name":"$tag","body":"changes","prerelease":$preview,"assets":[{"name":"app-debug.apk","browser_download_url":"$apkUrl"}${if (metadata) ",{\"name\":\"output-metadata.json\",\"browser_download_url\":\"$metadataUrl\"}" else ""}]}"""

    private fun respond(url: String, body: String, code: Int = 200) {
        coEvery { http.get(url, any(), any()) } answers {
            Response.Builder().request(Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun metadata(code: Int, packageName: String = BuildConfig.APPLICATION_ID) =
        """{"applicationId":"$packageName","elements":[{"versionCode":$code,"outputFile":"app-debug.apk"}]}"""

    @Test fun checksOurRepositoryAndUsesApkVersionCodeInsteadOfDateTagOrdering() = runBlocking {
        assertTrue(MaaApi.APP_GITHUB_RELEASES_BETA.startsWith("https://api.github.com/repos/highlights0/Maa_Chess/"))
        respond(MaaApi.APP_GITHUB_RELEASES_BETA, "[${release("android-preview-2026-11-13", true)}]")
        respond(metadataUrl, metadata(81))
        val result = checker.check("android-preview-2026-11-13-20-gabcdef0", UpdateChannel.BETA)
        assertEquals("android-preview-2026-11-13", (result as UpdateCheckResult.Available).info.version)
        respond(metadataUrl, metadata(80))
        assertTrue(checker.check("0.0.1", UpdateChannel.BETA) is UpdateCheckResult.UpToDate)
        respond(metadataUrl, metadata(79))
        assertTrue(checker.check("0.0.1", UpdateChannel.BETA) is UpdateCheckResult.UpToDate)
    }

    @Test fun stableChannelSkipsPreviewAndReleasesWithoutAnApk() = runBlocking {
        respond(MaaApi.APP_GITHUB_RELEASES_BETA,
            "[${release("android-preview-2026-11-14", true)}, {\"tag_name\":\"source-only\"}, ${release("v1.0.0")}]")
        respond(metadataUrl, metadata(81))
        val result = checker.check("0.0.1", UpdateChannel.STABLE) as UpdateCheckResult.Available
        assertEquals("v1.0.0", result.info.version)
        respond(MaaApi.APP_GITHUB_RELEASES_BETA, "[${release("android-preview-2026-11-14", true)}]")
        assertTrue(checker.check("0.0.1", UpdateChannel.STABLE) is UpdateCheckResult.UpToDate)
    }

    @Test fun oldPreviewWithoutMetadataDoesNotOfferADowngradeOfTheCurrentLocalBuild() = runBlocking {
        respond(MaaApi.APP_GITHUB_RELEASES_BETA, "[${release("android-preview-2026-11-13", true, false)}]")
        assertTrue(checker.check("android-preview-2026-11-13-8-g2f07954", UpdateChannel.BETA) is UpdateCheckResult.UpToDate)
    }

    @Test fun failedApiOrMismatchedMetadataDoNotBecomeUpdatePrompts() = runBlocking {
        respond(MaaApi.APP_GITHUB_RELEASES_BETA, "{}", 403)
        assertTrue(checker.check("1.0.0", UpdateChannel.BETA) is UpdateCheckResult.Error)
        respond(MaaApi.APP_GITHUB_RELEASES_BETA, "[${release("v1.1.0")}]")
        respond(metadataUrl, metadata(99, "other.app"))
        assertTrue(checker.check("1.0.0", UpdateChannel.BETA) is UpdateCheckResult.Error)
    }

    @Test fun cancellationIsNotConvertedIntoAnUpdateFailure() {
        coEvery { http.get(any(), any(), any()) } throws CancellationException("stop")
        assertThrows(CancellationException::class.java) {
            runBlocking { checker.check("1.0.0", UpdateChannel.BETA) }
        }
    }

    @Test fun downloadsOurApkUsingTheExactPreviewTagAndStillAcceptsSemanticVersions() = runBlocking {
        val resolver = GitHubAppDownloadUrlResolver(http)
        respond(MaaApi.appGitHubReleaseByTag("android-preview-2026-11-13"), release("android-preview-2026-11-13", true))
        assertEquals(apkUrl, resolver.resolve("android-preview-2026-11-13", UpdateChannel.BETA).getOrThrow())
        respond(MaaApi.appGitHubReleaseByTag("v1.0.0"), release("v1.0.0"))
        assertEquals(apkUrl, resolver.resolve("1.0.0", UpdateChannel.STABLE).getOrThrow())
        coVerify(exactly = 0) { http.get(match { it.contains("/tags/vandroid-preview-") }, any(), any()) }
    }
}
