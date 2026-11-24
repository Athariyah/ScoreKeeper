package update

import java.util.Properties

/**
 * Сведения о сборке. Версию и репозиторий GitHub подставляет Gradle в `app.properties`
 * (`-PappVersion=1.3.0 -PgithubRepo=owner/repo`; на GitHub Actions это делает воркфлоу релиза).
 */
object AppInfo {
    private val props: Properties by lazy {
        Properties().also { p ->
            AppInfo::class.java.getResourceAsStream("/app.properties")?.use { p.load(it) }
        }
    }

    /** Версия приложения, например «1.2.0». «0.0.0» — сборка не через Gradle. */
    val version: String get() = clean(props.getProperty("version")) ?: "0.0.0"

    /** Репозиторий с релизами в виде «владелец/репозиторий» или null, если он не задан. */
    val repository: String? get() = clean(props.getProperty("repo"))

    // Если Gradle не обработал файл, в нём остаётся «${appVersion}» — считаем значение пустым.
    private fun clean(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("\${") }
}
