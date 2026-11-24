package update

/** Настройки автообновления. */
object UpdateConfig {
    /** Показывать окно обновления только при подключении к Wi-Fi. */
    const val REQUIRE_WIFI = true

    /** Подходящий файл установщика среди файлов релиза: ScoreKeeper-Setup-1.3.0.exe. */
    val installerAsset = Regex("""^ScoreKeeper-Setup.*\.exe$""", RegexOption.IGNORE_CASE)

    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 8_000

    /** `-Dscorekeeper.update.force=true` включает проверку обновлений и вне установленной копии (для отладки). */
    const val FORCE_PROPERTY = "scorekeeper.update.force"
}
