package update

/** Файл релиза GitHub. */
data class ReleaseAsset(val name: String, val url: String, val size: Long = 0, val digest: String? = null)

/** Релиз GitHub (только то, что нужно для обновления). */
data class ReleaseData(
    val tag: String,
    val pageUrl: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** Что нужно скачать и поставить. */
data class UpdateInfo(
    val version: String,
    val tag: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** SHA-256 установщика в нижнем регистре или null, если сумма не опубликована. */
    val sha256: String?,
    val pageUrl: String?,
    /** Адрес файла «…exe.sha256» среди файлов релиза — на случай, если GitHub не прислал digest. */
    val checksumUrl: String? = null,
)

/** Решает, годится ли релиз как обновление. Чистая логика без сети — проверяется тестами. */
object ReleaseSelector {

    fun select(release: ReleaseData, repository: String, currentVersion: String): UpdateInfo? {
        if (release.draft || release.prerelease) return null
        if (!Versions.isNewer(release.tag, currentVersion)) return null

        val installer = release.assets.firstOrNull {
            UpdateConfig.installerAsset.matches(it.name) && isTrustedUrl(it.url, repository)
        } ?: return null   // релиз опубликован, а установщик ещё не загружен — проверим в следующий запуск

        val checksum = release.assets.firstOrNull {
            it.name.equals(installer.name + ".sha256", ignoreCase = true) && isTrustedUrl(it.url, repository)
        }
        val digest = installer.digest
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.lowercase()
            ?.takeIf { SHA256.matches(it) }

        return UpdateInfo(
            version = Versions.parse(release.tag)!!.joinToString("."),
            tag = release.tag,
            assetName = installer.name,
            downloadUrl = installer.url,
            sizeBytes = installer.size,
            sha256 = digest,
            pageUrl = release.pageUrl,
            checksumUrl = if (digest == null) checksum?.url else null,
        )
    }

    /** Скачиваем только с github.com из релизов именно этого репозитория. */
    fun isTrustedUrl(url: String, repository: String): Boolean =
        url.startsWith("https://github.com/$repository/releases/download/", ignoreCase = true)

    /** Достаёт сумму из «sha256sum»-файла: «<64 hex>  имя». */
    fun parseChecksum(text: String): String? =
        text.trim().split(Regex("""\s+""")).firstOrNull()?.lowercase()?.takeIf { SHA256.matches(it) }

    private val SHA256 = Regex("^[0-9a-f]{64}$")
}
