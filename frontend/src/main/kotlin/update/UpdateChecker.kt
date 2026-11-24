package update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
    val digest: String? = null,
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val page: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

/** Спрашивает у GitHub последний опубликованный релиз и решает, есть ли обновление. */
object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    /** Возвращает обновление, если на GitHub лежит более новая версия с установщиком; иначе null. */
    fun check(repository: String, currentVersion: String): UpdateInfo? {
        // /releases/latest не отдаёт черновики и пре-релизы.
        val body = get(
            "https://api.github.com/repos/$repository/releases/latest",
            currentVersion, accept = "application/vnd.github+json",
        ) ?: return null

        val release = json.decodeFromString<GhRelease>(body)
        val data = ReleaseData(
            tag = release.tag,
            pageUrl = release.page,
            draft = release.draft,
            prerelease = release.prerelease,
            assets = release.assets.map { ReleaseAsset(it.name, it.url, it.size, it.digest) },
        )
        var info = ReleaseSelector.select(data, repository, currentVersion) ?: return null

        // GitHub обычно сам присылает SHA-256 файла (digest); если нет — берём файл «….exe.sha256» из релиза.
        val checksumUrl = info.checksumUrl
        if (info.sha256 == null && checksumUrl != null) {
            val text = get(checksumUrl, currentVersion, accept = "*/*")
            info = info.copy(sha256 = text?.let(ReleaseSelector::parseChecksum), checksumUrl = null)
        }
        return info
    }

    private fun get(url: String, appVersion: String, accept: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = UpdateConfig.CONNECT_TIMEOUT_MS
            connection.readTimeout = UpdateConfig.READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "ScoreKeeper/$appVersion")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.use { it.readNBytes(2_000_000) }.toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }
}
